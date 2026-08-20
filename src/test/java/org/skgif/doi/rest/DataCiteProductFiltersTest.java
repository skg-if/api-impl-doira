package org.skgif.doi.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skgif.doi.spec.ProductFilterKeys;

class DataCiteProductFiltersTest {

    @Test
    void clauseBuilders_coverEveryProductFilterKey() {
        assertThat(DataCiteProductFilters.CLAUSE_BUILDERS.keySet())
                .containsExactlyInAnyOrder(ProductFilterKeys.values());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("equalityCases")
    void toDataCiteQuery_matchesExpectedOrNull(String label, String input, String expected) {
        assertThat(DataCiteProductFilters.toDataCiteQuery(input)).isEqualTo(Optional.ofNullable(expected));
    }

    static Stream<Arguments> equalityCases() {
        String esrfAddress = "ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France";
        String orcidUrl = "https://orcid.org/0000-0002-1008-0687";
        String rorUrl = "https://ror.org/02550n020";
        return Stream.of(
                Arguments.of("returns null for null input", null, null),
                Arguments.of("returns null for blank input", "  ", null),
                Arguments.of("family name searches both creators and contributors",
                        "contributions.by.family_name:Choiniere",
                        "(creators.familyName:\"Choiniere\" OR contributors.familyName:\"Choiniere\")"),
                Arguments.of("escapes quotes in family name",
                        "contributions.by.family_name:O\"Brien",
                        "(creators.familyName:\"O\\\"Brien\" OR contributors.familyName:\"O\\\"Brien\")"),
                Arguments.of("combines multiple filters with and",
                        "identifiers.id:10.15151/esrf-dc-2493599001,contributions.by.family_name:Choiniere",
                        "doi:\"10.15151/esrf-dc-2493599001\" AND " +
                                "(creators.familyName:\"Choiniere\" OR contributors.familyName:\"Choiniere\")"),
                // Real DataCite data frequently has commas inside values (addresses, "Family,Given"
                // name strings, subject lists like "LS-3436,LS-3437") - a naive split(",") would
                // misparse these as bogus additional filter segments.
                Arguments.of("value containing commas is not misparsed as separate segments",
                        "contributions.declared_affiliations.name:" + esrfAddress,
                        "(creators.affiliation.name:\"" + esrfAddress +
                                "\" OR contributors.affiliation.name:\"" + esrfAddress + "\")"),
                Arguments.of("value containing commas followed by another real filter",
                        "contributions.declared_affiliations.name:ESRF, Grenoble,product_type:bogus",
                        "(creators.affiliation.name:\"ESRF, Grenoble\" OR " +
                                "contributors.affiliation.name:\"ESRF, Grenoble\")" +
                                " AND doi:\"__no_match__\""),
                Arguments.of("product type research software ors its resource types",
                        "product_type:research software",
                        "(types.resourceTypeGeneral:\"ComputationalNotebook\" OR " +
                                "types.resourceTypeGeneral:\"Software\" OR types.resourceTypeGeneral:\"Workflow\")"),
                Arguments.of("product type unrecognized value has no match",
                        "product_type:bogus",
                        "doi:\"__no_match__\""),
                // "award" isn't a value of SKG-IF's product_type enum at all (Awards are grants, routed
                // to /datacite/grants instead - see DataCiteProductsResource) - a filter requesting it is
                // well-formed but can never match.
                Arguments.of("product type award is not a valid product type, no match",
                        "product_type:award",
                        "doi:\"__no_match__\""),
                Arguments.of("identifiers scheme is a no-op for doi",
                        "identifiers.scheme:doi",
                        null),
                // We only ever expose doi identifiers - a request for any other scheme must force zero
                // results rather than silently no-op (matching everything), which was the pre-fix bug.
                Arguments.of("identifiers scheme zero match for other scheme",
                        "identifiers.scheme:pmid",
                        "doi:\"__no_match__\""),
                Arguments.of("by local identifier searches both roles with value as-is",
                        "contributions.by.local_identifier:" + orcidUrl,
                        "(creators.nameIdentifiers.nameIdentifier:\"" + orcidUrl +
                                "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + orcidUrl + "\")"),
                Arguments.of("by identifiers id adds orcid prefix and searches both roles",
                        "contributions.by.identifiers.id:0000-0002-1008-0687",
                        "(creators.nameIdentifiers.nameIdentifier:\"https://orcid.org/0000-0002-1008-0687\" OR " +
                                "contributors.nameIdentifiers.nameIdentifier:" +
                                "\"https://orcid.org/0000-0002-1008-0687\")"),
                Arguments.of("by identifiers scheme is a no-op for orcid",
                        "contributions.by.identifiers.scheme:orcid",
                        null),
                Arguments.of("by identifiers scheme zero match for other scheme",
                        "contributions.by.identifiers.scheme:isni",
                        "doi:\"__no_match__\""),
                Arguments.of("by given name searches both roles",
                        "contributions.by.given_name:Jonah",
                        "(creators.givenName:\"Jonah\" OR contributors.givenName:\"Jonah\")"),
                // Values containing a comma would be misparsed as two filter segments by
                // toDataCiteQuery's top-level splitter (a pre-existing, separate limitation of the
                // comma-separated filter grammar, out of scope here) - e.g. DataCite's actual
                // "Choiniere,Jonah" name string can't be used as-is, so this uses a comma-free value.
                Arguments.of("by name searches both roles",
                        "contributions.by.name:Wilkinson",
                        "(creators.name:\"Wilkinson\" OR contributors.name:\"Wilkinson\")"),
                Arguments.of("declared affiliations local identifier searches both roles with value as-is",
                        "contributions.declared_affiliations.local_identifier:" + rorUrl,
                        "(creators.affiliation.affiliationIdentifier:\"" + rorUrl +
                                "\" OR contributors.affiliation.affiliationIdentifier:\"" + rorUrl + "\")"),
                Arguments.of("declared affiliations identifiers id adds ror prefix and searches both roles",
                        "contributions.declared_affiliations.identifiers.id:02550n020",
                        "(creators.affiliation.affiliationIdentifier:\"https://ror.org/02550n020\"" +
                                " OR contributors.affiliation.affiliationIdentifier:\"https://ror.org/02550n020\")"),
                Arguments.of("declared affiliations identifiers scheme is a no-op for ror",
                        "contributions.declared_affiliations.identifiers.scheme:ror",
                        null),
                Arguments.of("declared affiliations identifiers scheme zero match for other scheme",
                        "contributions.declared_affiliations.identifiers.scheme:isni",
                        "doi:\"__no_match__\""),
                Arguments.of("declared affiliations name searches both roles",
                        "contributions.declared_affiliations.name:European Synchrotron Radiation Facility",
                        "(creators.affiliation.name:\"European Synchrotron Radiation Facility\"" +
                                " OR contributors.affiliation.name:\"European Synchrotron Radiation Facility\")"),
                Arguments.of("funding grant number",
                        "funding.grant_number:MX-2738",
                        "fundingReferences.awardNumber:\"MX-2738\""),
                Arguments.of("cf.cites strips full doi url prefix",
                        "cf.cites:https://doi.org/10.15151/esrf-dc-2493599001",
                        "relatedIdentifiers.relatedIdentifier:\"10.15151/esrf-dc-2493599001\""),
                Arguments.of("cf.cited_by bare doi passes through unchanged",
                        "cf.cited_by:10.15151/esrf-dc-2493599001",
                        "relatedIdentifiers.relatedIdentifier:\"10.15151/esrf-dc-2493599001\""));
    }

    @Test
    void toDataCiteQuery_productType_researchData_includesDataset() {
        Optional<String> query = DataCiteProductFilters.toDataCiteQuery("product_type:research data");
        assertThat(query.orElseThrow()).contains("types.resourceTypeGeneral:\"Dataset\"");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptionCases")
    void toDataCiteQuery_unsupportedOrMalformedFilter_throws(String label, String input,
            String expectedMessageFragment) {
        var thrown = assertThatThrownBy(() -> DataCiteProductFilters.toDataCiteQuery(input))
                .isInstanceOf(FilterQuerySyntax.UnsupportedFilterException.class);
        if (expectedMessageFragment != null) {
            thrown.hasMessageContaining(expectedMessageFragment);
        }
    }

    static Stream<Arguments> exceptionCases() {
        return Stream.of(
                // funding.identifiers.id is deliberately NOT implemented - see DataCiteProductFilters'
                // class javadoc - so it's a stable choice for this test, unlike the other
                // contributions.by.* keys which are now supported.
                Arguments.of("unsupported key throws",
                        "funding.identifiers.id:10.3030/101095129",
                        "funding.identifiers.id"),
                Arguments.of("malformed segment throws",
                        "contributions.by.family_name",
                        null));
    }

    @Test
    void toDataCiteQuery_cfContributionsOrcid_producesSameClauseAsIdentifiersId() {
        assertThat(DataCiteProductFilters.toDataCiteQuery("contributions.by.identifiers.id:0000-0002-1008-0687"))
                .isEqualTo(DataCiteProductFilters.toDataCiteQuery("cf.contributions_orcid:0000-0002-1008-0687"));
    }

    @Test
    void toDataCiteQuery_cfContributionsAffRor_producesSameClauseAsIdentifiersId() {
        assertThat(DataCiteProductFilters.toDataCiteQuery(
                "contributions.declared_affiliations.identifiers.id:02550n020"))
                .isEqualTo(DataCiteProductFilters.toDataCiteQuery("cf.contributions_aff_ror:02550n020"));
    }

    @Test
    void toDataCiteQuery_cfCites_producesSameClauseAsCfCitesDoi() {
        assertThat(DataCiteProductFilters.toDataCiteQuery("cf.cites_doi:10.15151/esrf-dc-2493599001"))
                .isEqualTo(
                        DataCiteProductFilters.toDataCiteQuery("cf.cites:https://doi.org/10.15151/esrf-dc-2493599001"));
    }
}
