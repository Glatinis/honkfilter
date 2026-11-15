package app.netlify.glatinis.honkfilter;

import app.netlify.glatinis.honkfilter.commands.HonkfilterCommand;
import app.netlify.glatinis.honkfilter.listeners.ChatListener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Honkfilter extends JavaPlugin {

    @Override
    public void onEnable() {
        ConfigManager config = new ConfigManager(this);
        FilterManager filters = new FilterManager(config);
        ViolationManager violations = new ViolationManager(this, config);

        config.validate().forEach(w -> getLogger().warning("Uh oh! " + w));

        ChatListener chat = new ChatListener(this, config, filters, violations);

        getServer().getPluginManager().registerEvents(chat, this);
        getServer().getPluginManager().registerEvent(AsyncChatEvent.class, chat, config.getChatPriority(),
                (l, e) -> chat.onChat((AsyncChatEvent) e), this, true);

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
