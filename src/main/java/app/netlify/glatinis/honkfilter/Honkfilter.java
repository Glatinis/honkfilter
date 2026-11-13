package app.netlify.glatinis.honkfilter;

import app.netlify.glatinis.honkfilter.commands.HonkfilterCommand;
import app.netlify.glatinis.honkfilter.listeners.ChatListener;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Honkfilter extends JavaPlugin {

    @Override
    public void onEnable() {
        ConfigManager config = new ConfigManager(this);
        FilterManager filters = new FilterManager(config);
        ViolationManager violations = new ViolationManager(this, config);

        config.validate().forEach(w -> getLogger().warning("Uh oh! " + w));

        getServer().getPluginManager().registerEvents(new ChatListener(this, config, filters, violations), this);

        HonkfilterCommand command = new HonkfilterCommand(this, config, filters);
        PluginCommand honkfilter = getCommand("honkfilter");
        honkfilter.setExecutor(command);
        honkfilter.setTabCompleter(command);

        getLogger().info("Loaded plugin");
    }

    @Override
    public void onDisable() {
        getLogger().info("Unloaded plugin");
    }
}
