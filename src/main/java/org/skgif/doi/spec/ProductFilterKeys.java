package org.skgif.doi.spec;

/**
 * The SKG-IF spec's {@code filter} query keys for the Product filter table - single source of
 * truth shared by {@code CrossrefFilters} and {@code DataCiteProductFilters} (each of which
 * implements only the subset it supports) so the two providers never drift apart on these
 * strings. Distinct from {@link GrantFilterKeys}: some key names coincide textually with a Grant
 * filter key (e.g. {@code identifiers.scheme}), but they belong to a different entity's filter
 * table with different allowed semantics, so the two are kept as separate classes rather than
 * merged into one flat namespace.
 */
public enum ProductFilterKeys {

    PRODUCT_TYPE("product_type"),
    IDENTIFIERS_ID("identifiers.id"),
    IDENTIFIERS_SCHEME("identifiers.scheme"),
    CONTRIBUTIONS_BY_LOCAL_IDENTIFIER("contributions.by.local_identifier"),
    CONTRIBUTIONS_BY_IDENTIFIERS_ID("contributions.by.identifiers.id"),
    CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME("contributions.by.identifiers.scheme"),
    CONTRIBUTIONS_BY_FAMILY_NAME("contributions.by.family_name"),
    CONTRIBUTIONS_BY_GIVEN_NAME("contributions.by.given_name"),
    CONTRIBUTIONS_BY_NAME("contributions.by.name"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER("contributions.declared_affiliations.local_identifier"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_ID("contributions.declared_affiliations.identifiers.id"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME("contributions.declared_affiliations.identifiers.scheme"),
    CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME("contributions.declared_affiliations.name"),
    FUNDING_GRANT_NUMBER("funding.grant_number"),
    CF_SEARCH_TITLE("cf.search.title"),
    CF_SEARCH_TITLE_ABSTRACT("cf.search.title_abstract"),
    CF_CONTRIBUTIONS_ORCID("cf.contributions_orcid"),
    CF_CONTRIBUTIONS_AFF_ROR("cf.contributions_aff_ror"),
    CF_CITES("cf.cites"),
    CF_CITED_BY("cf.cited_by"),
    CF_CITES_DOI("cf.cites_doi"),
    CF_CITED_BY_DOI("cf.cited_by_doi");

    // Field intentionally shares its name with its accessor below, same idiom
    // CrossrefFilters.ParsedFilter.Builder's fields already do this for.
    /** The constant's underlying SKG-IF {@code filter} query key string. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final String key;

    ProductFilterKeys(String key) {
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
    public static ProductFilterKeys fromKey(String key) {
        for (ProductFilterKeys candidate : values()) {
            if (candidate.key.equals(key)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown product filter key: " + key);
    }
}
