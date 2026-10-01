package com.github.maprevealer.wall;

import com.github.maprevealer.MapRevealerPlugin;
import com.github.maprevealer.util.ColorScheme;
import com.github.maprevealer.util.MapRenderService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns map-wall definitions, validates frame ownership, and schedules safe
 * terrain refreshes. It never replaces an item that it did not create.
 */
public final class MapWallManager implements Listener {
    private static final Pattern WALL_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private final MapRevealerPlugin plugin;
    private final MapRenderService renderer;
    private final File storageFile;
    private final NamespacedKey wallKey;
    private final NamespacedKey mapKey;
    private final Map<String, MapWall> walls = new LinkedHashMap<>();
    private final Set<String> refreshingWalls = new HashSet<>();
    private final Set<String> pendingRefreshes = new HashSet<>();

    public MapWallManager(MapRevealerPlugin plugin, MapRenderService renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.storageFile = new File(plugin.getDataFolder(), "walls.yml");
        this.wallKey = new NamespacedKey(plugin, "map_wall");
        this.mapKey = new NamespacedKey(plugin, "map_wall_map");
        load();
        attachOverlays();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshDueWalls, 1200L, 1200L);
    }

    public String create(Player player, String rawName, Integer requestedScale) {
        String name = normalizeName(rawName);
        if (!WALL_NAME.matcher(name).matches()) return "Wall names may contain a-z, 0-9, underscores, and hyphens (32 characters maximum).";
        if (walls.containsKey(name)) return "A managed map wall named '" + name + "' already exists.";
        ItemFrame target = targetFrame(player).orElse(null);
        if (target == null) return "Look directly at an item frame or glow item frame within 8 blocks.";
        if (!isVertical(target.getFacing())) return "Map walls must use frames attached to a vertical surface.";
        List<Cell> cells = connectedFrames(target);
        if (cells.isEmpty()) return "No connected item-frame wall was found.";
        int maximum = Math.max(1, plugin.getConfig().getInt("map-walls.max-frames", 256));
        if (cells.size() > maximum) return "That wall contains more than the configured " + maximum + " frame limit.";
        if (!isRectangle(cells)) return "The connected frames must form a complete rectangle before the wall can be created.";
        if (cells.stream().anyMatch(cell -> !cell.frame().getItem().getType().isAir()
                && cell.frame().getItem().getType() != Material.FILLED_MAP)) {
            return "Creation stopped: frames may be empty or contain filled maps. Other items are never replaced.";
        }
        if (cells.stream().anyMatch(cell -> isManaged(cell.frame()))) {
            return "Creation stopped: at least one frame already belongs to a managed wall.";
        }

        World world = target.getWorld();
        MapView originMap = mapInFrame(target);
        int scaleValue = originMap == null ? (requestedScale == null ? 0 : requestedScale)
                : originMap.getScale().getValue();
        int maxScale = Math.max(0, Math.min(4, plugin.getConfig().getInt("map-walls.max-scale", 4)));
        if (scaleValue < 0 || scaleValue > maxScale) return "Wall map scale must be between 0 and " + maxScale + ".";
        if (originMap != null && requestedScale != null && requestedScale != scaleValue) {
            return "The selected map uses scale " + scaleValue + ", not scale " + requestedScale + ".";
        }
        int width = 128 * (1 << scaleValue);
        int originX = originMap == null ? player.getLocation().getBlockX() : originMap.getCenterX();
        int originZ = originMap == null ? player.getLocation().getBlockZ() : originMap.getCenterZ();
        Set<Integer> managedMapIds = managedMapIds();
        for (Cell cell : cells) {
            MapView existing = mapInFrame(cell.frame());
            if (existing == null && cell.frame().getItem().getType() == Material.FILLED_MAP) {
                return "Creation stopped: at least one filled map has invalid map data.";
            }
            if (existing != null && (existing.getWorld() == null || !existing.getWorld().equals(world)
                    || existing.getScale().getValue() != scaleValue
                    || existing.getCenterX() != originX + cell.column() * width
                    || existing.getCenterZ() != originZ - cell.row() * width)) {
                return "Creation stopped: existing maps must already use the same scale and a seamless grid.";
            }
            if (existing != null && managedMapIds.contains(existing.getId())) {
                return "Creation stopped: map " + existing.getId() + " already belongs to another managed wall.";
            }
        }
        boolean locked = originMap == null
                ? plugin.getConfig().getBoolean("map-walls.defaults.locked", true) : originMap.isLocked();
        boolean markers = plugin.getConfig().getBoolean("map-walls.defaults.player-markers", false);
        boolean autoExpand = plugin.getConfig().getBoolean("map-walls.defaults.auto-expand", false);
        long interval = Math.max(1L, plugin.getConfig().getLong("map-walls.defaults.refresh-minutes", 30L));
        MapWall wall = new MapWall(name, world.getUID(), locked, markers, autoExpand, "", interval,
                System.currentTimeMillis() + interval * 60_000L, new ArrayList<>());

        List<ChangedFrame> changedFrames = new ArrayList<>();
        try {
            for (Cell cell : cells) {
                changedFrames.add(snapshot(cell.frame()));
                int centerX = originX + cell.column() * width;
                int centerZ = originZ - cell.row() * width;
                WallFrame created = attachFrame(wall, cell.frame(), cell.row(), cell.column(), centerX, centerZ,
                        MapView.Scale.valueOf((byte) scaleValue));
                wall.frames().add(created);
            }
        } catch (RuntimeException exception) {
            for (ChangedFrame changed : changedFrames) restore(changed);
            return "Wall creation was rolled back: " + exception.getMessage();
        }

        walls.put(name, wall);
        save();
        refresh(wall, player, true);
        return "Created '" + name + "' with " + cells.size() + " maps. "
                + (originMap == null ? "The selected frame uses your current X/Z position." : "Existing map IDs were preserved.");
    }

    public String expand(Player player, String rawName) {
        MapWall wall = walls.get(normalizeName(rawName));
        if (wall == null) return "No managed wall named '" + rawName + "' exists.";
        ItemFrame reference = targetFrame(player).orElse(null);
        WallFrame referenceData = reference == null ? null : findFrame(wall, reference.getUniqueId());
        if (referenceData == null) return "Look directly at a frame that already belongs to '" + wall.name() + "'.";

        List<Cell> connected = connectedFrames(reference);
        int maxFrames = Math.max(1, plugin.getConfig().getInt("map-walls.max-frames", 256));
        int remaining = maxFrames - wall.frames().size();
        int added = 0;
        MapView referenceMap = Bukkit.getMap(referenceData.mapId());
        if (referenceMap == null) return "The selected wall map no longer exists.";
        int mapWidth = 128 * (1 << referenceMap.getScale().getValue());

        for (Cell cell : connected) {
            int row = referenceData.row() + cell.row();
            int column = referenceData.column() + cell.column();
            if (added >= remaining || hasCell(wall, row, column) || isManaged(cell.frame())
                    || !cell.frame().getItem().getType().isAir()) continue;
            int centerX = referenceData.centerX() + cell.column() * mapWidth;
            int centerZ = referenceData.centerZ() - cell.row() * mapWidth;
            wall.frames().add(attachFrame(wall, cell.frame(), row, column, centerX, centerZ, referenceMap.getScale()));
            added++;
        }
        if (added == 0) return "No adjacent empty frames were available. Occupied frames were left unchanged.";
        save();
        refresh(wall, player, true);
        return "Added " + added + " empty frame" + (added == 1 ? "" : "s") + " to '" + wall.name() + "'.";
    }

    public String remove(String rawName) {
        MapWall wall = walls.remove(normalizeName(rawName));
        if (wall == null) return "No managed wall named '" + rawName + "' exists.";
        for (WallFrame frame : wall.frames()) {
            MapView map = Bukkit.getMap(frame.mapId());
            if (map != null) detachOverlay(wall.name(), map);
            Entity entity = Bukkit.getEntity(frame.entityId());
            if (entity instanceof ItemFrame itemFrame) clearOwnedFrame(itemFrame, wall.name(), false);
        }
        save();
        return "Stopped managing '" + wall.name() + "'. Its frames and map items were not removed.";
    }

    public List<String> list() {
        if (walls.isEmpty()) return List.of("No managed map walls exist.");
        return walls.values().stream().map(wall -> wall.name() + ": " + wall.frames().size()
                + " maps, every " + wall.refreshMinutes() + " min, locked=" + wall.locked()
                + ", markers=" + wall.playerMarkers() + ", theme=" + wall.scheme.getId()
                + ", depth=" + (wall.depth == null ? "auto" : wall.depth)
                + ", auto-expand=" + wall.autoExpand()).toList();
    }

    public String forceRefresh(String rawName, CommandSender sender) {
        if (rawName.equalsIgnoreCase("all")) {
            walls.values().forEach(wall -> refresh(wall, sender, true));
            return "Queued " + walls.size() + " managed map walls for refresh.";
        }
        MapWall wall = walls.get(normalizeName(rawName));
        if (wall == null) return "No managed wall named '" + rawName + "' exists.";
        refresh(wall, sender, true);
        return "Queued '" + wall.name() + "' for refresh.";
    }

    public String configure(String rawName, String rawOption, String rawValue) {
        MapWall wall = walls.get(normalizeName(rawName));
        if (wall == null) return "No managed wall named '" + rawName + "' exists.";
        String option = rawOption.toLowerCase(Locale.ROOT);
        boolean renderChanged = false;
        switch (option) {
            case "theme", "scheme" -> {
                ColorScheme scheme = ColorScheme.fromString(rawValue);
                if (scheme == null) return "Unknown theme. Use /revealmap schemes to list themes.";
                wall.scheme = scheme;
                renderChanged = true;
            }
            case "depth" -> {
                if (rawValue.equalsIgnoreCase("auto") || rawValue.equalsIgnoreCase("surface")) wall.depth = null;
                else {
                    World world = Bukkit.getWorld(wall.worldId());
                    if (world == null) return "The wall's world is not loaded.";
                    try {
                        int depth = Integer.parseInt(rawValue);
                        if (depth < world.getMinHeight() || depth >= world.getMaxHeight()) {
                            return "Depth must be between " + world.getMinHeight() + " and " + (world.getMaxHeight() - 1) + ".";
                        }
                        wall.depth = depth;
                    } catch (NumberFormatException exception) { return "Depth must be a Y-level or auto."; }
                }
                renderChanged = true;
            }
            case "interval" -> {
                try {
                    long minutes = Long.parseLong(rawValue);
                    long minimum = Math.max(1, plugin.getConfig().getLong("map-walls.minimum-refresh-minutes", 5));
                    if (minutes < minimum) return "Refresh intervals must be at least " + minimum + " minutes.";
                    wall.refreshMinutes(minutes);
                    wall.nextRefresh(System.currentTimeMillis() + minutes * 60_000L);
                } catch (NumberFormatException exception) {
                    return "The interval must be a whole number of minutes.";
                }
            }
            case "locked" -> {
                Boolean value = parseBoolean(rawValue);
                if (value == null) return "Use on or off for the locked setting.";
                if (!value && requiresLock(wall)) return "Themed, depth, and Nether maps must stay locked to preserve their rendering.";
                wall.locked(value);
            }
            case "markers" -> {
                Boolean value = parseBoolean(rawValue);
                if (value == null) return "Use on or off for the markers setting.";
                wall.playerMarkers(value);
            }
            case "auto-expand" -> {
                Boolean value = parseBoolean(rawValue);
                if (value == null) return "Use on or off for the auto-expand setting.";
                wall.autoExpand(value);
            }
            case "label" -> wall.label(rawValue.equalsIgnoreCase("off") ? "" : rawValue.substring(0, Math.min(48, rawValue.length())));
            default -> { return "Unknown setting. Use theme, depth, interval, locked, markers, auto-expand, or label."; }
        }
        if (requiresLock(wall)) wall.locked(true);
        applyWallOptions(wall);
        save();
        if (renderChanged) refresh(wall, null, true);
        return "Updated " + option + " for '" + wall.name() + "'.";
    }

    public List<String> names() {
        return List.copyOf(walls.keySet());
    }

    public boolean isRefreshing(String name) { return refreshingWalls.contains(normalizeName(name)); }

    private WallFrame attachFrame(MapWall wall, ItemFrame frame, int row, int column,
                                  int centerX, int centerZ, MapView.Scale scale) {
        if (isManaged(frame)) {
            throw new IllegalStateException("a frame changed while the wall was being prepared");
        }
        ItemStack item = frame.getItem();
        MapView map = mapInFrame(frame);
        if (map == null) {
            if (!item.getType().isAir()) throw new IllegalStateException("a filled map has invalid map data");
            map = Bukkit.createMap(frame.getWorld());
            map.setWorld(frame.getWorld());
            map.setCenterX(centerX);
            map.setCenterZ(centerZ);
            map.setScale(scale);
            item = new ItemStack(Material.FILLED_MAP);
        }
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        if (requiresLock(wall)) wall.locked(true);
        map.setLocked(wall.locked());

        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(map);
        meta.getPersistentDataContainer().set(wallKey, PersistentDataType.STRING, wall.name());
        meta.getPersistentDataContainer().set(mapKey, PersistentDataType.INTEGER, map.getId());
        item.setItemMeta(meta);
        frame.setItem(item, false);
        frame.setRotation(Rotation.NONE);
        frame.getPersistentDataContainer().set(wallKey, PersistentDataType.STRING, wall.name());
        frame.getPersistentDataContainer().set(mapKey, PersistentDataType.INTEGER, map.getId());

        WallFrame wallFrame = new WallFrame(frame.getUniqueId(), map.getId(), row, column, centerX, centerZ, "");
        attachOverlay(wall, wallFrame, map);
        return wallFrame;
    }

    private void refreshDueWalls() {
        long now = System.currentTimeMillis();
        for (MapWall wall : walls.values()) {
            if (wall.nextRefresh() <= now) refresh(wall, null, false);
        }
    }

    private void refresh(MapWall wall, CommandSender sender, boolean forced) {
        if (!refreshingWalls.add(wall.name())) {
            if (forced) pendingRefreshes.add(wall.name());
            if (sender != null) sender.sendMessage("Map wall '" + wall.name() + "' already has a refresh in progress.");
            return;
        }
        World world = Bukkit.getWorld(wall.worldId());
        if (world == null) {
            refreshingWalls.remove(wall.name());
            if (sender != null) sender.sendMessage("World for '" + wall.name() + "' is not loaded.");
            return;
        }
        wall.nextRefresh(System.currentTimeMillis() + wall.refreshMinutes() * 60_000L);
        List<WallFrame> eligible = wall.frames().stream().filter(frame -> validManagedMap(wall, frame))
                .sorted(Comparator.comparingInt(frame -> Math.abs(frame.row()) + Math.abs(frame.column())))
                .toList();
        if (eligible.isEmpty()) {
            refreshingWalls.remove(wall.name());
            if (sender != null) sender.sendMessage("No loaded, unchanged wall frames were available to refresh.");
            save();
            return;
        }

        RefreshProgress progress = new RefreshProgress(eligible.size(), sender, wall.name());
        if (sender != null) {
            MapView origin = Bukkit.getMap(eligible.get(0).mapId());
            int width = origin == null ? 128 : 128 * (1 << origin.getScale().getValue());
            sender.sendMessage("Rendering " + eligible.size() + " maps for '" + wall.name() + "' ("
                    + width + " x " + width + " blocks per map). Terrain appears progressively, starting near the selected frame.");
            if (!plugin.getConfig().getBoolean("rendering.generate-missing-chunks", false))
                sender.sendMessage("Only existing terrain is shown. Maps outside generated terrain remain transparent; larger scales can contain mostly empty areas.");
        }
        for (WallFrame frame : eligible) {
            MapView map = Bukkit.getMap(frame.mapId());
            if (map == null) {
                progress.complete(MapRenderService.RenderResult.failure("map " + frame.mapId() + " is missing"));
                continue;
            }
            map.setLocked(wall.locked() || requiresLock(wall));
            attachOverlay(wall, frame, map);
            renderer.render(map, world, wall.depth, wall.scheme, result -> {
                if (result.success()) frame.lastHash(result.hash());
                if (result.success() && validManagedMap(wall, frame)) sendFrameMap(wall, frame, null);
                if (!result.success()) plugin.getLogger().warning("Wall '" + wall.name() + "', map " + frame.mapId() + ": " + result.error());
                progress.complete(result);
                if (progress.done()) save();
            }, rows -> {
                progress.currentRows = rows;
                if (validManagedMap(wall, frame)) sendFrameMap(wall, frame, null);
            });
        }
        if (!forced) save();
    }

    private boolean requiresLock(MapWall wall) {
        World world = Bukkit.getWorld(wall.worldId());
        return wall.scheme != ColorScheme.NORMAL || wall.depth != null
                || (world != null && world.getEnvironment() == World.Environment.NETHER);
    }

    private void sendFrameMap(MapWall wall, WallFrame frame, Player onlyViewer) {
        Entity entity = Bukkit.getEntity(frame.entityId());
        MapView map = Bukkit.getMap(frame.mapId());
        if (!(entity instanceof ItemFrame itemFrame) || map == null || !validManagedMap(wall, frame)) return;
        List<Player> viewers = onlyViewer == null ? itemFrame.getWorld().getPlayers() : List.of(onlyViewer);
        for (Player viewer : viewers) {
            if (viewer.isOnline() && viewer.getWorld().equals(itemFrame.getWorld())
                    && viewer.getLocation().distanceSquared(itemFrame.getLocation()) <= 64 * 64) viewer.sendMap(map);
        }
    }

    private void resendWalls(Player viewer) {
        int delay = 10;
        for (MapWall wall : walls.values()) {
            if (!viewer.getWorld().getUID().equals(wall.worldId())) continue;
            for (WallFrame frame : wall.frames()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendFrameMap(wall, frame, viewer), delay++ / 4);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) { resendWalls(event.getPlayer()); }

    @EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) { resendWalls(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> resendWalls(event.getPlayer()), 20L);
    }

    private boolean validManagedMap(MapWall wall, WallFrame frame) {
        Entity entity = Bukkit.getEntity(frame.entityId());
        if (!(entity instanceof ItemFrame itemFrame) || !wall.name().equals(readWall(itemFrame))) return false;
        ItemStack item = itemFrame.getItem();
        if (item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)
                || !meta.hasMapView() || meta.getMapView() == null) return false;
        return meta.getMapView().getId() == frame.mapId()
                && wall.name().equals(meta.getPersistentDataContainer().get(wallKey, PersistentDataType.STRING));
    }

    private void applyWallOptions(MapWall wall) {
        if (requiresLock(wall)) wall.locked(true);
        for (WallFrame frame : wall.frames()) {
            MapView map = Bukkit.getMap(frame.mapId());
            if (map != null) {
                map.setLocked(wall.locked());
                map.setTrackingPosition(false);
                attachOverlay(wall, frame, map);
            }
        }
    }

    private void attachOverlays() {
        walls.values().forEach(this::applyWallOptions);
    }

    private void attachOverlay(MapWall wall, WallFrame frame, MapView map) {
        detachOverlay(wall.name(), map);
        if (wall.playerMarkers() || (!wall.label().isBlank() && frame.row() == 0 && frame.column() == 0)) {
            map.addRenderer(new WallOverlayRenderer(wall.name(), wall.worldId(), wall.playerMarkers(),
                    frame.row() == 0 && frame.column() == 0 ? wall.label() : ""));
        }
    }

    private static void detachOverlay(String wallName, MapView map) {
        map.getRenderers().stream()
                .filter(renderer -> renderer instanceof WallOverlayRenderer overlay
                        && overlay.wallName().equals(wallName))
                .toList().forEach(map::removeRenderer);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFramePlaced(HangingPlaceEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !frame.getItem().getType().isAir()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> tryAutoExpand(frame));
    }

    private void tryAutoExpand(ItemFrame frame) {
        if (!frame.isValid() || !frame.getItem().getType().isAir() || isManaged(frame)) return;
        for (MapWall wall : walls.values()) {
            if (!wall.autoExpand() || !wall.worldId().equals(frame.getWorld().getUID())) continue;
            for (WallFrame existing : wall.frames()) {
                Entity entity = Bukkit.getEntity(existing.entityId());
                if (!(entity instanceof ItemFrame neighbor) || neighbor.getFacing() != frame.getFacing()) continue;
                Cell delta = relativeCell(neighbor, frame);
                if (Math.abs(delta.row()) + Math.abs(delta.column()) != 1) continue;
                int row = existing.row() + delta.row();
                int column = existing.column() + delta.column();
                if (hasCell(wall, row, column)) return;
                MapView neighborMap = Bukkit.getMap(existing.mapId());
                if (neighborMap == null || wall.frames().size() >= plugin.getConfig().getInt("map-walls.max-frames", 256)) return;
                int width = 128 * (1 << neighborMap.getScale().getValue());
                wall.frames().add(attachFrame(wall, frame, row, column,
                        existing.centerX() + delta.column() * width,
                        existing.centerZ() - delta.row() * width, neighborMap.getScale()));
                save();
                refresh(wall, null, true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameBroken(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        String wallName = readWall(frame);
        if (wallName == null) return;
        MapWall wall = walls.get(wallName);
        if (wall == null) return;
        WallFrame removed = findFrame(wall, frame.getUniqueId());
        if (removed != null && wall.frames().remove(removed)) {
            MapView map = Bukkit.getMap(removed.mapId());
            if (map != null) detachOverlay(wallName, map);
            save();
        }
    }

    private Optional<ItemFrame> targetFrame(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), 8.0, 0.25,
                entity -> entity instanceof ItemFrame);
        return result != null && result.getHitEntity() instanceof ItemFrame frame ? Optional.of(frame) : Optional.empty();
    }

    private List<Cell> connectedFrames(ItemFrame origin) {
        int span = Math.max(2, plugin.getConfig().getInt("map-walls.discovery-span", 24));
        Map<CellKey, ItemFrame> candidates = new HashMap<>();
        for (Entity entity : origin.getWorld().getNearbyEntities(origin.getLocation(), span, span, span,
                entity -> entity instanceof ItemFrame)) {
            ItemFrame frame = (ItemFrame) entity;
            if (frame.getFacing() != origin.getFacing() || !samePlane(origin, frame)) continue;
            Cell relative = relativeCell(origin, frame);
            candidates.put(new CellKey(relative.row(), relative.column()), frame);
        }
        candidates.put(new CellKey(0, 0), origin);

        Queue<CellKey> queue = new ArrayDeque<>();
        Set<CellKey> visited = new HashSet<>();
        queue.add(new CellKey(0, 0));
        while (!queue.isEmpty()) {
            CellKey current = queue.remove();
            if (!visited.add(current)) continue;
            for (CellKey next : List.of(new CellKey(current.row() + 1, current.column()),
                    new CellKey(current.row() - 1, current.column()),
                    new CellKey(current.row(), current.column() + 1),
                    new CellKey(current.row(), current.column() - 1))) {
                if (candidates.containsKey(next) && !visited.contains(next)) queue.add(next);
            }
        }
        return visited.stream().map(key -> new Cell(candidates.get(key), key.row(), key.column()))
                .sorted(Comparator.comparingInt(Cell::row).reversed().thenComparingInt(Cell::column)).toList();
    }

    private static boolean samePlane(ItemFrame first, ItemFrame second) {
        return switch (first.getFacing()) {
            case NORTH, SOUTH -> first.getLocation().getBlockZ() == second.getLocation().getBlockZ();
            case EAST, WEST -> first.getLocation().getBlockX() == second.getLocation().getBlockX();
            default -> false;
        };
    }

    private static Cell relativeCell(ItemFrame origin, ItemFrame frame) {
        int deltaX = frame.getLocation().getBlockX() - origin.getLocation().getBlockX();
        int deltaY = frame.getLocation().getBlockY() - origin.getLocation().getBlockY();
        int deltaZ = frame.getLocation().getBlockZ() - origin.getLocation().getBlockZ();
        int column = switch (origin.getFacing()) {
            case NORTH -> -deltaX;
            case SOUTH -> deltaX;
            case EAST -> -deltaZ;
            case WEST -> deltaZ;
            default -> 0;
        };
        return new Cell(frame, deltaY, column);
    }

    private static boolean isRectangle(List<Cell> cells) {
        int minRow = cells.stream().mapToInt(Cell::row).min().orElse(0);
        int maxRow = cells.stream().mapToInt(Cell::row).max().orElse(0);
        int minColumn = cells.stream().mapToInt(Cell::column).min().orElse(0);
        int maxColumn = cells.stream().mapToInt(Cell::column).max().orElse(0);
        return cells.size() == (maxRow - minRow + 1) * (maxColumn - minColumn + 1);
    }

    private static boolean isVertical(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private boolean isManaged(ItemFrame frame) {
        String wall = readWall(frame);
        return wall != null && walls.containsKey(wall);
    }

    private String readWall(ItemFrame frame) {
        return frame.getPersistentDataContainer().get(wallKey, PersistentDataType.STRING);
    }

    private void clearOwnedFrame(ItemFrame frame, String wallName, boolean removeOwnedMap) {
        PersistentDataContainer data = frame.getPersistentDataContainer();
        if (!wallName.equals(data.get(wallKey, PersistentDataType.STRING))) return;
        ItemStack item = frame.getItem();
        if (item.getItemMeta() instanceof MapMeta meta
                && wallName.equals(meta.getPersistentDataContainer().get(wallKey, PersistentDataType.STRING))) {
            if (removeOwnedMap) {
                frame.setItem(new ItemStack(Material.AIR), false);
            } else {
                meta.getPersistentDataContainer().remove(wallKey);
                meta.getPersistentDataContainer().remove(mapKey);
                item.setItemMeta(meta);
                frame.setItem(item, false);
            }
        }
        data.remove(wallKey);
        data.remove(mapKey);
    }

    private ChangedFrame snapshot(ItemFrame frame) {
        MapView map = mapInFrame(frame);
        PersistentDataContainer data = frame.getPersistentDataContainer();
        return new ChangedFrame(frame, frame.getItem().clone(), frame.getRotation(),
                data.get(wallKey, PersistentDataType.STRING), data.get(mapKey, PersistentDataType.INTEGER), map,
                map != null && map.isTrackingPosition(), map != null && map.isUnlimitedTracking(),
                map != null && map.isLocked());
    }

    private void restore(ChangedFrame changed) {
        try {
            if (changed.map() != null) {
                changed.map().setTrackingPosition(changed.tracking());
                changed.map().setUnlimitedTracking(changed.unlimitedTracking());
                changed.map().setLocked(changed.locked());
            }
            changed.frame().setItem(changed.item(), false);
            changed.frame().setRotation(changed.rotation());
            PersistentDataContainer data = changed.frame().getPersistentDataContainer();
            data.remove(wallKey);
            data.remove(mapKey);
            if (changed.wallOwner() != null) data.set(wallKey, PersistentDataType.STRING, changed.wallOwner());
            if (changed.mapOwner() != null) data.set(mapKey, PersistentDataType.INTEGER, changed.mapOwner());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not fully restore a frame after wall creation failed: "
                    + exception.getMessage());
        }
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT).trim();
    }

    private static Boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")
                || value.equalsIgnoreCase("yes") || value.equals("1")) return true;
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")
                || value.equalsIgnoreCase("no") || value.equals("0")) return false;
        return null;
    }

    private static WallFrame findFrame(MapWall wall, UUID entityId) {
        return wall.frames().stream().filter(frame -> frame.entityId().equals(entityId)).findFirst().orElse(null);
    }

    private static boolean hasCell(MapWall wall, int row, int column) {
        return wall.frames().stream().anyMatch(frame -> frame.row() == row && frame.column() == column);
    }

    private Set<Integer> managedMapIds() {
        Set<Integer> ids = new HashSet<>();
        walls.values().forEach(wall -> wall.frames().forEach(frame -> ids.add(frame.mapId())));
        return ids;
    }

    private static MapView mapInFrame(ItemFrame frame) {
        ItemStack item = frame.getItem();
        if (item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)
                || !meta.hasMapView()) return null;
        return meta.getMapView();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = yaml.getConfigurationSection("walls");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) continue;
            try {
                UUID world = UUID.fromString(section.getString("world", ""));
                List<WallFrame> frames = new ArrayList<>();
                for (Map<?, ?> serialized : section.getMapList("frames")) {
                    frames.add(new WallFrame(
                            UUID.fromString(String.valueOf(serialized.get("entity"))),
                            integer(serialized, "map"), integer(serialized, "row"), integer(serialized, "column"),
                            integer(serialized, "center-x"), integer(serialized, "center-z"),
                            serialized.containsKey("last-hash") ? String.valueOf(serialized.get("last-hash")) : ""));
                }
                long interval = Math.max(1L, section.getLong("refresh-minutes", 30L));
                walls.put(name, new MapWall(name, world, section.getBoolean("locked", true),
                        section.getBoolean("player-markers", false), section.getBoolean("auto-expand", false),
                        section.getString("label", ""), interval,
                        section.getLong("next-refresh", System.currentTimeMillis() + interval * 60_000L), frames));
                MapWall loaded = walls.get(name);
                ColorScheme scheme = ColorScheme.fromString(section.getString("theme", "normal"));
                loaded.scheme = scheme == null ? ColorScheme.NORMAL : scheme;
                loaded.depth = section.contains("depth") ? section.getInt("depth") : null;
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid map wall '" + name + "': " + exception.getMessage());
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (MapWall wall : walls.values()) {
            String path = "walls." + wall.name();
            yaml.set(path + ".world", wall.worldId().toString());
            yaml.set(path + ".locked", wall.locked());
            yaml.set(path + ".player-markers", wall.playerMarkers());
            yaml.set(path + ".auto-expand", wall.autoExpand());
            yaml.set(path + ".label", wall.label());
            yaml.set(path + ".theme", wall.scheme.getId());
            yaml.set(path + ".depth", wall.depth);
            yaml.set(path + ".refresh-minutes", wall.refreshMinutes());
            yaml.set(path + ".next-refresh", wall.nextRefresh());
            yaml.set(path + ".frames", wall.frames().stream().map(frame -> {
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("entity", frame.entityId().toString());
                serialized.put("map", frame.mapId());
                serialized.put("row", frame.row());
                serialized.put("column", frame.column());
                serialized.put("center-x", frame.centerX());
                serialized.put("center-z", frame.centerZ());
                serialized.put("last-hash", frame.lastHash());
                return serialized;
            }).toList());
        }
        try {
            yaml.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save map walls: " + exception.getMessage());
        }
    }

    private static int integer(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    public void shutdown() {
        save();
    }

    private record Cell(ItemFrame frame, int row, int column) { }
    private record ChangedFrame(ItemFrame frame, ItemStack item, Rotation rotation, String wallOwner,
                                Integer mapOwner, MapView map, boolean tracking,
                                boolean unlimitedTracking, boolean locked) { }
    private record CellKey(int row, int column) { }

    private static final class MapWall {
        private final String name;
        private final UUID worldId;
        private boolean locked;
        private boolean playerMarkers;
        private boolean autoExpand;
        private String label;
        private ColorScheme scheme = ColorScheme.NORMAL;
        private Integer depth;
        private long refreshMinutes;
        private long nextRefresh;
        private final List<WallFrame> frames;

        private MapWall(String name, UUID worldId, boolean locked, boolean playerMarkers, boolean autoExpand,
                        String label, long refreshMinutes, long nextRefresh, List<WallFrame> frames) {
            this.name = name;
            this.worldId = worldId;
            this.locked = locked;
            this.playerMarkers = playerMarkers;
            this.autoExpand = autoExpand;
            this.label = label;
            this.refreshMinutes = refreshMinutes;
            this.nextRefresh = nextRefresh;
            this.frames = frames;
        }

        private String name() { return name; }
        private UUID worldId() { return worldId; }
        private boolean locked() { return locked; }
        private void locked(boolean value) { locked = value; }
        private boolean playerMarkers() { return playerMarkers; }
        private void playerMarkers(boolean value) { playerMarkers = value; }
        private boolean autoExpand() { return autoExpand; }
        private void autoExpand(boolean value) { autoExpand = value; }
        private String label() { return label; }
        private void label(String value) { label = value; }
        private long refreshMinutes() { return refreshMinutes; }
        private void refreshMinutes(long value) { refreshMinutes = value; }
        private long nextRefresh() { return nextRefresh; }
        private void nextRefresh(long value) { nextRefresh = value; }
        private List<WallFrame> frames() { return frames; }
    }

    private static final class WallFrame {
        private final UUID entityId;
        private final int mapId;
        private final int row;
        private final int column;
        private final int centerX;
        private final int centerZ;
        private String lastHash;

        private WallFrame(UUID entityId, int mapId, int row, int column, int centerX, int centerZ, String lastHash) {
            this.entityId = entityId;
            this.mapId = mapId;
            this.row = row;
            this.column = column;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.lastHash = lastHash;
        }

        private UUID entityId() { return entityId; }
        private int mapId() { return mapId; }
        private int row() { return row; }
        private int column() { return column; }
        private int centerX() { return centerX; }
        private int centerZ() { return centerZ; }
        private String lastHash() { return lastHash; }
        private void lastHash(String value) { lastHash = value; }
    }

    private final class RefreshProgress {
        private int remaining;
        private int changedMaps;
        private int changedPixels;
        private int failures;
        private int missingChunks;
        private int emptyMaps;
        private int currentRows;
        private final int total;
        private final BukkitTask notificationTask;
        private String firstError;
        private final CommandSender sender;
        private final String wall;

        private RefreshProgress(int remaining, CommandSender sender, String wall) {
            this.remaining = remaining;
            this.total = remaining;
            this.sender = sender;
            this.wall = wall;
            this.notificationTask = sender == null ? null : plugin.getServer().getScheduler().runTaskTimer(plugin,
                    () -> sender.sendMessage("Map wall '" + wall + "': " + (total - this.remaining) + "/" + total
                            + " maps complete; current map " + (currentRows * 100 / 128) + "%."), 200L, 200L);
        }

        private void complete(MapRenderService.RenderResult result) {
            remaining--;
            currentRows = 0;
            if (!result.success()) {
                failures++;
                if (firstError == null) firstError = result.error();
            }
            else if (result.changedPixels() > 0) {
                changedMaps++;
                changedPixels += result.changedPixels();
            }
            if (result.success()) {
                missingChunks += result.requestedChunks() - result.capturedChunks();
                if (result.capturedChunks() == 0) emptyMaps++;
            }
            if (remaining == 0 && sender != null) {
                sender.sendMessage("Map wall '" + wall + "' refreshed: " + changedMaps + " changed maps, "
                        + changedPixels + " changed pixels" + (failures == 0 ? "." : ", " + failures + " failures: " + firstError));
                if (missingChunks > 0) sender.sendMessage(missingChunks + " terrain chunks do not exist; " + emptyMaps
                        + " maps have no generated terrain. Use a lower scale or explore/pregenerate the area, then refresh."
                        + " New terrain generation is opt-in via rendering.generate-missing-chunks.");
            }
            if (remaining == 0) {
                if (notificationTask != null) notificationTask.cancel();
                refreshingWalls.remove(wall);
                MapWall definition = walls.get(wall);
                if (definition != null) {
                    if (pendingRefreshes.remove(wall)) plugin.getServer().getScheduler().runTask(plugin, () -> refresh(definition, null, true));
                    // A second pass after all maps finish repairs stale client frame textures.
                    int delay = 20;
                    for (WallFrame frame : definition.frames()) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendFrameMap(definition, frame, null), delay++ / 4);
                    }
                }
            }
        }

        private boolean done() { return remaining == 0; }
    }

    private final class WallOverlayRenderer extends MapRenderer {
        private final String wallName;
        private final UUID worldId;
        private final boolean players;
        private final String label;

        private WallOverlayRenderer(String wallName, UUID worldId, boolean players, String label) {
            super(true);
            this.wallName = wallName;
            this.worldId = worldId;
            this.players = players;
            this.label = label;
        }

        private String wallName() { return wallName; }

        @Override
        public void render(MapView map, MapCanvas canvas, Player viewer) {
            MapCursorCollection cursors = new MapCursorCollection();
            if (!label.isBlank()) {
                cursors.addCursor(new MapCursor((byte) 0, (byte) 0, (byte) 0,
                        MapCursor.Type.BANNER_ORANGE, true, Component.text(label)));
            }
            if (players) {
                World world = Bukkit.getWorld(worldId);
                if (world != null) {
                    int scale = 1 << map.getScale().getValue();
                    for (Player player : world.getPlayers()) {
                        int cursorX = (int) Math.round((player.getLocation().getX() - map.getCenterX()) * 2.0 / scale);
                        int cursorZ = (int) Math.round((player.getLocation().getZ() - map.getCenterZ()) * 2.0 / scale);
                        if (cursorX < -127 || cursorX > 127 || cursorZ < -127 || cursorZ > 127) continue;
                        byte direction = (byte) Math.floorMod((int) Math.floor((player.getLocation().getYaw() + 180.0) * 16.0 / 360.0), 16);
                        cursors.addCursor(new MapCursor((byte) cursorX, (byte) cursorZ, direction,
                                MapCursor.Type.WHITE_POINTER, true, player.name()));
                    }
                }
            }
            canvas.setCursors(cursors);
        }
    }
}
