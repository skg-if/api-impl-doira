package org.skgif.doi.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.spec.GrantFilterKeys;
import org.skgif.doi.spec.ProductFilterKeys;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Translates the SKG-IF {@code filter} query syntax into a Crossref REST API {@code filter}
 * clause (plus, for the two free-text keys, a separate {@code query.title}/{@code
 * query.bibliographic} parameter - Crossref's relevance-ranked search is a distinct mechanism
 * from its exact-match {@code filter=}, unlike DataCite where both go through the same {@code
 * query} string) - see {@link FilterQuerySyntax} for the shared comma-splitting mechanics used
 * by {@link DataCiteProductFilters}/{@link DataCiteGrantFilters} on the DataCite side.
 *
 * <p>Crossref's {@code filter=} already uses comma-joining for both AND (different filter
 * names) and OR (repeated filter name) semantics - see
 * https://github.com/CrossRef/rest-api-doc#filters - so, unlike the DataCite Lucene-syntax
 * filters, no parenthesized {@code OR} construction is needed here.
 *
 * <p>Only a modest, explicitly-supported subset of the spec's filter keys is implemented, mainly
 * the ones Crossref has a documented {@code filter=} facet for
 * ({@code type}, {@code orcid}, {@code doi}, {@code award.number}) - per the spec, "each filter
 * implementation is optional" and unsupported filters must 422.
 */
final class CrossrefFilters {

    private static final String NO_MATCH_CLAUSE = "doi:__no_match__";

    private static final Set<String> PRODUCT_SUPPORTED = Set.of(
            ProductFilterKeys.PRODUCT_TYPE,
            ProductFilterKeys.IDENTIFIERS_ID,
            ProductFilterKeys.IDENTIFIERS_SCHEME,
            ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID,
            ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
            ProductFilterKeys.CF_CONTRIBUTIONS_ORCID,
            ProductFilterKeys.FUNDING_GRANT_NUMBER,
            ProductFilterKeys.CF_SEARCH_TITLE,
            ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT);

    private static final Set<String> GRANT_SUPPORTED = Set.of(
            GrantFilterKeys.IDENTIFIERS_VALUE,
            GrantFilterKeys.IDENTIFIERS_SCHEME,
            GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE,
            GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
            GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE,
            GrantFilterKeys.CF_SEARCH_TITLE,
            GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT);

    private CrossrefFilters() {
    }

    static ParsedFilter toProductsQuery(String filter) {
        return parse(filter, PRODUCT_SUPPORTED, CrossrefFilters::toProductClause);
    }

    static ParsedFilter toGrantsQuery(String filter) {
        return parse(filter, GRANT_SUPPORTED, CrossrefFilters::toGrantClause);
    }

    private interface ClauseBuilder {
        String clause(String key, String value, ParsedFilter.Builder builder);
    }

    private static ParsedFilter parse(String filter, Set<String> supported, ClauseBuilder clauseBuilder) {
        ParsedFilter.Builder result = new ParsedFilter.Builder();
        if (filter == null || filter.isBlank()) {
            return result.build();
        }
        List<String> clauses = FilterQuerySyntax.parseClauses(filter, supported,
                (key, value) -> clauseBuilder.clause(key, value, result));
        result.filter(clauses.isEmpty() ? null : String.join(",", clauses));
        return result.build();
    }

    private static String toProductClause(String key, String value, ParsedFilter.Builder builder) {
        return switch (key) {
            case ProductFilterKeys.PRODUCT_TYPE -> productTypeClause(value);
            case ProductFilterKeys.IDENTIFIERS_ID -> "doi:" + FilterQuerySyntax.stripDoiUrl(value);
            case ProductFilterKeys.IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);
            case ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID, ProductFilterKeys.CF_CONTRIBUTIONS_ORCID ->
                "orcid:" + stripOrcidUrl(value);
            case ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "orcid", NO_MATCH_CLAUSE);
            case ProductFilterKeys.FUNDING_GRANT_NUMBER -> "award.number:" + value;
            case ProductFilterKeys.CF_SEARCH_TITLE -> {
                builder.queryTitle(value);
                yield null;
            }
            case ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT -> {
                builder.queryBibliographic(value);
                yield null;
            }
            default -> null;
        };
    }

    private static String toGrantClause(String key, String value, ParsedFilter.Builder builder) {
        return switch (key) {
            case GrantFilterKeys.IDENTIFIERS_VALUE -> "doi:" + FilterQuerySyntax.stripDoiUrl(value);
            case GrantFilterKeys.IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);
            case GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE -> "orcid:" + stripOrcidUrl(value);
            // Grant contributions can be organisational (ror) too, but Crossref's "orcid" filter
            // only ever matches a person - a ror-scoped value harmlessly never matches.
            case GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME ->
                ("orcid".equalsIgnoreCase(value) || "ror".equalsIgnoreCase(value)) ? null : NO_MATCH_CLAUSE;
            case GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE -> "award.funder:" + value;
            case GrantFilterKeys.CF_SEARCH_TITLE -> {
                builder.queryTitle(value);
                yield null;
            }
            case GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT -> {
                builder.queryBibliographic(value);
                yield null;
            }
            default -> null;
        };
    }

    /**
     * A {@code product_type} filter has no single Crossref field to match against - it maps to
     * the (possibly several) {@code type} values {@link CrossrefTypeMapping} maps onto that
     * product_type. Crossref's own comma-joining already means OR-across-repeated-filter-name,
     * so this returns the already-comma-joined sub-clause directly.
     *
     * @param value the SKG-IF product_type filter value
     * @return the comma-joined Crossref {@code type:} sub-clause, or NO_MATCH_CLAUSE if value is
     *         unrecognized or maps to no Crossref type
     */
    private static String productTypeClause(String value) {
        Product.ProductTypeEnum productType;
        try {
            productType = Product.ProductTypeEnum.fromValue(value);
        } catch (IllegalArgumentException e) {
            return NO_MATCH_CLAUSE;
        }
        List<String> types = CrossrefTypeMapping.typesFor(productType);
        if (types.isEmpty()) {
            return NO_MATCH_CLAUSE;
        }
        return types.stream().map(type -> "type:" + type).collect(Collectors.joining(","));
    }

    private static String stripOrcidUrl(String value) {
        return value.startsWith(ExternalIdentifierUrls.ORCID_BASE_URL)
                ? value.substring(ExternalIdentifierUrls.ORCID_BASE_URL.length())
                : value;
    }

    /**
     * The three independent query components Crossref's {@code /works} list endpoint accepts.
     *
     * @param filter             the {@code filter=} clause
     * @param queryTitle         the {@code query.title} free-text search value
     * @param queryBibliographic the {@code query.bibliographic} free-text search value
     */
    record ParsedFilter(String filter, String queryTitle, String queryBibliographic) {

        // Fields intentionally share their names with their fluent setters below, same
        // builder idiom checkstyle.xml's HiddenField already special-cases for this codebase.
        @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
        private static final class Builder {
            private String filter;
            private String queryTitle;
            private String queryBibliographic;

            void filter(String value) {
                this.filter = value;
            }

            void queryTitle(String value) {
                this.queryTitle = value;
            }

            void queryBibliographic(String value) {
                this.queryBibliographic = value;
            }

            ParsedFilter build() {
                return new ParsedFilter(filter, queryTitle, queryBibliographic);
            }
        }
    }
}
