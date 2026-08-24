package org.skgif.doi.spec;

/**
 * The SKG-IF spec's {@code filter} query keys for the Grant filter table - single source of truth
 * shared by {@code CrossrefFilters} and {@code DataCiteGrantFilters} (each of which implements
 * only the subset it supports) so the two providers never drift apart on these strings. Distinct
 * from {@link ProductFilterKeys}: some key names coincide textually with a Product filter key
 * (e.g. {@code identifiers.scheme}), but they belong to a different entity's filter table with
 * different allowed semantics, so the two are kept as separate classes rather than merged into
 * one flat namespace.
 */
public enum GrantFilterKeys {

    IDENTIFIERS_VALUE("identifiers.value"),
    IDENTIFIERS_SCHEME("identifiers.scheme"),
    CONTRIBUTIONS_BY_LOCAL_IDENTIFIER("contributions.by.local_identifier"),
    CONTRIBUTIONS_BY_IDENTIFIERS_VALUE("contributions.by.identifiers.value"),
    CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME("contributions.by.identifiers.scheme"),
    CONTRIBUTIONS_BY_FAMILY_NAME("contributions.by.family_name"),
    CONTRIBUTIONS_BY_GIVEN_NAME("contributions.by.given_name"),
    CONTRIBUTIONS_BY_NAME("contributions.by.name"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER("contributions.declared_affiliations.local_identifier"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_VALUE("contributions.declared_affiliations.identifiers.value"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME("contributions.declared_affiliations.identifiers.scheme"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME("contributions.declared_affiliations.name"),
    BENEFICIARIES_IDENTIFIERS_SCHEME("beneficiaries.identifiers.scheme"),
    BENEFICIARIES_IDENTIFIERS_VALUE("beneficiaries.identifiers.value"),
    BENEFICIARIES_NAME("beneficiaries.name"),
    FUNDING_AGENCY_NAME("funding_agency.name"),
    FUNDING_AGENCY_IDENTIFIERS_SCHEME("funding_agency.identifiers.scheme"),
    FUNDING_AGENCY_IDENTIFIERS_VALUE("funding_agency.identifiers.value"),
    CF_SEARCH_TITLE("cf.search.title"),
    CF_SEARCH_TITLE_ABSTRACT("cf.search.title_abstract");

    // Field intentionally shares its name with its accessor below, same idiom
    // CrossrefFilters.ParsedFilter.Builder's fields already do this for.
    /** The constant's underlying SKG-IF {@code filter} query key string. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final String key;

    GrantFilterKeys(String key) {
        this.key = key;
    }

    /**
     * Unwraps the constant to the raw filter key the SKG-IF spec expects.
     *
     * @return the SKG-IF {@code filter} query key string this constant represents
     */
    public String key() {
        return key;
    }

    /**
     * Resolves a raw filter key back to its constant.
     *
     * @param key a raw filter key string, expected to match one of this enum's {@link #key()} values
     * @return the constant whose {@link #key()} equals key
     * @throws IllegalArgumentException if no constant matches - callers are expected to have
     *                                  already validated key against a supportedKeys set built from
     *                                  this enum, so this indicates a caller/enum mismatch bug
     */
    public static GrantFilterKeys fromKey(String key) {
        for (GrantFilterKeys candidate : values()) {
            if (candidate.key.equals(key)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown grant filter key: " + key);
    }
}
