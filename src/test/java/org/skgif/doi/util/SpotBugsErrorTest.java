package org.skgif.doi.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.skgif.doi.util.SpotBugsError.Category;

/**
 * Guards the two halves of {@link SpotBugsError} against drifting apart, and its documentation
 * links against rotting.
 *
 * <p>{@link SpotBugsError.Code} exists only because a {@code @SuppressFBWarnings} argument has to
 * be a compile-time constant, which an enum constant is not - so every pattern name is written
 * twice, once as a {@code String} constant and once as an enum member consuming it. The spelling
 * can't diverge (the member takes the constant as its argument), but either side can gain or lose
 * an entry silently: a constant with no member is undocumented and uncategorized, and a member with
 * no constant can't be used in an annotation at all.
 *
 * <p>What it can't catch: a category that is syntactically fine but wrong (naming
 * {@link Category#STYLE} for a pattern SpotBugs actually files under
 * {@link Category#CORRECTNESS}), or a documentation link that is well-formed but points at an
 * anchor upstream has since renamed. Both were verified against the tools' own {@code findbugs.xml}
 * metadata and the live docs when each constant was added; nothing offline can re-verify them.
 */
final class SpotBugsErrorTest {

    /** The enum's own source file, read to check the Javadoc that the compiler discards. */
    private static final Path SOURCE = Path.of("src/main/java/org/skgif/doi/util/SpotBugsError.java");

    /**
     * Captures the anchor URL and link text of a {@code @see} documentation link, in either of the
     * two shapes it appears in. A short link (FindSecBugs's) stays on one {@code @see <a href="...">
     * TEXT</a>} line; a long one (SpotBugs's, past the line-length limit) is wrapped by Spotless onto
     * a {@code href="...">TEXT</a>} continuation line, with {@code @see <a} left on the line above.
     */
    private static final Pattern SEE_LINK = Pattern.compile("^\\s*\\*\\s*(?:@see <a )?href=\"([^\"]+)\">([^<]+)</a>$");

    /** Captures an enum constant declaration, which in this enum is always the line after its Javadoc. */
    private static final Pattern CONSTANT = Pattern.compile("^ {4}([A-Z][A-Z0-9_]*)\\(Code\\.");

    @Test
    void everyPatternNameConstantHasAMatchingEnumMember() {
        Map<String, String> constants = codeConstants();

        assertThat(constants).isNotEmpty();
        for (Map.Entry<String, String> constant : constants.entrySet()) {
            assertThat(SpotBugsError.fromCode(constant.getValue()))
                    .as("Code.%s has no SpotBugsError member carrying it", constant.getKey())
                    .isPresent();
        }
        assertThat(SpotBugsError.values()).hasSameSizeAs(constants.entrySet());
    }

    @Test
    void everyEnumMemberIsNamedAfterThePatternItCarries() {
        for (SpotBugsError error : SpotBugsError.values()) {
            assertThat(error.code()).isEqualTo(error.name());
            assertThat(error.category()).isNotNull();
        }
    }

    @Test
    void fromCodeResolvesEveryKnownPatternAndNothingElse() {
        for (SpotBugsError error : SpotBugsError.values()) {
            assertThat(SpotBugsError.fromCode(error.code())).hasValue(error);
        }

        assertThat(SpotBugsError.fromCode("NP_ALWAYS_NULL")).isEmpty();
        assertThat(SpotBugsError.fromCode("")).isEmpty();
    }

    @Test
    void fromCodeIsCaseSensitiveSoItNeedsNoLocaleAwareComparison() {
        assertThat(SpotBugsError.fromCode("ei_expose_rep")).isEmpty();
        assertThat(SpotBugsError.fromCode("Ei_Expose_Rep")).isEmpty();
    }

    @Test
    void everyMemberDocumentsAnUpstreamLinkNamingItself() throws IOException {
        Map<String, String> links = documentationLinks();

        for (SpotBugsError error : SpotBugsError.values()) {
            String url = links.get(error.name());
            assertThat(url).as("%s has no @see documentation link in %s", error.name(), SOURCE).isNotNull();
            assertThat(url)
                    .as("%s should link to the tool that reports it", error.name())
                    .startsWith(error.category() == Category.SECURITY ? "https://find-sec-bugs.github.io/bugs.htm#" :
                            "https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#");
        }
        assertThat(links).hasSameSizeAs(SpotBugsError.values());
    }

    @Test
    void categoryCoversSpotBugsWholeReportingVocabulary() {
        assertThat(Category.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("BAD_PRACTICE", "CORRECTNESS", "EXPERIMENTAL", "I18N", "MT_CORRECTNESS",
                        "NOISE", "MALICIOUS_CODE", "PERFORMANCE", "SECURITY", "STYLE");
    }

    /**
     * Reads the {@code Code} holder's constants reflectively, so a constant added there without an
     * enum member is caught rather than being invisible to a hand-maintained list.
     *
     * @return each constant's field name mapped to the pattern name it holds
     */
    private static Map<String, String> codeConstants() {
        Map<String, String> constants = new LinkedHashMap<>();
        for (Field field : SpotBugsError.Code.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            constants.put(field.getName(), readConstant(field));
        }
        return constants;
    }

    /**
     * Pulls the value out of a {@code public static final String} field, translating the checked
     * reflection failure into a test failure.
     *
     * @param field a static {@code String} field of the {@code Code} holder
     * @return the field's value
     * @throws AssertionError if the field cannot be read, which for a public constant means the
     *                        holder's shape changed rather than the value being wrong
     */
    private static String readConstant(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Code." + field.getName() + " is no longer publicly readable", e);
        }
    }

    /**
     * Pairs each enum constant in the source file with the {@code @see} link documented above it.
     *
     * <p>Javadoc isn't retained in the class file, so this reads the source: a Javadoc block's
     * {@code @see} line is remembered until the next constant declaration claims it. Only links
     * whose text matches the constant's own name are collected, which is what makes a copy-pasted
     * block pointing at the neighbouring pattern show up as a missing link rather than passing.
     *
     * @return each documented constant's name mapped to its upstream anchor URL
     * @throws IOException if the enum's own source file can't be read
     */
    private static Map<String, String> documentationLinks() throws IOException {
        List<String> lines = Files.readAllLines(SOURCE, UTF_8);
        Map<String, String> links = new LinkedHashMap<>();
        Map<String, String> pending = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher link = SEE_LINK.matcher(line);
            if (link.matches()) {
                pending.put(link.group(2), link.group(1));
                continue;
            }
            Matcher constant = CONSTANT.matcher(line);
            if (constant.find()) {
                String name = constant.group(1);
                String url = pending.get(name);
                if (url != null) {
                    links.put(name, url);
                }
                pending.clear();
            }
        }
        return links;
    }
}
