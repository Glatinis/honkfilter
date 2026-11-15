package app.netlify.glatinis.honkfilter.filter;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class WordMatcher {

    private static final String SEPARATORS = " ._-*,|";
    private static final int MAX_SPACED_RUN = 12;

    private final List<Entry> words = new ArrayList<>();
    private final List<Entry> partials = new ArrayList<>();
    private final List<Pattern> regexes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> whitelist;
    private final Map<Character, Character> leet;
    private final boolean spacedLetters;
    private final int spacedMinimum;

    public WordMatcher(Rules rules) {
        this.whitelist = rules.whitelist();
        this.leet = rules.leet();
        this.spacedLetters = rules.spacedLetters();
        this.spacedMinimum = rules.spacedMinimum();

        rules.words().forEach(w -> words.add(new Entry(w, build(w, true, rules))));
        rules.partials().forEach(w -> partials.add(new Entry(w, build(w, false, rules))));

        for (String regex : rules.regexes()) {
            try {
                regexes.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException e) {
                warnings.add("Skipping bad regex '" + regex + "': " + e.getDescription());
            }
        }
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<Match> find(String text) {
        List<Match> found = new ArrayList<>();
        Folded folded = fold(text);
        List<Token> tokens = tokenize(folded, text);

        for (Token token : tokens) {
            if (!token.hasLetter() || whitelist.contains(token.text())) {
                continue;
            }

            Entry hit = wholeMatch(token.text());

            if (hit != null) {
                add(found, folded, token.start(), token.end(), hit.word());
                continue;
            }

            String masked = mask(token.text());

            for (Entry entry : partials) {
                Matcher m = entry.pattern().matcher(masked);

                while (m.find()) {
                    add(found, folded, token.start() + m.start(), token.start() + m.end(), entry.word());
                }
            }
        }

        if (spacedLetters) {
            findSpaced(found, folded, tokens, text);
        }

        for (Pattern regex : regexes) {
            Matcher m = regex.matcher(text);

            while (m.find()) {
                if (m.end() > m.start()) {
                    found.add(new Match(m.start(), m.end(), regex.pattern()));
                }
            }
        }

        return found;
    }

    public static String censor(String text, List<Match> matches, char symbol) {
        if (matches.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text);

        for (Match m : matches) {
            for (int i = m.start(); i < m.end() && i < sb.length(); i++) {
                if (!Character.isWhitespace(sb.charAt(i))) {
                    sb.setCharAt(i, symbol);
                }
            }
        }

        return sb.toString();
    }

    public static String replace(String text, List<Match> matches, List<String> replacements) {
        if (matches.isEmpty() || replacements.isEmpty()) {
            return text;
        }

        List<Match> sorted = matches.stream().sorted(Comparator.comparingInt(Match::start)).toList();
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        int i = 0;

        while (i < sorted.size()) {
            int start = sorted.get(i).start();
            int end = sorted.get(i).end();

            while (i + 1 < sorted.size() && sorted.get(i + 1).start() <= end) {
                end = Math.max(end, sorted.get(i + 1).end());
                i++;
            }

            i++;

            if (start < cursor) {
                continue;
            }

            String original = text.substring(start, end);
            String word = replacements.get(ThreadLocalRandom.current().nextInt(replacements.size()));
            boolean shouting = original.chars().filter(Character::isLetter).count() > 1
                    && original.chars().filter(Character::isLetter).allMatch(Character::isUpperCase);

            sb.append(text, cursor, start).append(shouting ? word.toUpperCase() : word);
            cursor = end;
        }

        return sb.append(text, cursor, text.length()).toString();
    }

    public static List<String> distinctWords(List<Match> matches) {
        return matches.stream()
                .sorted(Comparator.comparingInt(Match::start))
                .map(Match::word)
                .distinct()
                .toList();
    }

    private Entry wholeMatch(String candidate) {
        return words.stream()
                .filter(e -> e.pattern().matcher(candidate).matches())
                .findFirst()
                .orElse(null);
    }

    private void findSpaced(List<Match> found, Folded folded, List<Token> tokens, String text) {
        List<Token> run = new ArrayList<>();

        for (Token token : tokens) {
            boolean single = token.end() - token.start() == 1 && token.hasLetter();

            if (single && (run.isEmpty() || joins(folded, text, run.get(run.size() - 1), token))) {
                run.add(token);
                continue;
            }

            checkRun(found, folded, run);
            run.clear();

            if (single) {
                run.add(token);
            }
        }

        checkRun(found, folded, run);
    }

    private boolean joins(Folded folded, String text, Token prev, Token next) {
        int gapStart = folded.map()[prev.end() - 1] + 1;
        int gapEnd = folded.map()[next.start()];

        return gapEnd - gapStart == 1 && SEPARATORS.indexOf(text.charAt(gapStart)) >= 0;
    }

    private void checkRun(List<Match> found, Folded folded, List<Token> run) {
        for (int a = 0; a + spacedMinimum <= run.size(); a++) {
            for (int b = a + spacedMinimum; b <= run.size() && b - a <= MAX_SPACED_RUN; b++) {
                StringBuilder joined = new StringBuilder();

                for (int k = a; k < b; k++) {
                    joined.append(run.get(k).text());
                }

                String candidate = joined.toString();

                if (whitelist.contains(candidate)) {
                    continue;
                }

                Entry hit = wholeMatch(candidate);

                if (hit != null) {
                    add(found, folded, run.get(a).start(), run.get(b - 1).end(), hit.word());
                }
            }
        }
    }

    private String mask(String token) {
        String masked = token;

        for (String safe : whitelist) {
            if (masked.contains(safe)) {
                masked = masked.replace(safe, " ".repeat(safe.length()));
            }
        }

        return masked;
    }

    private void add(List<Match> found, Folded folded, int foldedStart, int foldedEnd, String word) {
        found.add(new Match(folded.map()[foldedStart], folded.map()[foldedEnd - 1] + 1, word));
    }

    private Folded fold(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int[] map = new int[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (Character.getType(c) == Character.FORMAT) {
                continue;
            }

            char lower = Character.toLowerCase(c);
            Character swapped = leet.get(lower);
            char out;

            if (swapped != null) {
                out = swapped;
            } else {
                String decomposed = Normalizer.normalize(String.valueOf(lower), Normalizer.Form.NFKD);

                if (decomposed.isEmpty() || Character.getType(decomposed.charAt(0)) == Character.NON_SPACING_MARK) {
                    continue;
                }

                Character swappedBase = leet.get(decomposed.charAt(0));
                out = swappedBase != null ? swappedBase : decomposed.charAt(0);
            }

            map[sb.length()] = i;
            sb.append(out);
        }

        return new Folded(sb.toString(), map);
    }

    private List<Token> tokenize(Folded folded, String original) {
        List<Token> tokens = new ArrayList<>();
        String text = folded.text();
        int i = 0;

        while (i < text.length()) {
            if (!Character.isLetterOrDigit(text.charAt(i))) {
                i++;
                continue;
            }

            int start = i;
            boolean hasLetter = false;

            while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
                if (!Character.isDigit(original.charAt(folded.map()[i]))) {
                    hasLetter = true;
                }

                i++;
            }

            tokens.add(new Token(start, i, text.substring(start, i), hasLetter));
        }

        return tokens;
    }

    private Pattern build(String word, boolean whole, Rules rules) {
        StringBuilder rx = new StringBuilder();
        int i = 0;

        while (i < word.length()) {
            char c = word.charAt(i);
            int j = i;

            while (j < word.length() && word.charAt(j) == c) {
                j++;
            }

            int count = j - i;
            String literal = Character.isLetterOrDigit(c) ? String.valueOf(c) : "\\" + c;

            if (rules.repeatedLetters()) {
                rx.append(literal).append(count == 1 ? "+" : "{" + count + ",}");
            } else {
                rx.append(literal.repeat(count));
            }

            i = j;
        }

        if (whole && !rules.suffixes().isEmpty()) {
            String options = String.join("|", rules.suffixes().stream().map(Pattern::quote).toList());
            rx.append("(?:").append(options).append(")?");
        }

        return Pattern.compile(rx.toString());
    }

    public record Match(int start, int end, String word) {
    }

    private record Entry(String word, Pattern pattern) {
    }

    private record Folded(String text, int[] map) {
    }

    private record Token(int start, int end, String text, boolean hasLetter) {
    }
}
