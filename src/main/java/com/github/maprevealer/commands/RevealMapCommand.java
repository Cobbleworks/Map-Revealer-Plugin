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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        return handleRevealCommand(player, args);
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
        player.sendMessage("§e/revealmap lock §7- Lock map to prevent updates");
        player.sendMessage("§e/revealmap schemes §7- List available color schemes");
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

        Integer depth = null;
        ColorScheme colorScheme = ColorScheme.NORMAL;
        
        for (String arg : args) {
            try {
                int parsedDepth = Integer.parseInt(arg);
                int minHeight = player.getWorld().getMinHeight();
                int maxHeight = player.getWorld().getMaxHeight();
                if (parsedDepth < minHeight || parsedDepth > maxHeight) {
                    player.sendMessage("§cDepth must be between " + minHeight + " and " + maxHeight + "!");
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

        final Integer finalDepth = depth;
        final ColorScheme finalScheme = colorScheme;
        
        String depthInfo = depth != null ? " at Y=" + depth : " (surface)";
        String schemeInfo = colorScheme != ColorScheme.NORMAL ? " with §d" + colorScheme.getId() + "§a scheme" : "";
        player.sendMessage("§aRevealing map" + depthInfo + schemeInfo + "... This may take a moment.");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long startTime = System.currentTimeMillis();
            
            MapRevealer.revealMap(mapView, player.getWorld(), finalDepth, finalScheme);

            if (finalScheme != ColorScheme.NORMAL && !MapRevealer.isMapLocked(mapView)) {
                MapRevealer.lockMap(mapView);
            }
            
            long duration = System.currentTimeMillis() - startTime;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§aMap revealed successfully! (Took " + duration + "ms)");
                player.sendMessage("§7Map Center: " + mapView.getCenterX() + ", " + mapView.getCenterZ());
                player.sendMessage("§7Scale: " + mapView.getScale().name() + " (1:" + (1 << mapView.getScale().getValue()) + ")");
                if (finalDepth != null) {
                    player.sendMessage("§7Depth: Y=" + finalDepth);
                }
                if (finalScheme != ColorScheme.NORMAL) {
                    player.sendMessage("§7Color Scheme: " + finalScheme.getId() + " (map locked to preserve scheme)");
                }
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            List<String> subCommands = Arrays.asList("lock", "schemes", "colors", "help");
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
}