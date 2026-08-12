package org.skgif.doi.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProductFiltersTest {

    @Test
    void toDataCiteQuery_nullOrBlank_returnsNull() {
        assertNull(ProductFilters.toDataCiteQuery(null));
        assertNull(ProductFilters.toDataCiteQuery("  "));
    }

    @Test
    void toDataCiteQuery_familyName_searchesBothCreatorsAndContributors() {
        assertEquals("(creators.familyName:\"Choiniere\" OR contributors.familyName:\"Choiniere\")",
                ProductFilters.toDataCiteQuery("contributions.by.family_name:Choiniere"));
    }

    @Test
    void toDataCiteQuery_familyName_escapesQuotes() {
        assertEquals("(creators.familyName:\"O\\\"Brien\" OR contributors.familyName:\"O\\\"Brien\")",
                ProductFilters.toDataCiteQuery("contributions.by.family_name:O\"Brien"));
    }

    @Test
    void toDataCiteQuery_combinesMultipleFiltersWithAnd() {
        assertEquals(
                "doi:\"10.15151/esrf-dc-2493599001\" AND "
                        + "(creators.familyName:\"Choiniere\" OR contributors.familyName:\"Choiniere\")",
                ProductFilters.toDataCiteQuery(
                        "identifiers.id:10.15151/esrf-dc-2493599001,contributions.by.family_name:Choiniere"));
    }

    @Test
    void toDataCiteQuery_valueContainingCommas_notMisparsedAsSeparateSegments() {
        // Real DataCite data frequently has commas inside values (addresses, "Family,Given"
        // name strings, subject lists like "LS-3436,LS-3437") - a naive split(",") would
        // misparse these as bogus additional filter segments.
        assertEquals(
                "(creators.affiliation.name:\"ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France\""
                        + " OR contributors.affiliation.name:\"ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France\")",
                ProductFilters.toDataCiteQuery(
                        "contributions.declared_affiliations.name:ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France"));
    }

    @Test
    void toDataCiteQuery_valueContainingCommas_followedByAnotherRealFilter() {
        assertEquals(
                "(creators.affiliation.name:\"ESRF, Grenoble\" OR contributors.affiliation.name:\"ESRF, Grenoble\")"
                        + " AND doi:\"__no_match__\"",
                ProductFilters.toDataCiteQuery(
                        "contributions.declared_affiliations.name:ESRF, Grenoble,product_type:bogus"));
    }

    @Test
    void toDataCiteQuery_unsupportedKey_throws() {
        // funding.identifiers.id is deliberately NOT implemented - see ProductFilters' class
        // javadoc - so it's a stable choice for this test, unlike the other contributions.by.*
        // keys which are now supported.
        var exception = assertThrows(FilterQuerySyntax.UnsupportedFilterException.class,
                () -> ProductFilters.toDataCiteQuery("funding.identifiers.id:10.3030/101095129"));
        assertEquals(true, exception.getMessage().contains("funding.identifiers.id"));
    }

    @Test
    void toDataCiteQuery_malformedSegment_throws() {
        assertThrows(FilterQuerySyntax.UnsupportedFilterException.class,
                () -> ProductFilters.toDataCiteQuery("contributions.by.family_name"));
    }

    @Test
    void toDataCiteQuery_productType_researchSoftware_orsItsResourceTypes() {
        assertEquals(
                "(types.resourceTypeGeneral:\"ComputationalNotebook\" OR types.resourceTypeGeneral:\"Software\""
                        + " OR types.resourceTypeGeneral:\"Workflow\")",
                ProductFilters.toDataCiteQuery("product_type:research software"));
    }

    @Test
    void toDataCiteQuery_productType_researchData_includesDataset() {
        String query = ProductFilters.toDataCiteQuery("product_type:research data");
        assertEquals(true, query.contains("types.resourceTypeGeneral:\"Dataset\""));
    }

    @Test
    void toDataCiteQuery_productType_unrecognizedValue_noMatch() {
        assertEquals("doi:\"__no_match__\"", ProductFilters.toDataCiteQuery("product_type:bogus"));
    }

    @Test
    void toDataCiteQuery_productType_award_isNotAValidProductType_noMatch() {
        // "award" isn't a value of SKG-IF's product_type enum at all (Awards are grants, routed
        // to /datacite/grants instead - see ProductsResource) - a filter requesting it is well-formed
        // but can never match.
        assertEquals("doi:\"__no_match__\"", ProductFilters.toDataCiteQuery("product_type:award"));
    }

    @Test
    void toDataCiteQuery_identifiersScheme_noOpForDoi() {
        assertNull(ProductFilters.toDataCiteQuery("identifiers.scheme:doi"));
    }

    @Test
    void toDataCiteQuery_identifiersScheme_zeroMatchForOtherScheme() {
        // We only ever expose doi identifiers - a request for any other scheme must force zero
        // results rather than silently no-op (matching everything), which was the pre-fix bug.
        assertEquals("doi:\"__no_match__\"", ProductFilters.toDataCiteQuery("identifiers.scheme:pmid"));
    }

    @Test
    void toDataCiteQuery_byLocalIdentifier_searchesBothRolesWithValueAsIs() {
        String orcidUrl = "https://orcid.org/0000-0002-1008-0687";
        assertEquals("(creators.nameIdentifiers.nameIdentifier:\"" + orcidUrl
                        + "\" OR contributors.nameIdentifiers.nameIdentifier:\"" + orcidUrl + "\")",
                ProductFilters.toDataCiteQuery("contributions.by.local_identifier:" + orcidUrl));
    }

    @Test
    void toDataCiteQuery_byIdentifiersId_addsOrcidPrefixAndSearchesBothRoles() {
        assertEquals(
                "(creators.nameIdentifiers.nameIdentifier:\"https://orcid.org/0000-0002-1008-0687\""
                        + " OR contributors.nameIdentifiers.nameIdentifier:\"https://orcid.org/0000-0002-1008-0687\")",
                ProductFilters.toDataCiteQuery("contributions.by.identifiers.id:0000-0002-1008-0687"));
    }

    @Test
    void toDataCiteQuery_cfContributionsOrcid_producesSameClauseAsIdentifiersId() {
        assertEquals(
                ProductFilters.toDataCiteQuery("contributions.by.identifiers.id:0000-0002-1008-0687"),
                ProductFilters.toDataCiteQuery("cf.contributions_orcid:0000-0002-1008-0687"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersScheme_noOpForOrcid() {
        assertNull(ProductFilters.toDataCiteQuery("contributions.by.identifiers.scheme:orcid"));
    }

    @Test
    void toDataCiteQuery_byIdentifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"", ProductFilters.toDataCiteQuery("contributions.by.identifiers.scheme:isni"));
    }

    @Test
    void toDataCiteQuery_byGivenName_searchesBothRoles() {
        assertEquals("(creators.givenName:\"Jonah\" OR contributors.givenName:\"Jonah\")",
                ProductFilters.toDataCiteQuery("contributions.by.given_name:Jonah"));
    }

    @Test
    void toDataCiteQuery_byName_searchesBothRoles() {
        // Values containing a comma would be misparsed as two filter segments by
        // toDataCiteQuery's top-level splitter (a pre-existing, separate limitation of the
        // comma-separated filter grammar, out of scope here) - e.g. DataCite's actual
        // "Choiniere,Jonah" name string can't be used as-is, so this uses a comma-free value.
        assertEquals("(creators.name:\"Wilkinson\" OR contributors.name:\"Wilkinson\")",
                ProductFilters.toDataCiteQuery("contributions.by.name:Wilkinson"));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsLocalIdentifier_searchesBothRolesWithValueAsIs() {
        String rorUrl = "https://ror.org/02550n020";
        assertEquals("(creators.affiliation.affiliationIdentifier:\"" + rorUrl
                        + "\" OR contributors.affiliation.affiliationIdentifier:\"" + rorUrl + "\")",
                ProductFilters.toDataCiteQuery("contributions.declared_affiliations.local_identifier:" + rorUrl));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsIdentifiersId_addsRorPrefixAndSearchesBothRoles() {
        assertEquals(
                "(creators.affiliation.affiliationIdentifier:\"https://ror.org/02550n020\""
                        + " OR contributors.affiliation.affiliationIdentifier:\"https://ror.org/02550n020\")",
                ProductFilters.toDataCiteQuery("contributions.declared_affiliations.identifiers.id:02550n020"));
    }

    @Test
    void toDataCiteQuery_cfContributionsAffRor_producesSameClauseAsIdentifiersId() {
        assertEquals(
                ProductFilters.toDataCiteQuery("contributions.declared_affiliations.identifiers.id:02550n020"),
                ProductFilters.toDataCiteQuery("cf.contributions_aff_ror:02550n020"));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsIdentifiersScheme_noOpForRor() {
        assertNull(ProductFilters.toDataCiteQuery("contributions.declared_affiliations.identifiers.scheme:ror"));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsIdentifiersScheme_zeroMatchForOtherScheme() {
        assertEquals("doi:\"__no_match__\"",
                ProductFilters.toDataCiteQuery("contributions.declared_affiliations.identifiers.scheme:isni"));
    }

    @Test
    void toDataCiteQuery_declaredAffiliationsName_searchesBothRoles() {
        assertEquals(
                "(creators.affiliation.name:\"European Synchrotron Radiation Facility\""
                        + " OR contributors.affiliation.name:\"European Synchrotron Radiation Facility\")",
                ProductFilters.toDataCiteQuery(
                        "contributions.declared_affiliations.name:European Synchrotron Radiation Facility"));
    }

    @Test
    void toDataCiteQuery_fundingGrantNumber() {
        assertEquals("fundingReferences.awardNumber:\"MX-2738\"",
                ProductFilters.toDataCiteQuery("funding.grant_number:MX-2738"));
    }

    @Test
    void toDataCiteQuery_cfCites_stripsFullDoiUrlPrefix() {
        assertEquals("relatedIdentifiers.relatedIdentifier:\"10.15151/esrf-dc-2493599001\"",
                ProductFilters.toDataCiteQuery("cf.cites:https://doi.org/10.15151/esrf-dc-2493599001"));
    }

    @Test
    void toDataCiteQuery_cfCitedBy_bareDoiPassesThroughUnchanged() {
        assertEquals("relatedIdentifiers.relatedIdentifier:\"10.15151/esrf-dc-2493599001\"",
                ProductFilters.toDataCiteQuery("cf.cited_by:10.15151/esrf-dc-2493599001"));
    }

    @Test
    void toDataCiteQuery_cfCites_producesSameClauseAsCfCitesDoi() {
        assertEquals(
                ProductFilters.toDataCiteQuery("cf.cites_doi:10.15151/esrf-dc-2493599001"),
                ProductFilters.toDataCiteQuery("cf.cites:https://doi.org/10.15151/esrf-dc-2493599001"));
    }
}
