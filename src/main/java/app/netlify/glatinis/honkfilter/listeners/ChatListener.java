package app.netlify.glatinis.honkfilter.listeners;

import app.netlify.glatinis.honkfilter.ConfigManager;
import app.netlify.glatinis.honkfilter.FilterManager;
import app.netlify.glatinis.honkfilter.Honkfilter;
import app.netlify.glatinis.honkfilter.ViolationManager;
import app.netlify.glatinis.honkfilter.filter.FilterResult;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

    private final Honkfilter plugin;
    private final ConfigManager config;
    private final FilterManager filters;
    private final ViolationManager violations;

    public ChatListener(Honkfilter plugin, ConfigManager config, FilterManager filters, ViolationManager violations) {
        this.plugin = plugin;
        this.config = config;
        this.filters = filters;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        try {
            handleChat(e);
        } catch (Exception ex) {
            plugin.getLogger().warning("Uh oh! The filter hit an error so the message went through: " + ex);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        try {
            handleCommand(e);
        } catch (Exception ex) {
            plugin.getLogger().warning("Uh oh! The filter hit an error so the command went through: " + ex);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        filters.forget(e.getPlayer().getUniqueId());
        violations.forget(e.getPlayer().getUniqueId());
    }

    private void handleChat(AsyncChatEvent e) {
        Player plr = e.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        FilterResult res = filters.process(plr, text, true);

        if (res.isBlocked()) {
            e.setCancelled(true);
            tell(plr, res);
            report(plr, text, res);
            return;
        }

        if (!res.text().equals(text)) {
            e.message(Component.text(res.text()));
        }

        if (res.shadow()) {
            e.viewers().removeIf(a -> !(a instanceof ConsoleCommandSender) && !a.equals(plr));
        }

        if (res.warning() != null) {
            plr.sendMessage(config.getMessage(res.warning()));
        }

        report(plr, text, res);
    }

    private void handleCommand(PlayerCommandPreprocessEvent e) {
        if (!config.isCommandsEnabled()) {
            return;
        }

        String full = e.getMessage();
        int space = full.indexOf(' ');

        if (space < 0) {
            return;
        }

        String label = full.substring(1, space).toLowerCase();

        if (label.startsWith("minecraft:")) {
            label = label.substring("minecraft:".length());
        }

        if (!config.getFilteredCommands().contains(label)) {
            return;
        }

        String args = full.substring(space + 1);
        FilterResult res = filters.process(e.getPlayer(), args, false);

        if (res.isBlocked()) {
            e.setCancelled(true);
            tell(e.getPlayer(), res);
            report(e.getPlayer(), args, res);
            return;
        }

        if (!res.text().equals(args)) {
            e.setMessage(full.substring(0, space + 1) + res.text());
        }

        if (res.warning() != null) {
            e.getPlayer().sendMessage(config.getMessage(res.warning()));
        }

        report(e.getPlayer(), args, res);
    }

    private void tell(Player plr, FilterResult res) {
        plr.sendMessage(config.getMessage(res.blocked(), Placeholder.unparsed("seconds", res.detail())));
    }

    private void report(Player plr, String text, FilterResult res) {
        if (res.violation() == null) {
            return;
        }

        violations.record(plr);

        if (!config.isNotifyStaff()) {
            return;
        }

        Component note = config.getMessage("staff-notify",
                Placeholder.unparsed("player", plr.getName()),
                Placeholder.unparsed("filter", res.violation()),
                Placeholder.unparsed("detail", res.detail().isEmpty() ? text : res.detail() + " | " + text));

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("honkfilter.notify"))
                .forEach(p -> p.sendMessage(note));
        Bukkit.getConsoleSender().sendMessage(note);
    }
}
