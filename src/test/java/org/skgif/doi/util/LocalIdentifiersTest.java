package org.skgif.doi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class LocalIdentifiersTest {

    /** The instance under test. */
    private final LocalIdentifiers localIdentifiers = new LocalIdentifiers("https://doi.org/");

    private static Stream<Arguments> toDoiCases() {
        return Stream.of(
                arguments("bare DOI returns unchanged", "10.15151/esrf-es-2210534378", "10.15151/esrf-es-2210534378"),
                arguments("canonical full local identifier strips base URL",
                        "https://doi.org/10.15151/esrf-es-2210534378", "10.15151/esrf-es-2210534378"),
                // Reproduces the value @PathParam actually receives when a client sends the full
                // local_identifier URL as a raw, unescaped path segment: Vert.x's own HTTP routing
                // collapses the literal "//" in the raw request path down to a single "/" before
                // path-param binding runs, so toDoi() sees "https:/doi.org/..." (one slash), not
                // "https://doi.org/...".
                arguments("Vert.x-collapsed single-slash form strips base URL",
                        "https:/doi.org/10.15151/esrf-es-2210534378", "10.15151/esrf-es-2210534378"),
                arguments("unrelated value passes through unchanged", "not-a-doi-or-url", "not-a-doi-or-url"),
                // Only the exact configured scheme (https) is tolerated - http is a different,
                // valid URL, not a collapsed variant of the configured https base URL, and must
                // not be silently treated as equivalent.
                arguments("different scheme is not tolerated", "http://doi.org/10.15151/esrf-es-2210534378",
                        "http://doi.org/10.15151/esrf-es-2210534378"));
    }

    @MethodSource("toDoiCases")
    @ParameterizedTest(name = "{0}")
    void toDoi(String label, String input, String expected) {
        assertThat(localIdentifiers.toDoi(input)).isEqualTo(expected);
    }

    @Test
    void toFullLocalIdentifier_unaffectedByFix() {
        assertThat(localIdentifiers.toFullLocalIdentifier("10.15151/esrf-es-2210534378"))
                .isEqualTo("https://doi.org/10.15151/esrf-es-2210534378");
    }
}
