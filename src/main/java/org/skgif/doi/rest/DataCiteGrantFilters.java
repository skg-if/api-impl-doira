package org.skgif.doi.rest;

import java.util.List;
import java.util.Set;
import org.skgif.doi.spec.GrantFilterKeys;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Translates the SKG-IF {@code filter} query syntax into a DataCite REST API {@code query}
 * string for the {@code /datacite/grants} endpoint - see {@link FilterQuerySyntax} for the shared
 * comma-splitting mechanics, and {@link DataCiteProductFilters} for the equivalent on {@code
 * /datacite/products}. Only a modest, explicitly-supported subset of the spec's Grant filter keys is
 * implemented - per the spec, "each filter implementation is optional" and unsupported filters
 * must 422.
 *
 * <p>Note the spec's own Grant filter table uses {@code identifiers.value} / {@code
 * contributions.by.identifiers.value} / etc. (the {@code .value} suffix), whereas its Product
 * filter table uses {@code identifiers.id} for the same concept - an inconsistency in the spec
 * itself, not something introduced here; each entity's filters follow its own documented table.
 *
 * <p>Deliberately NOT implemented, since {@code DataCiteToSkgIfMapper.toGrant} never populates
 * these fields (no reliable generic DataCite source for them - see its class javadoc):
 * {@code acronym}, {@code currency}, {@code website}, {@code funding_stream}, {@code
 * grant_number}, {@code contributions.role}, and the {@code short_name}/{@code website}/{@code
 * country} sub-filters of {@code beneficiaries.*}, {@code contributions.declared_affiliations.*}
 * and {@code funding_agency.*}.
 */
final class DataCiteGrantFilters {

    private static final String NO_MATCH_CLAUSE = FilterQuerySyntax.NO_MATCH_CLAUSE;
    private static final String SCHEME_ROR = "ror";

    private static final Set<String> SUPPORTED = Set.of(
            GrantFilterKeys.IDENTIFIERS_SCHEME,
            GrantFilterKeys.IDENTIFIERS_VALUE,
            GrantFilterKeys.CONTRIBUTIONS_BY_LOCAL_IDENTIFIER,
            GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME,
            GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE,
            GrantFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME,
            GrantFilterKeys.CONTRIBUTIONS_BY_GIVEN_NAME,
            GrantFilterKeys.CONTRIBUTIONS_BY_NAME,
            GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER,
            GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME,
            GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_VALUE,
            GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME,
            GrantFilterKeys.BENEFICIARIES_IDENTIFIERS_SCHEME,
            GrantFilterKeys.BENEFICIARIES_IDENTIFIERS_VALUE,
            GrantFilterKeys.BENEFICIARIES_NAME,
            GrantFilterKeys.FUNDING_AGENCY_NAME,
            GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_SCHEME,
            GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE,
            GrantFilterKeys.CF_SEARCH_TITLE,
            GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT);

    private DataCiteGrantFilters() {
    }

    static String toDataCiteQuery(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        List<String> clauses = FilterQuerySyntax.parseClauses(filter, SUPPORTED, DataCiteGrantFilters::toClause);
        return clauses.isEmpty() ? null : String.join(" AND ", clauses);
    }

    private static String toClause(String key, String value) {
        return switch (key) {
            case GrantFilterKeys.IDENTIFIERS_VALUE -> "doi:\"" + escape(value) + "\"";
            // We only ever expose doi identifiers, so any other requested scheme never matches.
            case GrantFilterKeys.IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);

            // contributions.by.* - populated from remaining creators[] + all contributors[]
            // (see DataCiteToSkgIfMapper.toGrant), so every by-filter matches against either.
            case GrantFilterKeys.CONTRIBUTIONS_BY_LOCAL_IDENTIFIER ->
                FilterQuerySyntax.creatorOrContributorClause("nameIdentifiers.nameIdentifier", value);
            // Unlike Product contributions (always a person -> orcid only), Grant contributions
            // can be organisational (-> ror), so both schemes are valid here.
            case GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME ->
                ("orcid".equalsIgnoreCase(value) || SCHEME_ROR.equalsIgnoreCase(value)) ? null : NO_MATCH_CLAUSE;
            case GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE -> byIdentifierValueClause(value);
            case GrantFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("familyName", value);
            case GrantFilterKeys.CONTRIBUTIONS_BY_GIVEN_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("givenName", value);
            case GrantFilterKeys.CONTRIBUTIONS_BY_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("name", value);

            // contributions.declared_affiliations.* - built from creator/contributor
            // affiliation[], same as Product.
            case GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER ->
                FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier", value);
            case GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_VALUE ->
                FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier",
                        ExternalIdentifierUrls.ROR_BASE_URL + value);
            case GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, SCHEME_ROR, NO_MATCH_CLAUSE);
            case GrantFilterKeys.CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME ->
                FilterQuerySyntax.creatorOrContributorClause("affiliation.name", value);

            // beneficiaries.* - organisational contributors only (see
            // DataCiteToSkgIfMapper.grantBeneficiaries), but DataCite has no way to constrain a
            // flat query to just the organisational subset, so this matches all contributors.
            case GrantFilterKeys.BENEFICIARIES_IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, SCHEME_ROR, NO_MATCH_CLAUSE);
            case GrantFilterKeys.BENEFICIARIES_IDENTIFIERS_VALUE ->
                rorClause("contributors.nameIdentifiers.nameIdentifier", value);
            case GrantFilterKeys.BENEFICIARIES_NAME -> "contributors.name:\"" + escape(value) + "\"";

            // funding_agency.* - the first ROR-bearing creator (falling back to publisher, which
            // has no separate DataCite field to filter on beyond the creator name/ROR itself).
            case GrantFilterKeys.FUNDING_AGENCY_NAME -> "creators.name:\"" + escape(value) + "\"";
            case GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_SCHEME ->
                FilterQuerySyntax.schemeOnlyFilter(value, SCHEME_ROR, NO_MATCH_CLAUSE);
            case GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE ->
                rorClause("creators.nameIdentifiers.nameIdentifier", value);

            case GrantFilterKeys.CF_SEARCH_TITLE, GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT -> escape(value);
            default -> null;
        };
    }

    /**
     * A {@code contributions.by.identifiers.value} filter doesn't say which scheme it's for
     * (unlike Product, where it's always orcid) - so this matches either an ORCID or a ROR full
     * URL against either creators or contributors.
     *
     * @param bareValue the bare ORCID or ROR id from the filter value
     * @return a Lucene clause matching bareValue as either an ORCID or ROR URL, on either role
     */
    private static String byIdentifierValueClause(String bareValue) {
        String orcid = ExternalIdentifierUrls.ORCID_BASE_URL + escape(bareValue);
        String ror = ExternalIdentifierUrls.ROR_BASE_URL + escape(bareValue);
        return "(creators.nameIdentifiers.nameIdentifier:\"" + orcid
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + orcid
                + "\" OR creators.nameIdentifiers.nameIdentifier:\"" + ror
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + ror + "\")";
    }

    /**
     * Bare ROR id -> the given single field, matched against the full ROR URL.
     *
     * @param field   the DataCite field to match against
     * @param bareRor the bare ROR id from the filter value
     * @return a Lucene clause matching field against the full ROR URL for bareRor
     */
    private static String rorClause(String field, String bareRor) {
        return field + ":\"" + escape(ExternalIdentifierUrls.ROR_BASE_URL + bareRor) + "\"";
    }

    private static String escape(String value) {
        return FilterQuerySyntax.escape(value);
    }
}
