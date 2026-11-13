package app.netlify.glatinis.honkfilter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationManager {

    private final Honkfilter plugin;
    private final ConfigManager config;
    private final Map<UUID, Deque<Long>> violations = new ConcurrentHashMap<>();

    public ViolationManager(Honkfilter plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void record(Player plr) {
        if (!config.isPunishmentsEnabled()) {
            return;
        }

        Deque<Long> times = violations.computeIfAbsent(plr.getUniqueId(), id -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        int count;

        synchronized (times) {
            times.removeIf(t -> now - t > config.getPunishmentWindowMillis());
            times.add(now);
            count = times.size();
        }

        config.getPunishmentCommands(count).forEach(cmd -> run(cmd.replace("{player}", plr.getName())));
    }

    public void forget(UUID id) {
        violations.remove(id);
    }

    private void run(String cmd) {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
    }
}
