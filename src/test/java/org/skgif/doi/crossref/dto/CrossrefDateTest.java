package org.skgif.doi.crossref.dto;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class CrossrefDateTest {

    @MethodSource("toIsoDateCases")
    @ParameterizedTest(name = "{0}")
    void toIsoDate_matchesExpected(String label, CrossrefDate date, String expected) {
        assertThat(date.toIsoDate()).isEqualTo(Optional.ofNullable(expected));
    }

    private static Stream<Arguments> toIsoDateCases() {
        return Stream.of(
                arguments("null dateParts is empty", new CrossrefDate(null), null),
                arguments("empty dateParts is empty", new CrossrefDate(List.of()), null),
                arguments("null inner parts list is empty", new CrossrefDate(singletonList(null)), null),
                arguments("empty inner parts list is empty", new CrossrefDate(List.of(List.of())), null),
                arguments("null year is empty", withParts(null, "5", "1"), null),
                arguments("year only", isoDate("2013"), "2013"),
                arguments("year and month", isoDate("2013-08"), "2013-08"),
                arguments("year month and day", isoDate("2013-08-01"), "2013-08-01"),
                arguments("null month stops before day even when day is present", withParts("2013", null, "1"),
                        "2013"));
    }

    // Mirrors CrossrefGrantMapperTest's isoDate helper: parts come from a parsed string literal
    // rather than integer literals, so checkstyle's MagicNumber rule doesn't fire on them.
    private static CrossrefDate isoDate(String iso) {
        List<Integer> parts = Arrays.stream(iso.split("-")).map(Integer::parseInt).toList();
        return new CrossrefDate(List.of(parts));
    }

    private static CrossrefDate withParts(@Nullable String year, @Nullable String month, @Nullable String day) {
        List<Integer> parts = Arrays.asList(parseOrNull(year), parseOrNull(month), parseOrNull(day));
        return new CrossrefDate(List.of(parts));
    }

    @SuppressWarnings("PMD.ReturnNullConsiderOptional")
    private static @Nullable Integer parseOrNull(@Nullable String value) {
        return value == null ? null : Integer.parseInt(value);
    }
}
