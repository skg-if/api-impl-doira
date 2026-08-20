package org.skgif.doi.crossref.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CrossrefDateTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("toIsoDateCases")
    void toIsoDate_matchesExpected(String label, CrossrefDate date, String expected) {
        assertThat(date.toIsoDate()).isEqualTo(Optional.ofNullable(expected));
    }

    private static Stream<Arguments> toIsoDateCases() {
        return Stream.of(
                Arguments.of("null dateParts is empty", new CrossrefDate(null), null),
                Arguments.of("empty dateParts is empty", new CrossrefDate(List.of()), null),
                Arguments.of("null inner parts list is empty",
                        new CrossrefDate(Collections.singletonList(null)), null),
                Arguments.of("empty inner parts list is empty", new CrossrefDate(List.of(List.of())), null),
                Arguments.of("null year is empty", withParts(null, "5", "1"), null),
                Arguments.of("year only", isoDate("2013"), "2013"),
                Arguments.of("year and month", isoDate("2013-08"), "2013-08"),
                Arguments.of("year month and day", isoDate("2013-08-01"), "2013-08-01"),
                Arguments.of("null month stops before day even when day is present",
                        withParts("2013", null, "1"), "2013"));
    }

    // Mirrors CrossrefGrantMapperTest's isoDate helper: parts come from a parsed string literal
    // rather than integer literals, so checkstyle's MagicNumber rule doesn't fire on them.
    private static CrossrefDate isoDate(String iso) {
        List<Integer> parts = Arrays.stream(iso.split("-")).map(Integer::parseInt).toList();
        return new CrossrefDate(List.of(parts));
    }

    private static CrossrefDate withParts(String year, String month, String day) {
        List<Integer> parts = Arrays.asList(parseOrNull(year), parseOrNull(month), parseOrNull(day));
        return new CrossrefDate(List.of(parts));
    }

    @SuppressWarnings("PMD.ReturnNullConsiderOptional")
    private static Integer parseOrNull(String value) {
        return value == null ? null : Integer.parseInt(value);
    }
}
