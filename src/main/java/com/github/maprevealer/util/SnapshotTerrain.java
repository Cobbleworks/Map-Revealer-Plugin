package com.github.maprevealer.util;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

import java.util.Map;

/** Immutable terrain view that is safe to sample away from the server thread. */
public final class SnapshotTerrain implements MapRevealer.TerrainReader {
    private final Map<Long, ChunkSnapshot> chunks;
    private final int minHeight;

    public SnapshotTerrain(Map<Long, ChunkSnapshot> chunks, int minHeight) {
        this.chunks = Map.copyOf(chunks);
        this.minHeight = minHeight;
    }

    @Override
    public boolean isAvailable(int worldX, int worldZ) {
        return chunks.containsKey(key(worldX >> 4, worldZ >> 4));
    }

    @Override
    public Material getBlockType(int worldX, int y, int worldZ) {
        ChunkSnapshot snapshot = chunks.get(key(worldX >> 4, worldZ >> 4));
        if (snapshot == null) return Material.AIR;
        return snapshot.getBlockType(Math.floorMod(worldX, 16), y, Math.floorMod(worldZ, 16));
    }

    @Override
    public int getHighestBlockYAt(int worldX, int worldZ) {
        ChunkSnapshot snapshot = chunks.get(key(worldX >> 4, worldZ >> 4));
        if (snapshot == null) return minHeight;
        return snapshot.getHighestBlockYAt(Math.floorMod(worldX, 16), Math.floorMod(worldZ, 16));
    }

    @Override
    public int getMinHeight() {
        return minHeight;
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
