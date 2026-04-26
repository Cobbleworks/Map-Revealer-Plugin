<p align="center">
  <img src="https://github.com/BerndHagen/Minecraft-Server-Plugins/raw/main/img/img_v1.0.1-mcplugin.png" alt="Map Revealer" width="128" />
</p>
<h1 align="center">Map Revealer</h1>
<p align="center">
  <b>Instantly reveal entire Minecraft maps with custom color schemes.</b><br>
  <b>Depth rendering, map locking, and asynchronous processing for zero lag.</b>
</p>
<p align="center">
  <a href="https://github.com/Cobbleworks/Map-Revealer/releases"><img src="https://img.shields.io/github/v/release/Cobbleworks/Map-Revealer?include_prereleases&style=flat-square&color=4CAF50" alt="Latest Release"></a>&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Map-Revealer/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"></a>&nbsp;&nbsp;<img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square" alt="Java Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Minecraft-1.21+-green?style=flat-square" alt="Minecraft Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Platform-Spigot%2FPaper-yellow?style=flat-square" alt="Platform">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Status-Active-brightgreen?style=flat-square" alt="Status">
</p>

Map Revealer is an open-source Minecraft plugin that allows players to instantly reveal entire maps without exploring every chunk. With a single command, any map item held in hand is fully rendered — no travel required. The plugin supports nine distinct color schemes for thematic map appearances, optional depth-based Y-level rendering for cave maps, and automatic map locking to prevent Minecraft's engine from overwriting custom reveals after the fact. All map processing runs asynchronously to avoid any impact on server performance.

### **Core Features**

- **Instant Map Reveal:** Completely reveal any map held in hand with a single command — no exploration required
- **Color Schemes:** Nine distinct color schemes available: `withered`, `ender`, `mystic`, `nether`, `sepia`, `grayscale`, `inverted`, `ocean`, and `autumn`
- **Depth Rendering:** Optional Y-level rendering for cave map reveals — specify an exact Y coordinate for underground exploration maps
- **Map Locking:** Locks maps with applied color schemes to prevent Minecraft's automatic update system from overwriting custom rendering
- **Performance Optimized:** All map rendering is processed asynchronously to ensure no server lag during large or complex reveals
- **Scheme Listing:** In-game command to list all available color scheme names for easy reference

### **Supported Platforms**

- **Server Software:** `Spigot`, `Paper`, `Purpur`, `CraftBukkit`
- **Minecraft Versions:** `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10` and higher
- **Java Requirements:** `Java 17+`

### **Installation**

1. Download the latest `.jar` from the [Releases](https://github.com/Cobbleworks/Map-Revealer/releases) page
2. Stop your Minecraft server
3. Copy the `.jar` into your server's `plugins` folder
4. Start your server — a default configuration folder is generated at `plugins/MapRevealer/`

### **Player Commands**

| Command | Description |
|---------|-------------|
| `/revealmap` | Reveal the map in hand at surface level with default colors |
| `/revealmap [depth]` | Reveal at a specific Y level (e.g., `64` for sea level cave maps) |
| `/revealmap [depth] [scheme]` | Reveal with a custom color scheme (e.g., `withered`, `ender`, `ocean`) |
| `/revealmap lock` | Lock the map to prevent automatic updates from overwriting the reveal |
| `/revealmap schemes` | List all available color scheme names |
| `/revealmap help` | Display help information and command usage |

**Aliases:** `/mapreveal`, `/mr`

**Usage Examples:**
```
/revealmap                    — Basic surface reveal with default colors
/revealmap 64 withered        — Cave reveal at Y=64 with withered color scheme
/revealmap nether             — Surface reveal with nether color scheme
/revealmap lock               — Lock the current map to preserve its reveal
```

### **License**

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
