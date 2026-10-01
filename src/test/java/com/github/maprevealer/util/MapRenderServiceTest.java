package com.github.maprevealer.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapRenderServiceTest {
    @Test
    void missingTerrainAtExplicitDepthIsTransparentWithoutScanningAirColumns() {
        MapRevealer.TerrainReader terrain = new MapRevealer.TerrainReader() {
            @Override public boolean isAvailable(int x, int z) { return false; }
            @Override public Material getBlockType(int x, int y, int z) { throw new AssertionError("Missing chunk was read"); }
            @Override public int getHighestBlockYAt(int x, int z) { throw new AssertionError("Missing chunk was read"); }
            @Override public int getMinHeight() { return 0; }
        };
        assertArrayEquals(new byte[16384], MapRevealer.renderPixels(terrain, 0, 0, 16, 64, ColorScheme.NORMAL));
    }
    @Test
    void scaleZeroMapRequestsEightByEightChunks() {
        assertEquals(64, MapRenderService.requiredChunks(0, 0, 1).size());
    }

    @Test
    void scaleOneMapRequestsSixteenBySixteenChunks() {
        assertEquals(256, MapRenderService.requiredChunks(0, 0, 2).size());
    }

    @Test
    void allFiveScalesCaptureEverySampleIncludingNegativeAndUnalignedCenters() {
        for (int level = 0; level <= 4; level++) {
            int scale = 1 << level;
            var all = MapRenderService.requiredChunks(-17, 23, scale);
            var strips = new java.util.HashSet<Long>();
            int rows = Math.max(1, 32 / scale);
            for (int z = 0; z < 128; z += rows) {
                var strip = MapRenderService.requiredChunks(-17, 23, scale, z, Math.min(128, z + rows));
                assertTrue(strip.size() <= 256, "Snapshot strips must remain bounded at scale " + level);
                strips.addAll(strip);
            }
            assertEquals(all, strips);
            assertTrue(all.size() <= 16384);
        }
        assertEquals(16384, MapRenderService.requiredChunks(0, 0, 16).size());
    }

    @Test
    void endIslandRendersBesideVoidWithoutReadingBelowWorldBounds() {
        MapRevealer.TerrainReader terrain = new MapRevealer.TerrainReader() {
            @Override public Material getBlockType(int x, int y, int z) {
                if (y < 0) throw new IllegalArgumentException("Out of world bounds");
                return x >= 0 && y == 64 ? Material.END_STONE : Material.AIR;
            }
            @Override public int getHighestBlockYAt(int x, int z) { return x < 0 ? -1 : 64; }
            @Override public int getMinHeight() { return 0; }
        };
        for (ColorScheme scheme : ColorScheme.values()) {
            byte[] pixels = MapRevealer.renderPixels(terrain, 0, 0, 1, null, scheme);
            assertEquals(0, pixels[0]);
            assertTrue(Byte.toUnsignedInt(pixels[127]) >= 4);
        }
    }

    @Test
    void netherSliceShowsLavaBelowBedrockRoof() {
        MapRevealer.TerrainReader terrain = new MapRevealer.TerrainReader() {
            @Override public Material getBlockType(int x, int y, int z) {
                return y >= 123 ? Material.BEDROCK : y == 32 ? Material.LAVA : Material.AIR;
            }
            @Override public int getHighestBlockYAt(int x, int z) { return 127; }
            @Override public int getMinHeight() { return 0; }
        };
        byte[] slice = MapRevealer.renderPixels(terrain, 0, 0, 1, 64, ColorScheme.NORMAL);
        byte[] roof = MapRevealer.renderPixels(terrain, 0, 0, 1, null, ColorScheme.NORMAL);
        assertTrue(slice[0] != roof[0]);
        assertTrue(java.util.Arrays.stream(toInts(slice)).allMatch(color -> color >= 4));
    }

    @Test
    void streamedSamplingMatchesCompleteRenderAcrossLargeWall() {
        MapRevealer.TerrainReader terrain = new MapRevealer.TerrainReader() {
            @Override public Material getBlockType(int x, int y, int z) { return Material.STONE; }
            @Override public int getHighestBlockYAt(int x, int z) { return Math.floorMod(x + z, 80); }
            @Override public int getMinHeight() { return 0; }
        };
        for (int row = 0; row < 7; row++) {
            for (int column = 0; column < 13; column++) {
                MapRevealer.TerrainSamples samples = new MapRevealer.TerrainSamples();
                for (int z = 0; z < 128; z += 16) {
                    MapRevealer.sampleRows(terrain, column * 128, row * 128, 1, null, samples, z, z + 16);
                }
                assertArrayEquals(MapRevealer.renderPixels(terrain, column * 128, row * 128, 1, null, ColorScheme.SEPIA),
                        MapRevealer.colorPixels(samples, ColorScheme.SEPIA));
            }
        }
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

    @Test
    void mapWriterMarksWholeRectangleIncludingOppositeEdges() {
        FakeMapData map = new FakeMapData();
        byte[] replacement = new byte[16384];
        replacement[127] = 5;
        replacement[16256] = 7;
        assertEquals(2, MapDataAccess.writeBuffer(map, replacement));
        assertEquals(List.of("0,0", "127,127"), map.dirtyPixels);
        assertThrows(IllegalArgumentException.class, () -> MapDataAccess.writeBuffer(map, new byte[1]));
    }

    @Test
    void mapWriterSupportsPaperThreeArgumentDirtyMethod() {
        ModernMapData data = new ModernMapData();
        byte[] pixels = new byte[16384];
        pixels[16383] = 9;
        assertEquals(1, MapDataAccess.writeBuffer(data, pixels));
        assertTrue(data.saved);
    }

    private static final class ModernMapData {
        private final byte[] colors = new byte[16384];
        private boolean saved;
        private void setColorsDirty(int x, int z, boolean saved) { this.saved = saved; }
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
