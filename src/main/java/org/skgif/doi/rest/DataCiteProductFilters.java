package org.skgif.doi.rest;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.spec.ProductFilterKeys;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Translates the SKG-IF {@code filter} query syntax into a DataCite REST API {@code query}
 * string for the {@code /datacite/products} endpoint - see {@link FilterQuerySyntax} for the shared
 * comma-splitting mechanics. Only a modest, explicitly-supported subset of the spec's filter
 * keys is implemented - per the spec, "each filter implementation is optional" and unsupported
 * filters must 422.
 *
 * <p>Deliberately NOT implemented, with reasons (see the follow-up task in the project plan
 * for the live verification behind each):
 * <ul>
 * <li>{@code contributions.declared_affiliations.short_name} - never populated by
 * {@code DataCiteToSkgIfMapper}; DataCite's affiliation schema has no such concept either.
 * <li>{@code funding.local_identifier} - always a synthesized {@code otf___} id, unguessable
 * by any real client.
 * <li>{@code funding.identifiers.id}/{@code .scheme} - {@code funding[].identifiers} (the
 * grant's own identifiers, distinct from {@code funding[].funding_agency.identifiers}) is
 * never populated; live-checking whether DataCite's unused {@code awardUri} field could
 * back this showed real-world funding references essentially never carry it.
 * <li>{@code cf.contributions_aff_country} - confirmed against DataCite's own metadata schema
 * docs: affiliations have no country attribute at all.
 * </ul>
 */
final class DataCiteProductFilters {

    private static final String NO_MATCH_CLAUSE = FilterQuerySyntax.NO_MATCH_CLAUSE;

    // Every ProductFilterKeys constant is implemented below, so supportedKeys is derived directly
    // from the enum rather than re-listed - a newly-added constant can't silently fall out of sync.
    private static final Set<String> SUPPORTED = Arrays.stream(ProductFilterKeys.values())
            .map(ProductFilterKeys::key)
            .collect(Collectors.toUnmodifiableSet());

    private DataCiteProductFilters() {
    }

    static Optional<String> toDataCiteQuery(String filter) {
        if (filter == null || filter.isBlank()) {
            return Optional.empty();
        }
        List<String> clauses = FilterQuerySyntax.parseClauses(filter, SUPPORTED, DataCiteProductFilters::toClause);
        return clauses.isEmpty() ? Optional.empty() : Optional.of(String.join(" AND ", clauses));
    }

    /**
     * Maps every {@link ProductFilterKeys} constant to its clause builder - see {@link #toClause}.
     * Package-private (not private) so {@code DataCiteProductFiltersTest} can assert it covers
     * every enum constant.
     */
    static final Map<ProductFilterKeys, Function<String, String>> CLAUSE_BUILDERS =
            new EnumMap<>(ProductFilterKeys.class);

    static {
        CLAUSE_BUILDERS.put(ProductFilterKeys.PRODUCT_TYPE, DataCiteProductFilters::productTypeClause);
        CLAUSE_BUILDERS.put(ProductFilterKeys.IDENTIFIERS_ID, value -> "doi:\"" + escape(value) + "\"");
        // We only ever expose doi identifiers, so any other requested scheme never matches.
        CLAUSE_BUILDERS.put(ProductFilterKeys.IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE));

        // contributions.by.* - "contributions" is populated from both DataCite creators[]
        // and contributors[] (see DataCiteToSkgIfMapper), so every by-filter has to match
        // against either.
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_LOCAL_IDENTIFIER,
                // by.local_identifier is already the full https://orcid.org/... URL when
                // known (or an unguessable otf id otherwise, which harmlessly never
                // matches) - DataCite stores nameIdentifier in that same full-URL form.
                value -> FilterQuerySyntax.creatorOrContributorClause("nameIdentifiers.nameIdentifier", value));
        Function<String, String> orcidClauseBuilder = DataCiteProductFilters::orcidClause;
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CONTRIBUTIONS_ORCID, orcidClauseBuilder);
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID, orcidClauseBuilder);
        // We only ever emit "orcid" as the scheme for by.identifiers.
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, "orcid", NO_MATCH_CLAUSE));
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("familyName", value));
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_GIVEN_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("givenName", value));
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_BY_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("name", value));

        // contributions.declared_affiliations.* - same creators[]/contributors[] duality.
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER,
                // Mirrors by.local_identifier above: already a full https://ror.org/... URL
                // when known, matching DataCite's own stored affiliationIdentifier format
                // (confirmed live: 15166 matches for the full-URL form vs. 2 for bare).
                value -> FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier", value));
        Function<String, String> rorClauseBuilder = DataCiteProductFilters::rorClause;
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_ID, rorClauseBuilder);
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CONTRIBUTIONS_AFF_ROR, rorClauseBuilder);
        // We only ever emit "ror" as the scheme for declared_affiliations.identifiers.
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME,
                value -> FilterQuerySyntax.schemeOnlyFilter(value, "ror", NO_MATCH_CLAUSE));
        CLAUSE_BUILDERS.put(ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME,
                value -> FilterQuerySyntax.creatorOrContributorClause("affiliation.name", value));

        CLAUSE_BUILDERS.put(ProductFilterKeys.FUNDING_GRANT_NUMBER,
                value -> "fundingReferences.awardNumber:\"" + escape(value) + "\"");

        Function<String, String> searchClauseBuilder = DataCiteProductFilters::escape;
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_SEARCH_TITLE, searchClauseBuilder);
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT, searchClauseBuilder);
        // "a local_identifier" per spec, which for our products is DOI-based in bare-or-
        // full-URL form - normalize first so both work. cf.cites/cf.cited_by produce the
        // same clause as cites_doi/cited_by_doi (relationType direction isn't reliably
        // scopable to the same relatedIdentifiers array element in a flat query string -
        // a pre-existing simplification, not something introduced here).
        Function<String, String> citesClauseBuilder = value -> "relatedIdentifiers.relatedIdentifier:\"" +
                escape(ExternalIdentifierUrls.stripDoiUrl(value)) + "\"";
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CITES, citesClauseBuilder);
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CITED_BY, citesClauseBuilder);
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CITES_DOI, citesClauseBuilder);
        CLAUSE_BUILDERS.put(ProductFilterKeys.CF_CITED_BY_DOI, citesClauseBuilder);
    }

    // Sole call site is toDataCiteQuery's `FilterQuerySyntax.parseClauses(filter, SUPPORTED,
    // DataCiteProductFilters::toClause)` above - PMD's symbol table doesn't reliably trace a
    // private method through a method reference passed as the BinaryOperator<String>
    // clause-builder argument once the generated OpenAPI sources are on the compile classpath, so
    // it misreports this method as unused.
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static String toClause(String key, String value) {
        return CLAUSE_BUILDERS.get(ProductFilterKeys.fromKey(key)).apply(value);
    }

    /**
     * A {@code product_type} filter has no single DataCite field to match against - it maps to
     * the (possibly several) {@code resourceTypeGeneral} values that {@link ResourceTypeMapping}
     * maps onto that product_type, ORed together. An unrecognized product_type value is a
     * well-formed filter that simply never matches.
     *
     * @param value the SKG-IF product_type filter value
     * @return an OR'd Lucene clause over the matching resourceTypeGeneral values
     */
    private static String productTypeClause(String value) {
        Product.ProductTypeEnum productType;
        try {
            productType = Product.ProductTypeEnum.fromValue(value);
        } catch (IllegalArgumentException e) {
            return NO_MATCH_CLAUSE;
        }
        List<String> resourceTypes = ResourceTypeMapping.resourceTypesFor(productType);
        if (resourceTypes.isEmpty()) {
            return NO_MATCH_CLAUSE;
        }
        return "(" + resourceTypes.stream()
                .map(resourceType -> "types.resourceTypeGeneral:\"" + resourceType + "\"")
                .collect(Collectors.joining(" OR ")) + ")";
    }

    /**
     * Bare orcid -> matches creators/contributors nameIdentifiers, either role.
     *
     * @param bareOrcid the bare ORCID id from the filter value
     * @return a Lucene clause matching bareOrcid as a full ORCID URL, on either role
     */
    private static String orcidClause(String bareOrcid) {
        return FilterQuerySyntax.creatorOrContributorClause("nameIdentifiers.nameIdentifier",
                ExternalIdentifierUrls.ORCID_BASE_URL + bareOrcid);
    }

    /**
     * Bare ROR id -> matches creators/contributors affiliation identifiers, either role.
     *
     * @param bareRor the bare ROR id from the filter value
     * @return a Lucene clause matching bareRor as a full ROR URL, on either role's affiliation
     */
    private static String rorClause(String bareRor) {
        return FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier",
                ExternalIdentifierUrls.ROR_BASE_URL + bareRor);
    }

    private static String escape(String value) {
        return FilterQuerySyntax.escape(value);
    }
}
