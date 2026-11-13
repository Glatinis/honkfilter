package app.netlify.glatinis.honkfilter.filter;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordMatcherTest {

    private static WordMatcher matcher;

    @BeforeAll
    static void load() throws Exception {
        try (Reader reader = new InputStreamReader(WordMatcherTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            ConfigurationSection words = YamlConfiguration.loadConfiguration(reader).getConfigurationSection("words");
            matcher = new WordMatcher(Rules.from(words));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Scunthorpe", "class", "assassin", "assassins", "assess", "passage", "peacock", "cockpit",
            "Hancock", "Dickinson", "analyst", "analysis", "cocky", "cocker", "cocked", "cumin",
            "classic", "bass", "grass", "compass", "assume", "title", "shiitake", "Essex", "Sussex",
            "hello", "as", "ash", "mass", "glass", "I am a boy", "that is a nice glass of water",
            "455", "I have 455 diamonds", "the assassin passed the class", "pass the bass please",
            "I a m a b o y", "it is a s a p", "hancock street", "Dickinson College"
    })
    void innocentTextIsLeftAlone(String text) {
        assertEquals(java.util.List.of(), matcher.find(text), text);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fuck", "FUCK", "fucking", "fuuuuuck", "FUUUCK you", "fucks", "sh1t", "a$$", "@ss", "4ss",
            "you are an ass", "asses", "assholes", "b1tch", "biiiitch", "f u c k", "f.u.c.k", "a s s",
            "what the fuck", "motherfucking", "xxfuckxx", "dumb ass", "Shit", "bullshit", "you cunt"
    })
    void badTextIsCaught(String text) {
        assertFalse(matcher.find(text).isEmpty(), text);
    }

    @Test
    void censorKeepsTheRestOfTheMessage() {
        String text = "well that is shit, but class is fine";
        assertEquals("well that is ****, but class is fine", WordMatcher.censor(text, matcher.find(text), '*'));
    }

    @Test
    void censorCoversTheWholeBadWord() {
        String text = "fuuuuuck this";
        assertEquals("******** this", WordMatcher.censor(text, matcher.find(text), '*'));
    }

    @Test
    void partialRulesOnlyHideTheFragment() {
        String text = "abcfuckdef";
        assertEquals("abc****def", WordMatcher.censor(text, matcher.find(text), '*'));
    }

    @Test
    void spacedLettersAreCensoredInPlace() {
        String text = "oh f u c k it";
        assertEquals("oh * * * * it", WordMatcher.censor(text, matcher.find(text), '*'));
    }

    @Test
    void leetSpeakKeepsRealWordPosition() {
        String text = "that is sh1t";
        assertTrue(WordMatcher.censor(text, matcher.find(text), '*').endsWith("is ****"));
    }

    @Test
    void badRegexIsSkippedNotFatal() {
        Rules rules = new Rules(java.util.List.of(), java.util.List.of(), java.util.List.of("(unclosed", "gr+eat"), java.util.List.of(),
                java.util.List.of(), true, java.util.Map.of(), false, 3);
        WordMatcher m = new WordMatcher(rules);

        assertEquals(1, m.getWarnings().size());
        assertEquals(1, m.find("so greeat").size() + m.find("grrreat").size());
    }
}
