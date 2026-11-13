package app.netlify.glatinis.honkfilter.commands;

import app.netlify.glatinis.honkfilter.ConfigManager;
import app.netlify.glatinis.honkfilter.FilterManager;
import app.netlify.glatinis.honkfilter.Honkfilter;
import app.netlify.glatinis.honkfilter.filter.WordMatcher;
import app.netlify.glatinis.honkfilter.filter.WordMatcher.Match;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class HonkfilterCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "add", "remove", "list", "test");
    private static final Map<String, String> LISTS = Map.of(
            "words", "words.blocked-words",
            "partials", "words.blocked-partials",
            "whitelist", "words.whitelist",
            "regex", "words.blocked-regex"
    );

    private final Honkfilter plugin;
    private final ConfigManager config;
    private final FilterManager filters;

    public HonkfilterCommand(Honkfilter plugin, ConfigManager config, FilterManager filters) {
        this.plugin = plugin;
        this.config = config;
        this.filters = filters;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("honkfilter.admin")) {
            sender.sendMessage(config.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            return usage(sender, "/" + label + " <reload|add|remove|list|test>");
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> reload(sender);
            case "add" -> edit(sender, label, args, true);
            case "remove" -> edit(sender, label, args, false);
            case "list" -> list(sender, label, args);
            case "test" -> test(sender, label, args);
            default -> usage(sender, "/" + label + " <reload|add|remove|list|test>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("honkfilter.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return match(SUBCOMMANDS.stream(), args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2 && List.of("add", "remove", "list").contains(sub)) {
            return match(LISTS.keySet().stream(), args[1]);
        }

        if (args.length == 3 && sub.equals("remove") && LISTS.containsKey(args[1].toLowerCase())) {
            return match(config.getList(LISTS.get(args[1].toLowerCase())).stream(), args[2]);
        }

        return List.of();
    }

    private void reload(CommandSender sender) {
        try {
            config.reload();
            filters.reload().forEach(w -> sender.sendMessage(config.getMessage("reload-warning", Placeholder.unparsed("warning", w))));
            sender.sendMessage(config.getMessage("reloaded"));
        } catch (Exception e) {
            plugin.getLogger().warning("Uh oh! Reload failed, keeping the old rules: " + e);
            sender.sendMessage(config.getMessage("reload-failed"));
        }
    }

    private void edit(CommandSender sender, String label, String[] args, boolean add) {
        if (args.length < 3) {
            usage(sender, "/" + label + " " + args[0] + " <" + String.join("|", LISTS.keySet()) + "> <word>");
            return;
        }

        String name = args[1].toLowerCase();
        String path = LISTS.get(name);

        if (path == null) {
            sender.sendMessage(config.getMessage("unknown-list", Placeholder.unparsed("lists", String.join(", ", LISTS.keySet()))));
            return;
        }

        String word = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        boolean changed = add ? config.addToList(path, name.equals("regex") ? word : word.toLowerCase()) : config.removeFromList(path, word);
        String key = changed ? (add ? "added" : "removed") : (add ? "already-added" : "not-in-list");

        sender.sendMessage(config.getMessage(key, Placeholder.unparsed("word", word), Placeholder.unparsed("list", name)));

        if (changed) {
            filters.reload().forEach(w -> sender.sendMessage(config.getMessage("reload-warning", Placeholder.unparsed("warning", w))));
        }
    }

    private void list(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, "/" + label + " list <" + String.join("|", LISTS.keySet()) + ">");
            return;
        }

        String name = args[1].toLowerCase();
        String path = LISTS.get(name);

        if (path == null) {
            sender.sendMessage(config.getMessage("unknown-list", Placeholder.unparsed("lists", String.join(", ", LISTS.keySet()))));
            return;
        }

        List<String> entries = config.getList(path);

        if (entries.isEmpty()) {
            sender.sendMessage(config.getMessage("list-empty"));
            return;
        }

        sender.sendMessage(config.getMessage("list-header", Placeholder.unparsed("list", name)));
        sender.sendMessage(config.getMessage("list-entries", Placeholder.unparsed("entries", String.join(", ", entries))));
    }

    private void test(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, "/" + label + " test <text>");
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        List<Match> found = filters.getMatcher().find(text);

        if (found.isEmpty()) {
            sender.sendMessage(config.getMessage("test-clean"));
            return;
        }

        sender.sendMessage(config.getMessage("test-flagged", Placeholder.unparsed("matches", String.join(", ", WordMatcher.distinctWords(found)))));
        sender.sendMessage(config.getMessage("test-result", Placeholder.unparsed("result", WordMatcher.censor(text, found, config.getCensorCharacter()))));
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(config.getMessage("usage", Placeholder.unparsed("usage", usage)));
        return true;
    }

    private List<String> match(Stream<String> options, String typed) {
        return options.filter(o -> o.toLowerCase().startsWith(typed.toLowerCase())).sorted().toList();
    }
}
