package app.netlify.glatinis.honkfilter.filter;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Rules(
        List<String> words,
        List<String> partials,
        List<String> regexes,
        List<String> whitelist,
        List<String> suffixes,
        boolean repeatedLetters,
        Map<Character, Character> leet,
        boolean spacedLetters,
        int spacedMinimum
) {

    public static Rules from(ConfigurationSection sec) {
        Map<Character, Character> leet = new HashMap<>();
        ConfigurationSection leetSec = sec.getConfigurationSection("leet-speak.replacements");

        if (sec.getBoolean("leet-speak.enabled", true) && leetSec != null) {
            for (String key : leetSec.getKeys(false)) {
                String value = leetSec.getString(key, "");

                if (key.length() == 1 && value.length() == 1) {
                    leet.put(key.charAt(0), value.charAt(0));
                }
            }
        }

        return new Rules(
                lower(sec.getStringList("blocked-words")),
                lower(sec.getStringList("blocked-partials")),
                sec.getStringList("blocked-regex"),
                lower(sec.getStringList("whitelist")),
                lower(sec.getStringList("allowed-suffixes")),
                sec.getBoolean("repeated-letters", true),
                leet,
                sec.getBoolean("spaced-letters.enabled", true),
                Math.max(2, sec.getInt("spaced-letters.minimum-letters", 3))
        );
    }

    private static List<String> lower(List<String> list) {
        return list.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }
}
