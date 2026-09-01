<p align="center">
  <img src="images/plugin-logo.png" alt="Map Revealer Plugin" width="200" height="200" />
</p>
<h1 align="center">Map Revealer Plugin</h1>
<p align="center">
  <b>Fill a Minecraft map from world terrain with optional depth and color controls.</b>
</p>
<p align="center">
  <a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/releases"><img src="https://img.shields.io/github/v/release/Cobbleworks/Map-Revealer-Plugin?include_prereleases&style=flat-square&color=4CAF50" alt="Latest Release"></a>&nbsp;&nbsp;<a href="https://github.com/Cobbleworks/Map-Revealer-Plugin/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License"></a>&nbsp;&nbsp;<img src="https://img.shields.io/badge/Java-17+-orange?style=flat-square" alt="Java Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Minecraft-1.20+-green?style=flat-square" alt="Minecraft Version">&nbsp;&nbsp;<img src="https://img.shields.io/badge/Platform-Spigot%2FPaper-yellow?style=flat-square" alt="Platform">
</p>

Map Revealer renders the terrain covered by a filled map without requiring a player to explore it first. It can use the surface or a chosen Y-level, transform the result with a themed palette, and lock the finished map so normal exploration does not overwrite custom colors.

### Core Features

- Renders all 128 × 128 pixels of the map held in the player's main hand
- Supports surface rendering and fixed Y-level slices
- Includes normal colors and nine themed palettes
- Preserves terrain shading while applying themed colors
- Automatically locks maps rendered with a themed scheme
- Provides a separate command for locking any filled map
- Processes the reveal with an asynchronous task and reports its duration
- Requires no configuration file or third-party plugin

### Supported Platforms

- Minecraft 1.20 or newer
- Spigot, Paper, Purpur, or a compatible Bukkit server
- Java 17 or newer

## Table of Contents

1. [Installation](#installation)
2. [Third-Party Plugins](#third-party-plugins)
3. [Using Map Revealer](#using-map-revealer)
4. [Color Schemes](#color-schemes)
5. [Commands](#commands)
6. [Permissions](#permissions)
7. [Operational Notes](#operational-notes)
8. [Building From Source](#building-from-source)
9. [License](#license)
10. [Screenshots](#screenshots)

## Installation

1. Download the latest jar from [Releases](https://github.com/Cobbleworks/Map-Revealer-Plugin/releases).
2. Stop the server and copy the jar into `plugins/`.
3. Start the server.
4. Give trusted players `maprevealer.reveal`, or use the command as an operator.
5. Hold a filled map in the main hand and run `/revealmap`.

The plugin does not create a configuration file or data directory.

## Third-Party Plugins

None. Map Revealer is self-contained and does not hook into a permissions plugin directly. Any Bukkit-compatible permission manager can grant its permission node.

## Using Map Revealer

The command samples one world position for every map pixel. The map's center and scale determine the sampled X/Z coordinates.

- With no depth, each pixel uses the world's surface and skips fully transparent blocks.
- With a depth, sampling begins at the requested Y-level and moves downward past air or transparent blocks.
- Water and foliage receive specialized color handling; surrounding elevation is used for terrain shading.
- A themed color scheme remaps the calculated Minecraft map color while retaining its shade where appropriate.
- The completed pixel data is written to the existing map item; the command does not create a new map.

The player must remain online for the completion message, and the filled map must contain a valid `MapView`.

## Color Schemes

There are 10 scheme values: the standard palette plus nine themed alternatives.

| Scheme | Appearance |
|--------|------------|
| `normal` | Standard Minecraft map colors |
| `withered` | Faded and desaturated |
| `ender` | Purple void tones |
| `mystic` | Blue and purple magical tones |
| `nether` | Red and orange volcanic tones |
| `sepia` | Warm antique tones |
| `grayscale` | Monochrome shades |
| `inverted` | Reversed color treatment |
| `ocean` | Blue and green nautical tones |
| `autumn` | Orange and brown seasonal tones |

Any scheme other than `normal` is locked automatically after rendering. Normal maps remain able to update through ordinary Minecraft exploration unless `/revealmap lock` is used.

## Commands

`/reveal` and `/mapreveal` are aliases for `/revealmap`. Commands are player-only and require a filled map in the main hand where noted.

| Command | Description |
|---------|-------------|
| `/revealmap` | Render the held map at surface level with normal colors. |
| `/revealmap <depth>` | Render from a specific Y-level within the current world's height bounds. |
| `/revealmap <scheme>` | Render the surface with one of the listed schemes. |
| `/revealmap <depth> <scheme>` | Combine a Y-level with a color scheme. The two arguments may be supplied in either order. |
| `/revealmap lock` | Lock the held map against normal exploration updates. |
| `/revealmap schemes` | List all schemes in chat. `/revealmap colors` does the same. |
| `/revealmap help` | Show the in-game command summary. |

Examples:

```text
/revealmap 64
/revealmap sepia
/revealmap -10 grayscale
/revealmap mystic 32
```

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `maprevealer.reveal` | Operators | Use all Map Revealer commands. |

## Operational Notes

- Larger map scales sample positions farther apart but still render 16,384 pixels.
- Revealing terrain may access world chunks covered by the map, so test the command under normal server load before granting it broadly.
- Map locking uses the server's internal map data in addition to disabling position tracking. Test locking again after a major server update because internal field names can change.
- The plugin reports the map center, scale, selected depth, scheme, and elapsed render time after completion.

## Building From Source

Requirements: Java 17 or newer and Maven 3.6 or newer.

```bash
git clone https://github.com/Cobbleworks/Map-Revealer-Plugin.git
cd Map-Revealer-Plugin
mvn clean package
```

The compiled jar is written to `target/`.

## License

This project is licensed under the [MIT License](LICENSE).

## Screenshots

<table>
  <tr>
    <th>Sepia Theme in an Item Frame</th>
    <th>Grayscale Theme</th>
  </tr>
  <tr>
    <td><a href="images/screenshot-sepia-itemframe.png"><img src="images/screenshot-sepia-itemframe.png" alt="Sepia map displayed in an item frame" width="450"></a></td>
    <td><a href="images/screenshot-grayscale-theme.png"><img src="images/screenshot-grayscale-theme.png" alt="Grayscale map theme" width="450"></a></td>
  </tr>
  <tr>
    <th>Mystic Theme</th>
    <th>Autumn Theme</th>
  </tr>
  <tr>
    <td><a href="images/screenshot-mystic-theme.png"><img src="images/screenshot-mystic-theme.png" alt="Mystic map theme" width="450"></a></td>
    <td><a href="images/screenshot-autumn-theme.png"><img src="images/screenshot-autumn-theme.png" alt="Autumn map theme" width="450"></a></td>
  </tr>
  <tr>
    <th>Desert Reveal</th>
    <th>Nether Theme</th>
  </tr>
  <tr>
    <td><a href="images/screenshot-desert-reveal.png"><img src="images/screenshot-desert-reveal.png" alt="Revealed desert map" width="450"></a></td>
    <td><a href="images/screenshot-nether-map.png"><img src="images/screenshot-nether-map.png" alt="Nether map theme" width="450"></a></td>
  </tr>
</table>
