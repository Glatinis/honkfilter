package app.netlify.glatinis.honkfilter;

import app.netlify.glatinis.honkfilter.filter.Rules;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager {

    private static final Set<String> WORD_MODES = Set.of("censor", "block", "shadow");
    private static final Set<String> CAPS_ACTIONS = Set.of("lowercase", "block");
    private static final Set<String> LINK_ACTIONS = Set.of("block", "censor");

    private final Honkfilter plugin;

    public ConfigManager(Honkfilter plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public List<String> validate() {
        List<String> warnings = new ArrayList<>();

        if (!WORD_MODES.contains(raw("words.mode"))) {
            warnings.add("words.mode must be censor, block or shadow. Using censor.");
        }

        if (!CAPS_ACTIONS.contains(raw("caps.action"))) {
            warnings.add("caps.action must be lowercase or block. Using lowercase.");
        }

        if (!LINK_ACTIONS.contains(raw("links.action"))) {
            warnings.add("links.action must be block or censor. Using block.");
        }

        if (plugin.getConfig().getString("words.censor-character", "*").length() != 1) {
            warnings.add("words.censor-character must be a single character. Using *.");
        }

        ConfigurationSection steps = plugin.getConfig().getConfigurationSection("punishments.steps");

        if (steps != null) {
            steps.getKeys(false).stream()
                    .filter(k -> !k.matches("\\d+"))
                    .forEach(k -> warnings.add("punishments.steps key '" + k + "' is not a number. It will never run."));
        }

        return warnings;
    }

    public Rules getWordRules() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("words");
        return Rules.from(sec != null ? sec : new MemoryConfiguration());
    }

    public String getWordMode() {
        return pick("words.mode", WORD_MODES, "censor");
    }

    public char getCensorCharacter() {
        String value = plugin.getConfig().getString("words.censor-character", "*");
        return value.length() == 1 ? value.charAt(0) : '*';
    }

    public boolean isWarnPlayer() {
        return plugin.getConfig().getBoolean("words.warn-player", true);
    }

    public boolean isCapsEnabled() {
        return plugin.getConfig().getBoolean("caps.enabled", true);
    }

    public int getCapsMinimumLength() {
        return plugin.getConfig().getInt("caps.minimum-length", 8);
    }

    public int getCapsMaxPercent() {
        return plugin.getConfig().getInt("caps.max-percent", 70);
    }

    public String getCapsAction() {
        return pick("caps.action", CAPS_ACTIONS, "lowercase");
    }

    public boolean isRepeatedCharactersEnabled() {
        return plugin.getConfig().getBoolean("repeated-characters.enabled", true);
    }

    public int getMaxRepeatedCharacters() {
        return Math.max(1, plugin.getConfig().getInt("repeated-characters.max-allowed", 4));
    }

    public boolean isSpamEnabled() {
        return plugin.getConfig().getBoolean("spam.enabled", true);
    }

    public long getCooldownMillis() {
        return (long) (plugin.getConfig().getDouble("spam.cooldown-seconds", 1.0) * 1000);
    }

    public boolean isBlockRepeatedMessages() {
        return plugin.getConfig().getBoolean("spam.block-repeated-messages", true);
    }

    public long getRepeatWindowMillis() {
        return plugin.getConfig().getLong("spam.repeat-window-seconds", 10) * 1000;
    }

    public boolean isLinksEnabled() {
        return plugin.getConfig().getBoolean("links.enabled", false);
    }

    public String getLinksAction() {
        return pick("links.action", LINK_ACTIONS, "block");
    }

    public List<String> getAllowedDomains() {
        return plugin.getConfig().getStringList("links.allowed-domains").stream()
                .map(String::toLowerCase)
                .toList();
    }

    public List<String> getLinkPatterns() {
        return plugin.getConfig().getStringList("links.patterns");
    }

    public boolean isCommandsEnabled() {
        return plugin.getConfig().getBoolean("commands.enabled", true);
    }

    public Set<String> getFilteredCommands() {
        return plugin.getConfig().getStringList("commands.list").stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public boolean isPunishmentsEnabled() {
        return plugin.getConfig().getBoolean("punishments.enabled", false);
    }

    public long getPunishmentWindowMillis() {
        return plugin.getConfig().getLong("punishments.window-seconds", 300) * 1000;
    }

    public List<String> getPunishmentCommands(int count) {
        return plugin.getConfig().getStringList("punishments.steps." + count);
    }

    public boolean isNotifyStaff() {
        return plugin.getConfig().getBoolean("notify-staff.enabled", true);
    }

    public Component getMessage(String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        return MiniMessage.miniMessage().deserialize(raw, resolvers);
    }

    public List<String> getList(String path) {
        return plugin.getConfig().getStringList(path);
    }

    public boolean addToList(String path, String value) {
        List<String> list = new ArrayList<>(getList(path));

        if (list.stream().anyMatch(value::equalsIgnoreCase)) {
            return false;
        }

        list.add(value);
        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        return true;
    }

    public boolean removeFromList(String path, String value) {
        List<String> list = new ArrayList<>(getList(path));

        if (!list.removeIf(value::equalsIgnoreCase)) {
            return false;
        }

        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        return true;
    }

    private String raw(String path) {
        return plugin.getConfig().getString(path, "").toLowerCase(Locale.ROOT);
    }

    private String pick(String path, Set<String> allowed, String fallback) {
        String value = raw(path);
        return allowed.contains(value) ? value : fallback;
    }
}
