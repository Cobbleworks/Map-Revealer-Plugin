package com.github.maprevealer.util;

import com.github.maprevealer.MapRevealerPlugin;
import org.bukkit.Chunk;
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

    public MapRenderService(MapRevealerPlugin plugin) {
        this.plugin = plugin;
    }

    public void render(MapView map, World world, Integer depth, ColorScheme scheme,
                       Consumer<RenderResult> callback) {
        if (stopped) {
            callback.accept(RenderResult.failure("Map rendering is shutting down."));
            return;
        }
        int maxPending = Math.max(1, plugin.getConfig().getInt("rendering.max-pending-maps", 256));
        if (queue.size() >= maxPending) {
            callback.accept(RenderResult.failure("The map render queue is full."));
            return;
        }
        queue.add(new Request(map, world, depth, scheme == null ? ColorScheme.NORMAL : scheme, callback));
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
        renderStrip(request, centerX, centerZ, scale, minHeight, depth,
                new MapRevealer.TerrainSamples(), new LinkedHashSet<>(), keys.size(), 0);
    }

    public Integer effectiveDepth(World world, Integer depth) {
        if (depth != null) return depth;
        if (world.getEnvironment() != World.Environment.NETHER) return null;
        return Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1,
                plugin.getConfig().getInt("rendering.nether-depth", 64)));
    }

    private void renderStrip(Request request, int centerX, int centerZ, int scale, int minHeight,
                             Integer depth, MapRevealer.TerrainSamples samples, Set<Long> captured,
                             int requestedChunks, int firstRow) {
        if (stopped) return;
        int endRow = Math.min(MAP_SIZE, firstRow + Math.max(1, 16 / scale));
        Set<Long> keys = requiredChunks(centerX, centerZ, scale, firstRow, endRow);
        capture(request.world(), keys).whenComplete((snapshots, throwable) -> {
            if (stopped) return;
            if (throwable != null) {
                runSync(() -> finish(request, RenderResult.failure("Could not load terrain: " + throwable.getMessage())));
                return;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    MapRevealer.sampleRows(new SnapshotTerrain(snapshots, minHeight),
                            centerX, centerZ, scale, depth, samples, firstRow, endRow);
                    captured.addAll(snapshots.keySet());
                    if (endRow < MAP_SIZE) {
                        runSync(() -> renderStrip(request, centerX, centerZ, scale, minHeight,
                                depth, samples, captured, requestedChunks, endRow));
                    } else {
                        byte[] pixels = MapRevealer.colorPixels(samples, request.scheme());
                        String hash = hash(pixels);
                        runSync(() -> apply(request, pixels, hash, captured.size(), requestedChunks));
                    }
                } catch (RuntimeException exception) {
                    runSync(() -> finish(request, RenderResult.failure("Could not process terrain: " + exception.getMessage())));
                }
            });
        });
    }

    private CompletableFuture<Map<Long, ChunkSnapshot>> capture(World world, Set<Long> keys) {
        CompletableFuture<Map<Long, ChunkSnapshot>> result = new CompletableFuture<>();
        captureBatch(world, new ArrayList<>(keys), 0, new HashMap<>(), result);
        return result;
    }

    private void captureBatch(World world, List<Long> keys, int start, Map<Long, ChunkSnapshot> snapshots,
                              CompletableFuture<Map<Long, ChunkSnapshot>> result) {
        if (stopped) return;
        boolean generate = plugin.getConfig().getBoolean("rendering.generate-missing-chunks", false);
        int end = Math.min(keys.size(), start + Math.max(1,
                plugin.getConfig().getInt("rendering.capture-chunks-per-tick", 16)));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (long key : keys.subList(start, end)) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (!generate && !world.isChunkGenerated(chunkX, chunkZ)) continue;
            CompletableFuture<Chunk> chunkFuture = world.isChunkLoaded(chunkX, chunkZ)
                    ? CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ))
                    : world.getChunkAtAsync(chunkX, chunkZ, generate);
            CompletableFuture<Void> snapshotFuture = new CompletableFuture<>();
            chunkFuture.whenComplete((chunk, failure) -> runSync(() -> {
                if (failure != null) { snapshotFuture.completeExceptionally(failure); return; }
                try {
                    if (chunk == null) throw new IllegalStateException("Chunk " + chunkX + "," + chunkZ + " could not be loaded.");
                    snapshots.put(key, chunk.getChunkSnapshot(true, false, false));
                    snapshotFuture.complete(null);
                } catch (RuntimeException exception) { snapshotFuture.completeExceptionally(exception); }
            }));
            futures.add(snapshotFuture);
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else if (end == keys.size()) result.complete(snapshots);
            else runSync(() -> captureBatch(world, keys, end, snapshots, result));
        });
    }

    private void apply(Request request, byte[] pixels, String hash, int capturedChunks, int requestedChunks) {
        try {
            int changed = MapDataAccess.write(request.map(), pixels);
            finish(request, new RenderResult(true, changed, hash, capturedChunks, requestedChunks, null));
        } catch (RuntimeException exception) {
            finish(request, RenderResult.failure(exception.getMessage()));
        }
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
        if (!stopped) plugin.getServer().getScheduler().runTask(plugin, () -> {
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
                           Consumer<RenderResult> callback) { }

    public record RenderResult(boolean success, int changedPixels, String hash,
                               int capturedChunks, int requestedChunks, String error) {
        public static RenderResult failure(String error) {
            return new RenderResult(false, 0, null, 0, 0, error);
        }
    }
}
