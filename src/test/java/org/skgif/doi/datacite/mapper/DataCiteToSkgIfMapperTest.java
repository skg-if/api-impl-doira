package org.skgif.doi.datacite.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataCiteToSkgIfMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataCiteToSkgIfMapper mapper = new DataCiteToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
    }

    private org.skgif.doi.datacite.dto.DataCiteAttributes readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            return response.data.attributes;
        }
    }

    @Test
    void mapsCoreFieldsFromRealEsrfDataciteRecord() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertEquals("https://doi.org/10.15151/esrf-dc-2493599001", product.getLocalIdentifier());
        assertEquals("product", product.getEntityType());
        assertEquals(Product.ProductTypeEnum.RESEARCH_DATA, product.getProductType());
        assertEquals(1, product.getIdentifiers().size());
        assertEquals("doi", product.getIdentifiers().get(0).getScheme());
        assertEquals("10.15151/esrf-dc-2493599001", product.getIdentifiers().get(0).getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitlesAndAbstracts() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertTrue(titles.get("en").get(0).contains("Aleodon"));

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        assertTrue(abstracts.get("en").get(0).contains("Aleodon"));
    }

    @Test
    void mapsCreatorsAsAuthorContributionsWithOrcid() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertFalse(product.getContributions().isEmpty());
        ProductContribution first = product.getContributions().get(0);
        assertEquals(ProductContribution.RoleEnum.AUTHOR, first.getRole());
        assertEquals(1, first.getRank());
        assertEquals("Jonah", first.getBy().getGivenName());
        assertEquals("Choiniere", first.getBy().getFamilyName());
        assertEquals("orcid", first.getBy().getIdentifiers().get(0).getScheme());
        assertEquals("0000-0002-1008-0687", first.getBy().getIdentifiers().get(0).getValue());
    }

    @Test
    void mapsManifestationAccessRightsAndLicenceFromRightsList() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        ProductManifestation manifestation = product.getManifestations().get(0);
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, manifestation.getAccessRights().getStatus());
        assertTrue(manifestation.getLicence().contains("creativecommons.org"));
        assertEquals("1", manifestation.getVersion());
        // hosting_data_source comes generically from the DataCite record's own "publisher"
        // field, not a hardcoded organisation - this fixture's publisher happens to be ESRF.
        assertEquals("European Synchrotron Radiation Facility",
                manifestation.getBiblio().getHostingDataSource().getName());
    }

    @Test
    void doesNotFabricateRelevantOrganisations() throws IOException {
        // No generic, reliable DataCite field identifies "the organisation behind this
        // product" - affiliation data is already captured under contributions[].declared_
        // affiliations, so relevant_organisations is left unset rather than guessed at.
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertNull(product.getRelevantOrganisations());
    }

    @Test
    void mapsResourceTypeGeneralToProductType() throws IOException {
        assertEquals(Product.ProductTypeEnum.RESEARCH_DATA,
                mapFixture("datacite-esrf-dc-2493599001.json").getProductType());
        assertEquals(Product.ProductTypeEnum.RESEARCH_SOFTWARE,
                mapFixture("datacite-zenodo-software-21826016.json").getProductType());
        assertEquals(Product.ProductTypeEnum.LITERATURE,
                mapFixture("datacite-zenodo-text-20750072.json").getProductType());
    }

    @Test
    void mapsSubjectsAsTopics() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertFalse(product.getTopics().isEmpty());
        boolean hasFossilTopic = product.getTopics().stream()
                .anyMatch(topic -> Map.of("en", "fossil").equals(topic.getTerm().getLabels()));
        assertTrue(hasFossilTopic);
    }

    // datacite-esrf-es-2210534378.json: unlike the dataset above, this record's creators/
    // contributors carry plain-string affiliations and it has a funder/grant reference -
    // both empty in the fixture used by the tests above.

    @Test
    void mapsCreatorDeclaredAffiliationFromPlainStringForm() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        ProductContribution first = product.getContributions().get(0);
        assertEquals("Pojer", first.getBy().getFamilyName());
        assertFalse(first.getDeclaredAffiliations().isEmpty());
        assertEquals("EPFL - PTPSP, Protein Production and Structure Core Facilit, EPFL SV PTECH PTPSP, Station 19, Ch-1015 Lausanne, Switzerland",
                first.getDeclaredAffiliations().get(0).getName());
        // DataCite gave a plain string, not a structured affiliation object, so there's no
        // external identifier to carry over.
        assertTrue(first.getDeclaredAffiliations().get(0).getIdentifiers().isEmpty());
    }

    @Test
    void mapsContributorsWithRolesAndAffiliations() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        // 1 creator + 2 contributors (DataCollector, ProjectManager)
        assertEquals(3, product.getContributions().size());

        ProductContribution dataCollector = product.getContributions().stream()
                .filter(c -> "De Sanctis".equals(c.getBy().getFamilyName()))
                .findFirst()
                .orElseThrow();
        // "DataCollector" doesn't map to editor/publisher, so it falls back to author.
        assertEquals(ProductContribution.RoleEnum.AUTHOR, dataCollector.getRole());
        assertEquals("ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France",
                dataCollector.getDeclaredAffiliations().get(0).getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsFundingReferenceWithNormalizedRorOnFundingAgency() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        assertEquals(1, product.getFunding().size());
        var grant = product.getFunding().get(0);
        assertEquals("MX-2738", grant.getGrantNumber());
        assertTrue(((Map<String, List<String>>) grant.getTitles()).get("en").get(0).contains("Swiss consortium"));
        assertEquals("European Synchrotron Radiation Facility", grant.getFundingAgency().getName());
        assertEquals("ror", grant.getFundingAgency().getIdentifiers().get(0).getScheme());
        // DataCite gives the full https://ror.org/... URL - the mapper normalizes to the bare id,
        // consistent with how ESRF's own ROR is stored elsewhere (relevant_organisations).
        assertEquals("02550n020", grant.getFundingAgency().getIdentifiers().get(0).getValue());
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
        assertEquals("doi", grant.getIdentifiers().get(0).getScheme());
        assertEquals("10.71707/r3sy-7371", grant.getIdentifiers().get(0).getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstracts() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) grant.getTitles();
        assertTrue(titles.get("en").get(0).contains("2i2c"));

        Map<String, List<String>> abstracts = (Map<String, List<String>>) grant.getAbstracts();
        assertTrue(abstracts.get("en").get(0).contains("open cloud service"));
    }

    @Test
    void toGrant_derivesFundingAgencyFromRorBearingCreator() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        assertEquals("The Navigation Fund", grant.getFundingAgency().getName());
        assertEquals("ror", grant.getFundingAgency().getIdentifiers().get(0).getScheme());
        assertEquals("00mgfk810", grant.getFundingAgency().getIdentifiers().get(0).getValue());
    }

    @Test
    void toGrant_mapsPersonalContributorAsContribution() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        // The only creator was consumed as the funding agency, so contributions holds just the
        // two contributors, in fixture order: the personal project leader first.
        assertEquals(2, grant.getContributions().size());
        var contribution = grant.getContributions().get(0);
        assertEquals("Holdgraf, Chris", contribution.getBy().getName());
        assertEquals("Chris", contribution.getBy().getGivenName());
        assertEquals("Holdgraf", contribution.getBy().getFamilyName());
        assertEquals("orcid", contribution.getBy().getIdentifiers().get(0).getScheme());
        assertEquals("0000-0002-9420-9301", contribution.getBy().getIdentifiers().get(0).getValue());
    }

    @Test
    void toGrant_mapsOrganisationalContributorAsContributionAndBeneficiary() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        var contribution = grant.getContributions().get(1);
        assertEquals("Code for Science & Society", contribution.getBy().getName());
        assertEquals("organisation", contribution.getBy().getEntityType().toString());
        assertEquals("ror", contribution.getBy().getIdentifiers().get(0).getScheme());
        assertEquals("01dmavx46", contribution.getBy().getIdentifiers().get(0).getValue());

        assertEquals(1, grant.getBeneficiaries().size());
        assertEquals("Code for Science & Society", grant.getBeneficiaries().get(0).getName());
        assertEquals("01dmavx46", grant.getBeneficiaries().get(0).getIdentifiers().get(0).getValue());
    }
}
