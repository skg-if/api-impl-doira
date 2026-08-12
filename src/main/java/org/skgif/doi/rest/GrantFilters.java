package org.skgif.doi.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Translates the SKG-IF {@code filter} query syntax into a DataCite REST API {@code query}
 * string for the {@code /datacite/grants} endpoint - see {@link FilterQuerySyntax} for the shared
 * comma-splitting mechanics, and {@link ProductFilters} for the equivalent on {@code
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
final class GrantFilters {

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

    private GrantFilters() {
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
            case "identifiers.value" -> "doi:\"" + escape(value) + "\"";
            // We only ever expose doi identifiers, so any other requested scheme never matches.
            case "identifiers.scheme" -> schemeOnlyFilter(value, "doi");

            // contributions.by.* - populated from remaining creators[] + all contributors[]
            // (see DataCiteToSkgIfMapper.toGrant), so every by-filter matches against either.
            case "contributions.by.local_identifier" ->
                    "(creators.nameIdentifiers.nameIdentifier:\"" + escape(value)
                            + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + escape(value) + "\")";
            // Unlike Product contributions (always a person -> orcid only), Grant contributions
            // can be organisational (-> ror), so both schemes are valid here.
            case "contributions.by.identifiers.scheme" -> ("orcid".equalsIgnoreCase(value) || "ror".equalsIgnoreCase(value))
                    ? null : NO_MATCH_CLAUSE;
            case "contributions.by.identifiers.value" -> byIdentifierValueClause(value);
            case "contributions.by.family_name" ->
                    "(creators.familyName:\"" + escape(value) + "\" OR contributors.familyName:\"" + escape(value) + "\")";
            case "contributions.by.given_name" ->
                    "(creators.givenName:\"" + escape(value) + "\" OR contributors.givenName:\"" + escape(value) + "\")";
            case "contributions.by.name" ->
                    "(creators.name:\"" + escape(value) + "\" OR contributors.name:\"" + escape(value) + "\")";

            // contributions.declared_affiliations.* - built from creator/contributor
            // affiliation[], same as Product.
            case "contributions.declared_affiliations.local_identifier" ->
                    "(creators.affiliation.affiliationIdentifier:\"" + escape(value)
                            + "\" OR contributors.affiliation.affiliationIdentifier:\"" + escape(value) + "\")";
            case "contributions.declared_affiliations.identifiers.value" -> rorClause(
                    "(creators.affiliation.affiliationIdentifier:\"%s\" OR contributors.affiliation.affiliationIdentifier:\"%s\")",
                    value);
            case "contributions.declared_affiliations.identifiers.scheme" -> schemeOnlyFilter(value, "ror");
            case "contributions.declared_affiliations.name" ->
                    "(creators.affiliation.name:\"" + escape(value) + "\" OR contributors.affiliation.name:\"" + escape(value) + "\")";

            // beneficiaries.* - organisational contributors only (see
            // DataCiteToSkgIfMapper.grantBeneficiaries), but DataCite has no way to constrain a
            // flat query to just the organisational subset, so this matches all contributors.
            case "beneficiaries.identifiers.scheme" -> schemeOnlyFilter(value, "ror");
            case "beneficiaries.identifiers.value" -> rorClause("contributors.nameIdentifiers.nameIdentifier:\"%s\"", value);
            case "beneficiaries.name" -> "contributors.name:\"" + escape(value) + "\"";

            // funding_agency.* - the first ROR-bearing creator (falling back to publisher, which
            // has no separate DataCite field to filter on beyond the creator name/ROR itself).
            case "funding_agency.name" -> "creators.name:\"" + escape(value) + "\"";
            case "funding_agency.identifiers.scheme" -> schemeOnlyFilter(value, "ror");
            case "funding_agency.identifiers.value" -> rorClause("creators.nameIdentifiers.nameIdentifier:\"%s\"", value);

            case "cf.search.title", "cf.search.title_abstract" -> escape(value);
            default -> null;
        };
    }

    /**
     * A {@code contributions.by.identifiers.value} filter doesn't say which scheme it's for
     * (unlike Product, where it's always orcid) - so this matches either an ORCID or a ROR full
     * URL against either creators or contributors.
     */
    private static String byIdentifierValueClause(String bareValue) {
        String orcid = ORCID_BASE_URL + escape(bareValue);
        String ror = ROR_BASE_URL + escape(bareValue);
        return "(creators.nameIdentifiers.nameIdentifier:\"" + orcid
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + orcid
                + "\" OR creators.nameIdentifiers.nameIdentifier:\"" + ror
                + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + ror + "\")";
    }

    /** Bare ROR id -> the given single-placeholder clause template, filled with the full ROR URL. */
    private static String rorClause(String template, String bareRor) {
        String value = ROR_BASE_URL + escape(bareRor);
        return String.format(template, value, value);
    }

    /** For attributes we only ever emit one fixed scheme/value for - no-op if it matches, else forces zero results. */
    private static String schemeOnlyFilter(String value, String expectedScheme) {
        return expectedScheme.equalsIgnoreCase(value) ? null : NO_MATCH_CLAUSE;
    }

    private static String escape(String value) {
        return FilterQuerySyntax.escape(value);
    }
}
