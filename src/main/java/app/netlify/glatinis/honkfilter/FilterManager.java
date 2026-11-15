package app.netlify.glatinis.honkfilter;

import app.netlify.glatinis.honkfilter.filter.FilterResult;
import app.netlify.glatinis.honkfilter.filter.WordMatcher;
import app.netlify.glatinis.honkfilter.filter.WordMatcher.Match;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterManager {

    private final ConfigManager config;
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();

    private volatile WordMatcher matcher;
    private volatile List<Pattern> linkPatterns = List.of();

    public FilterManager(ConfigManager config) {
        this.config = config;
        this.matcher = new WordMatcher(config.getWordRules());
        this.linkPatterns = buildLinkPatterns(new ArrayList<>());
    }

    public List<String> reload() {
        List<String> warnings = new ArrayList<>(config.validate());
        WordMatcher fresh = new WordMatcher(config.getWordRules());

        warnings.addAll(fresh.getWarnings());
        matcher = fresh;
        linkPatterns = buildLinkPatterns(warnings);
        return warnings;
    }

    public WordMatcher getMatcher() {
        return matcher;
    }

    public boolean bypasses(Player plr, String filter) {
        return config.isBypassEnabled() && plr.hasPermission("honkfilter.bypass." + filter);
    }

    public void forget(UUID id) {
        lastMessageTime.remove(id);
        lastMessage.remove(id);
    }

    public FilterResult process(Player plr, String text, boolean chat) {
        String current = text;

        if (chat && config.isSpamEnabled() && !bypasses(plr, "spam")) {
            FilterResult spam = checkSpam(plr, text);

            if (spam != null) {
                return spam;
            }
        }

        if (config.isLinksEnabled() && !bypasses(plr, "links")) {
            List<Match> links = findLinks(current);

            if (!links.isEmpty()) {
                String detail = String.join(", ", WordMatcher.distinctWords(links));

                if (config.getLinksAction().equals("block")) {
                    return FilterResult.block(text, "blocked-links", "links", detail);
                }

                current = WordMatcher.censor(current, links, config.getCensorCharacter());
            }
        }

        if (chat && config.isRepeatedCharactersEnabled() && !bypasses(plr, "repeats")) {
            current = shorten(current, config.getMaxRepeatedCharacters());
        }

        boolean shadow = false;
        String warning = null;
        String violation = null;
        String detail = "";

        if (!bypasses(plr, "words")) {
            List<Match> bad = matcher.find(current);

            if (!bad.isEmpty()) {
                violation = "words";
                detail = String.join(", ", WordMatcher.distinctWords(bad));

                String mode = config.getWordMode();

                if (mode.equals("block") || (!chat && mode.equals("shadow"))) {
                    return FilterResult.block(text, "blocked-words", violation, detail);
                }

                if (mode.equals("shadow")) {
                    shadow = true;
                } else {
                    current = censor(current, bad);
                }

                if (config.isWarnPlayer()) {
                    warning = "censored-words";
                }
            }
        }

        if (chat && config.isCapsEnabled() && !bypasses(plr, "caps") && tooLoud(current)) {
            if (config.getCapsAction().equals("block")) {
                return FilterResult.block(text, "blocked-caps", null, "");
            }

            current = current.toLowerCase();
        }

        return new FilterResult(current, null, warning, shadow, violation, detail);
    }

    public String censor(String text, List<Match> matches) {
        if (config.getCensorStyle().equals("replace")) {
            return WordMatcher.replace(text, matches, config.getReplacementWords());
        }

        return WordMatcher.censor(text, matches, config.getCensorCharacter());
    }

    private FilterResult checkSpam(Player plr, String text) {
        UUID id = plr.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(id);

        if (last != null && now - last < config.getCooldownMillis()) {
            double left = (config.getCooldownMillis() - (now - last)) / 1000.0;
            return FilterResult.block(text, "blocked-cooldown", null, String.format("%.1f", left));
        }

        String previous = lastMessage.get(id);

        if (config.isBlockRepeatedMessages() && previous != null && last != null
                && now - last < config.getRepeatWindowMillis() && previous.equalsIgnoreCase(text.trim())) {
            return FilterResult.block(text, "blocked-repeat", null, "");
        }

        lastMessageTime.put(id, now);
        lastMessage.put(id, text.trim());
        return null;
    }

    private boolean tooLoud(String text) {
        long letters = text.chars().filter(Character::isLetter).count();

        if (text.length() < config.getCapsMinimumLength() || letters == 0) {
            return false;
        }

        long caps = text.chars().filter(Character::isUpperCase).count();
        return caps * 100 / letters > config.getCapsMaxPercent();
    }

    private String shorten(String text, int max) {
        Matcher m = Pattern.compile("(\\S)\\1{" + max + ",}").matcher(text);
        return m.replaceAll(r -> Matcher.quoteReplacement(r.group(1).repeat(max)));
    }

    private List<Match> findLinks(String text) {
        List<String> allowed = config.getAllowedDomains();
        List<Match> found = new ArrayList<>();

        for (Pattern pattern : linkPatterns) {
            Matcher m = pattern.matcher(text);

            while (m.find()) {
                String hit = m.group().toLowerCase();

                if (allowed.stream().noneMatch(hit::contains)) {
                    found.add(new Match(m.start(), m.end(), m.group()));
                }
            }
        }

        return found;
    }

    private List<Pattern> buildLinkPatterns(List<String> warnings) {
        List<Pattern> patterns = new ArrayList<>();

        for (String regex : config.getLinkPatterns()) {
            try {
                patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                warnings.add("Skipping bad links pattern '" + regex + "': " + e.getDescription());
            }
        }

        return patterns;
    }
}
