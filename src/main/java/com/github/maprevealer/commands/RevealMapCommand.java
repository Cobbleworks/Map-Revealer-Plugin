package com.github.maprevealer.commands;

import com.github.maprevealer.MapRevealerPlugin;
import com.github.maprevealer.util.ColorScheme;
import com.github.maprevealer.util.MapRevealer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validates reveal options and delegates map rendering for the held map item.
 */
public class RevealMapCommand implements CommandExecutor, TabCompleter {

    private final MapRevealerPlugin plugin;

    public RevealMapCommand(MapRevealerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("wall")) {
            return handleWallCommand(player, Arrays.copyOfRange(args, 1, args.length));
        }
        if (!player.hasPermission("maprevealer.reveal")) {
            player.sendMessage("§cYou do not have permission to reveal maps.");
            return true;
        }

        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("lock")) {
                return handleLockCommand(player);
            }

            if (subCommand.equals("schemes") || subCommand.equals("colors")) {
                return handleSchemesCommand(player);
            }

            if (subCommand.equals("help")) {
                return handleHelpCommand(player);
            }

        }

        return handleRevealCommand(player, args.length > 0 && args[0].equalsIgnoreCase("reveal")
                ? Arrays.copyOfRange(args, 1, args.length) : args);
    }

    private boolean handleLockCommand(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != Material.FILLED_MAP) {
            player.sendMessage("§cYou must be holding a filled map to use this command!");
            return true;
        }

        MapMeta mapMeta = (MapMeta) itemInHand.getItemMeta();
        if (mapMeta == null || !mapMeta.hasMapView()) {
            player.sendMessage("§cThis map does not have valid map data!");
            return true;
        }

        MapView mapView = mapMeta.getMapView();
        if (mapView == null) {
            player.sendMessage("§cCould not retrieve map view!");
            return true;
        }

        if (MapRevealer.isMapLocked(mapView)) {
            player.sendMessage("§eThis map is already locked!");
            return true;
        }

        if (MapRevealer.lockMap(mapView)) {
            player.sendMessage("§aMap locked successfully! It will no longer update when you explore.");
            player.sendMessage("§7Map ID: " + mapView.getId());
        } else {
            player.sendMessage("§cFailed to lock the map. Please try again.");
        }

        return true;
    }

    private boolean handleSchemesCommand(Player player) {
        player.sendMessage("§6=== Available Color Schemes ===");
        for (ColorScheme scheme : ColorScheme.values()) {
            player.sendMessage("§e" + scheme.getId() + " §7- " + scheme.getDescription());
        }
        player.sendMessage("§7Usage: /revealmap [depth] [scheme]");
        return true;
    }

    private boolean handleHelpCommand(Player player) {
        player.sendMessage("§6=== MapRevealer Help ===");
        player.sendMessage("§e/revealmap §7- Reveal the map in your hand");
        player.sendMessage("§e/revealmap [depth] §7- Reveal at specific Y level");
        player.sendMessage("§e/revealmap [depth] [scheme] §7- Reveal with color scheme");
        player.sendMessage("§e/revealmap reveal [depth] [theme] §7- Render the held map (scales 0-4)");
        player.sendMessage("§e/revealmap lock §7- Lock map to prevent updates");
        player.sendMessage("§e/revealmap schemes §7- List available color schemes");
        if (player.hasPermission("maprevealer.wall.manage")) {
            player.sendMessage("§e/revealmap wall §7- Manage automatically refreshed item-frame map walls");
        }
        player.sendMessage("§e/revealmap help §7- Show this help");
        return true;
    }

    private boolean handleRevealCommand(Player player, String[] args) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != Material.FILLED_MAP) {
            player.sendMessage("§cYou must be holding a filled map to use this command!");
            return true;
        }

        MapMeta mapMeta = (MapMeta) itemInHand.getItemMeta();
        if (mapMeta == null || !mapMeta.hasMapView()) {
            player.sendMessage("§cThis map does not have valid map data!");
            return true;
        }

        MapView mapView = mapMeta.getMapView();
        if (mapView == null) {
            player.sendMessage("§cCould not retrieve map view!");
            return true;
        }
        World renderWorld = mapView.getWorld();
        if (renderWorld == null) {
            player.sendMessage("§cThis map is not associated with a loaded world!");
            return true;
        }

        Integer depth = null;
        ColorScheme colorScheme = ColorScheme.NORMAL;
        
        for (String arg : args) {
            try {
                int parsedDepth = Integer.parseInt(arg);
                int minHeight = renderWorld.getMinHeight();
                int maxHeight = renderWorld.getMaxHeight();
                if (parsedDepth < minHeight || parsedDepth >= maxHeight) {
                    player.sendMessage("§cDepth must be between " + minHeight + " and " + (maxHeight - 1) + "!");
                    return true;
                }
                depth = parsedDepth;
                continue;
            } catch (NumberFormatException ignored) {}

            ColorScheme scheme = ColorScheme.fromString(arg);
            if (scheme != null) {
                colorScheme = scheme;
            } else {
                player.sendMessage("§cUnknown argument: " + arg);
                player.sendMessage("§7Use /revealmap help for usage info, or /revealmap schemes for color schemes.");
                return true;
            }
        }

        final Integer finalDepth = plugin.renderer().effectiveDepth(renderWorld, depth);
        final ColorScheme finalScheme = colorScheme;
        
        String depthInfo = finalDepth != null ? " at Y=" + finalDepth : " (surface)";
        String schemeInfo = colorScheme != ColorScheme.NORMAL ? " with §d" + colorScheme.getId() + "§a scheme" : "";
        player.sendMessage("§aRevealing map" + depthInfo + schemeInfo + "... This may take a moment.");

        long startTime = System.currentTimeMillis();
        plugin.renderer().render(mapView, renderWorld, finalDepth, finalScheme, result -> {
            if (!result.success()) {
                if (player.isOnline()) player.sendMessage("§cMap reveal failed: " + result.error());
                return;
            }
            if ((finalScheme != ColorScheme.NORMAL || finalDepth != null
                    || renderWorld.getEnvironment() == World.Environment.NETHER) && !MapRevealer.isMapLocked(mapView)) {
                MapRevealer.lockMap(mapView);
            }
            long duration = System.currentTimeMillis() - startTime;
            if (player.isOnline()) {
                player.sendMap(mapView);
                player.sendMessage("§aMap revealed successfully! §7(" + result.changedPixels()
                        + " changed pixels, " + duration + "ms)");
                player.sendMessage("§7Map Center: " + mapView.getCenterX() + ", " + mapView.getCenterZ());
                player.sendMessage("§7Scale: " + mapView.getScale().name() + " (1:" + (1 << mapView.getScale().getValue()) + ")");
                if (result.capturedChunks() < result.requestedChunks()) {
                    player.sendMessage("§e" + (result.requestedChunks() - result.capturedChunks())
                            + " ungenerated terrain chunks were left blank.");
                }
                if (finalDepth != null) {
                    player.sendMessage("§7Depth: Y=" + finalDepth);
                }
                if (finalScheme != ColorScheme.NORMAL) {
                    player.sendMessage("§7Color Scheme: " + finalScheme.getId() + " (map locked to preserve scheme)");
                }
            }
        }, rows -> { if (player.isOnline()) player.sendMap(mapView); });

        return true;
    }

    private boolean handleWallCommand(Player player, String[] args) {
        if (!player.hasPermission("maprevealer.wall.manage")) {
            player.sendMessage("§cYou do not have permission to manage map walls.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.sendMessage("§6=== Managed Map Walls ===");
            player.sendMessage("§e/revealmap wall create <name> [scale] §7- Fill a connected rectangle of empty frames");
            player.sendMessage("§e/revealmap wall expand <name> §7- Add connected empty frames");
            player.sendMessage("§e/revealmap wall refresh <name|all> §7- Force a terrain refresh");
            player.sendMessage("§e/revealmap wall set <name> interval <minutes>");
            player.sendMessage("§e/revealmap wall set <name> <locked|markers|auto-expand> <on|off>");
            player.sendMessage("§e/revealmap wall set <name> label <text|off>");
            player.sendMessage("§e/revealmap wall set <name> theme <theme> §7- Apply sepia, nether, and other themes");
            player.sendMessage("§e/revealmap wall set <name> depth <Y|auto> §7- Choose terrain height; Nether defaults to Y=64");
            player.sendMessage("§e/revealmap wall list §7- Show managed walls");
            player.sendMessage("§e/revealmap wall remove <name> §7- Stop managing without removing maps");
            return true;
        }

        String action = args[0].toLowerCase();
        String result;
        switch (action) {
            case "create" -> {
                if (args.length < 2 || args.length > 3) result = "Usage: /revealmap wall create <name> [scale: 0-4]";
                else {
                    try {
                        Integer scale = args.length >= 3 ? Integer.parseInt(args[2]) : null;
                        result = plugin.walls().create(player, args[1], scale);
                    } catch (NumberFormatException exception) {
                        result = "Scale must be a whole number.";
                    }
                }
            }
            case "expand" -> result = args.length < 2
                    ? "Usage: /revealmap wall expand <name>" : plugin.walls().expand(player, args[1]);
            case "refresh" -> result = args.length < 2
                    ? "Usage: /revealmap wall refresh <name|all>" : plugin.walls().forceRefresh(args[1], player);
            case "remove" -> result = args.length < 2
                    ? "Usage: /revealmap wall remove <name>" : plugin.walls().remove(args[1]);
            case "list" -> {
                player.sendMessage("§6=== Managed Map Walls ===");
                plugin.walls().list().forEach(line -> player.sendMessage("§7" + line));
                return true;
            }
            case "set" -> result = args.length < 4
                    ? "Usage: /revealmap wall set <name> <setting> <value>"
                    : plugin.walls().configure(args[1], args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            default -> result = "Unknown wall action. Use /revealmap wall help.";
        }
        player.sendMessage("§7" + result);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            List<String> subCommands = new ArrayList<>(Arrays.asList("reveal", "lock", "schemes", "colors", "help"));
            if (sender.hasPermission("maprevealer.wall.manage")) subCommands.add("wall");
            for (String sub : subCommands) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }

            for (ColorScheme scheme : ColorScheme.values()) {
                if (scheme.getId().startsWith(partial)) {
                    completions.add(scheme.getId());
                }
            }
        } else if (args[0].equalsIgnoreCase("wall")) {
            return completeWall(args);
        } else if (args[0].equalsIgnoreCase("reveal") && args.length <= 3) {
            return prefix(Arrays.stream(ColorScheme.values()).map(ColorScheme::getId).toList(), args[args.length - 1]);
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();

            try {
                Integer.parseInt(args[0]);
                for (ColorScheme scheme : ColorScheme.values()) {
                    if (scheme.getId().startsWith(partial)) {
                        completions.add(scheme.getId());
                    }
                }
            } catch (NumberFormatException e) {
            }
        }
        
        return completions;
    }

    private List<String> completeWall(String[] args) {
        if (args.length == 2) {
            return prefix(List.of("create", "expand", "refresh", "set", "list", "remove", "help"), args[1]);
        }
        if (args.length == 3 && List.of("expand", "set", "remove").contains(args[1].toLowerCase())) {
            return prefix(plugin.walls().names(), args[2]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("refresh")) {
            List<String> names = new ArrayList<>(plugin.walls().names());
            names.add("all");
            return prefix(names, args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            return prefix(List.of("theme", "depth", "interval", "locked", "markers", "auto-expand", "label"), args[3]);
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("set") && List.of("theme", "scheme").contains(args[3].toLowerCase())) {
            return prefix(Arrays.stream(ColorScheme.values()).map(ColorScheme::getId).toList(), args[4]);
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("depth")) {
            return prefix(List.of("auto", "32", "64", "96"), args[4]);
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("set")
                && List.of("locked", "markers", "auto-expand").contains(args[3].toLowerCase())) {
            return prefix(List.of("on", "off"), args[4]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
            return prefix(List.of("0", "1", "2", "3", "4"), args[3]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> values, String partial) {
        String lower = partial.toLowerCase();
        return values.stream().filter(value -> value.toLowerCase().startsWith(lower)).toList();
    }
}
