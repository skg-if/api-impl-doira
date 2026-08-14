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
public final class ProductFilterKeys {

    public static final String PRODUCT_TYPE = "product_type";
    public static final String IDENTIFIERS_ID = "identifiers.id";
    public static final String IDENTIFIERS_SCHEME = "identifiers.scheme";
    public static final String CONTRIBUTIONS_BY_LOCAL_IDENTIFIER = "contributions.by.local_identifier";
    public static final String CONTRIBUTIONS_BY_IDENTIFIERS_ID = "contributions.by.identifiers.id";
    public static final String CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME = "contributions.by.identifiers.scheme";
    public static final String CONTRIBUTIONS_BY_FAMILY_NAME = "contributions.by.family_name";
    public static final String CONTRIBUTIONS_BY_GIVEN_NAME = "contributions.by.given_name";
    public static final String CONTRIBUTIONS_BY_NAME = "contributions.by.name";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_LOCAL_IDENTIFIER =
            "contributions.declared_affiliations.local_identifier";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_ID =
            "contributions.declared_affiliations.identifiers.id";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_IDENTIFIERS_SCHEME =
            "contributions.declared_affiliations.identifiers.scheme";
    public static final String CONTRIBUTIONS_DECLARED_AFFILIATIONS_NAME = "contributions.declared_affiliations.name";
    public static final String FUNDING_GRANT_NUMBER = "funding.grant_number";
    public static final String CF_SEARCH_TITLE = "cf.search.title";
    public static final String CF_SEARCH_TITLE_ABSTRACT = "cf.search.title_abstract";
    public static final String CF_CONTRIBUTIONS_ORCID = "cf.contributions_orcid";
    public static final String CF_CONTRIBUTIONS_AFF_ROR = "cf.contributions_aff_ror";
    public static final String CF_CITES = "cf.cites";
    public static final String CF_CITED_BY = "cf.cited_by";
    public static final String CF_CITES_DOI = "cf.cites_doi";
    public static final String CF_CITED_BY_DOI = "cf.cited_by_doi";

    private ProductFilterKeys() {
    }
}
