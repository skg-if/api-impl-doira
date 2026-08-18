package org.skgif.doi.rest;

import java.util.List;
import java.util.Set;
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

    private static final Set<String> SUPPORTED = Set.of(
            ProductFilterKeys.PRODUCT_TYPE,
            ProductFilterKeys.IDENTIFIERS_ID,
            ProductFilterKeys.IDENTIFIERS_SCHEME,
            ProductFilterKeys.CONTRIBUTIONS_BY_LOCAL_IDENTIFIER,
            ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID,
            ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
            ProductFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME,
            ProductFilterKeys.CONTRIBUTIONS_BY_GIVEN_NAME,
            ProductFilterKeys.CONTRIBUTIONS_BY_NAME,
            ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER,
            ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_ID,
            ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME,
            ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME,
            ProductFilterKeys.FUNDING_GRANT_NUMBER,
            ProductFilterKeys.CF_SEARCH_TITLE,
            ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT,
            ProductFilterKeys.CF_CONTRIBUTIONS_ORCID,
            ProductFilterKeys.CF_CONTRIBUTIONS_AFF_ROR,
            ProductFilterKeys.CF_CITES,
            ProductFilterKeys.CF_CITED_BY,
            ProductFilterKeys.CF_CITES_DOI,
            ProductFilterKeys.CF_CITED_BY_DOI);

    private DataCiteProductFilters() {
    }

    static String toDataCiteQuery(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        List<String> clauses = FilterQuerySyntax.parseClauses(filter, SUPPORTED, DataCiteProductFilters::toClause);
        return clauses.isEmpty() ? null : String.join(" AND ", clauses);
    }

    private static String toClause(String key, String value) {
        return switch (key) {
            case ProductFilterKeys.PRODUCT_TYPE -> productTypeClause(value);
            case ProductFilterKeys.IDENTIFIERS_ID -> "doi:\"" + escape(value) + "\"";
            // We only ever expose doi identifiers, so any other requested scheme never matches.
            case ProductFilterKeys.IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);

            // contributions.by.* - "contributions" is populated from both DataCite creators[]
            // and contributors[] (see DataCiteToSkgIfMapper), so every by-filter has to match
            // against either.
            case ProductFilterKeys.CONTRIBUTIONS_BY_LOCAL_IDENTIFIER ->
                // by.local_identifier is already the full https://orcid.org/... URL when
                // known (or an unguessable otf id otherwise, which harmlessly never
                // matches) - DataCite stores nameIdentifier in that same full-URL form.
                FilterQuerySyntax.creatorOrContributorClause("nameIdentifiers.nameIdentifier", value);
            case ProductFilterKeys.CF_CONTRIBUTIONS_ORCID, ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID ->
                orcidClause(value);
            // We only ever emit "orcid" as the scheme for by.identifiers.
            case ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "orcid", NO_MATCH_CLAUSE);
            case ProductFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("familyName", value);
            case ProductFilterKeys.CONTRIBUTIONS_BY_GIVEN_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("givenName", value);
            case ProductFilterKeys.CONTRIBUTIONS_BY_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("name", value);

            // contributions.declared_affiliations.* - same creators[]/contributors[] duality.
            case ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER ->
                // Mirrors by.local_identifier above: already a full https://ror.org/... URL
                // when known, matching DataCite's own stored affiliationIdentifier format
                // (confirmed live: 15166 matches for the full-URL form vs. 2 for bare).
                FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier", value);
            case ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_ID,
                    ProductFilterKeys.CF_CONTRIBUTIONS_AFF_ROR -> rorClause(value);
            // We only ever emit "ror" as the scheme for declared_affiliations.identifiers.
            case ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "ror", NO_MATCH_CLAUSE);
            case ProductFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("affiliation.name", value);

            case ProductFilterKeys.FUNDING_GRANT_NUMBER -> "fundingReferences.awardNumber:\"" + escape(value) + "\"";

            case ProductFilterKeys.CF_SEARCH_TITLE, ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT -> escape(value);
            // "a local_identifier" per spec, which for our products is DOI-based in bare-or-
            // full-URL form - normalize first so both work. cf.cites/cf.cited_by produce the
            // same clause as cites_doi/cited_by_doi (relationType direction isn't reliably
            // scopable to the same relatedIdentifiers array element in a flat query string -
            // a pre-existing simplification, not something introduced here).
            case ProductFilterKeys.CF_CITES, ProductFilterKeys.CF_CITED_BY, ProductFilterKeys.CF_CITES_DOI,
                    ProductFilterKeys.CF_CITED_BY_DOI ->
                "relatedIdentifiers.relatedIdentifier:\"" + escape(FilterQuerySyntax.stripDoiUrl(value)) + "\"";
            default -> null;
        };
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
