# Changelog

All notable changes to Map Revealer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.0.0] - 2026-04-28

Map Revealer v1.0.0 is the initial release, delivering instant asynchronous map reveals with nine color schemes, Y-level depth control, and automatic map locking — no configuration required.

### Map Reveal

- **Instant Full Reveal**: Fills all 128×128 pixels of the held map in a single `/revealmap` command — no chunk loading or player movement required
- **Asynchronous Processing**: The reveal runs on a background thread so the server main thread is never blocked
- **Y-Level Depth Control**: Optional depth argument samples terrain at a specific Y coordinate for accurate cave and underground cross-section maps

### Color Schemes

- **Nine Built-In Themes**: `normal`, `withered`, `ender`, `mystic`, `nether`, `sepia`, `grayscale`, `inverted`, `ocean`, `autumn`
- **Automatic Map Locking**: When a non-default scheme is applied, the map is automatically locked to prevent Minecraft's exploration system from overwriting the custom colors
- **Manual Lock**: `/revealmap lock` permanently preserves the current map appearance regardless of scheme

### Usability

- **Performance Feedback**: After reveal, the player receives elapsed time, map center coordinates, scale ratio, applied depth, and active scheme
- **Tab Completion**: Full tab completion for all subcommands and all nine color scheme names
- **Zero Configuration**: No config files generated — works immediately after installation

**Note:** If you encounter any bugs or issues, please don't hesitate to open an [issue](https://github.com/Cobbleworks/Map-Revealer-Plugin/issues). For any questions or to start a discussion, feel free to initiate a [discussion](https://github.com/Cobbleworks/Map-Revealer-Plugin/discussions) on the GitHub repository.
