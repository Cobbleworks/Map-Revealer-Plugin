# Changelog

All notable changes to Map Revealer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

## [1.2.0] - 2026-09-30

### Added

- Support all five Minecraft map scales (0–4) for managed walls, including scale-4 chunk limits and tab completion.
- Persist wall themes and rendering depth; `/revealmap wall set <name> theme <theme>` and `depth <Y|auto>` refresh automatically.
- Add explicit `/revealmap reveal [depth] [theme]` while preserving existing command forms.
- Render Nether terrain below the roof at configurable Y=64 by default for walls and held maps.

### Fixed

- Handle below-minimum heights in empty End columns without aborting map rendering; preserve transparent void pixels.
- Resend completed wall maps to nearby clients, after wall completion, and when players join, teleport, or change dimensions.
- Support modern Paper map dirty methods and invalidate the entire changed pixel rectangle.

### Changed

- Capture snapshots on the server thread in bounded batches and sample large maps in strips to limit memory and chunk-load bursts.
- Upgrade previous shipped configuration limits while preserving custom limits; report ungenerated terrain and render failures.
- Keep themed, sliced, and Nether maps locked so vanilla exploration cannot replace their pixels.
## [1.1.0] - 2026-09-04

### Added

- Added persistent, automatically refreshed item-frame and glow-item-frame map walls.
- Added safe creation for empty rectangular frame grids and in-place adoption of correctly aligned existing maps.
- Added per-wall refresh intervals, locked/unlocked state, optional player markers, an origin-map region label, and opt-in automatic expansion.
- Added wall list, create, expand, configure, remove, and force-refresh commands.
- Added `walls.yml` persistence for wall options, frame UUIDs, map IDs, centers, slots, and render hashes.

### Changed

- Replaced off-thread Bukkit world access with a queued snapshot pipeline: generated chunks load asynchronously, immutable snapshots are processed off-thread, and map buffers are changed on the server thread.
- Map updates now compare pixel buffers and mark only changed map regions dirty.
- Map locking now uses Bukkit's public `MapView#setLocked` API instead of reflective field access.

### Security

- Wall creation aborts on non-map items, irregular grids, ownership conflicts, invalid maps, or mismatched map centers/scales.
- Existing map IDs are preserved, wall removal leaves frames and map items in place, and automatic expansion only claims newly placed empty frames when explicitly enabled.
- A generated chunk that cannot be captured aborts that map refresh instead of clearing the affected region as though it were empty terrain.
- Existing map IDs cannot be claimed by two managed walls, and a failed creation restores the original items, rotations, ownership data, and map settings.

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
