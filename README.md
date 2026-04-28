<p align="center">
  <img src="images/plugin-logo.png" alt="Map Revealer Plugin" width="180" />
</p>
<h1 align="center">Map Revealer Plugin</h1>
<p align="center">
  <b>Instantly reveal any Minecraft map without exploring every chunk.</b><br>
  <b>9 color schemes, Y-level depth control, and automatic map locking.</b>
</p>
<p align="center">
  <a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/releases"><img src="https://img.shields.io/github/v/release/Cobbleworks/Map-Revealer-Plugin?include_prereleases&style=flat-square&color=4CAF50" alt="Latest Release"></a>&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"></a>&nbsp;&nbsp;<img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square" alt="Java Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Minecraft-1.20+-green?style=flat-square" alt="Minecraft Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Platform-Spigot%2FPaper-yellow?style=flat-square" alt="Platform">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Status-Active-brightgreen?style=flat-square" alt="Status">&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/issues"><img src="https://img.shields.io/github/issues/Cobbleworks/Map-Revealer-Plugin?style=flat-square&color=orange" alt="Open Issues"></a>
</p>

Map Revealer is an open-source Minecraft plugin that instantly reveals the full content of any map item without requiring the player to physically explore every chunk. Hold a filled map, run `/revealmap`, and the plugin asynchronously samples the world's terrain to fill every pixel -- surface colors by default, or a specific Y-level for cave and underground maps. Nine built-in color schemes (Normal, Withered, Ender, Mystic, Nether, Sepia, Grayscale, Inverted, Ocean, and Autumn) let you dramatically change the visual appearance of any revealed map. Maps revealed with a non-default scheme are automatically locked so Minecraft's own exploration system never overwrites the custom colors.

Originally built to generate decorative and functional maps for custom server builds without endless walking, Map Revealer is designed to be minimal to install and simple to use. It requires no configuration files and no dependencies beyond a compatible server.

### **Core Features**

- **Instant Full Reveal:** Fills all 128x128 pixels of the held map in a single command -- no chunk loading or player movement required
- **Asynchronous Processing:** The reveal runs on a background thread so the server main thread is never blocked, even for large or complex world maps
- **Y-Level Depth Control:** Optional depth argument samples terrain at a specific Y coordinate instead of the surface, enabling accurate cave maps, underground base maps, and cross-section views
- **9 Color Schemes:** Transform the visual style of any map with one of nine built-in themes: `normal`, `withered`, `ender`, `mystic`, `nether`, `sepia`, `grayscale`, `inverted`, `ocean`, `autumn`
- **Automatic Map Locking:** When a non-default color scheme is applied, the map is automatically locked - disabling position tracking and preventing Minecraft's exploration system from overwriting the custom colors
- **Manual Lock Command:** Lock any map manually with `/revealmap lock` to preserve its current appearance permanently, regardless of scheme
- **Performance Feedback:** After reveal completes, the player receives the elapsed time, map center coordinates, scale ratio, applied depth, and active color scheme
- **Tab Completion:** Full tab completion for all subcommands and all 9 color scheme names
- **No Configuration Required:** The plugin generates no config files and requires no setup beyond installation

### **Supported Platforms**

- **Server Software:** `Spigot`, `Paper`, `Purpur`, `CraftBukkit`
- **Minecraft Versions:** `1.20` and higher
- **Java Requirements:** `Java 17+`
- **Dependencies:** None -- fully self-contained, no external plugins required

## **Table of Contents**

1. [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation Steps](#installation-steps)
    - [First Launch](#first-launch)
    - [Verifying Installation](#verifying-installation)
2. [Color Schemes](#color-schemes)
3. [How It Works](#how-it-works)
    - [Map Reveal Process](#map-reveal-process)
    - [Color Scheme Transformation](#color-scheme-transformation)
    - [Map Locking](#map-locking)
4. [Player Commands](#player-commands)
    - [Command Reference](#command-reference)
    - [Command Details & Examples](#command-details--examples)
    - [Tab Completion](#tab-completion)
5. [Permissions](#permissions)
6. [Building from Source](#building-from-source)
7. [License](#license)
8. [Screenshots](#screenshots)

## **Getting Started**

### **Prerequisites**

Before installing Map Revealer, confirm the following requirements are met:

- A Minecraft server running **Spigot**, **Paper**, **Purpur**, or any compatible fork
- Server version **1.20 or higher** (`api-version: 1.20` is the minimum)
- **Java 17** or newer installed on the machine running the server
- Operator or console access to install plugin files and grant permissions

No additional plugins or libraries are needed. Map Revealer has zero external dependencies and generates no configuration files on startup.

### **Installation Steps**

1. Download the latest `MapRevealer-x.x.x.jar` from the [Releases](https://github.com/Cobbleworks/Map-Revealer-Plugin/releases) page
2. **Stop your server completely** before placing any files
3. Copy the `.jar` into your server's `plugins/` directory
4. Start the server -- Map Revealer loads immediately with no setup required

### **First Launch**

Map Revealer does not create any configuration files or data directories. On the first server start after installation, the plugin simply registers the `/revealmap` command and enables. There is nothing to configure.

The only thing to check is whether players who should use the plugin have the `maprevealer.reveal` permission. By default this is restricted to operators - see the [Permissions](#permissions) section.

### **Verifying Installation**

- Run `/plugins` in-game -- `MapRevealer` should appear green in the list
- Run `/version MapRevealer` to confirm the installed version matches the release you downloaded
- Hold a filled map, run `/revealmap`, and check that the map fills with terrain
- If the plugin fails to load, check the server console for `MapRevealer` error messages (common causes: wrong Java version, corrupt JAR, or unsupported API version)

## **Color Schemes**

Map Revealer includes 9 color schemes that transform the visual appearance of revealed maps by remapping Minecraft's 128-color palette to different tonal ranges. The default `normal` scheme uses Minecraft's standard map colors.

| Scheme | Description |
|--------|-------------|
| `normal` | Default Minecraft map colors -- standard terrain appearance |
| `withered` | Faded, washed-out desaturated tones -- like an aged or worn map |
| `ender` | Purple-tinted void colors - otherworldly End dimension feel |
| `mystic` | Mystical blue-purple tones - magical, ethereal appearance |
| `nether` | Fiery red-orange tones - dark volcanic underworld palette |
| `sepia` | Warm antique sepia tones - aged parchment, historical aesthetic |
| `grayscale` | Black and white tones - high-contrast monochrome map |
| `inverted` | Inverted negative colors - striking reversed terrain appearance |
| `ocean` | Blue-green ocean tones - nautical, aquatic atmosphere |
| `autumn` | Warm orange-brown autumn tones - seasonal forest palette |

> **Auto-lock behavior:** Any scheme other than `normal` automatically locks the map after reveal to prevent Minecraft's exploration system from overwriting the custom colors. The `normal` scheme does not lock the map -- it remains live and will continue updating as players explore.

## **How It Works**

### **Map Reveal Process**

This is what happens step by step when a player runs `/revealmap`:

1. The plugin confirms the player is holding a `FILLED_MAP` item with a valid `MapView`
2. If a depth argument was provided, it is validated against the world's min/max Y bounds
3. If a color scheme argument was provided, it is resolved from the `ColorScheme` enum; unknown arguments are rejected with an informational message
4. The reveal task is submitted to Bukkit's **async scheduler** -- the main server thread is never blocked
5. For every pixel in the 128x128 map grid, the corresponding world X/Z coordinate is calculated based on the map's center and scale
6. If a depth was specified: the block at that exact Y coordinate is sampled for its map color. If no depth: the highest non-transparent block at surface level is sampled using `HeightMap.WORLD_SURFACE`
7. The raw Minecraft map color byte is retrieved for each block and passed through the active `ColorScheme.transformColor()` method, which remaps the color ID to a different palette entry
8. All 128x128 bytes are applied to the `MapView`'s canvas
9. If the scheme is not `normal` and the map is not already locked, it is locked automatically
10. A summary message is sent to the player on the main thread with timing, center coordinates, scale, depth, and active scheme

### **Color Scheme Transformation**

Each color scheme works by remapping the Minecraft map color byte using a `switch` expression. Minecraft's map color system uses base IDs (0-60+) multiplied by 4 and offset by a shade value (0-3, representing dark/normal/light/dimmer variants). The transformation preserves the shade for most schemes -- only the base color ID is remapped -- so depth shading in the original terrain is visually preserved in the transformed map.

### **Map Locking**

Map locking disables two Minecraft map behaviors:
- **Position tracking:** `MapView.setTrackingPosition(false)` -- the player cursor no longer moves on the map
- **Auto-exploration updates:** The underlying NMS `WorldMap.locked` flag is set via reflection, which prevents Minecraft's chunk-loading system from overwriting the map's pixel data when players walk through the area

The plugin accesses the NMS `locked` field by name (`locked` on modern versions, `e` on some older Paper mappings) via reflection, with graceful fallback if neither field is found.

## **Player Commands**

All commands require the `maprevealer.reveal` permission. By default this is restricted to operators -- grant it to player groups as needed via your permission manager.

### **Command Reference**

| Command | Description |
|---------|-------------|
| `/revealmap` | Reveal the map in your main hand at surface level with normal colors |
| `/revealmap [depth]` | Reveal at a specific Y-level (e.g., `64` for sea level, `-10` for deep underground) |
| `/revealmap [depth] [scheme]` | Reveal with a specific color scheme (e.g., `sepia`, `ender`, `ocean`) |
| `/revealmap [scheme]` | Reveal at surface level with a specific color scheme |
| `/revealmap lock` | Lock the map you are holding to prevent future automatic updates |
| `/revealmap schemes` | List all 9 available color schemes with descriptions |
| `/revealmap colors` | Alias for `schemes` - same output |
| `/revealmap help` | Show the in-game command reference |

**Aliases:** `/reveal`, `/mapreveal`  
**Requirement:** Must be holding a filled map (`FILLED_MAP`) in the main hand

### **Command Details & Examples**

**`/revealmap`**  
Reveals the held map at surface level using normal Minecraft map colors. The map remains live after reveal - it will continue updating as players explore the area.

```
/revealmap
```

---

**`/revealmap [depth]`**  
Reveals the map by sampling blocks at a specific Y coordinate instead of the surface. Useful for cave maps, underground base overviews, or any scenario where the surface layer should be ignored.

```
/revealmap 64         # Reveal at sea level (Y=64)
/revealmap -10        # Reveal deep underground
/revealmap 10         # Reveal just above bedrock level
```

---

**`/revealmap [depth] [scheme]`**  
Combines depth control with a color scheme. The order of arguments is flexible -- the plugin will parse any integer as the depth and any recognized scheme name as the scheme, regardless of order.

```
/revealmap 64 sepia         # Sea level with sepia tones
/revealmap nether           # Surface with nether fire palette
/revealmap mystic 32        # Y=32 with mystic blue-purple tones
/revealmap grayscale        # Surface, black and white
```

---

**`/revealmap lock`**  
Locks the held map immediately, preventing Minecraft from updating it when players walk through the mapped area. Use this to preserve any map's current appearance -- whether it was revealed with this plugin or filled naturally.

```
/revealmap lock
```

---

**`/revealmap schemes`**  
Displays all 9 available color schemes with their short descriptions in chat. Useful as a quick reference while building or decorating.

```
/revealmap schemes
/revealmap colors     # same output
```

### **Tab Completion**

Full tab completion is provided for all subcommands and color scheme names. Typing `/revealmap ` and pressing Tab shows all available subcommands and all 9 scheme names as options for the first argument.

| First argument suggestions | Second argument suggestions |
|----------------------------|-----------------------------|
| `lock`, `schemes`, `colors`, `help` | (all 9 scheme names) |
| `normal`, `withered`, `ender`, `mystic`, `nether` |  |
| `sepia`, `grayscale`, `inverted`, `ocean`, `autumn` |  |

## **Permissions**

Map Revealer has a single permission node that controls access to all commands.

| Permission | Description | Default |
|------------|-------------|---------|
| `maprevealer.reveal` | Use `/revealmap` and all subcommands including reveal, lock, schemes, and help | `op` |

By default, only server operators can use the plugin. To grant access to specific player groups, use your permission manager. Example using LuckPerms:

```
/lp group builder permission set maprevealer.reveal true
/lp group admin permission set maprevealer.reveal true
```

To allow all players to use the plugin, grant the permission to the default group:

```
/lp group default permission set maprevealer.reveal true
```

## **Building from Source**

Map Revealer uses **Apache Maven** as its build system. It is packaged as a standard plugin JAR -- no fat JAR or shading is required since the plugin has no external dependencies.

**Requirements:**
- Java 17 or newer
- Apache Maven 3.6 or newer

**Steps:**

```bash
# Clone the repository
git clone https://github.com/Cobbleworks/Map-Revealer-Plugin.git
cd Map-Revealer

# Compile and package
mvn clean package
```

The output JAR is written to `target/MapRevealer-x.x.x.jar`. Copy it into your server's `plugins/` folder as described in the [Installation Steps](#installation-steps) section.

**Project Structure:**

```
src/main/
├── java/com/github/maprevealer/
│   ├── MapRevealerPlugin.java        - Plugin entry point (onEnable / onDisable)
│   ├── commands/
│   │   └── RevealMapCommand.java     - All /revealmap subcommands + tab completion
│   └── util/
│       ├── ColorScheme.java          - Enum with 9 schemes and transformColor() logic
│       └── MapRevealer.java          - Core reveal, lock, and NMS reflection logic
└── resources/
    └── plugin.yml                    - Plugin metadata, commands, permissions
```

## **License**

This project is licensed under the **MIT License** -- see the [LICENSE](LICENSE) file for details.

## **Screenshots**

The screenshots below demonstrate Map Revealer across several color schemes and scenarios, including custom-themed maps displayed in item frames and in-hand views across different biomes and depth levels.

<table>
  <tr>
    <th>Map Revealer - Sepia Theme (Item Frame)</th>
    <th>Map Revealer - Grayscale Theme</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-sepia-itemframe.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-sepia-itemframe.png" alt="Sepia Theme in Item Frame" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-grayscale-theme.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-grayscale-theme.png" alt="Grayscale Theme" width="450"></a></td>
  </tr>
  <tr>
    <th>Map Revealer - Mystic Theme</th>
    <th>Map Revealer - Autumn Theme</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-mystic-theme.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-mystic-theme.png" alt="Mystic Theme" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-autumn-theme.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-autumn-theme.png" alt="Autumn Theme" width="450"></a></td>
  </tr>
  <tr>
    <th>Map Revealer - Desert Reveal</th>
    <th>Map Revealer - Nether Map</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-desert-reveal.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-desert-reveal.png" alt="Desert Reveal" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-nether-map.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer-Plugin/raw/main/images/screenshot-nether-map.png" alt="Nether Map" width="450"></a></td>
  </tr>
</table>