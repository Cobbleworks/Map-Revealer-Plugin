package com.github.maprevealer.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapRenderServiceTest {
    @Test
    void scaleZeroMapRequestsEightByEightChunks() {
        assertEquals(64, MapRenderService.requiredChunks(0, 0, 1).size());
    }

    @Test
    void scaleOneMapRequestsSixteenBySixteenChunks() {
        assertEquals(256, MapRenderService.requiredChunks(0, 0, 2).size());
    }

    @Test
    void flatStoneTerrainProducesACompleteStableBuffer() {
        MapRevealer.TerrainReader terrain = new MapRevealer.TerrainReader() {
            @Override public Material getBlockType(int worldX, int y, int worldZ) {
                return y == 64 ? Material.STONE : Material.AIR;
            }
            @Override public int getHighestBlockYAt(int worldX, int worldZ) { return 64; }
            @Override public int getMinHeight() { return -64; }
        };

        byte[] pixels = MapRevealer.renderPixels(terrain, 0, 0, 1, null, ColorScheme.NORMAL);

        assertEquals(128 * 128, pixels.length);
        assertTrue(java.util.Arrays.stream(toInts(pixels)).allMatch(color -> color == (11 * 4 + 2)));
    }

    @Test
    void mapWriterDirtiesOnlyPixelsWhoseColorsChanged() {
        FakeMapData map = new FakeMapData();
        byte[] replacement = new byte[128 * 128];
        replacement[10] = 5;
        replacement[20] = 7;

        assertEquals(2, MapDataAccess.writeBuffer(map, replacement));
        assertEquals(List.of("10,0", "20,0"), map.dirtyPixels);
        assertEquals(0, MapDataAccess.writeBuffer(map, replacement));
        assertEquals(2, map.dirtyPixels.size());
    }

    private static final class FakeMapData {
        private final byte[] colors = new byte[128 * 128];
        private final List<String> dirtyPixels = new ArrayList<>();

        @SuppressWarnings("unused")
        private void setColorsDirty(int x, int z) {
            dirtyPixels.add(x + "," + z);
        }
    }

    private static int[] toInts(byte[] bytes) {
        int[] values = new int[bytes.length];
        for (int index = 0; index < bytes.length; index++) values[index] = Byte.toUnsignedInt(bytes[index]);
        return values;
    }
}
