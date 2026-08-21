package org.skgif.doi.datacite.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;

class DataCiteToSkgIfMapperGrantTest extends DataCiteToSkgIfMapperTestBase {

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
    }

    // datacite-award-r3sy-7371.json: a real DataCite Award record (resourceTypeGeneral: "Award")
    // - the grant itself, not a product with a funding reference. Creator = the funding body (The
    // Navigation Fund, with ROR); contributors = a personal project leader (ORCID) and a
    // beneficiary organisation (Code for Science & Society, with ROR).

    @Test
    void toGrant_mapsCoreFieldsFromRealAwardRecord() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        assertThat(grant.getLocalIdentifier()).isEqualTo("https://doi.org/10.71707/r3sy-7371");
        assertThat(grant.getEntityType()).hasToString("grant");
        assertThat(grant.getIdentifiers()).hasSize(1);
        assertThat(grant.getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(grant.getIdentifiers().getFirst().getValue()).isEqualTo("10.71707/r3sy-7371");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstracts() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertThat(titles.get("en")).contains("2i2c");

        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertThat(abstracts.get("en")).contains("open cloud service");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_groupsAndJoinsTitlesByLanguage() throws IOException {
        Grant grant = mapGrantFixture("datacite-french-titles-16o9y.json");

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertThat(titles).doesNotContainKey("en")
                .containsEntry("fr",
                        "Doctorants, panels et données d'enquêtes en sciences sociales Rencontre annuelle ELIPSS#3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_groupsAbstractsByLanguage() throws IOException {
        Grant grant = mapGrantFixture("datacite-multilang-abstracts-swp-2026-29.json");

        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertThat(abstracts.get("en")).contains("large heterogeneity in the cyclicality");
        assertThat(abstracts.get("fr")).contains("grande hétérogénéité dans la cyclicité");
    }

    @Test
    void toGrant_derivesFundingAgencyFromRorBearingCreator() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        assertThat(grant.getFundingAgency().getName()).isEqualTo("The Navigation Fund");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getScheme()).isEqualTo("ror");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getValue()).isEqualTo("00mgfk810");
    }

    @Test
    void toGrant_mapsPersonalContributorAsContribution() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        // The only creator was consumed as the funding agency, so contributions holds just the
        // two contributors, in fixture order: the personal project leader first.
        assertThat(grant.getContributions()).hasSize(2);
        GrantContribution contribution = (GrantContribution) grant.getContributions().getFirst();
        PersonLite by = (PersonLite) contribution.getBy();
        assertThat(by.getName()).isEqualTo("Holdgraf, Chris");
        assertThat(by.getGivenName()).isEqualTo("Chris");
        assertThat(by.getFamilyName()).isEqualTo("Holdgraf");
        assertThat(by.getIdentifiers().getFirst().getScheme()).isEqualTo("orcid");
        assertThat(by.getIdentifiers().getFirst().getValue()).isEqualTo("0000-0002-9420-9301");
    }

    @Test
    void toGrant_mapsOrganisationalContributorAsContributionAndBeneficiary() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        GrantContribution contribution = (GrantContribution) grant.getContributions().get(1);
        Organisation by = (Organisation) contribution.getBy();
        assertThat(by.getName()).isEqualTo("Code for Science & Society");
        assertThat(by.getEntityType()).isEqualTo("organisation");
        assertThat(by.getIdentifiers().getFirst().getScheme()).isEqualTo("ror");
        assertThat(by.getIdentifiers().getFirst().getValue()).isEqualTo("01dmavx46");

        assertThat(grant.getBeneficiaries()).hasSize(1);
        Organisation beneficiary = (Organisation) grant.getBeneficiaries().getFirst();
        assertThat(beneficiary.getName()).isEqualTo("Code for Science & Society");
        assertThat(beneficiary.getIdentifiers().getFirst().getValue()).isEqualTo("01dmavx46");
    }
}
