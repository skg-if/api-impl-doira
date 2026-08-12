package org.skgif.doi.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GrantFiltersTest {

    @Test
    void toDataCiteQuery_nullOrBlank_returnsNull() {
        assertNull(GrantFilters.toDataCiteQuery(null));
        assertNull(GrantFilters.toDataCiteQuery("  "));
    }

    @Test
    void toDataCiteQuery_identifiersValue_mapsToDoiClause() {
        assertEquals("doi:\"10.3565/83eg-9981\"", GrantFilters.toDataCiteQuery("identifiers.value:10.3565/83eg-9981"));
    }

    @Test
    void toDataCiteQuery_identifiersScheme_noOpForDoi() {
        assertNull(GrantFilters.toDataCiteQuery("identifiers.scheme:doi"));
    }

    @Test
    void toDataCiteQuery_identifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"", GrantFilters.toDataCiteQuery("identifiers.scheme:handle"));
    }

    @Test
    void toDataCiteQuery_byFamilyName_searchesBothCreatorsAndContributors() {
        assertEquals("(creators.familyName:\"Smith\" OR contributors.familyName:\"Smith\")",
                GrantFilters.toDataCiteQuery("contributions.by.family_name:Smith"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersScheme_noOpForOrcidOrRor() {
        assertNull(GrantFilters.toDataCiteQuery("contributions.by.identifiers.scheme:orcid"));
        assertNull(GrantFilters.toDataCiteQuery("contributions.by.identifiers.scheme:ror"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"", GrantFilters.toDataCiteQuery("contributions.by.identifiers.scheme:isni"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersValue_orsOrcidAndRorFormsAcrossBothRoles() {
        assertEquals(
                "(creators.nameIdentifiers.nameIdentifier:\"https://orcid.org/018n2ja79\""
                        + " OR contributors.nameIdentifiers.nameIdentifier:\"https://orcid.org/018n2ja79\""
                        + " OR creators.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\""
                        + " OR contributors.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\")",
                GrantFilters.toDataCiteQuery("contributions.by.identifiers.value:018n2ja79"));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsName_searchesBothRoles() {
        assertEquals(
                "(creators.affiliation.name:\"Brown University\" OR contributors.affiliation.name:\"Brown University\")",
                GrantFilters.toDataCiteQuery("contributions.declared_affiliations.name:Brown University"));
    }

    @Test
    void toDataCiteQuery_beneficiariesName_matchesContributorsName() {
        assertEquals("contributors.name:\"Atlas of Living Australia\"",
                GrantFilters.toDataCiteQuery("beneficiaries.name:Atlas of Living Australia"));
    }

    @Test
    void toDataCiteQuery_beneficiariesIdentifiersValue_addsRorPrefix() {
        assertEquals("contributors.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\"",
                GrantFilters.toDataCiteQuery("beneficiaries.identifiers.value:018n2ja79"));
    }

    @Test
    void toDataCiteQuery_beneficiariesIdentifiersScheme_noOpForRor() {
        assertNull(GrantFilters.toDataCiteQuery("beneficiaries.identifiers.scheme:ror"));
    }

    @Test
    void toDataCiteQuery_fundingAgencyName_matchesCreatorsName() {
        assertEquals("creators.name:\"Australian Research Data Commons\"",
                GrantFilters.toDataCiteQuery("funding_agency.name:Australian Research Data Commons"));
    }

    @Test
    void toDataCiteQuery_fundingAgencyIdentifiersValue_addsRorPrefix() {
        assertEquals("creators.nameIdentifiers.nameIdentifier:\"https://ror.org/038sjwq14\"",
                GrantFilters.toDataCiteQuery("funding_agency.identifiers.value:038sjwq14"));
    }

    @Test
    void toDataCiteQuery_fundingAgencyIdentifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"", GrantFilters.toDataCiteQuery("funding_agency.identifiers.scheme:isni"));
    }

    @Test
    void toDataCiteQuery_searchTitle_passesThroughEscaped() {
        assertEquals("GraspOS", GrantFilters.toDataCiteQuery("cf.search.title:GraspOS"));
    }

    @Test
    void toDataCiteQuery_unsupportedKey_throws() {
        // grant_number is deliberately NOT implemented - see GrantFilters' class javadoc.
        var exception = assertThrows(FilterQuerySyntax.UnsupportedFilterException.class,
                () -> GrantFilters.toDataCiteQuery("grant_number:101095129"));
        assertEquals(true, exception.getMessage().contains("grant_number"));
    }

    @Test
    void toDataCiteQuery_malformedSegment_throws() {
        assertThrows(FilterQuerySyntax.UnsupportedFilterException.class,
                () -> GrantFilters.toDataCiteQuery("contributions.by.family_name"));
    }
}
