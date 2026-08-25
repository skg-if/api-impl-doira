package org.skgif.doi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class MapperTextUtilsTest {

    @MethodSource("slugCases")
    @ParameterizedTest(name = "{0}")
    void slug_matchesExpected(String label, String input, String expected) {
        assertThat(MapperTextUtils.slug(input)).isEqualTo(expected);
    }

    static Stream<Arguments> slugCases() {
        final int maxSlugLength = 40;
        final int overMaxSlugLength = 50;
        return Stream.of(
                arguments("null text becomes unknown", null, "unknown"),
                arguments("empty text becomes unknown", "", "unknown"),
                arguments("text with no alphanumeric characters becomes unknown", "!!!---", "unknown"),
                arguments("mixed case and punctuation is lowercased and hyphenated",
                        "European Synchrotron, Radiation Facility!", "european-synchrotron-radiation-facility"),
                arguments("text longer than 40 characters is truncated", "a".repeat(overMaxSlugLength), "a".repeat(
                        maxSlugLength)));
    }

    @Test
    void otf_combinesSluggedDoiAndLabel() {
        assertThat(MapperTextUtils.otf("10.1234/abc", "Jane Doe")).isEqualTo("otf___10-1234-abc___jane-doe");
    }
}
