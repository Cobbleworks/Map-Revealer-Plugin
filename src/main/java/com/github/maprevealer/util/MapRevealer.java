package com.github.maprevealer.util;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.map.MapView;

public class MapRevealer {

    public static boolean lockMap(MapView mapView) {
        try {
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);
            lockMapNMS(mapView);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void lockMapNMS(MapView mapView) {
        try {
            var craftMapView = mapView;
            var worldMapField = craftMapView.getClass().getDeclaredField("worldMap");
            worldMapField.setAccessible(true);
            var worldMap = worldMapField.get(craftMapView);

            if (worldMap != null) {
                try {
                    var lockedField = worldMap.getClass().getDeclaredField("locked");
                    lockedField.setAccessible(true);
                    lockedField.set(worldMap, true);
                } catch (NoSuchFieldException e) {
                    try {
                        var lockedField = worldMap.getClass().getDeclaredField("e");
                        lockedField.setAccessible(true);
                        lockedField.set(worldMap, true);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isMapLocked(MapView mapView) {
        try {
            var craftMapView = mapView;
            var worldMapField = craftMapView.getClass().getDeclaredField("worldMap");
            worldMapField.setAccessible(true);
            var worldMap = worldMapField.get(craftMapView);

            if (worldMap != null) {
                try {
                    var lockedField = worldMap.getClass().getDeclaredField("locked");
                    lockedField.setAccessible(true);
                    return (boolean) lockedField.get(worldMap);
                } catch (NoSuchFieldException e) {
                    try {
                        var lockedField = worldMap.getClass().getDeclaredField("e");
                        lockedField.setAccessible(true);
                        return (boolean) lockedField.get(worldMap);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        
        return !mapView.isTrackingPosition();
    }

    private static final int MAP_SIZE = 128;

    public static void revealMap(MapView mapView, World world) {
        revealMap(mapView, world, null, ColorScheme.NORMAL);
    }

    public static void revealMap(MapView mapView, World world, ColorScheme colorScheme) {
        revealMap(mapView, world, null, colorScheme);
    }

    public static void revealMap(MapView mapView, World world, Integer depth) {
        revealMap(mapView, world, depth, ColorScheme.NORMAL);
    }

    public static void revealMap(MapView mapView, World world, Integer depth, ColorScheme colorScheme) {
        int centerX = mapView.getCenterX();
        int centerZ = mapView.getCenterZ();
        int scale = 1 << mapView.getScale().getValue();

        int halfMapBlocks = (MAP_SIZE / 2) * scale;

        int[][] heights = new int[MAP_SIZE][MAP_SIZE];
        byte[][] baseColors = new byte[MAP_SIZE][MAP_SIZE];
        boolean[][] isWater = new boolean[MAP_SIZE][MAP_SIZE];

        for (int pixelX = 0; pixelX < MAP_SIZE; pixelX++) {
            for (int pixelZ = 0; pixelZ < MAP_SIZE; pixelZ++) {
                int worldX = centerX - halfMapBlocks + (pixelX * scale) + (scale / 2);
                int worldZ = centerZ - halfMapBlocks + (pixelZ * scale) + (scale / 2);

                HeightColorData data = getHeightAndColor(world, worldX, worldZ, depth);
                heights[pixelX][pixelZ] = data.height;
                baseColors[pixelX][pixelZ] = data.baseColor;
                isWater[pixelX][pixelZ] = data.isWater;
            }
        }

        ColorScheme scheme = colorScheme != null ? colorScheme : ColorScheme.NORMAL;
        
        for (int pixelX = 0; pixelX < MAP_SIZE; pixelX++) {
            for (int pixelZ = 0; pixelZ < MAP_SIZE; pixelZ++) {
                byte baseColor = baseColors[pixelX][pixelZ];
                byte finalColor;
                
                if (isWater[pixelX][pixelZ]) {
                    finalColor = scheme.transformColor(baseColor);
                } else {
                    int shade = calculateShade(heights, pixelX, pixelZ);
                    
                    finalColor = scheme.transformColor(applyShade(baseColor, shade));
                }
                
                setMapPixel(mapView, pixelX, pixelZ, finalColor);
            }
        }
    }

    private static class HeightColorData {
        int height;
        byte baseColor;
        boolean isWater;

        HeightColorData(int height, byte baseColor, boolean isWater) {
            this.height = height;
            this.baseColor = baseColor;
            this.isWater = isWater;
        }
    }

    private static HeightColorData getHeightAndColor(World world, int worldX, int worldZ, Integer depth) {
        int startY;
        
        if (depth != null) {
            startY = depth;
        } else {
            startY = world.getHighestBlockYAt(worldX, worldZ, HeightMap.WORLD_SURFACE);
        }
        
        Block block = world.getBlockAt(worldX, startY, worldZ);
        
        while (block.getType().isAir() || isFullyTransparent(block.getType())) {
            startY--;
            if (startY < world.getMinHeight()) {
                return new HeightColorData(world.getMinHeight(), (byte) 0, false);
            }
            block = world.getBlockAt(worldX, startY, worldZ);
        }

        if (block.getType() == Material.WATER) {
            byte waterColor = getWaterColor(world, worldX, startY, worldZ);
            return new HeightColorData(startY, waterColor, true);
        }

        if (isFoliage(block.getType())) {
            byte foliageColor = getBaseColorForMaterial(block.getType());
            int groundY = startY - 1;
            while (groundY > world.getMinHeight()) {
                Material below = world.getBlockAt(worldX, groundY, worldZ).getType();
                if (!below.isAir() && !isFullyTransparent(below) && !isFoliage(below)) {
                    break;
                }
                groundY--;
            }
            return new HeightColorData(groundY, foliageColor, false);
        }

        byte baseColor = getBaseColorForMaterial(block.getType());
        return new HeightColorData(startY, baseColor, false);
    }

    private static boolean isFoliage(Material material) {
        return switch (material) {
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, MANGROVE_LEAVES, CHERRY_LEAVES,
                 AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES,
                 VINE, CAVE_VINES, CAVE_VINES_PLANT, WEEPING_VINES, WEEPING_VINES_PLANT,
                 TWISTING_VINES, TWISTING_VINES_PLANT, GLOW_LICHEN -> true;
            default -> false;
        };
    }

    private static int calculateShade(int[][] heights, int pixelX, int pixelZ) {
        int currentHeight = heights[pixelX][pixelZ];
        
        if (pixelZ == 0) {
            return 2;
        }
        
        int northHeight = heights[pixelX][pixelZ - 1];
        int heightDiff = currentHeight - northHeight;
        
        if (heightDiff > 0) {
            return 3;
        } else if (heightDiff < 0) {
            return heightDiff < -1 ? 0 : 1;
        } else {
            return 2;
        }
    }

    private static byte applyShade(byte baseColor, int shade) {
        int baseId = (baseColor & 0xFF) / 4;
        return (byte) (baseId * 4 + shade);
    }

    private static boolean isFullyTransparent(Material material) {
        return switch (material) {
            case GLASS, GLASS_PANE, TINTED_GLASS,
                 WHITE_STAINED_GLASS, ORANGE_STAINED_GLASS, MAGENTA_STAINED_GLASS,
                 LIGHT_BLUE_STAINED_GLASS, YELLOW_STAINED_GLASS, LIME_STAINED_GLASS,
                 PINK_STAINED_GLASS, GRAY_STAINED_GLASS, LIGHT_GRAY_STAINED_GLASS,
                 CYAN_STAINED_GLASS, PURPLE_STAINED_GLASS, BLUE_STAINED_GLASS,
                 BROWN_STAINED_GLASS, GREEN_STAINED_GLASS, RED_STAINED_GLASS,
                 BLACK_STAINED_GLASS,
                 WHITE_STAINED_GLASS_PANE, ORANGE_STAINED_GLASS_PANE, MAGENTA_STAINED_GLASS_PANE,
                 LIGHT_BLUE_STAINED_GLASS_PANE, YELLOW_STAINED_GLASS_PANE, LIME_STAINED_GLASS_PANE,
                 PINK_STAINED_GLASS_PANE, GRAY_STAINED_GLASS_PANE, LIGHT_GRAY_STAINED_GLASS_PANE,
                 CYAN_STAINED_GLASS_PANE, PURPLE_STAINED_GLASS_PANE, BLUE_STAINED_GLASS_PANE,
                 BROWN_STAINED_GLASS_PANE, GREEN_STAINED_GLASS_PANE, RED_STAINED_GLASS_PANE,
                 BLACK_STAINED_GLASS_PANE,
                 BARRIER, LIGHT, STRUCTURE_VOID,
                 TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH, REDSTONE_TORCH, REDSTONE_WALL_TORCH,
                 LADDER, LEVER, TRIPWIRE, TRIPWIRE_HOOK, STRING,
                 RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL,
                 REDSTONE_WIRE, REPEATER, COMPARATOR,
                 FLOWER_POT, POTTED_OAK_SAPLING, POTTED_SPRUCE_SAPLING, POTTED_BIRCH_SAPLING,
                 POTTED_JUNGLE_SAPLING, POTTED_ACACIA_SAPLING, POTTED_DARK_OAK_SAPLING,
                 POTTED_CHERRY_SAPLING, POTTED_MANGROVE_PROPAGULE, POTTED_AZALEA_BUSH,
                 POTTED_FLOWERING_AZALEA_BUSH, POTTED_FERN, POTTED_DANDELION, POTTED_POPPY,
                 POTTED_BLUE_ORCHID, POTTED_ALLIUM, POTTED_AZURE_BLUET, POTTED_RED_TULIP,
                 POTTED_ORANGE_TULIP, POTTED_WHITE_TULIP, POTTED_PINK_TULIP, POTTED_OXEYE_DAISY,
                 POTTED_CORNFLOWER, POTTED_LILY_OF_THE_VALLEY, POTTED_WITHER_ROSE,
                 POTTED_RED_MUSHROOM, POTTED_BROWN_MUSHROOM, POTTED_DEAD_BUSH,
                 POTTED_CACTUS, POTTED_BAMBOO, POTTED_CRIMSON_FUNGUS, POTTED_WARPED_FUNGUS,
                 POTTED_CRIMSON_ROOTS, POTTED_WARPED_ROOTS, POTTED_TORCHFLOWER,
                 END_ROD, LIGHTNING_ROD -> true;
            default -> false;
        };
    }

    private static byte getWaterColor(World world, int x, int surfaceY, int z) {
        int depth = 0;
        int y = surfaceY;
        
        while (y > world.getMinHeight() && world.getBlockAt(x, y, z).getType() == Material.WATER) {
            depth++;
            y--;
        }

        if (depth > 8) {
            return 48;
        } else if (depth > 4) {
            return 49;
        } else {
            return 50;
        }
    }

    private static void setMapPixel(MapView mapView, int x, int z, byte color) {
        try {
            var world = mapView.getWorld();
            if (world != null) {
                int mapId = mapView.getId();
                
                var server = org.bukkit.Bukkit.getServer();
                var mapDataMethod = server.getClass().getMethod("getMap", int.class);
                var mapViewFromServer = (MapView) mapDataMethod.invoke(server, mapId);
                
                if (mapViewFromServer != null) {
                }
            }
        } catch (Exception ignored) {
        }

        setMapPixelNMS(mapView, x, z, color);
    }

    private static void setMapPixelNMS(MapView mapView, int x, int z, byte color) {
        try {
            var craftMapView = mapView;
            var worldMapField = craftMapView.getClass().getDeclaredField("worldMap");
            worldMapField.setAccessible(true);
            var worldMap = worldMapField.get(craftMapView);

            if (worldMap != null) {
                var colorsField = worldMap.getClass().getDeclaredField("colors");
                colorsField.setAccessible(true);
                byte[] colors = (byte[]) colorsField.get(worldMap);

                if (colors != null && x >= 0 && x < 128 && z >= 0 && z < 128) {
                    colors[x + z * 128] = color;
                }

                try {
                    var setColorsDirtyMethod = worldMap.getClass().getMethod("setColorsDirty", int.class, int.class);
                    setColorsDirtyMethod.invoke(worldMap, x, z);
                } catch (NoSuchMethodException e) {
                    try {
                        var flagDirtyMethod = worldMap.getClass().getMethod("a", int.class, int.class);
                        flagDirtyMethod.invoke(worldMap, x, z);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
        }
    }

    private static byte getBaseColorForMaterial(Material material) {
        return switch (material) {
            case GRASS_BLOCK, SLIME_BLOCK -> (byte) (1 * 4 + 2);
            
            case SAND, SANDSTONE, SANDSTONE_SLAB, SANDSTONE_STAIRS, SANDSTONE_WALL,
                 CUT_SANDSTONE, SMOOTH_SANDSTONE,
                 SMOOTH_SANDSTONE_SLAB, SMOOTH_SANDSTONE_STAIRS,
                 RED_SANDSTONE, RED_SANDSTONE_SLAB, RED_SANDSTONE_STAIRS, RED_SANDSTONE_WALL,
                 CUT_RED_SANDSTONE, SMOOTH_RED_SANDSTONE,
                 END_STONE, END_STONE_BRICKS, END_STONE_BRICK_SLAB, END_STONE_BRICK_STAIRS, END_STONE_BRICK_WALL,
                 GLOWSTONE, SHROOMLIGHT,
                 BIRCH_PLANKS, BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD,
                 BIRCH_SLAB, BIRCH_STAIRS, BIRCH_FENCE, BIRCH_FENCE_GATE, BIRCH_DOOR, BIRCH_TRAPDOOR,
                 BIRCH_SIGN, BIRCH_WALL_SIGN, BIRCH_HANGING_SIGN, BIRCH_WALL_HANGING_SIGN,
                 BIRCH_PRESSURE_PLATE, BIRCH_BUTTON,
                 BONE_BLOCK, TURTLE_EGG, SNIFFER_EGG, SCAFFOLDING -> (byte) (2 * 4 + 2);
            
            case COBWEB, MUSHROOM_STEM -> (byte) (3 * 4 + 2);
            
            case LAVA, FIRE, TNT, REDSTONE_BLOCK -> (byte) (4 * 4 + 2);
            
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> (byte) (5 * 4 + 2);
            

            case IRON_BLOCK, IRON_DOOR, IRON_TRAPDOOR, BREWING_STAND,
                 HEAVY_WEIGHTED_PRESSURE_PLATE, IRON_BARS,
                 CHAIN, LANTERN, SOUL_LANTERN -> (byte) (6 * 4 + 2);
            
            case OAK_LEAVES, SPRUCE_LEAVES, BIRCH_LEAVES, JUNGLE_LEAVES,
                 ACACIA_LEAVES, DARK_OAK_LEAVES, MANGROVE_LEAVES, CHERRY_LEAVES,
                 AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES,
                 VINE, LILY_PAD,
                 SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN,
                 SUGAR_CANE, BAMBOO, BAMBOO_SAPLING,
                 CACTUS, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS,
                 SMALL_DRIPLEAF, BIG_DRIPLEAF, BIG_DRIPLEAF_STEM,
                 AZALEA, FLOWERING_AZALEA, SPORE_BLOSSOM,
                 OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING,
                 ACACIA_SAPLING, DARK_OAK_SAPLING, CHERRY_SAPLING, MANGROVE_PROPAGULE -> (byte) (7 * 4 + 2);
            
            case SNOW, SNOW_BLOCK, POWDER_SNOW,
                 WHITE_WOOL, WHITE_CARPET, WHITE_BED,
                 WHITE_CONCRETE, WHITE_CONCRETE_POWDER,
                 WHITE_SHULKER_BOX, WHITE_GLAZED_TERRACOTTA,
                 WHITE_CANDLE, WHITE_BANNER, WHITE_WALL_BANNER -> (byte) (8 * 4 + 2);
            
            case CLAY, INFESTED_STONE, INFESTED_COBBLESTONE, INFESTED_STONE_BRICKS,
                 INFESTED_MOSSY_STONE_BRICKS, INFESTED_CRACKED_STONE_BRICKS, INFESTED_CHISELED_STONE_BRICKS,
                 INFESTED_DEEPSLATE -> (byte) (9 * 4 + 2);
            

            case DIRT, COARSE_DIRT, ROOTED_DIRT, FARMLAND, DIRT_PATH, MUD,
                 GRANITE, POLISHED_GRANITE, GRANITE_SLAB, GRANITE_STAIRS, GRANITE_WALL,
                 POLISHED_GRANITE_SLAB, POLISHED_GRANITE_STAIRS,
                 JUNGLE_PLANKS, JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD,
                 JUNGLE_SLAB, JUNGLE_STAIRS, JUNGLE_FENCE, JUNGLE_FENCE_GATE, JUNGLE_DOOR, JUNGLE_TRAPDOOR,
                 JUNGLE_SIGN, JUNGLE_WALL_SIGN, JUNGLE_HANGING_SIGN, JUNGLE_WALL_HANGING_SIGN,
                 JUNGLE_PRESSURE_PLATE, JUNGLE_BUTTON,
                 PACKED_MUD, MUD_BRICKS, MUD_BRICK_SLAB, MUD_BRICK_STAIRS, MUD_BRICK_WALL -> (byte) (10 * 4 + 2);
            

            case STONE, COBBLESTONE, MOSSY_COBBLESTONE,
                 STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS, CHISELED_STONE_BRICKS,
                 SMOOTH_STONE, SMOOTH_STONE_SLAB,
                 STONE_SLAB, STONE_STAIRS, COBBLESTONE_SLAB, COBBLESTONE_STAIRS,
                 STONE_BRICK_SLAB, STONE_BRICK_STAIRS, STONE_BRICK_WALL,
                 MOSSY_COBBLESTONE_SLAB, MOSSY_COBBLESTONE_STAIRS, MOSSY_COBBLESTONE_WALL,
                 MOSSY_STONE_BRICK_SLAB, MOSSY_STONE_BRICK_STAIRS, MOSSY_STONE_BRICK_WALL,
                 COBBLESTONE_WALL,
                 ANDESITE, POLISHED_ANDESITE, ANDESITE_SLAB, ANDESITE_STAIRS, ANDESITE_WALL,
                 POLISHED_ANDESITE_SLAB, POLISHED_ANDESITE_STAIRS,
                 GRAVEL, DEAD_TUBE_CORAL_BLOCK, DEAD_BRAIN_CORAL_BLOCK, DEAD_BUBBLE_CORAL_BLOCK,
                 DEAD_FIRE_CORAL_BLOCK, DEAD_HORN_CORAL_BLOCK,
                 FURNACE, BLAST_FURNACE, SMOKER, STONECUTTER, DISPENSER, DROPPER,
                 BEDROCK, SPAWNER, CAULDRON, HOPPER, OBSERVER,
                 ENDER_CHEST, LODESTONE, GRINDSTONE, STONE_PRESSURE_PLATE,
                 STONE_BUTTON, STICKY_PISTON, PISTON, PISTON_HEAD, MOVING_PISTON -> (byte) (11 * 4 + 2);
            

            case WATER, BUBBLE_COLUMN -> (byte) (12 * 4 + 2);
            

            case OAK_PLANKS, OAK_LOG, OAK_WOOD, STRIPPED_OAK_LOG, STRIPPED_OAK_WOOD,
                 OAK_SLAB, OAK_STAIRS, OAK_FENCE, OAK_FENCE_GATE, OAK_DOOR, OAK_TRAPDOOR,
                 OAK_SIGN, OAK_WALL_SIGN, OAK_HANGING_SIGN, OAK_WALL_HANGING_SIGN,
                 OAK_PRESSURE_PLATE, OAK_BUTTON,
                 BOOKSHELF, CHISELED_BOOKSHELF, LECTERN,
                 CRAFTING_TABLE, CARTOGRAPHY_TABLE, FLETCHING_TABLE, SMITHING_TABLE, LOOM,
                 COMPOSTER, BARREL, CHEST, TRAPPED_CHEST,
                 JUKEBOX, NOTE_BLOCK, BEEHIVE, BEE_NEST,
                 BAMBOO_BLOCK, STRIPPED_BAMBOO_BLOCK, BAMBOO_PLANKS,
                 BAMBOO_SLAB, BAMBOO_STAIRS, BAMBOO_FENCE, BAMBOO_FENCE_GATE, BAMBOO_DOOR, BAMBOO_TRAPDOOR,
                 BAMBOO_SIGN, BAMBOO_WALL_SIGN, BAMBOO_HANGING_SIGN, BAMBOO_WALL_HANGING_SIGN,
                 BAMBOO_PRESSURE_PLATE, BAMBOO_BUTTON,
                 BAMBOO_MOSAIC, BAMBOO_MOSAIC_SLAB, BAMBOO_MOSAIC_STAIRS,
                 DEAD_BUSH, BROWN_MUSHROOM, BROWN_MUSHROOM_BLOCK -> (byte) (13 * 4 + 2);
            

            case QUARTZ_BLOCK, QUARTZ_BRICKS, QUARTZ_PILLAR, CHISELED_QUARTZ_BLOCK, SMOOTH_QUARTZ,
                 QUARTZ_SLAB, QUARTZ_STAIRS, SMOOTH_QUARTZ_SLAB, SMOOTH_QUARTZ_STAIRS,
                 DIORITE, POLISHED_DIORITE, DIORITE_SLAB, DIORITE_STAIRS, DIORITE_WALL,
                 POLISHED_DIORITE_SLAB, POLISHED_DIORITE_STAIRS,
                 SEA_LANTERN, TARGET, DAYLIGHT_DETECTOR -> (byte) (14 * 4 + 2);
            

            case ORANGE_WOOL, ORANGE_CARPET, ORANGE_BED,
                 ORANGE_CONCRETE, ORANGE_CONCRETE_POWDER,
                 ORANGE_SHULKER_BOX, ORANGE_GLAZED_TERRACOTTA,
                 ORANGE_CANDLE, ORANGE_BANNER, ORANGE_WALL_BANNER,
                 PUMPKIN, CARVED_PUMPKIN, JACK_O_LANTERN,
                 ACACIA_PLANKS, ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD,
                 ACACIA_SLAB, ACACIA_STAIRS, ACACIA_FENCE, ACACIA_FENCE_GATE, ACACIA_DOOR, ACACIA_TRAPDOOR,
                 ACACIA_SIGN, ACACIA_WALL_SIGN, ACACIA_HANGING_SIGN, ACACIA_WALL_HANGING_SIGN,
                 ACACIA_PRESSURE_PLATE, ACACIA_BUTTON,
                 HONEY_BLOCK, HONEYCOMB_BLOCK,
                 RED_SAND,
                 COPPER_BLOCK, CUT_COPPER, CUT_COPPER_SLAB, CUT_COPPER_STAIRS,
                 WAXED_COPPER_BLOCK, WAXED_CUT_COPPER, WAXED_CUT_COPPER_SLAB, WAXED_CUT_COPPER_STAIRS,
                 CHISELED_COPPER, WAXED_CHISELED_COPPER,
                 COPPER_GRATE, WAXED_COPPER_GRATE,
                 COPPER_BULB, WAXED_COPPER_BULB -> (byte) (15 * 4 + 2);
            

            case MAGENTA_WOOL, MAGENTA_CARPET, MAGENTA_BED,
                 MAGENTA_CONCRETE, MAGENTA_CONCRETE_POWDER,
                 MAGENTA_SHULKER_BOX, MAGENTA_GLAZED_TERRACOTTA,
                 MAGENTA_CANDLE, MAGENTA_BANNER, MAGENTA_WALL_BANNER,
                 PURPUR_BLOCK, PURPUR_PILLAR, PURPUR_SLAB, PURPUR_STAIRS -> (byte) (16 * 4 + 2);
            

            case LIGHT_BLUE_WOOL, LIGHT_BLUE_CARPET, LIGHT_BLUE_BED,
                 LIGHT_BLUE_CONCRETE, LIGHT_BLUE_CONCRETE_POWDER,
                 LIGHT_BLUE_SHULKER_BOX, LIGHT_BLUE_GLAZED_TERRACOTTA,
                 LIGHT_BLUE_CANDLE, LIGHT_BLUE_BANNER, LIGHT_BLUE_WALL_BANNER,
                 SOUL_FIRE -> (byte) (17 * 4 + 2);
            

            case YELLOW_WOOL, YELLOW_CARPET, YELLOW_BED,
                 YELLOW_CONCRETE, YELLOW_CONCRETE_POWDER,
                 YELLOW_SHULKER_BOX, YELLOW_GLAZED_TERRACOTTA,
                 YELLOW_CANDLE, YELLOW_BANNER, YELLOW_WALL_BANNER,
                 HAY_BLOCK, SPONGE, WET_SPONGE,
                 HORN_CORAL_BLOCK, OCHRE_FROGLIGHT -> (byte) (18 * 4 + 2);
            

            case LIME_WOOL, LIME_CARPET, LIME_BED,
                 LIME_CONCRETE, LIME_CONCRETE_POWDER,
                 LIME_SHULKER_BOX, LIME_GLAZED_TERRACOTTA,
                 LIME_CANDLE, LIME_BANNER, LIME_WALL_BANNER,
                 MELON -> (byte) (19 * 4 + 2);
            

            case PINK_WOOL, PINK_CARPET, PINK_BED,
                 PINK_CONCRETE, PINK_CONCRETE_POWDER,
                 PINK_SHULKER_BOX, PINK_GLAZED_TERRACOTTA,
                 PINK_CANDLE, PINK_BANNER, PINK_WALL_BANNER,
                 CHERRY_PLANKS, CHERRY_LOG, CHERRY_WOOD, STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_WOOD,
                 CHERRY_SLAB, CHERRY_STAIRS, CHERRY_FENCE, CHERRY_FENCE_GATE, CHERRY_DOOR, CHERRY_TRAPDOOR,
                 CHERRY_SIGN, CHERRY_WALL_SIGN, CHERRY_HANGING_SIGN, CHERRY_WALL_HANGING_SIGN,
                 CHERRY_PRESSURE_PLATE, CHERRY_BUTTON,
                 BRAIN_CORAL_BLOCK, PEARLESCENT_FROGLIGHT -> (byte) (20 * 4 + 2);
            

            case GRAY_WOOL, GRAY_CARPET, GRAY_BED,
                 GRAY_CONCRETE, GRAY_CONCRETE_POWDER,
                 GRAY_SHULKER_BOX, GRAY_GLAZED_TERRACOTTA,
                 GRAY_CANDLE, GRAY_BANNER, GRAY_WALL_BANNER,
                 DEAD_TUBE_CORAL, DEAD_BRAIN_CORAL, DEAD_BUBBLE_CORAL, DEAD_FIRE_CORAL, DEAD_HORN_CORAL,
                 TINTED_GLASS, TUFF, POLISHED_TUFF, TUFF_BRICKS,
                 TUFF_SLAB, TUFF_STAIRS, TUFF_WALL,
                 POLISHED_TUFF_SLAB, POLISHED_TUFF_STAIRS, POLISHED_TUFF_WALL,
                 TUFF_BRICK_SLAB, TUFF_BRICK_STAIRS, TUFF_BRICK_WALL,
                 CHISELED_TUFF, CHISELED_TUFF_BRICKS -> (byte) (21 * 4 + 2);
            

            case LIGHT_GRAY_WOOL, LIGHT_GRAY_CARPET, LIGHT_GRAY_BED,
                 LIGHT_GRAY_CONCRETE, LIGHT_GRAY_CONCRETE_POWDER,
                 LIGHT_GRAY_SHULKER_BOX, LIGHT_GRAY_GLAZED_TERRACOTTA,
                 LIGHT_GRAY_CANDLE, LIGHT_GRAY_BANNER, LIGHT_GRAY_WALL_BANNER,
                 STRUCTURE_BLOCK, JIGSAW -> (byte) (22 * 4 + 2);
            

            case CYAN_WOOL, CYAN_CARPET, CYAN_BED,
                 CYAN_CONCRETE, CYAN_CONCRETE_POWDER,
                 CYAN_SHULKER_BOX, CYAN_GLAZED_TERRACOTTA,
                 CYAN_CANDLE, CYAN_BANNER, CYAN_WALL_BANNER,
                 PRISMARINE, PRISMARINE_SLAB, PRISMARINE_STAIRS, PRISMARINE_WALL,
                 WARPED_ROOTS, WARPED_FUNGUS, NETHER_SPROUTS,
                 SCULK_SENSOR, CALIBRATED_SCULK_SENSOR -> (byte) (23 * 4 + 2);
            

            case PURPLE_WOOL, PURPLE_CARPET, PURPLE_BED,
                 PURPLE_CONCRETE, PURPLE_CONCRETE_POWDER,
                 PURPLE_SHULKER_BOX, PURPLE_GLAZED_TERRACOTTA,
                 PURPLE_CANDLE, PURPLE_BANNER, PURPLE_WALL_BANNER,
                 MYCELIUM, CHORUS_PLANT, CHORUS_FLOWER,
                 AMETHYST_BLOCK, BUDDING_AMETHYST, AMETHYST_CLUSTER,
                 SMALL_AMETHYST_BUD, MEDIUM_AMETHYST_BUD, LARGE_AMETHYST_BUD,
                 BUBBLE_CORAL_BLOCK -> (byte) (24 * 4 + 2);
            

            case BLUE_WOOL, BLUE_CARPET, BLUE_BED,
                 BLUE_CONCRETE, BLUE_CONCRETE_POWDER,
                 BLUE_SHULKER_BOX, BLUE_GLAZED_TERRACOTTA,
                 BLUE_CANDLE, BLUE_BANNER, BLUE_WALL_BANNER,
                 TUBE_CORAL_BLOCK -> (byte) (25 * 4 + 2);
            

            case BROWN_WOOL, BROWN_CARPET, BROWN_BED,
                 BROWN_CONCRETE, BROWN_CONCRETE_POWDER,
                 BROWN_SHULKER_BOX, BROWN_GLAZED_TERRACOTTA,
                 BROWN_CANDLE, BROWN_BANNER, BROWN_WALL_BANNER,
                 SOUL_SAND, SOUL_SOIL,
                 COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK -> (byte) (26 * 4 + 2);
            

            case GREEN_WOOL, GREEN_CARPET, GREEN_BED,
                 GREEN_CONCRETE, GREEN_CONCRETE_POWDER,
                 GREEN_SHULKER_BOX, GREEN_GLAZED_TERRACOTTA,
                 GREEN_CANDLE, GREEN_BANNER, GREEN_WALL_BANNER,
                 MOSS_BLOCK, MOSS_CARPET, DRIED_KELP_BLOCK,
                 SEA_PICKLE, GLOW_LICHEN -> (byte) (27 * 4 + 2);
            

            case RED_WOOL, RED_CARPET, RED_BED,
                 RED_CONCRETE, RED_CONCRETE_POWDER,
                 RED_SHULKER_BOX, RED_GLAZED_TERRACOTTA,
                 RED_CANDLE, RED_BANNER, RED_WALL_BANNER,
                 BRICKS, BRICK_SLAB, BRICK_STAIRS, BRICK_WALL,
                 RED_MUSHROOM_BLOCK, RED_MUSHROOM,
                 FIRE_CORAL_BLOCK, MANGROVE_ROOTS, MUDDY_MANGROVE_ROOTS,
                 DECORATED_POT, ENCHANTING_TABLE,
                 NETHER_WART, NETHER_WART_BLOCK -> (byte) (28 * 4 + 2);
            

            case BLACK_WOOL, BLACK_CARPET, BLACK_BED,
                 BLACK_CONCRETE, BLACK_CONCRETE_POWDER,
                 BLACK_SHULKER_BOX, BLACK_GLAZED_TERRACOTTA,
                 BLACK_CANDLE, BLACK_BANNER, BLACK_WALL_BANNER,
                 OBSIDIAN, CRYING_OBSIDIAN, RESPAWN_ANCHOR,
                 COAL_BLOCK, COAL_ORE, DEEPSLATE_COAL_ORE,
                 BASALT, SMOOTH_BASALT, POLISHED_BASALT,
                 BLACKSTONE, POLISHED_BLACKSTONE, POLISHED_BLACKSTONE_BRICKS,
                 CHISELED_POLISHED_BLACKSTONE, GILDED_BLACKSTONE,
                 BLACKSTONE_SLAB, BLACKSTONE_STAIRS, BLACKSTONE_WALL,
                 POLISHED_BLACKSTONE_SLAB, POLISHED_BLACKSTONE_STAIRS, POLISHED_BLACKSTONE_WALL,
                 POLISHED_BLACKSTONE_BRICK_SLAB, POLISHED_BLACKSTONE_BRICK_STAIRS, POLISHED_BLACKSTONE_BRICK_WALL,
                 POLISHED_BLACKSTONE_PRESSURE_PLATE, POLISHED_BLACKSTONE_BUTTON,
                 END_PORTAL_FRAME, END_GATEWAY, DRAGON_EGG,
                 SCULK, SCULK_VEIN, SCULK_CATALYST, SCULK_SHRIEKER,
                 ANCIENT_DEBRIS -> (byte) (29 * 4 + 2);
            

            case GOLD_BLOCK, RAW_GOLD_BLOCK, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 BELL, LIGHT_WEIGHTED_PRESSURE_PLATE -> (byte) (30 * 4 + 2);
            

            case DIAMOND_BLOCK, DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 BEACON, CONDUIT,
                 PRISMARINE_BRICKS, PRISMARINE_BRICK_SLAB, PRISMARINE_BRICK_STAIRS,
                 DARK_PRISMARINE, DARK_PRISMARINE_SLAB, DARK_PRISMARINE_STAIRS -> (byte) (31 * 4 + 2);
            

            case LAPIS_BLOCK, LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> (byte) (32 * 4 + 2);
            

            case EMERALD_BLOCK, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> (byte) (33 * 4 + 2);
            

            case PODZOL,
                 SPRUCE_PLANKS, SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD,
                 SPRUCE_SLAB, SPRUCE_STAIRS, SPRUCE_FENCE, SPRUCE_FENCE_GATE, SPRUCE_DOOR, SPRUCE_TRAPDOOR,
                 SPRUCE_SIGN, SPRUCE_WALL_SIGN, SPRUCE_HANGING_SIGN, SPRUCE_WALL_HANGING_SIGN,
                 SPRUCE_PRESSURE_PLATE, SPRUCE_BUTTON,
                 CAMPFIRE, SOUL_CAMPFIRE -> (byte) (34 * 4 + 2);
            

            case NETHERRACK, NETHER_QUARTZ_ORE,
                 MAGMA_BLOCK, NETHER_BRICK_FENCE -> (byte) (35 * 4 + 2);
            

            case TERRACOTTA, WHITE_TERRACOTTA,
                 CALCITE -> (byte) (36 * 4 + 2);
            

            case ORANGE_TERRACOTTA -> (byte) (37 * 4 + 2);
            

            case MAGENTA_TERRACOTTA -> (byte) (38 * 4 + 2);
            

            case LIGHT_BLUE_TERRACOTTA -> (byte) (39 * 4 + 2);
            

            case YELLOW_TERRACOTTA -> (byte) (40 * 4 + 2);
            

            case LIME_TERRACOTTA, VERDANT_FROGLIGHT -> (byte) (41 * 4 + 2);
            

            case PINK_TERRACOTTA -> (byte) (42 * 4 + 2);
            

            case GRAY_TERRACOTTA -> (byte) (43 * 4 + 2);
            

            case LIGHT_GRAY_TERRACOTTA -> (byte) (44 * 4 + 2);
            

            case CYAN_TERRACOTTA -> (byte) (45 * 4 + 2);
            

            case PURPLE_TERRACOTTA, SHULKER_BOX -> (byte) (46 * 4 + 2);
            

            case BLUE_TERRACOTTA -> (byte) (47 * 4 + 2);
            

            case BROWN_TERRACOTTA, POINTED_DRIPSTONE, DRIPSTONE_BLOCK -> (byte) (48 * 4 + 2);
            

            case GREEN_TERRACOTTA -> (byte) (49 * 4 + 2);
            

            case RED_TERRACOTTA -> (byte) (50 * 4 + 2);
            

            case BLACK_TERRACOTTA -> (byte) (51 * 4 + 2);
            

            case CRIMSON_NYLIUM -> (byte) (52 * 4 + 2);
            

            case CRIMSON_STEM, STRIPPED_CRIMSON_STEM -> (byte) (53 * 4 + 2);
            

            case CRIMSON_HYPHAE, STRIPPED_CRIMSON_HYPHAE,
                 CRIMSON_PLANKS, CRIMSON_SLAB, CRIMSON_STAIRS, CRIMSON_FENCE, CRIMSON_FENCE_GATE,
                 CRIMSON_DOOR, CRIMSON_TRAPDOOR,
                 CRIMSON_SIGN, CRIMSON_WALL_SIGN, CRIMSON_HANGING_SIGN, CRIMSON_WALL_HANGING_SIGN,
                 CRIMSON_PRESSURE_PLATE, CRIMSON_BUTTON -> (byte) (54 * 4 + 2);
            

            case WARPED_NYLIUM -> (byte) (55 * 4 + 2);
            

            case WARPED_STEM, STRIPPED_WARPED_STEM -> (byte) (56 * 4 + 2);
            

            case WARPED_HYPHAE, STRIPPED_WARPED_HYPHAE,
                 WARPED_PLANKS, WARPED_SLAB, WARPED_STAIRS, WARPED_FENCE, WARPED_FENCE_GATE,
                 WARPED_DOOR, WARPED_TRAPDOOR,
                 WARPED_SIGN, WARPED_WALL_SIGN, WARPED_HANGING_SIGN, WARPED_WALL_HANGING_SIGN,
                 WARPED_PRESSURE_PLATE, WARPED_BUTTON -> (byte) (57 * 4 + 2);
            

            case WARPED_WART_BLOCK -> (byte) (58 * 4 + 2);
            

            case DEEPSLATE, COBBLED_DEEPSLATE, DEEPSLATE_BRICKS, DEEPSLATE_TILES,
                 CHISELED_DEEPSLATE, POLISHED_DEEPSLATE, CRACKED_DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_TILES,
                 COBBLED_DEEPSLATE_SLAB, COBBLED_DEEPSLATE_STAIRS, COBBLED_DEEPSLATE_WALL,
                 DEEPSLATE_BRICK_SLAB, DEEPSLATE_BRICK_STAIRS, DEEPSLATE_BRICK_WALL,
                 DEEPSLATE_TILE_SLAB, DEEPSLATE_TILE_STAIRS, DEEPSLATE_TILE_WALL,
                 POLISHED_DEEPSLATE_SLAB, POLISHED_DEEPSLATE_STAIRS, POLISHED_DEEPSLATE_WALL,
                 REINFORCED_DEEPSLATE,
                 DEEPSLATE_IRON_ORE, DEEPSLATE_COPPER_ORE -> (byte) (59 * 4 + 2);
            

            case RAW_IRON_BLOCK,
                 IRON_ORE, COPPER_ORE,
                 EXPOSED_COPPER, EXPOSED_CUT_COPPER, EXPOSED_CUT_COPPER_SLAB, EXPOSED_CUT_COPPER_STAIRS,
                 WAXED_EXPOSED_COPPER, WAXED_EXPOSED_CUT_COPPER, WAXED_EXPOSED_CUT_COPPER_SLAB, WAXED_EXPOSED_CUT_COPPER_STAIRS,
                 EXPOSED_CHISELED_COPPER, WAXED_EXPOSED_CHISELED_COPPER,
                 EXPOSED_COPPER_GRATE, WAXED_EXPOSED_COPPER_GRATE,
                 EXPOSED_COPPER_BULB, WAXED_EXPOSED_COPPER_BULB -> (byte) (60 * 4 + 2);
            

            case RAW_COPPER_BLOCK,
                 OXIDIZED_COPPER, OXIDIZED_CUT_COPPER, OXIDIZED_CUT_COPPER_SLAB, OXIDIZED_CUT_COPPER_STAIRS,
                 WAXED_OXIDIZED_COPPER, WAXED_OXIDIZED_CUT_COPPER, WAXED_OXIDIZED_CUT_COPPER_SLAB, WAXED_OXIDIZED_CUT_COPPER_STAIRS,
                 OXIDIZED_CHISELED_COPPER, WAXED_OXIDIZED_CHISELED_COPPER,
                 OXIDIZED_COPPER_GRATE, WAXED_OXIDIZED_COPPER_GRATE,
                 OXIDIZED_COPPER_BULB, WAXED_OXIDIZED_COPPER_BULB,
                 WEATHERED_COPPER, WEATHERED_CUT_COPPER, WEATHERED_CUT_COPPER_SLAB, WEATHERED_CUT_COPPER_STAIRS,
                 WAXED_WEATHERED_COPPER, WAXED_WEATHERED_CUT_COPPER, WAXED_WEATHERED_CUT_COPPER_SLAB, WAXED_WEATHERED_CUT_COPPER_STAIRS,
                 WEATHERED_CHISELED_COPPER, WAXED_WEATHERED_CHISELED_COPPER,
                 WEATHERED_COPPER_GRATE, WAXED_WEATHERED_COPPER_GRATE,
                 WEATHERED_COPPER_BULB, WAXED_WEATHERED_COPPER_BULB -> (byte) (61 * 4 + 2);
            

            case DARK_OAK_PLANKS, DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD,
                 DARK_OAK_SLAB, DARK_OAK_STAIRS, DARK_OAK_FENCE, DARK_OAK_FENCE_GATE, DARK_OAK_DOOR, DARK_OAK_TRAPDOOR,
                 DARK_OAK_SIGN, DARK_OAK_WALL_SIGN, DARK_OAK_HANGING_SIGN, DARK_OAK_WALL_HANGING_SIGN,
                 DARK_OAK_PRESSURE_PLATE, DARK_OAK_BUTTON,
                 MANGROVE_PLANKS, MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD,
                 MANGROVE_SLAB, MANGROVE_STAIRS, MANGROVE_FENCE, MANGROVE_FENCE_GATE, MANGROVE_DOOR, MANGROVE_TRAPDOOR,
                 MANGROVE_SIGN, MANGROVE_WALL_SIGN, MANGROVE_HANGING_SIGN, MANGROVE_WALL_HANGING_SIGN,
                 MANGROVE_PRESSURE_PLATE, MANGROVE_BUTTON -> (byte) (26 * 4 + 2);
            

            case NETHER_BRICKS, NETHER_BRICK_SLAB, NETHER_BRICK_STAIRS, NETHER_BRICK_WALL,
                 CRACKED_NETHER_BRICKS, CHISELED_NETHER_BRICKS,
                 RED_NETHER_BRICKS, RED_NETHER_BRICK_SLAB, RED_NETHER_BRICK_STAIRS, RED_NETHER_BRICK_WALL -> (byte) (35 * 4 + 2);
            

            case DANDELION, SUNFLOWER, TORCHFLOWER -> (byte) (18 * 4 + 2);
            case POPPY, RED_TULIP, ROSE_BUSH -> (byte) (28 * 4 + 2);
            case BLUE_ORCHID -> (byte) (17 * 4 + 2);
            case ALLIUM, LILAC -> (byte) (16 * 4 + 2);
            case AZURE_BLUET, OXEYE_DAISY, WHITE_TULIP, LILY_OF_THE_VALLEY -> (byte) (8 * 4 + 2);
            case ORANGE_TULIP -> (byte) (15 * 4 + 2);
            case PINK_TULIP, PEONY, PINK_PETALS -> (byte) (20 * 4 + 2);
            case CORNFLOWER -> (byte) (25 * 4 + 2);
            case WITHER_ROSE -> (byte) (29 * 4 + 2);
            case PITCHER_PLANT, PITCHER_POD -> (byte) (23 * 4 + 2);
            

            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> (byte) (28 * 4 + 2);
            

            default -> (byte) (11 * 4 + 2);
        };
    }
}