package com.github.maprevealer;

import com.github.maprevealer.commands.RevealMapCommand;
import com.github.maprevealer.util.MapRenderService;
import com.github.maprevealer.wall.MapWallManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers map-reveal commands and exposes the configured rendering service.
 */
public class MapRevealerPlugin extends JavaPlugin {
    private MapRenderService renderer;
    private MapWallManager walls;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Upgrade only the old shipped limits; preserve administrator-customized limits.
        if (!getConfig().contains("config-version", true)) {
            if (getConfig().getInt("map-walls.max-scale", 4) == 1) getConfig().set("map-walls.max-scale", 4);
            if (getConfig().getInt("rendering.max-chunks-per-map", 16384) == 4096) getConfig().set("rendering.max-chunks-per-map", 16384);
            getConfig().set("config-version", 2);
            saveConfig();
        }
        renderer = new MapRenderService(this);
        walls = new MapWallManager(this, renderer);
        getServer().getPluginManager().registerEvents(walls, this);
        RevealMapCommand revealCommand = new RevealMapCommand(this);
        getCommand("revealmap").setExecutor(revealCommand);
        getCommand("revealmap").setTabCompleter(revealCommand);
        getLogger().info("MapRevealer has been enabled!");
    }

    @Override
    public void onDisable() {
        if (walls != null) walls.shutdown();
        if (renderer != null) renderer.shutdown();
        getLogger().info("MapRevealer has been disabled!");
    }

    public MapRenderService renderer() { return renderer; }
    public MapWallManager walls() { return walls; }
}
