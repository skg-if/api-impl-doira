package org.skgif.doi.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Shared mechanics for translating the SKG-IF {@code filter} query syntax (comma-separated
 * {@code key:value}, AND-combined) into a DataCite REST API {@code query} string, used by both
 * {@link DataCiteProductFilters} and {@link DataCiteGrantFilters}.
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

    static final String DOI_URL_PREFIX = "https://doi.org/";

    static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    static String stripDoiUrl(String value) {
        return value.startsWith(DOI_URL_PREFIX) ? value.substring(DOI_URL_PREFIX.length()) : value;
    }

    /**
     * Builds {@code "(creators.<field>:\"value\" OR contributors.<field>:\"value\")"} - the
     * recurring shape for DataCite by/declared_affiliations filters, which must match against
     * either {@code creators[]} or {@code contributors[]} (see {@code DataCiteToSkgIfMapper}).
     */
    static String creatorOrContributorClause(String field, String value) {
        String escaped = escape(value);
        return "(creators." + field + ":\"" + escaped + "\" OR contributors." + field + ":\"" + escaped + "\")";
    }

    /** For attributes we only ever emit one fixed scheme/value for - no-op if it matches, else forces zero results. */
    static String schemeOnlyFilter(String value, String expectedScheme, String noMatchClause) {
        return expectedScheme.equalsIgnoreCase(value) ? null : noMatchClause;
    }

    /**
     * Splits {@code filter} into segments (see {@link #splitSegments}), validates each one as a
     * {@code key:value} pair with {@code key} in {@code supportedKeys}, and hands each valid pair
     * to {@code clauseBuilder} to produce a clause (or {@code null} to omit it from the result).
     * Shared by every provider's filter parser so the malformed-segment / unsupported-filter
     * error messages exist in exactly one place.
     */
    static List<String> parseClauses(String filter, Set<String> supportedKeys,
            BiFunction<String, String, String> clauseBuilder) {
        List<String> clauses = new ArrayList<>();
        for (String segment : splitSegments(filter, supportedKeys)) {
            int idx = segment.indexOf(':');
            if (idx < 0) {
                throw new UnsupportedFilterException(
                        "Malformed filter segment '" + segment + "', expected 'key:value'");
            }
            String key = segment.substring(0, idx).trim();
            String value = segment.substring(idx + 1).trim();
            if (!supportedKeys.contains(key)) {
                throw new UnsupportedFilterException("The filter '" + key
                        + "' is not supported by this implementation, valid filters are " + String.join(", ", supportedKeys));
            }
            String clause = clauseBuilder.apply(key, value);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        return clauses;
    }

    static final class UnsupportedFilterException extends RuntimeException {
        UnsupportedFilterException(String message) {
            super(java.util.Objects.requireNonNull(message));
        }
    }
}
