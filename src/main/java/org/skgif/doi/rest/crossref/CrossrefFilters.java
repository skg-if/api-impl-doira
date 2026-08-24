package org.skgif.doi.rest.crossref;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.spec.GrantFilterKeys;
import org.skgif.doi.spec.IdentifierScheme;
import org.skgif.doi.spec.ProductFilterKeys;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Translates the SKG-IF {@code filter} query syntax into a Crossref REST API {@code filter}
 * clause (plus, for the two free-text keys, a separate {@code query.title}/{@code
 * query.bibliographic} parameter - Crossref's relevance-ranked search is a distinct mechanism
 * from its exact-match {@code filter=}, unlike DataCite where both go through the same {@code
 * query} string) - see {@link FilterQuerySyntax} for the shared comma-splitting mechanics used
 * by {@code DataCiteProductFilters}/{@code DataCiteGrantFilters} on the DataCite side.
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

    // Unquoted, unlike FilterQuerySyntax.NO_MATCH_CLAUSE - Crossref's filter= syntax has no Lucene
    // quoting to escape, so a bare sentinel value is enough to guarantee zero matches.
    /** Crossref query clause guaranteed to match no record, for a filter that can't be satisfied. */
    private static final String NO_MATCH_CLAUSE = "doi:__no_match__";

    // Package-private (not private) so CrossrefFiltersTest can assert PRODUCT_CLAUSE_BUILDERS
    // covers exactly these keys.
    /** The {@link ProductFilterKeys} values the {@code /crossref/products} endpoint supports. */
    static final Set<String> PRODUCT_SUPPORTED = Set.of(
            ProductFilterKeys.PRODUCT_TYPE.key(),
            ProductFilterKeys.IDENTIFIERS_ID.key(),
            ProductFilterKeys.IDENTIFIERS_SCHEME.key(),
            ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID.key(),
            ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key(),
            ProductFilterKeys.CF_CONTRIBUTIONS_ORCID.key(),
            ProductFilterKeys.FUNDING_GRANT_NUMBER.key(),
            ProductFilterKeys.CF_SEARCH_TITLE.key(),
            ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT.key());

    // Package-private (not private) so CrossrefFiltersTest can assert GRANT_CLAUSE_BUILDERS
    // covers exactly these keys.
    /** The {@link GrantFilterKeys} values the {@code /crossref/grants} endpoint supports. */
    static final Set<String> GRANT_SUPPORTED = Set.of(
            GrantFilterKeys.IDENTIFIERS_VALUE.key(),
            GrantFilterKeys.IDENTIFIERS_SCHEME.key(),
            GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE.key(),
            GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key(),
            GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE.key(),
            GrantFilterKeys.CF_SEARCH_TITLE.key(),
            GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT.key());

    private CrossrefFilters() {
    }

    static ParsedFilter toProductsQuery(String filter) {
        return parse(filter, PRODUCT_SUPPORTED, CrossrefFilters::toProductClause);
    }

    static ParsedFilter toGrantsQuery(String filter) {
        return parse(filter, GRANT_SUPPORTED, CrossrefFilters::toGrantClause);
    }

    /**
     * Appends a {@code prefix:<prefix>} clause restricting results to a deployment-configured
     * Crossref DOI prefix, shared by {@link CrossrefGrantsResource}/{@link CrossrefProductsResource}.
     *
     * @param prefix the configured Crossref DOI prefix, if any
     * @param filter the filter clause built so far, or null
     * @return {@code filter} with the prefix clause appended, or unchanged if no prefix is configured
     */
    static String withPrefix(Optional<String> prefix, String filter) {
        String prefixValue = prefix.filter(p -> !p.isBlank()).orElse(null);
        if (prefixValue == null) {
            return filter;
        }
        String prefixClause = "prefix:" + prefixValue;
        return filter == null ? prefixClause : filter + "," + prefixClause;
    }

    @FunctionalInterface
    private interface ClauseBuilder {
        String clause(String key, String value, ParsedFilter.Builder builder);
    }

    /**
     * The per-filter-key clause builders held in {@link #PRODUCT_CLAUSE_BUILDERS}/
     * {@link #GRANT_CLAUSE_BUILDERS} - unlike {@link ClauseBuilder}, the key itself is already
     * baked into which builder got looked up, so it isn't passed again here.
     */
    @FunctionalInterface
    private interface ValueClauseBuilder {
        String clause(String value, ParsedFilter.Builder builder);
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

    /**
     * Maps every supported Product filter key to its clause builder - see {@link #toProductClause}.
     * Package-private (not private) so {@code CrossrefFiltersTest} can assert it covers every key
     * {@code PRODUCT_SUPPORTED} declares.
     */
    static final Map<ProductFilterKeys, ValueClauseBuilder> PRODUCT_CLAUSE_BUILDERS =
            new EnumMap<>(ProductFilterKeys.class);

    /**
     * Maps every supported Grant filter key to its clause builder - see {@link #toGrantClause}.
     * Package-private (not private) so {@code CrossrefFiltersTest} can assert it covers every key
     * {@code GRANT_SUPPORTED} declares.
     */
    static final Map<GrantFilterKeys, ValueClauseBuilder> GRANT_CLAUSE_BUILDERS =
            new EnumMap<>(GrantFilterKeys.class);

    static {
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.PRODUCT_TYPE, (value, _) -> productTypeClause(value));
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.IDENTIFIERS_ID,
                (value, _) -> "doi:" + ExternalIdentifierUrls.stripDoiUrl(value));
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.IDENTIFIERS_SCHEME,
                (value, _) -> FilterQuerySyntax.schemeOnlyFilter(value, IdentifierScheme.DOI.value(),
                        NO_MATCH_CLAUSE));
        ValueClauseBuilder orcidClause = (value, _) -> "orcid:" + ExternalIdentifierUrls.stripOrcidUrl(value);
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID, orcidClause);
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CONTRIBUTIONS_ORCID, orcidClause);
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
                (value, _) -> FilterQuerySyntax.schemeOnlyFilter(value, IdentifierScheme.ORCID.value(),
                        NO_MATCH_CLAUSE));
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.FUNDING_GRANT_NUMBER,
                (value, _) -> "award.number:" + value);
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.CF_SEARCH_TITLE, CrossrefFilters::queryTitleClause);
        PRODUCT_CLAUSE_BUILDERS.put(ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT,
                CrossrefFilters::queryBibliographicClause);

        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.IDENTIFIERS_VALUE,
                (value, _) -> "doi:" + ExternalIdentifierUrls.stripDoiUrl(value));
        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.IDENTIFIERS_SCHEME,
                (value, _) -> FilterQuerySyntax.schemeOnlyFilter(value, IdentifierScheme.DOI.value(),
                        NO_MATCH_CLAUSE));
        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE,
                (value, _) -> "orcid:" + ExternalIdentifierUrls.stripOrcidUrl(value));
        // Grant contributions can be organisational (ror) too, but Crossref's "orcid" filter
        // only ever matches a person - a ror-scoped value harmlessly never matches.
        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
                (value, _) -> (IdentifierScheme.ORCID.value().equalsIgnoreCase(value) ||
                        IdentifierScheme.ROR.value().equalsIgnoreCase(value)) ? null : NO_MATCH_CLAUSE);
        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE,
                (value, _) -> "award.funder:" + value);
        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.CF_SEARCH_TITLE, CrossrefFilters::queryTitleClause);
        GRANT_CLAUSE_BUILDERS.put(GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT, CrossrefFilters::queryBibliographicClause);
    }

    // Sole call site is toProductsQuery's `parse(filter, PRODUCT_SUPPORTED,
    // CrossrefFilters::toProductClause)` above.
    private static String toProductClause(String key, String value, ParsedFilter.Builder builder) {
        return PRODUCT_CLAUSE_BUILDERS.getOrDefault(ProductFilterKeys.fromKey(key), (_, _) -> null)
                .clause(value, builder);
    }

    // Sole call site is toGrantsQuery's `parse(filter, GRANT_SUPPORTED,
    // CrossrefFilters::toGrantClause)` above.
    private static String toGrantClause(String key, String value, ParsedFilter.Builder builder) {
        return GRANT_CLAUSE_BUILDERS.getOrDefault(GrantFilterKeys.fromKey(key), (_, _) -> null)
                .clause(value, builder);
    }

    @SuppressWarnings("PMD.ReturnNullConsiderOptional")
    private static String queryTitleClause(String value, ParsedFilter.Builder builder) {
        builder.queryTitle(value);
        return null;
    }

    @SuppressWarnings("PMD.ReturnNullConsiderOptional")
    private static String queryBibliographicClause(String value, ParsedFilter.Builder builder) {
        builder.queryBibliographic(value);
        return null;
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
        } catch (IllegalArgumentException _) {
            return NO_MATCH_CLAUSE;
        }
        List<String> types = CrossrefTypeMapping.typesFor(productType);
        if (types.isEmpty()) {
            return NO_MATCH_CLAUSE;
        }
        return types.stream().map(type -> "type:" + type).collect(Collectors.joining(","));
    }

    /**
     * The three independent query components Crossref's {@code /works} list endpoint accepts.
     *
     * @param filter             the {@code filter=} clause
     * @param queryTitle         the {@code query.title} free-text search value
     * @param queryBibliographic the {@code query.bibliographic} free-text search value
     */
    record ParsedFilter(
            String filter,
            String queryTitle,
            String queryBibliographic) {

        // Fields intentionally share their names with their fluent setters below, same
        // builder idiom checkstyle.xml's HiddenField already special-cases for this codebase.
        @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
        private static final class Builder {
            /** The {@code filter=} clause being assembled. */
            private String filter;
            /** The {@code query.title} free-text search value being assembled. */
            private String queryTitle;
            /** The {@code query.bibliographic} free-text search value being assembled. */
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
