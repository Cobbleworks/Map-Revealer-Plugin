# Map Revealer

Part of the Cobbleworks Minecraft plugin ecosystem.

Source section copied from the main plugin collection repository:
https://github.com/BerndHagen/Minecraft-Server-Plugins

## Overview

A utility plugin that allows players to instantly reveal entire maps without having to explore every chunk. Features multiple color schemes for thematic map appearances and automatic map locking to preserve custom reveals.

### Core Features:
- **Instant Map Reveal:** Completely reveal any map in your hand with a single command
- **Color Schemes:** 9 different color schemes including withered, ender, mystic, nether, sepia, grayscale, inverted, ocean, and autumn
- **Depth Rendering:** Optional Y-level rendering for cave maps or surface-only reveals
- **Map Locking:** Automatically locks maps with custom schemes to prevent Minecraft's automatic updates from overwriting them
- **Performance Optimized:** Asynchronous processing to avoid server lag during large map reveals

### Player Commands:

| Command | Description |
|---------|-------------|
| `/revealmap` | Reveal the map in your hand (surface, normal colors) |
| `/revealmap [depth]` | Reveal at specific Y level (e.g., 64 for sea level) |
| `/revealmap [depth] [scheme]` | Reveal with custom color scheme (e.g., withered, ender) |
| `/revealmap lock` | Lock the map to prevent automatic updates |
| `/revealmap schemes` | List all available color schemes |
| `/revealmap help` | Show help information |

**Aliases:** `/mapreveal`, `/mr`  
**Examples:**  
- `/revealmap` - Basic surface reveal  
- `/revealmap 64 withered` - Reveal caves at Y=64 with withered colors  
- `/revealmap nether` - Surface reveal with nether color scheme

## License

This project is licensed under the MIT License.