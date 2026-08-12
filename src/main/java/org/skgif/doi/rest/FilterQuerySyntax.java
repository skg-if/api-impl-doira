package org.skgif.doi.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared mechanics for translating the SKG-IF {@code filter} query syntax (comma-separated
 * {@code key:value}, AND-combined) into a DataCite REST API {@code query} string, used by both
 * {@link ProductFilters} and {@link GrantFilters}.
 *
 * <p>The spec defines the format as plain comma-separated {@code key:value} pairs with no
 * escaping mechanism for a comma that's part of a value itself (e.g. an affiliation name like
 * "Brown University, 69 Brown St, Providence, RI 02912, USA"), which real DataCite data
 * frequently contains. {@link #splitSegments} works around this: a comma only starts a new
 * segment if what follows it, up to the next colon, is one of the caller's supported keys -
 * otherwise it's treated as part of the current value. This is a heuristic, not a real escaping
 * syntax (the spec doesn't define one) - it would misfire only if a filter *value* happened to
 * exactly equal one of the supported keys, which is vanishingly unlikely in practice.
 */
final class FilterQuerySyntax {

    static final String NO_MATCH_CLAUSE = "doi:\"__no_match__\"";

    private FilterQuerySyntax() {
    }

    /**
     * Splits on commas EXCEPT where the text between a comma and the next colon isn't one of
     * {@code supportedKeys} - see the class javadoc for why. A value with no comma in it, or the
     * final segment of the filter string, always ends up as a single segment either way.
     */
    static List<String> splitSegments(String filter, Set<String> supportedKeys) {
        List<String> segments = new ArrayList<>();
        int segmentStart = 0;
        int searchFrom = 0;
        while (true) {
            int comma = filter.indexOf(',', searchFrom);
            if (comma == -1) {
                segments.add(filter.substring(segmentStart));
                return segments;
            }
            int nextColon = filter.indexOf(':', comma + 1);
            if (nextColon != -1 && supportedKeys.contains(filter.substring(comma + 1, nextColon).trim())) {
                segments.add(filter.substring(segmentStart, comma));
                segmentStart = comma + 1;
            }
            searchFrom = comma + 1;
        }
    }

    static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    static final class UnsupportedFilterException extends RuntimeException {
        UnsupportedFilterException(String message) {
            super(java.util.Objects.requireNonNull(message));
        }
    }
}
