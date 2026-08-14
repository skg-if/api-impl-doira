package org.skgif.doi.rest;

import java.util.List;
import java.util.Set;

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
    private static final String ORCID_BASE_URL = "https://orcid.org/";
    private static final String ROR_BASE_URL = "https://ror.org/";

    private static final Set<String> SUPPORTED = Set.of(
            "identifiers.scheme",
            "identifiers.value",
            "contributions.by.local_identifier",
            "contributions.by.identifiers.scheme",
            "contributions.by.identifiers.value",
            "contributions.by.family_name",
            "contributions.by.given_name",
            "contributions.by.name",
            "contributions.declared_affiliations.local_identifier",
            "contributions.declared_affiliations.identifiers.scheme",
            "contributions.declared_affiliations.identifiers.value",
            "contributions.declared_affiliations.name",
            "beneficiaries.identifiers.scheme",
            "beneficiaries.identifiers.value",
            "beneficiaries.name",
            "funding_agency.name",
            "funding_agency.identifiers.scheme",
            "funding_agency.identifiers.value",
            "cf.search.title",
            "cf.search.title_abstract");

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
            case "identifiers.value" -> "doi:\"" + escape(value) + "\"";
            // We only ever expose doi identifiers, so any other requested scheme never matches.
            case "identifiers.scheme" -> FilterQuerySyntax.schemeOnlyFilter(value, "doi", NO_MATCH_CLAUSE);

            // contributions.by.* - populated from remaining creators[] + all contributors[]
            // (see DataCiteToSkgIfMapper.toGrant), so every by-filter matches against either.
            case "contributions.by.local_identifier" ->
                    FilterQuerySyntax.creatorOrContributorClause("nameIdentifiers.nameIdentifier", value);
            // Unlike Product contributions (always a person -> orcid only), Grant contributions
            // can be organisational (-> ror), so both schemes are valid here.
            case "contributions.by.identifiers.scheme" -> ("orcid".equalsIgnoreCase(value) || "ror".equalsIgnoreCase(value))
                    ? null : NO_MATCH_CLAUSE;
            case "contributions.by.identifiers.value" -> byIdentifierValueClause(value);
            case "contributions.by.family_name" -> FilterQuerySyntax.creatorOrContributorClause("familyName", value);
            case "contributions.by.given_name" -> FilterQuerySyntax.creatorOrContributorClause("givenName", value);
            case "contributions.by.name" -> FilterQuerySyntax.creatorOrContributorClause("name", value);

            // contributions.declared_affiliations.* - built from creator/contributor
            // affiliation[], same as Product.
            case "contributions.declared_affiliations.local_identifier" ->
                    FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier", value);
            case "contributions.declared_affiliations.identifiers.value" ->
                    FilterQuerySyntax.creatorOrContributorClause("affiliation.affiliationIdentifier", ROR_BASE_URL + value);
            case "contributions.declared_affiliations.identifiers.scheme" -> FilterQuerySyntax.schemeOnlyFilter(value, "ror", NO_MATCH_CLAUSE);
            case "contributions.declared_affiliations.name" -> FilterQuerySyntax.creatorOrContributorClause("affiliation.name", value);

            // beneficiaries.* - organisational contributors only (see
            // DataCiteToSkgIfMapper.grantBeneficiaries), but DataCite has no way to constrain a
            // flat query to just the organisational subset, so this matches all contributors.
            case "beneficiaries.identifiers.scheme" -> FilterQuerySyntax.schemeOnlyFilter(value, "ror", NO_MATCH_CLAUSE);
            case "beneficiaries.identifiers.value" -> rorClause("contributors.nameIdentifiers.nameIdentifier", value);
            case "beneficiaries.name" -> "contributors.name:\"" + escape(value) + "\"";

            // funding_agency.* - the first ROR-bearing creator (falling back to publisher, which
            // has no separate DataCite field to filter on beyond the creator name/ROR itself).
            case "funding_agency.name" -> "creators.name:\"" + escape(value) + "\"";
            case "funding_agency.identifiers.scheme" -> FilterQuerySyntax.schemeOnlyFilter(value, "ror", NO_MATCH_CLAUSE);
            case "funding_agency.identifiers.value" -> rorClause("creators.nameIdentifiers.nameIdentifier", value);

            case "cf.search.title", "cf.search.title_abstract" -> escape(value);
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
        String orcid = ORCID_BASE_URL + escape(bareValue);
        String ror = ROR_BASE_URL + escape(bareValue);
        return "(creators.nameIdentifiers.nameIdentifier:\"" + orcid
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + orcid
                + "\" OR creators.nameIdentifiers.nameIdentifier:\"" + ror
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + ror + "\")";
    }

    /**
     * Bare ROR id -> the given single field, matched against the full ROR URL.
     *
     * @param field the DataCite field to match against
     * @param bareRor the bare ROR id from the filter value
     * @return a Lucene clause matching field against the full ROR URL for bareRor
     */
    private static String rorClause(String field, String bareRor) {
        return field + ":\"" + escape(ROR_BASE_URL + bareRor) + "\"";
    }

    private static String escape(String value) {
        return FilterQuerySyntax.escape(value);
    }
}
