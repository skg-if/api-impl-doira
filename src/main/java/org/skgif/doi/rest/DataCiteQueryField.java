package org.skgif.doi.rest;

/**
 * DataCite's own Lucene {@code query} field-path names - see
 * https://support.datacite.org/docs/queries#supported-fields for the full list DataCite exposes -
 * reused across {@link FilterQuerySyntax}, {@link DataCiteGrantFilters}, {@link
 * DataCiteProductFilters}, {@link DataCiteGrantsResource} and {@link DataCiteProductsResource} -
 * single source of truth so these don't drift when duplicated as raw string literals. Distinct
 * from {@code org.skgif.doi.spec}'s {@code GrantFilterKeys}/{@code ProductFilterKeys}: those are
 * the SKG-IF spec's filter *keys*; these are DataCite's own field *paths* used to build the
 * resulting Lucene clause.
 *
 * <p>Each constant name is {@code DATACITE_FILTER_} followed by its DataCite field-path string
 * upper-cased with both {@code .} and internal camelCase word boundaries turned into {@code _} -
 * e.g. {@code creators.familyName} becomes {@link #DATACITE_FILTER_CREATORS_FAMILY_NAME}. Every
 * constant is the complete field path as it appears in a DataCite query - none needs a role
 * prefix concatenated onto it at call time. Fields that exist on both the {@code creators[]} and
 * {@code contributors[]} roles (see {@code DataCiteToSkgIfMapper}) therefore get one constant per
 * role, grouped in pairs below; the pairing isn't enforced by the type system, so keep each pair
 * together when adding a new one.
 */
enum DataCiteQueryField {

    DATACITE_FILTER_CREATORS_NAME_IDENTIFIERS_NAME_IDENTIFIER("creators.nameIdentifiers.nameIdentifier"),
    DATACITE_FILTER_CONTRIBUTORS_NAME_IDENTIFIERS_NAME_IDENTIFIER("contributors.nameIdentifiers.nameIdentifier"),

    DATACITE_FILTER_CREATORS_FAMILY_NAME("creators.familyName"),
    DATACITE_FILTER_CONTRIBUTORS_FAMILY_NAME("contributors.familyName"),

    DATACITE_FILTER_CREATORS_GIVEN_NAME("creators.givenName"),
    DATACITE_FILTER_CONTRIBUTORS_GIVEN_NAME("contributors.givenName"),

    DATACITE_FILTER_CREATORS_NAME("creators.name"),
    DATACITE_FILTER_CONTRIBUTORS_NAME("contributors.name"),

    DATACITE_FILTER_CREATORS_AFFILIATION_AFFILIATION_IDENTIFIER("creators.affiliation.affiliationIdentifier"),
    DATACITE_FILTER_CONTRIBUTORS_AFFILIATION_AFFILIATION_IDENTIFIER("contributors.affiliation.affiliationIdentifier"),

    DATACITE_FILTER_CREATORS_AFFILIATION_NAME("creators.affiliation.name"),
    DATACITE_FILTER_CONTRIBUTORS_AFFILIATION_NAME("contributors.affiliation.name"),

    DATACITE_FILTER_FUNDING_REFERENCES_AWARD_NUMBER("fundingReferences.awardNumber"),
    DATACITE_FILTER_RELATED_IDENTIFIERS_RELATED_IDENTIFIER("relatedIdentifiers.relatedIdentifier"),
    DATACITE_FILTER_TYPES_RESOURCE_TYPE_GENERAL("types.resourceTypeGeneral");

    // Field intentionally shares its name with its accessor below, same idiom
    // IdentifierScheme's field already does this for.
    /** The constant's underlying DataCite Lucene field-path string. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final String value;

    DataCiteQueryField(String value) {
        this.value = value;
    }

    /**
     * @return the DataCite Lucene field-path string this constant represents
     */
    String value() {
        return value;
    }
}
