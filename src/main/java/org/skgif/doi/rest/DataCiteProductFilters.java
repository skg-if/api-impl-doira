package org.skgif.doi.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.generated.model.Product;

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
 *   <li>{@code contributions.declared_affiliations.short_name} - never populated by
 *       {@code DataCiteToSkgIfMapper}; DataCite's affiliation schema has no such concept either.
 *   <li>{@code funding.local_identifier} - always a synthesized {@code otf___} id, unguessable
 *       by any real client.
 *   <li>{@code funding.identifiers.id}/{@code .scheme} - {@code funding[].identifiers} (the
 *       grant's own identifiers, distinct from {@code funding[].funding_agency.identifiers}) is
 *       never populated; live-checking whether DataCite's unused {@code awardUri} field could
 *       back this showed real-world funding references essentially never carry it.
 *   <li>{@code cf.contributions_aff_country} - confirmed against DataCite's own metadata schema
 *       docs: affiliations have no country attribute at all.
 * </ul>
 */
final class DataCiteProductFilters {

    private static final String NO_MATCH_CLAUSE = FilterQuerySyntax.NO_MATCH_CLAUSE;
    private static final String ORCID_BASE_URL = "https://orcid.org/";
    private static final String ROR_BASE_URL = "https://ror.org/";
    private static final String DOI_URL_PREFIX = "https://doi.org/";

    private static final Set<String> SUPPORTED = Set.of(
            "product_type",
            "identifiers.id",
            "identifiers.scheme",
            "contributions.by.local_identifier",
            "contributions.by.identifiers.id",
            "contributions.by.identifiers.scheme",
            "contributions.by.family_name",
            "contributions.by.given_name",
            "contributions.by.name",
            "contributions.declared_affiliations.local_identifier",
            "contributions.declared_affiliations.identifiers.id",
            "contributions.declared_affiliations.identifiers.scheme",
            "contributions.declared_affiliations.name",
            "funding.grant_number",
            "cf.search.title",
            "cf.search.title_abstract",
            "cf.contributions_orcid",
            "cf.contributions_aff_ror",
            "cf.cites",
            "cf.cited_by",
            "cf.cites_doi",
            "cf.cited_by_doi");

    private DataCiteProductFilters() {
    }

    static String toDataCiteQuery(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        List<String> clauses = new ArrayList<>();
        for (String segment : FilterQuerySyntax.splitSegments(filter, SUPPORTED)) {
            int idx = segment.indexOf(':');
            if (idx < 0) {
                throw new FilterQuerySyntax.UnsupportedFilterException(
                        "Malformed filter segment '" + segment + "', expected 'key:value'");
            }
            String key = segment.substring(0, idx).trim();
            String value = segment.substring(idx + 1).trim();
            if (!SUPPORTED.contains(key)) {
                throw new FilterQuerySyntax.UnsupportedFilterException("The filter '" + key
                        + "' is not supported by this implementation, valid filters are " + String.join(", ", SUPPORTED));
            }
            String clause = toClause(key, value);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        return clauses.isEmpty() ? null : String.join(" AND ", clauses);
    }

    private static String toClause(String key, String value) {
        return switch (key) {
            case "product_type" -> productTypeClause(value);
            case "identifiers.id" -> "doi:\"" + escape(value) + "\"";
            // We only ever expose doi identifiers, so any other requested scheme never matches.
            case "identifiers.scheme" -> schemeOnlyFilter(value, "doi");

            // contributions.by.* - "contributions" is populated from both DataCite creators[]
            // and contributors[] (see DataCiteToSkgIfMapper), so every by-filter has to match
            // against either.
            case "contributions.by.local_identifier" ->
                    // by.local_identifier is already the full https://orcid.org/... URL when
                    // known (or an unguessable otf id otherwise, which harmlessly never
                    // matches) - DataCite stores nameIdentifier in that same full-URL form.
                    "(creators.nameIdentifiers.nameIdentifier:\"" + escape(value)
                            + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + escape(value) + "\")";
            case "cf.contributions_orcid", "contributions.by.identifiers.id" -> orcidClause(value);
            // We only ever emit "orcid" as the scheme for by.identifiers.
            case "contributions.by.identifiers.scheme" -> schemeOnlyFilter(value, "orcid");
            case "contributions.by.family_name" ->
                    "(creators.familyName:\"" + escape(value) + "\" OR contributors.familyName:\"" + escape(value) + "\")";
            case "contributions.by.given_name" ->
                    "(creators.givenName:\"" + escape(value) + "\" OR contributors.givenName:\"" + escape(value) + "\")";
            case "contributions.by.name" ->
                    "(creators.name:\"" + escape(value) + "\" OR contributors.name:\"" + escape(value) + "\")";

            // contributions.declared_affiliations.* - same creators[]/contributors[] duality.
            case "contributions.declared_affiliations.local_identifier" ->
                    // Mirrors by.local_identifier above: already a full https://ror.org/... URL
                    // when known, matching DataCite's own stored affiliationIdentifier format
                    // (confirmed live: 15166 matches for the full-URL form vs. 2 for bare).
                    "(creators.affiliation.affiliationIdentifier:\"" + escape(value)
                            + "\" OR contributors.affiliation.affiliationIdentifier:\"" + escape(value) + "\")";
            case "contributions.declared_affiliations.identifiers.id", "cf.contributions_aff_ror" -> rorClause(value);
            // We only ever emit "ror" as the scheme for declared_affiliations.identifiers.
            case "contributions.declared_affiliations.identifiers.scheme" -> schemeOnlyFilter(value, "ror");
            case "contributions.declared_affiliations.name" ->
                    "(creators.affiliation.name:\"" + escape(value) + "\" OR contributors.affiliation.name:\"" + escape(value) + "\")";

            case "funding.grant_number" -> "fundingReferences.awardNumber:\"" + escape(value) + "\"";

            case "cf.search.title", "cf.search.title_abstract" -> escape(value);
            // "a local_identifier" per spec, which for our products is DOI-based in bare-or-
            // full-URL form - normalize first so both work. cf.cites/cf.cited_by produce the
            // same clause as cites_doi/cited_by_doi (relationType direction isn't reliably
            // scopable to the same relatedIdentifiers array element in a flat query string -
            // a pre-existing simplification, not something introduced here).
            case "cf.cites", "cf.cited_by", "cf.cites_doi", "cf.cited_by_doi" ->
                    "relatedIdentifiers.relatedIdentifier:\"" + escape(stripDoiUrl(value)) + "\"";
            default -> null;
        };
    }

    /**
     * A {@code product_type} filter has no single DataCite field to match against - it maps to
     * the (possibly several) {@code resourceTypeGeneral} values that {@link ResourceTypeMapping}
     * maps onto that product_type, ORed together. An unrecognized product_type value is a
     * well-formed filter that simply never matches.
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

    /** Bare orcid -> matches creators/contributors nameIdentifiers, either role. */
    private static String orcidClause(String bareOrcid) {
        String value = ORCID_BASE_URL + escape(bareOrcid);
        return "(creators.nameIdentifiers.nameIdentifier:\"" + value
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + value + "\")";
    }

    /** Bare ROR id -> matches creators/contributors affiliation identifiers, either role. */
    private static String rorClause(String bareRor) {
        String value = ROR_BASE_URL + escape(bareRor);
        return "(creators.affiliation.affiliationIdentifier:\"" + value
                + "\" OR contributors.affiliation.affiliationIdentifier:\"" + value + "\")";
    }

    /** For attributes we only ever emit one fixed scheme/value for - no-op if it matches, else forces zero results. */
    private static String schemeOnlyFilter(String value, String expectedScheme) {
        return expectedScheme.equalsIgnoreCase(value) ? null : NO_MATCH_CLAUSE;
    }

    private static String stripDoiUrl(String value) {
        return value.startsWith(DOI_URL_PREFIX) ? value.substring(DOI_URL_PREFIX.length()) : value;
    }

    private static String escape(String value) {
        return FilterQuerySyntax.escape(value);
    }
}
