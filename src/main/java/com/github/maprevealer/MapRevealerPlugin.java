package com.github.maprevealer;

import com.github.maprevealer.commands.RevealMapCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class MapRevealerPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RevealMapCommand revealCommand = new RevealMapCommand(this);
        getCommand("revealmap").setExecutor(revealCommand);
        getCommand("revealmap").setTabCompleter(revealCommand);
        getLogger().info("MapRevealer has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MapRevealer has been disabled!");
    }
}