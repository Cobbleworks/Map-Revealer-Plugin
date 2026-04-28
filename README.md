<p align="center">
  <img src="images/ <p align="center">
  <img src="images/plugin-logo.png" alt="Map Revealer" width="180" />
</p>
<h1 align="center">Map Revealer</h1>
<p align="center">
  <b>Instantly reveal entire Minecraft maps with custom color schemes.</b><br>
  <b>Depth rendering, map locking, and asynchronous processing for zero lag.</b>
</p>
<p align="center">
  <a href="https://github.com/Cobbleworks/Map-Revealer/releases"><img src="https://img.shields.io/github/v/release/Cobbleworks/Map-Revealer?include_prereleases&style=flat-square&color=4CAF50" alt="Latest Release"></a>&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Map-Revealer/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"></a>&nbsp;&nbsp;<img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square" alt="Java Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Minecraft-1.20+-green?style=flat-square" alt="Minecraft Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Platform-Spigot%2FPaper-yellow?style=flat-square" alt="Platform">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Status-Active-brightgreen?style=flat-square" alt="Status">
</p>

Map Revealer is an open-source Minecraft plugin that allows players to instantly reveal entire maps without exploring every chunk. With a single command, any filled map item held in hand is fully rendered across all 128Ã—128 pixels â€” no travel required. The plugin supports ten colour schemes for thematic map appearances, optional depth-based Y-level rendering for cave and underground maps, and automatic map locking to prevent Minecraft's engine from overwriting custom reveals. All map processing runs asynchronously to avoid server lag. After a reveal, the plugin reports the map's centre coordinates, map scale, and processing time.

### **Core Features**

- **Instant Map Reveal:** Completely reveal any filled map held in hand â€” all 128Ã—128 pixels are sampled from the world and rendered in one command
- **10 Colour Schemes:** Choose from `normal` (default Minecraft colours) plus nine themed variants: `withered`, `ender`, `mystic`, `nether`, `sepia`, `grayscale`, `inverted`, `ocean`, and `autumn`
- **Depth Rendering:** Specify a Y-level to render blocks at a specific height â€” ideal for cave maps or underground exploration maps
- **Automatic Map Locking:** When a non-`normal` colour scheme is applied, the map is automatically locked to preserve the custom rendering and prevent Minecraft from overwriting it with exploration data
- **Manual Map Locking:** Lock any map directly with `/revealmap lock` to freeze its current state regardless of scheme
- **Asynchronous Processing:** Map rendering runs fully asynchronously â€” no server TPS impact even for large-scale maps
- **Reveal Feedback:** After reveal completes, the command reports map centre coordinates (X, Z), the map scale (e.g., `NORMAL 1:1`), and processing time in milliseconds
- **Tab Completion:** All subcommands and colour scheme names are tab-completable

### **Supported Platforms**

- **Server Software:** `Spigot`, `Paper`, `Purpur`, `CraftBukkit`
- **Minecraft Versions:** `1.20` and higher
- **Java Requirements:** `Java 17+`

### **Installation**

1. Download the latest `.jar` from the [Releases](https://github.com/Cobbleworks/Map-Revealer/releases) page
2. Stop your Minecraft server
3. Copy the `.jar` into your server's `plugins/` folder
4. Start the server â€” the plugin is ready immediately; no configuration file is required

### **Player Commands**

| Command | Description |
|---------|-------------|
| `/revealmap` | Reveal the filled map in hand at surface level with default colours |
| `/revealmap <depth>` | Reveal at a specific Y level (e.g., `64` for a cave map render) |
| `/revealmap <depth> <scheme>` | Reveal at a Y level with a specific colour scheme |
| `/revealmap <scheme>` | Reveal at surface level with a specific colour scheme |
| `/revealmap lock` | Lock the map in hand to prevent automatic updates from overwriting it |
| `/revealmap schemes` | List all available colour scheme names and descriptions |
| `/revealmap help` | Display all available commands and usage |

**Aliases:** `/reveal`, `/mapreveal`

**Colour Schemes:**

| Scheme ID | Description |
|-----------|-------------|
| `normal` | Default Minecraft colours |
| `withered` | Faded, washed-out colours |
| `ender` | Purple-tinted void colours |
| `mystic` | Mystical blue-purple tones |
| `nether` | Fiery red-orange tones |
| `sepia` | Antique sepia tones |
| `grayscale` | Black and white tones |
| `inverted` | Inverted negative colours |
| `ocean` | Blue-green ocean tones |
| `autumn` | Warm autumn orange-brown tones |

**Usage Examples:**
```
/revealmap                    â€” Surface reveal with default colours
/revealmap 64                 â€” Render at Y=64 with default colours
/revealmap 64 withered        â€” Render at Y=64 with Withered scheme (auto-locked)
/revealmap nether             â€” Surface reveal with Nether scheme (auto-locked)
/revealmap lock               â€” Lock the current map to preserve its render
/revealmap schemes            â€” Show all available colour schemes
```

**Note:** The depth value must be within the world's valid height range (`minHeight` to `maxHeight`). Revealing with any scheme other than `normal` automatically locks the map to preserve the custom colours.

### **Permissions**

| Permission | Description | Default |
|------------|-------------|---------|
| `maprevealer.reveal` | Allows the player to reveal and lock maps | `op` |

### **License**

This project is licensed under the **MIT License** â€” see the [LICENSE](LICENSE) file for details.


## **Screenshots**

The screenshots below demonstrate the core features of the Map Revealer plugin, including a nether-style generated map and revealing a full desert town map via command.

<table>
  <tr>
    <th>Map Revealer - Nether Style Map</th>
    <th>Map Revealer - Desert Town Reveal</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-nether-map.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-nether-map.png" alt="Nether Style Map" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-desert-reveal.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-desert-reveal.png" alt="Desert Town Reveal" width="450"></a></td>
  </tr>
</table>
.Value -replace 'width="180"', 'width="180"'  />
</p>
<h1 align="center">Map Revealer</h1>
<p align="center">
  <b>Instantly reveal entire Minecraft maps with custom color schemes.</b><br>
  <b>Depth rendering, map locking, and asynchronous processing for zero lag.</b>
</p>
<p align="center">
  <a href="https://github.com/Cobbleworks/Map-Revealer/releases"><img src="https://img.shields.io/github/v/release/Cobbleworks/Map-Revealer?include_prereleases&style=flat-square&color=4CAF50" alt="Latest Release"></a>&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Map-Revealer/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"></a>&nbsp;&nbsp;<img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square" alt="Java Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Minecraft-1.20+-green?style=flat-square" alt="Minecraft Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Platform-Spigot%2FPaper-yellow?style=flat-square" alt="Platform">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Status-Active-brightgreen?style=flat-square" alt="Status">
</p>

Map Revealer is an open-source Minecraft plugin that allows players to instantly reveal entire maps without exploring every chunk. With a single command, any filled map item held in hand is fully rendered across all 128Ã—128 pixels â€” no travel required. The plugin supports ten colour schemes for thematic map appearances, optional depth-based Y-level rendering for cave and underground maps, and automatic map locking to prevent Minecraft's engine from overwriting custom reveals. All map processing runs asynchronously to avoid server lag. After a reveal, the plugin reports the map's centre coordinates, map scale, and processing time.

### **Core Features**

- **Instant Map Reveal:** Completely reveal any filled map held in hand â€” all 128Ã—128 pixels are sampled from the world and rendered in one command
- **10 Colour Schemes:** Choose from `normal` (default Minecraft colours) plus nine themed variants: `withered`, `ender`, `mystic`, `nether`, `sepia`, `grayscale`, `inverted`, `ocean`, and `autumn`
- **Depth Rendering:** Specify a Y-level to render blocks at a specific height â€” ideal for cave maps or underground exploration maps
- **Automatic Map Locking:** When a non-`normal` colour scheme is applied, the map is automatically locked to preserve the custom rendering and prevent Minecraft from overwriting it with exploration data
- **Manual Map Locking:** Lock any map directly with `/revealmap lock` to freeze its current state regardless of scheme
- **Asynchronous Processing:** Map rendering runs fully asynchronously â€” no server TPS impact even for large-scale maps
- **Reveal Feedback:** After reveal completes, the command reports map centre coordinates (X, Z), the map scale (e.g., `NORMAL 1:1`), and processing time in milliseconds
- **Tab Completion:** All subcommands and colour scheme names are tab-completable

### **Supported Platforms**

- **Server Software:** `Spigot`, `Paper`, `Purpur`, `CraftBukkit`
- **Minecraft Versions:** `1.20` and higher
- **Java Requirements:** `Java 17+`

### **Installation**

1. Download the latest `.jar` from the [Releases](https://github.com/Cobbleworks/Map-Revealer/releases) page
2. Stop your Minecraft server
3. Copy the `.jar` into your server's `plugins/` folder
4. Start the server â€” the plugin is ready immediately; no configuration file is required

### **Player Commands**

| Command | Description |
|---------|-------------|
| `/revealmap` | Reveal the filled map in hand at surface level with default colours |
| `/revealmap <depth>` | Reveal at a specific Y level (e.g., `64` for a cave map render) |
| `/revealmap <depth> <scheme>` | Reveal at a Y level with a specific colour scheme |
| `/revealmap <scheme>` | Reveal at surface level with a specific colour scheme |
| `/revealmap lock` | Lock the map in hand to prevent automatic updates from overwriting it |
| `/revealmap schemes` | List all available colour scheme names and descriptions |
| `/revealmap help` | Display all available commands and usage |

**Aliases:** `/reveal`, `/mapreveal`

**Colour Schemes:**

| Scheme ID | Description |
|-----------|-------------|
| `normal` | Default Minecraft colours |
| `withered` | Faded, washed-out colours |
| `ender` | Purple-tinted void colours |
| `mystic` | Mystical blue-purple tones |
| `nether` | Fiery red-orange tones |
| `sepia` | Antique sepia tones |
| `grayscale` | Black and white tones |
| `inverted` | Inverted negative colours |
| `ocean` | Blue-green ocean tones |
| `autumn` | Warm autumn orange-brown tones |

**Usage Examples:**
```
/revealmap                    â€” Surface reveal with default colours
/revealmap 64                 â€” Render at Y=64 with default colours
/revealmap 64 withered        â€” Render at Y=64 with Withered scheme (auto-locked)
/revealmap nether             â€” Surface reveal with Nether scheme (auto-locked)
/revealmap lock               â€” Lock the current map to preserve its render
/revealmap schemes            â€” Show all available colour schemes
```

**Note:** The depth value must be within the world's valid height range (`minHeight` to `maxHeight`). Revealing with any scheme other than `normal` automatically locks the map to preserve the custom colours.

### **Permissions**

| Permission | Description | Default |
|------------|-------------|---------|
| `maprevealer.reveal` | Allows the player to reveal and lock maps | `op` |

### **License**

This project is licensed under the **MIT License** â€” see the [LICENSE](LICENSE) file for details.


## **Screenshots**

The screenshots below demonstrate the core features of the Map Revealer plugin, including a nether-style generated map and revealing a full desert town map via command.

<table>
  <tr>
    <th>Map Revealer - Nether Style Map</th>
    <th>Map Revealer - Desert Town Reveal</th>
  </tr>
  <tr>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-nether-map.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-nether-map.png" alt="Nether Style Map" width="450"></a></td>
    <td><a href="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-desert-reveal.png" target="_blank" rel="noopener noreferrer"><img src="https://github.com/Cobbleworks/Map-Revealer/raw/main/images/screenshot-desert-reveal.png" alt="Desert Town Reveal" width="450"></a></td>
  </tr>
</table>
