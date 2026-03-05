package it.patric.cittaexp;

import org.bukkit.plugin.java.JavaPlugin;

public final class CittaExpPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("CittaEXP enabled.");
        // TODO: wire services and commands in future iterations.
    }

    @Override
    public void onDisable() {
        getLogger().info("CittaEXP disabled.");
    }
}
