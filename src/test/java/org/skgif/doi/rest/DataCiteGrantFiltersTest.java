package org.skgif.doi.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataCiteGrantFiltersTest {

    @Test
    void toDataCiteQuery_nullOrBlank_returnsNull() {
        assertNull(DataCiteGrantFilters.toDataCiteQuery(null));
        assertNull(DataCiteGrantFilters.toDataCiteQuery("  "));
    }

    @Test
    void toDataCiteQuery_identifiersValue_mapsToDoiClause() {
        assertEquals("doi:\"10.71707/r3sy-7371\"",
                DataCiteGrantFilters.toDataCiteQuery("identifiers.value:10.71707/r3sy-7371"));
    }

    @Test
    void toDataCiteQuery_identifiersScheme_noOpForDoi() {
        assertNull(DataCiteGrantFilters.toDataCiteQuery("identifiers.scheme:doi"));
    }

    @Test
    void toDataCiteQuery_identifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"", DataCiteGrantFilters.toDataCiteQuery("identifiers.scheme:handle"));
    }

    @Test
    void toDataCiteQuery_byFamilyName_searchesBothCreatorsAndContributors() {
        assertEquals("(creators.familyName:\"Smith\" OR contributors.familyName:\"Smith\")",
                DataCiteGrantFilters.toDataCiteQuery("contributions.by.family_name:Smith"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersScheme_noOpForOrcidOrRor() {
        assertNull(DataCiteGrantFilters.toDataCiteQuery("contributions.by.identifiers.scheme:orcid"));
        assertNull(DataCiteGrantFilters.toDataCiteQuery("contributions.by.identifiers.scheme:ror"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"",
                DataCiteGrantFilters.toDataCiteQuery("contributions.by.identifiers.scheme:isni"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersValue_orsOrcidAndRorFormsAcrossBothRoles() {
        assertEquals(
                "(creators.nameIdentifiers.nameIdentifier:\"https://orcid.org/018n2ja79\""
                        + " OR contributors.nameIdentifiers.nameIdentifier:\"https://orcid.org/018n2ja79\""
                        + " OR creators.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\""
                        + " OR contributors.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\")",
                DataCiteGrantFilters.toDataCiteQuery("contributions.by.identifiers.value:018n2ja79"));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsName_searchesBothRoles() {
        assertEquals(
                "(creators.affiliation.name:\"Brown University\" "
                        + "OR contributors.affiliation.name:\"Brown University\")",
                DataCiteGrantFilters.toDataCiteQuery("contributions.declared_affiliations.name:Brown University"));
    }

    @Test
    void toDataCiteQuery_beneficiariesName_matchesContributorsName() {
        assertEquals("contributors.name:\"Atlas of Living Australia\"",
                DataCiteGrantFilters.toDataCiteQuery("beneficiaries.name:Atlas of Living Australia"));
    }

    @Test
    void toDataCiteQuery_beneficiariesIdentifiersValue_addsRorPrefix() {
        assertEquals("contributors.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\"",
                DataCiteGrantFilters.toDataCiteQuery("beneficiaries.identifiers.value:018n2ja79"));
    }

    @Test
    void toDataCiteQuery_beneficiariesIdentifiersScheme_noOpForRor() {
        assertNull(DataCiteGrantFilters.toDataCiteQuery("beneficiaries.identifiers.scheme:ror"));
    }

    @Test
    void toDataCiteQuery_fundingAgencyName_matchesCreatorsName() {
        assertEquals("creators.name:\"Australian Research Data Commons\"",
                DataCiteGrantFilters.toDataCiteQuery("funding_agency.name:Australian Research Data Commons"));
    }

    @Test
    void toDataCiteQuery_fundingAgencyIdentifiersValue_addsRorPrefix() {
        assertEquals("creators.nameIdentifiers.nameIdentifier:\"https://ror.org/038sjwq14\"",
                DataCiteGrantFilters.toDataCiteQuery("funding_agency.identifiers.value:038sjwq14"));
    }

    @Test
    void toDataCiteQuery_fundingAgencyIdentifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"",
                DataCiteGrantFilters.toDataCiteQuery("funding_agency.identifiers.scheme:isni"));
    }

    @Test
    void toDataCiteQuery_searchTitle_passesThroughEscaped() {
        assertEquals("GraspOS", DataCiteGrantFilters.toDataCiteQuery("cf.search.title:GraspOS"));
    }

    @Test
    void toDataCiteQuery_unsupportedKey_throws() {
        // grant_number is deliberately NOT implemented - see DataCiteGrantFilters' class javadoc.
        var exception = assertThrows(FilterQuerySyntax.UnsupportedFilterException.class,
                () -> DataCiteGrantFilters.toDataCiteQuery("grant_number:101095129"));
        assertTrue(exception.getMessage().contains("grant_number"));
    }

    @Test
    void toDataCiteQuery_malformedSegment_throws() {
        assertThrows(FilterQuerySyntax.UnsupportedFilterException.class,
                () -> DataCiteGrantFilters.toDataCiteQuery("contributions.by.family_name"));
    }
}
