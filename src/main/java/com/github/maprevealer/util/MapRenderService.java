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
        int maximum = Math.max(64, plugin.getConfig().getInt("rendering.max-chunks-per-map", 4096));
        if (keys.size() > maximum) {
            finish(request, RenderResult.failure("This map needs " + keys.size()
                    + " terrain chunks, above the configured limit of " + maximum + "."));
            return;
        }

        capture(request.world(), keys).whenComplete((snapshots, throwable) -> {
            if (throwable != null) {
                runSync(() -> finish(request, RenderResult.failure("Could not load terrain: " + throwable.getMessage())));
                return;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    byte[] pixels = MapRevealer.renderPixels(new SnapshotTerrain(snapshots, minHeight),
                            centerX, centerZ, scale, request.depth(), request.scheme());
                    String hash = hash(pixels);
                    runSync(() -> apply(request, pixels, hash, snapshots.size(), keys.size()));
                } catch (RuntimeException exception) {
                    runSync(() -> finish(request, RenderResult.failure("Could not process terrain: " + exception.getMessage())));
                }
            });
        });
    }

    private CompletableFuture<Map<Long, ChunkSnapshot>> capture(World world, Set<Long> keys) {
        boolean generate = plugin.getConfig().getBoolean("rendering.generate-missing-chunks", false);
        List<CompletableFuture<SnapshotEntry>> futures = new ArrayList<>();
        for (long key : keys) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (!generate && !world.isChunkGenerated(chunkX, chunkZ)) continue;
            CompletableFuture<Chunk> chunkFuture = world.isChunkLoaded(chunkX, chunkZ)
                    ? CompletableFuture.completedFuture(world.getChunkAt(chunkX, chunkZ))
                    : world.getChunkAtAsync(chunkX, chunkZ, generate);
            futures.add(chunkFuture.thenApply(chunk -> new SnapshotEntry(key,
                    chunk.getChunkSnapshot(true, false, false))));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
            for (CompletableFuture<SnapshotEntry> future : futures) {
                SnapshotEntry entry = future.join();
                snapshots.put(entry.key(), entry.snapshot());
            }
            return snapshots;
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
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    public void shutdown() {
        stopped = true;
        queue.clear();
    }

    static Set<Long> requiredChunks(int centerX, int centerZ, int scale) {
        int half = (MAP_SIZE / 2) * scale;
        Set<Long> keys = new LinkedHashSet<>();
        for (int pixelX = 0; pixelX < MAP_SIZE; pixelX++) {
            int worldX = centerX - half + pixelX * scale + scale / 2;
            for (int pixelZ = 0; pixelZ < MAP_SIZE; pixelZ++) {
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
    private record SnapshotEntry(long key, ChunkSnapshot snapshot) { }

    public record RenderResult(boolean success, int changedPixels, String hash,
                               int capturedChunks, int requestedChunks, String error) {
        public static RenderResult failure(String error) {
            return new RenderResult(false, 0, null, 0, 0, error);
        }
    }
}
