package org.skgif.doi.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Shared mechanics for translating the SKG-IF {@code filter} query syntax (comma-separated
 * {@code key:value}, AND-combined) into a DataCite REST API {@code query} string, used by both
 * {@code DataCiteProductFilters} and {@code DataCiteGrantFilters}.
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
public final class FilterQuerySyntax {

    /** DataCite query clause guaranteed to match no record, for a filter that can't be satisfied. */
    public static final String NO_MATCH_CLAUSE = "doi:\"__no_match__\"";

    private FilterQuerySyntax() {
    }

    /**
     * Splits on commas EXCEPT where the text between a comma and the next colon isn't one of
     * {@code supportedKeys} - see the class javadoc for why. A value with no comma in it, or the
     * final segment of the filter string, always ends up as a single segment either way.
     *
     * @param filter        the raw SKG-IF filter query string
     * @param supportedKeys the filter keys the caller recognizes
     * @return the filter string split into key:value segments
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

    /**
     * Escapes a double-quote in a filter value so it can be embedded in a quoted Lucene clause.
     *
     * @param value the raw filter value
     * @return value with every {@code "} escaped as {@code \"}
     */
    public static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    /**
     * Builds {@code field:"escapedValue"} - the single-field-equals-value shape recurring across
     * every provider's clause builders.
     *
     * @param field the DataCite field to match against
     * @param value the filter value to match (escaped internally)
     * @return the Lucene clause {@code field:"escapedValue"}
     */
    public static String quotedFieldClause(String field, String value) {
        return field + ":\"" + escape(value) + "\"";
    }

    /**
     * Builds {@code "(creatorsField:\"value\" OR contributorsField:\"value\")"} - the recurring
     * shape for DataCite by/declared_affiliations filters, which must match against either a
     * {@code creators[]} field or the equivalent {@code contributors[]} field (see {@code
     * DataCiteToSkgIfMapper}).
     *
     * @param creatorsField     the complete DataCite field path to match on creators[]
     * @param contributorsField the complete DataCite field path to match on contributors[]
     * @param value             the filter value to match
     * @return an OR'd Lucene clause matching value on either field
     */
    public static String creatorOrContributorClause(String creatorsField, String contributorsField, String value) {
        return "(" + quotedFieldClause(creatorsField, value) + " OR " + quotedFieldClause(contributorsField, value) +
                ")";
    }

    /**
     * For attributes we only ever emit one fixed scheme/value for - no-op if it matches, else forces zero results.
     *
     * @param value          the filter value to check
     * @param expectedScheme the only scheme value this API ever emits for the attribute
     * @param noMatchClause  the clause to return when value doesn't match expectedScheme
     * @return null (no-op) if value matches expectedScheme, else noMatchClause
     */
    // Bound as a BinaryOperator<String>/ClauseBuilder method reference inside a switch expression
    // - null means "no clause"; converting to Optional<String> is a separate, larger refactor of
    // that shared functional interface, out of scope here.
    @SuppressWarnings("PMD.ReturnNullConsiderOptional")
    public static @Nullable String schemeOnlyFilter(String value, String expectedScheme, String noMatchClause) {
        return expectedScheme.equalsIgnoreCase(value) ? null : noMatchClause;
    }

    /**
     * Splits {@code filter} into segments (see {@link #splitSegments}), validates each one as a
     * {@code key:value} pair with {@code key} in {@code supportedKeys}, and hands each valid pair
     * to {@code clauseBuilder} to produce a clause (or {@code null} to omit it from the result).
     * Shared by every provider's filter parser so the malformed-segment / unsupported-filter
     * error messages exist in exactly one place.
     *
     * @param filter        the raw SKG-IF filter query string
     * @param supportedKeys the filter keys this provider/entity implementation recognizes
     * @param clauseBuilder builds a provider-specific clause from each valid (key, value) pair,
     *                      or returns null to omit it from the result
     * @return the non-null clauses built from filter's segments
     * @throws UnsupportedFilterException if a segment is malformed, or its key isn't in supportedKeys
     */
    public static List<String> parseClauses(String filter, Set<String> supportedKeys,
            BinaryOperator<String> clauseBuilder) {
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
                throw new UnsupportedFilterException("The filter '" + key +
                        "' is not supported by this implementation, valid filters are " +
                        String.join(", ", supportedKeys));
            }
            String clause = clauseBuilder.apply(key, value);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        return clauses;
    }

    /** Signals a {@code filter} query this API does not support, surfaced to callers as a 400. */
    public static final class UnsupportedFilterException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnsupportedFilterException(String message) {
            super(java.util.Objects.requireNonNull(message));
        }
    }
}
