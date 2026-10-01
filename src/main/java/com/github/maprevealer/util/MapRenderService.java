package com.github.maprevealer.util;

import com.github.maprevealer.MapRevealerPlugin;
import org.bukkit.Chunk;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.map.MapView;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Serializes map renders, loads terrain chunks asynchronously, processes
 * immutable snapshots off-thread, and applies pixels on the server thread.
 */
public final class MapRenderService {
    private static final int MAP_SIZE = 128;
    private final MapRevealerPlugin plugin;
    private final Queue<Request> queue = new ArrayDeque<>();
    private boolean rendering;
    private boolean stopped;
    private int captureTick = -1;
    private int snapshotsThisTick;
    private int lookupsThisTick;

    public MapRenderService(MapRevealerPlugin plugin) {
        this.plugin = plugin;
    }

    public void render(MapView map, World world, Integer depth, ColorScheme scheme,
                       Consumer<RenderResult> callback) {
        render(map, world, depth, scheme, callback, rows -> { });
    }

    public void render(MapView map, World world, Integer depth, ColorScheme scheme,
                       Consumer<RenderResult> callback, Consumer<Integer> progress) {
        if (stopped) {
            callback.accept(RenderResult.failure("Map rendering is shutting down."));
            return;
        }
        int maxPending = Math.max(1, plugin.getConfig().getInt("rendering.max-pending-maps", 256));
        if (queue.size() >= maxPending) {
            callback.accept(RenderResult.failure("The map render queue is full."));
            return;
        }
        queue.add(new Request(map, world, depth, scheme == null ? ColorScheme.NORMAL : scheme, callback, progress));
        startNext();
    }

    private void startNext() {
        if (rendering || stopped) return;
        Request request = queue.poll();
        if (request == null) return;
        rendering = true;

        int centerX = request.map().getCenterX();
        int centerZ = request.map().getCenterZ();
        int scale = 1 << request.map().getScale().getValue();
        int minHeight = request.world().getMinHeight();
        Set<Long> keys = requiredChunks(centerX, centerZ, scale);
        int maximum = Math.max(64, plugin.getConfig().getInt("rendering.max-chunks-per-map", 16384));
        if (keys.size() > maximum) {
            finish(request, RenderResult.failure("This map needs " + keys.size()
                    + " terrain chunks, above the configured limit of " + maximum + "."));
            return;
        }

        Integer depth = effectiveDepth(request.world(), request.depth());
        try {
            renderStrip(request, centerX, centerZ, scale, minHeight, depth,
                    new MapRevealer.TerrainSamples(), new LinkedHashSet<>(), keys.size(), 0,
                    MapDataAccess.read(request.map()), 0);
        } catch (RuntimeException exception) {
            finish(request, RenderResult.failure(exception.getMessage()));
        }
    }

    public Integer effectiveDepth(World world, Integer depth) {
        if (depth != null) return depth;
        if (world.getEnvironment() != World.Environment.NETHER) return null;
        return Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1,
                plugin.getConfig().getInt("rendering.nether-depth", 64)));
    }

    private void renderStrip(Request request, int centerX, int centerZ, int scale, int minHeight,
                             Integer depth, MapRevealer.TerrainSamples samples, Set<Long> captured,
                             int requestedChunks, int firstRow, byte[] buffer, int changedPixels) {
        if (stopped) return;
        int endRow = Math.min(MAP_SIZE, firstRow + Math.max(1, 32 / scale));
        Set<Long> keys = requiredChunks(centerX, centerZ, scale, firstRow, endRow);
        capture(request.world(), keys).whenComplete((snapshots, throwable) -> {
            if (stopped) return;
            if (throwable != null) {
                runSync(() -> finish(request, RenderResult.failure("Could not load terrain: " + throwable.getMessage())));
                return;
            }
            Runnable process = () -> {
                try {
                    MapRevealer.sampleRows(new SnapshotTerrain(snapshots, minHeight),
                            centerX, centerZ, scale, depth, samples, firstRow, endRow);
                    captured.addAll(snapshots.keySet());
                    boolean publish = endRow % 8 == 0 || endRow == MAP_SIZE;
                    if (publish) {
                        byte[] pixels = MapRevealer.colorPixels(samples, request.scheme());
                        System.arraycopy(pixels, 0, buffer, 0, endRow * MAP_SIZE);
                    }
                    runSync(() -> {
                        try {
                            int changed = changedPixels;
                            if (publish) {
                                changed += MapDataAccess.write(request.map(), buffer);
                                request.progress().accept(endRow);
                            }
                            if (endRow < MAP_SIZE) renderStrip(request, centerX, centerZ, scale, minHeight,
                                    depth, samples, captured, requestedChunks, endRow, buffer, changed);
                            else finish(request, new RenderResult(true, changed, hash(buffer),
                                    captured.size(), requestedChunks, null));
                        } catch (RuntimeException exception) {
                            finish(request, RenderResult.failure(exception.getMessage()));
                        }
                    });
                } catch (RuntimeException exception) {
                    runSync(() -> finish(request, RenderResult.failure("Could not process terrain: " + exception.getMessage())));
                }
            };
            // An absent strip has no terrain work. Avoid an async/sync round trip for
            // every empty row in a large wall; lookup and capture budgets still apply.
            if (snapshots.isEmpty()) process.run();
            else plugin.getServer().getScheduler().runTaskAsynchronously(plugin, process);
        });
    }

    private CompletableFuture<Map<Long, ChunkSnapshot>> capture(World world, Set<Long> keys) {
        CompletableFuture<Map<Long, ChunkSnapshot>> result = new CompletableFuture<>();
        captureBatch(world, new ArrayList<>(keys), 0, new HashMap<>(), result);
        return result;
    }

    private void resetCaptureBudget() {
        int tick = Bukkit.getCurrentTick();
        if (captureTick != tick) {
            captureTick = tick;
            snapshotsThisTick = 0;
            lookupsThisTick = 0;
        }
    }

    private void captureBatch(World world, List<Long> keys, int start, Map<Long, ChunkSnapshot> snapshots,
                              CompletableFuture<Map<Long, ChunkSnapshot>> result) {
        if (stopped) return;
        resetCaptureBudget();
        int captureLimit = Math.max(1, plugin.getConfig().getInt("rendering.capture-chunks-per-tick", 16));
        int lookupLimit = Math.max(captureLimit, plugin.getConfig().getInt("rendering.lookup-chunks-per-tick", 256));
        if (lookupsThisTick >= lookupLimit) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> captureBatch(world, keys, start, snapshots, result), 1L);
            return;
        }
        boolean generate = plugin.getConfig().getBoolean("rendering.generate-missing-chunks", false);
        int batchSize = generate ? Math.min(captureLimit, lookupLimit - lookupsThisTick) : lookupLimit - lookupsThisTick;
        int end = Math.min(keys.size(), start + batchSize);
        lookupsThisTick += end - start;
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        try {
            for (long key : keys.subList(start, end)) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                // isChunkGenerated blocks on disk I/O on Paper. Non-generating async
                // loads return null for missing terrain without blocking a server tick.
                futures.add(world.isChunkLoaded(chunkX, chunkZ)
                        ? CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ))
                        : world.getChunkAtAsync(chunkX, chunkZ, generate));
            }
        } catch (RuntimeException exception) { result.completeExceptionally(exception); return; }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> runSync(() -> {
            if (failure != null) result.completeExceptionally(failure);
            else captureSnapshots(world, keys, start, end, snapshots, result, futures, 0, generate);
        }));
    }

    private void captureSnapshots(World world, List<Long> keys, int start, int end,
                                  Map<Long, ChunkSnapshot> snapshots, CompletableFuture<Map<Long, ChunkSnapshot>> result,
                                  List<CompletableFuture<Chunk>> futures, int first, boolean generate) {
        if (stopped) return;
        resetCaptureBudget();
        int captureLimit = Math.max(1, plugin.getConfig().getInt("rendering.capture-chunks-per-tick", 16));
        for (int index = first; index < futures.size(); index++) {
            Chunk chunk = futures.get(index).join(); // allOf has already completed; never blocks.
            if (chunk == null) {
                if (generate) { result.completeExceptionally(new IllegalStateException("Terrain could not be generated.")); return; }
                continue;
            }
            if (snapshotsThisTick >= captureLimit) {
                int next = index;
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> captureSnapshots(world, keys, start, end, snapshots, result, futures, next, generate), 1L);
                return;
            }
            try { snapshots.put(keys.get(start + index), chunk.getChunkSnapshot(true, false, false)); }
            catch (RuntimeException exception) { result.completeExceptionally(exception); return; }
            snapshotsThisTick++;
        }
        if (end == keys.size()) result.complete(snapshots);
        else captureBatch(world, keys, end, snapshots, result);
    }

    private void finish(Request request, RenderResult result) {
        try {
            request.callback().accept(result);
        } finally {
            rendering = false;
            startNext();
        }
    }

    private void runSync(Runnable runnable) {
        if (stopped) return;
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!stopped) runnable.run();
        });
    }

    public void shutdown() {
        stopped = true;
        queue.clear();
    }

    static Set<Long> requiredChunks(int centerX, int centerZ, int scale) {
        return requiredChunks(centerX, centerZ, scale, 0, MAP_SIZE);
    }

    static Set<Long> requiredChunks(int centerX, int centerZ, int scale, int firstRow, int endRow) {
        int half = (MAP_SIZE / 2) * scale;
        Set<Long> keys = new LinkedHashSet<>();
        for (int pixelX = 0; pixelX < MAP_SIZE; pixelX++) {
            int worldX = centerX - half + pixelX * scale + scale / 2;
            for (int pixelZ = firstRow; pixelZ < endRow; pixelZ++) {
                int worldZ = centerZ - half + pixelZ * scale + scale / 2;
                keys.add(SnapshotTerrain.key(worldX >> 4, worldZ >> 4));
            }
        }
        return keys;
    }

    private static String hash(byte[] pixels) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pixels);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Request(MapView map, World world, Integer depth, ColorScheme scheme,
                           Consumer<RenderResult> callback, Consumer<Integer> progress) { }

    public record RenderResult(boolean success, int changedPixels, String hash,
                               int capturedChunks, int requestedChunks, String error) {
        public static RenderResult failure(String error) {
            return new RenderResult(false, 0, null, 0, 0, error);
        }
    }
}
