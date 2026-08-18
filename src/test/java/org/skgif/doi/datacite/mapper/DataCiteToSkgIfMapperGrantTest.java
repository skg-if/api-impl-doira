package org.skgif.doi.datacite.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataCiteToSkgIfMapperGrantTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataCiteToSkgIfMapper mapper = new DataCiteToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
    }

    private DataCiteAttributes readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            return response.data().attributes();
        }
    }

    // datacite-award-r3sy-7371.json: a real DataCite Award record (resourceTypeGeneral: "Award")
    // - the grant itself, not a product with a funding reference. Creator = the funding body (The
    // Navigation Fund, with ROR); contributors = a personal project leader (ORCID) and a
    // beneficiary organisation (Code for Science & Society, with ROR).

    @Test
    void toGrant_mapsCoreFieldsFromRealAwardRecord() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        assertEquals("https://doi.org/10.71707/r3sy-7371", grant.getLocalIdentifier());
        assertEquals("grant", grant.getEntityType().toString());
        assertEquals(1, grant.getIdentifiers().size());
        assertEquals("doi", grant.getIdentifiers().getFirst().getScheme());
        assertEquals("10.71707/r3sy-7371", grant.getIdentifiers().getFirst().getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstracts() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertTrue(titles.get("en").contains("2i2c"));

        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertTrue(abstracts.get("en").contains("open cloud service"));
    }

    @Test
    void toGrant_derivesFundingAgencyFromRorBearingCreator() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        assertEquals("The Navigation Fund", grant.getFundingAgency().getName());
        assertEquals("ror", grant.getFundingAgency().getIdentifiers().getFirst().getScheme());
        assertEquals("00mgfk810", grant.getFundingAgency().getIdentifiers().getFirst().getValue());
    }

    @Test
    void toGrant_mapsPersonalContributorAsContribution() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        // The only creator was consumed as the funding agency, so contributions holds just the
        // two contributors, in fixture order: the personal project leader first.
        assertEquals(2, grant.getContributions().size());
        GrantContribution contribution = (GrantContribution) grant.getContributions().getFirst();
        PersonLite by = (PersonLite) contribution.getBy();
        assertEquals("Holdgraf, Chris", by.getName());
        assertEquals("Chris", by.getGivenName());
        assertEquals("Holdgraf", by.getFamilyName());
        assertEquals("orcid", by.getIdentifiers().getFirst().getScheme());
        assertEquals("0000-0002-9420-9301", by.getIdentifiers().getFirst().getValue());
    }

    @Test
    void toGrant_mapsOrganisationalContributorAsContributionAndBeneficiary() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        GrantContribution contribution = (GrantContribution) grant.getContributions().get(1);
        Organisation by = (Organisation) contribution.getBy();
        assertEquals("Code for Science & Society", by.getName());
        assertEquals("organisation", by.getEntityType());
        assertEquals("ror", by.getIdentifiers().getFirst().getScheme());
        assertEquals("01dmavx46", by.getIdentifiers().getFirst().getValue());

        assertEquals(1, grant.getBeneficiaries().size());
        Organisation beneficiary = (Organisation) grant.getBeneficiaries().getFirst();
        assertEquals("Code for Science & Society", beneficiary.getName());
        assertEquals("01dmavx46", beneficiary.getIdentifiers().getFirst().getValue());
    }
}
