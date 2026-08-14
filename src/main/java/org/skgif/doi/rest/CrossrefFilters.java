package org.skgif.doi.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.generated.model.Product;
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
            "product_type",
            "identifiers.id",
            "identifiers.scheme",
            "contributions.by.identifiers.id",
            "contributions.by.identifiers.scheme",
            "cf.contributions_orcid",
            "funding.grant_number",
            "cf.search.title",
            "cf.search.title_abstract");

    private static final Set<String> GRANT_SUPPORTED = Set.of(
            "identifiers.value",
            "identifiers.scheme",
            "contributions.by.identifiers.value",
            "contributions.by.identifiers.scheme",
            "funding_agency.identifiers.value",
            "cf.search.title",
            "cf.search.title_abstract");

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
            case "product_type" -> productTypeClause(value);
            case "identifiers.id" -> "doi:" + FilterQuerySyntax.stripDoiUrl(value);
            case "identifiers.scheme" -> FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);
            case "contributions.by.identifiers.id", "cf.contributions_orcid" -> "orcid:" + stripOrcidUrl(value);
            case "contributions.by.identifiers.scheme" ->
                FilterQuerySyntax.schemeOnlyFilter(value, "orcid", NO_MATCH_CLAUSE);
            case "funding.grant_number" -> "award.number:" + value;
            case "cf.search.title" -> {
                builder.queryTitle(value);
                yield null;
            }
            case "cf.search.title_abstract" -> {
                builder.queryBibliographic(value);
                yield null;
            }
            default -> null;
        };
    }

    private static String toGrantClause(String key, String value, ParsedFilter.Builder builder) {
        return switch (key) {
            case "identifiers.value" -> "doi:" + FilterQuerySyntax.stripDoiUrl(value);
            case "identifiers.scheme" -> FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);
            case "contributions.by.identifiers.value" -> "orcid:" + stripOrcidUrl(value);
            // Grant contributions can be organisational (ror) too, but Crossref's "orcid" filter
            // only ever matches a person - a ror-scoped value harmlessly never matches.
            case "contributions.by.identifiers.scheme" ->
                    ("orcid".equalsIgnoreCase(value) || "ror".equalsIgnoreCase(value)) ? null : NO_MATCH_CLAUSE;
            case "funding_agency.identifiers.value" -> "award.funder:" + value;
            case "cf.search.title" -> {
                builder.queryTitle(value);
                yield null;
            }
            case "cf.search.title_abstract" -> {
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
     *     unrecognized or maps to no Crossref type
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

    /** The three independent query components Crossref's {@code /works} list endpoint accepts. */
    static final class ParsedFilter {
        final String filter;
        final String queryTitle;
        final String queryBibliographic;

        private ParsedFilter(String filter, String queryTitle, String queryBibliographic) {
            this.filter = filter;
            this.queryTitle = queryTitle;
            this.queryBibliographic = queryBibliographic;
        }

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
