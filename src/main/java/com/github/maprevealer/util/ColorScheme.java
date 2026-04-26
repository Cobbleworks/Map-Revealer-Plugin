package com.github.maprevealer.util;

public enum ColorScheme {

    NORMAL("normal", "Default Minecraft colors"),
    WITHERED("withered", "Faded, washed-out colors"),
    ENDER("ender", "Purple-tinted void colors"),
    MYSTIC("mystic", "Mystical blue-purple tones"),
    NETHER("nether", "Fiery red-orange tones"),
    SEPIA("sepia", "Antique sepia tones"),
    GRAYSCALE("grayscale", "Black and white tones"),
    INVERTED("inverted", "Inverted negative colors"),
    OCEAN("ocean", "Blue-green ocean tones"),
    AUTUMN("autumn", "Warm autumn orange-brown tones");

    private final String id;
    private final String description;

    ColorScheme(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public byte transformColor(byte baseColor) {
        int colorValue = baseColor & 0xFF;
        if (colorValue < 4) return baseColor;
        
        int baseId = colorValue / 4;
        int shade = colorValue % 4;
        
        return switch (this) {
            case NORMAL -> baseColor;
            case WITHERED -> transformWithered(baseId, shade);
            case ENDER -> transformEnder(baseId, shade);
            case MYSTIC -> transformMystic(baseId, shade);
            case NETHER -> transformNether(baseId, shade);
            case SEPIA -> transformSepia(baseId, shade);
            case GRAYSCALE -> transformGrayscale(baseId, shade);
            case INVERTED -> transformInverted(baseId, shade);
            case OCEAN -> transformOcean(baseId, shade);
            case AUTUMN -> transformAutumn(baseId, shade);
        };
    }

    private byte transformWithered(int baseId, int shade) {
        int newShade = Math.max(0, shade - 1);
        
        int newBaseId = switch (baseId) {
            case 1 -> 22;
            case 7 -> 21;
            case 12 -> 21;
            case 27 -> 22;
            case 28 -> 26;
            case 4 -> 11;
            case 18, 19, 30 -> 2;
            case 15 -> 37;
            case 16, 20 -> 22;
            case 25, 32 -> 21;
            case 33 -> 22;
            case 31 -> 22;
            default -> baseId;
        };
        
        return (byte) (newBaseId * 4 + newShade);
    }

    private byte transformEnder(int baseId, int shade) {
        int newBaseId = switch (baseId) {
            case 1, 7, 27 -> 24;
            case 12 -> 29;
            case 2, 10 -> 46;
            case 11 -> 29;
            case 8, 14 -> 16;
            case 13 -> 24;
            case 4, 28 -> 16;
            case 18, 30 -> 24;
            case 25, 32 -> 29;
            case 33, 31 -> 16;
            case 15 -> 24;
            case 6 -> 29;
            case 3, 22 -> 16;
            case 21 -> 29;
            case 5 -> 17;
            default -> baseId;
        };
        
        return (byte) (newBaseId * 4 + shade);
    }

    private byte transformMystic(int baseId, int shade) {
        int newBaseId = switch (baseId) {
            case 1, 7, 27 -> 23;
            case 2, 10 -> 47;
            case 11 -> 25;
            case 8, 14 -> 17;
            case 13 -> 32;
            case 4, 28 -> 16;
            case 18, 30 -> 23;
            case 12 -> 32;
            case 15 -> 24;
            case 6 -> 31;
            case 33 -> 31;
            case 3 -> 17;
            case 21, 22 -> 25;
            case 5 -> 31;
            default -> baseId;
        };
        
        return (byte) (newBaseId * 4 + shade);
    }

    private byte transformNether(int baseId, int shade) {
        int newBaseId = switch (baseId) {
            case 1, 7, 27 -> 52;
            case 12 -> 4;
            case 2 -> 37;
            case 10 -> 35;
            case 11 -> 29;
            case 8, 14 -> 15;
            case 13 -> 54;
            case 25, 32 -> 35;
            case 18, 30 -> 15;
            case 33, 31 -> 30;
            case 23 -> 52;
            case 24 -> 54;
            case 6 -> 30;
            case 3 -> 15;
            case 21 -> 29;
            case 22 -> 35;
            case 5 -> 4;
            default -> baseId;
        };
        
        return (byte) (newBaseId * 4 + shade);
    }

    private byte transformSepia(int baseId, int shade) {
        int newBaseId = switch (baseId) {
            case 1, 7, 27, 33 -> 48;
            case 12 -> 26;
            case 11, 21 -> 43;
            case 8, 14 -> 36;
            case 13 -> 26;
            case 4, 28 -> 50;
            case 25, 32 -> 47;
            case 18, 30 -> 40;
            case 15 -> 37;
            case 16, 24 -> 38;
            case 23 -> 45;
            case 6, 31 -> 44;
            case 3, 22 -> 36;
            case 5 -> 39;
            case 2 -> 2;
            case 10 -> 10;
            default -> 48;
        };
        
        return (byte) (newBaseId * 4 + shade);
    }

    private byte transformGrayscale(int baseId, int shade) {
        int brightness = getColorBrightness(baseId);
        
        int newBaseId = switch (brightness) {
            case 0 -> 29;
            case 1 -> 21;
            case 2 -> 11;
            case 3 -> 22;
            case 4 -> 8;
            default -> 11;
        };
        
        return (byte) (newBaseId * 4 + shade);
    }

    private byte transformInverted(int baseId, int shade) {
        int invertedShade = 3 - shade;
        
        int newBaseId = switch (baseId) {
            case 29 -> 8;
            case 8 -> 29;
            case 21 -> 22;
            case 22 -> 21;
            case 1 -> 28;
            case 28 -> 1;
            case 12 -> 4;
            case 4 -> 12;
            case 25 -> 15;
            case 15 -> 25;
            case 18 -> 24;
            case 24 -> 18;
            case 27 -> 16;
            case 16 -> 27;
            case 23 -> 28;
            default -> baseId;
        };
        
        return (byte) (newBaseId * 4 + invertedShade);
    }

    private byte transformOcean(int baseId, int shade) {
        int newBaseId = switch (baseId) {
            case 1, 7, 27 -> 23;
            case 2, 10 -> 47;
            case 11 -> 31;
            case 8, 14 -> 17;
            case 13 -> 26;
            case 4, 28 -> 23;
            case 18, 30 -> 27;
            case 12 -> 12;
            case 15 -> 23;
            case 6 -> 31;
            case 3 -> 17;
            case 21 -> 32;
            case 22 -> 17;
            case 5 -> 5;
            case 33 -> 27;
            case 31 -> 31;
            case 25 -> 12;
            default -> 23;
        };
        
        int newShade = Math.max(0, shade - 1);
        return (byte) (newBaseId * 4 + newShade);
    }

    private byte transformAutumn(int baseId, int shade) {
        int newBaseId = switch (baseId) {
            case 1, 7 -> 15;
            case 27 -> 18;
            case 12 -> 25;
            case 2 -> 37;
            case 10 -> 26;
            case 11 -> 48;
            case 8, 14 -> 2;
            case 13 -> 34;
            case 18 -> 15;
            case 30 -> 37;
            case 33 -> 27;
            case 31 -> 17;
            case 5 -> 17;
            case 6 -> 26;
            case 3 -> 2;
            case 21 -> 48;
            case 22 -> 37;
            case 23 -> 25;
            case 24 -> 28;
            case 25 -> 25;
            case 16 -> 28;
            default -> baseId;
        };
        
        return (byte) (newBaseId * 4 + shade);
    }

    private int getColorBrightness(int baseId) {
        return switch (baseId) {
            case 29 -> 0;
            case 21, 11, 59, 32, 25 -> 1;
            case 10, 13, 26, 27, 28, 24, 35, 52, 54 -> 2;
            case 1, 7, 12, 6, 15, 16, 23, 30, 33, 31 -> 3;
            case 8, 14, 2, 5, 3, 22, 18, 19, 17 -> 4;
            default -> 2;
        };
    }

    public static ColorScheme fromString(String input) {
        if (input == null) return null;
        
        String lower = input.toLowerCase().trim();
        for (ColorScheme scheme : values()) {
            if (scheme.id.equals(lower) || scheme.name().equalsIgnoreCase(lower)) {
                return scheme;
            }
        }
        return null;
    }

    public static String getAvailableSchemes() {
        StringBuilder sb = new StringBuilder();
        for (ColorScheme scheme : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(scheme.id);
        }
        return sb.toString();
    }
}