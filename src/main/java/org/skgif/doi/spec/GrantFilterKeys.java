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
public final class GrantFilterKeys {

    public static final String IDENTIFIERS_VALUE = "identifiers.value";
    public static final String IDENTIFIERS_SCHEME = "identifiers.scheme";
    public static final String CONTRIBUTIONS_BY_LOCAL_IDENTIFIER = "contributions.by.local_identifier";
    public static final String CONTRIBUTIONS_BY_IDENTIFIERS_VALUE = "contributions.by.identifiers.value";
    public static final String CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME = "contributions.by.identifiers.scheme";
    public static final String CONTRIBUTIONS_BY_FAMILY_NAME = "contributions.by.family_name";
    public static final String CONTRIBUTIONS_BY_GIVEN_NAME = "contributions.by.given_name";
    public static final String CONTRIBUTIONS_BY_NAME = "contributions.by.name";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER =
            "contributions.declared_affiliations.local_identifier";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_VALUE =
            "contributions.declared_affiliations.identifiers.value";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME =
            "contributions.declared_affiliations.identifiers.scheme";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME = "contributions.declared_affiliations.name";
    public static final String BENEFICIARIES_IDENTIFIERS_SCHEME = "beneficiaries.identifiers.scheme";
    public static final String BENEFICIARIES_IDENTIFIERS_VALUE = "beneficiaries.identifiers.value";
    public static final String BENEFICIARIES_NAME = "beneficiaries.name";
    public static final String FUNDING_AGENCY_NAME = "funding_agency.name";
    public static final String FUNDING_AGENCY_IDENTIFIERS_SCHEME = "funding_agency.identifiers.scheme";
    public static final String FUNDING_AGENCY_IDENTIFIERS_VALUE = "funding_agency.identifiers.value";
    public static final String CF_SEARCH_TITLE = "cf.search.title";
    public static final String CF_SEARCH_TITLE_ABSTRACT = "cf.search.title_abstract";

    private GrantFilterKeys() {
    }
}
