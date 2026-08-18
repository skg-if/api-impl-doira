package org.skgif.doi.datacite.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.datacite.dto.DataCiteFundingReference;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.Topic;
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

    private DataCiteAttributes readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            return response.data().attributes();
        }
    }

    @Test
    void mapsCoreFieldsFromRealEsrfDataciteRecord() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertEquals("https://doi.org/10.15151/esrf-dc-2493599001", product.getLocalIdentifier());
        assertEquals("product", product.getEntityType());
        assertEquals(Product.ProductTypeEnum.RESEARCH_DATA, product.getProductType());
        assertEquals(1, product.getIdentifiers().size());
        assertEquals("doi", product.getIdentifiers().getFirst().getScheme());
        assertEquals("10.15151/esrf-dc-2493599001", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitlesAndAbstracts() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertTrue(titles.get("en").getFirst().contains("Aleodon"));

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        assertTrue(abstracts.get("en").getFirst().contains("Aleodon"));
    }

    @Test
    void mapsCreatorsAsAuthorContributionsWithOrcid() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertFalse(product.getContributions().isEmpty());
        ProductContribution first = product.getContributions().getFirst();
        assertEquals(ProductContribution.RoleEnum.AUTHOR, first.getRole());
        assertEquals(1, first.getRank());
        PersonLite by = (PersonLite) first.getBy();
        assertEquals("Jonah", by.getGivenName());
        assertEquals("Choiniere", by.getFamilyName());
        assertEquals("orcid", by.getIdentifiers().getFirst().getScheme());
        assertEquals("0000-0002-1008-0687", by.getIdentifiers().getFirst().getValue());
    }

    @Test
    void mapsManifestationAccessRightsAndLicenceFromRightsList() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, manifestation.getAccessRights().getStatus());
        assertTrue(manifestation.getLicence().contains("creativecommons.org"));
        assertEquals("1", manifestation.getVersion());
        // hosting_data_source comes generically from the DataCite record's own "publisher"
        // field, not a hardcoded organisation - this fixture's publisher happens to be ESRF.
        assertEquals("European Synchrotron Radiation Facility",
                ((DataSourceLite) manifestation.getBiblio().getHostingDataSource()).getName());
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
                .anyMatch(topic -> Map.of("en", "fossil").equals(((Topic) topic.getTerm()).getLabels()));
        assertTrue(hasFossilTopic);
    }

    // datacite-esrf-es-2210534378.json: unlike the dataset above, this record's creators/
    // contributors carry plain-string affiliations and it has a funder/grant reference -
    // both empty in the fixture used by the tests above.

    @Test
    void mapsCreatorDeclaredAffiliationFromPlainStringForm() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        ProductContribution first = product.getContributions().getFirst();
        assertEquals("Pojer", ((PersonLite) first.getBy()).getFamilyName());
        assertFalse(first.getDeclaredAffiliations().isEmpty());
        Organisation affiliation = (Organisation) first.getDeclaredAffiliations().getFirst();
        assertEquals(
                "EPFL - PTPSP, Protein Production and Structure Core Facilit, EPFL SV PTECH PTPSP, " +
                        "Station 19, Ch-1015 Lausanne, Switzerland",
                affiliation.getName());
        // DataCite gave a plain string, not a structured affiliation object, so there's no
        // external identifier to carry over.
        assertNull(affiliation.getIdentifiers());
    }

    @Test
    void mapsContributorsWithRolesAndAffiliations() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        // 1 creator + 2 contributors (DataCollector, ProjectManager) + 1 publisher
        final int expectedContributionCount = 4;
        assertEquals(expectedContributionCount, product.getContributions().size());

        ProductContribution dataCollector = product.getContributions().stream()
                .filter(c -> "De Sanctis".equals(((PersonLite) c.getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        // "DataCollector" doesn't map to editor/publisher, so it falls back to author.
        assertEquals(ProductContribution.RoleEnum.AUTHOR, dataCollector.getRole());
        assertEquals("ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France",
                ((Organisation) dataCollector.getDeclaredAffiliations().getFirst()).getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsFundingReferenceWithNormalizedRorOnFundingAgency() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        assertEquals(1, product.getFunding().size());
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertEquals("MX-2738", grant.getGrantNumber());
        assertTrue(((Map<String, String>) grant.getTitles()).get("en").contains("Swiss consortium"));
        assertEquals("European Synchrotron Radiation Facility", grant.getFundingAgency().getName());
        assertEquals("ror", grant.getFundingAgency().getIdentifiers().getFirst().getScheme());
        // DataCite gives the full https://ror.org/... URL - the mapper normalizes to the bare id,
        // consistent with how ESRF's own ROR is stored elsewhere (relevant_organisations).
        assertEquals("02550n020", grant.getFundingAgency().getIdentifiers().getFirst().getValue());
    }

    // datacite-thesis-crossref-funder-id-4342.json: a real UWTSD repository thesis (DOI
    // 10.82227/repository.uwtsd.ac.uk.00004342) whose sole funding reference identifies the
    // funder via funderIdentifierType "Crossref Funder ID" rather than "ROR" - unlike
    // datacite-esrf-es-2210534378.json above. funderIdentifier is a real, DOI-shaped
    // https://doi.org/10.13039/100010038 URL - the mapper detects that directly (regardless of
    // what funderIdentifierType claims) rather than requiring the type to literally say "ROR".

    @Test
    @SuppressWarnings("unchecked")
    void mapsFundingAgencyToDoiWhenFunderIdentifierIsDoiShapedRegardlessOfType() throws IOException {
        Product product = mapFixture("datacite-thesis-crossref-funder-id-4342.json");

        assertEquals(1, product.getFunding().size());
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertEquals("UWTSD", ((Map<String, String>) grant.getTitles()).get("en"));
        assertEquals("University of Wales Trinity Saint David", grant.getFundingAgency().getName());
        assertEquals("https://doi.org/10.13039/100010038", grant.getFundingAgency().getLocalIdentifier());
        assertEquals("doi", grant.getFundingAgency().getIdentifiers().getFirst().getScheme());
        assertEquals("10.13039/100010038", grant.getFundingAgency().getIdentifiers().getFirst().getValue());
    }

    @Test
    void doesNotExtractDoiFromNonDoiShapedFunderIdentifier() throws IOException {
        // A funderIdentifier that isn't ROR and isn't DOI-shaped (e.g. a bare GRID id) must still
        // fall back to an otf id rather than being mis-parsed.
        var attributes = readFixture("datacite-thesis-crossref-funder-id-4342.json");
        DataCiteFundingReference original = attributes.fundingReferences().getFirst();
        attributes.fundingReferences().set(0, new DataCiteFundingReference(
                original.funderName(), "grid.451003.6", "GRID",
                original.awardNumber(), original.awardTitle(), original.awardUri()));

        Product product = mapper.toProduct(attributes);

        GrantLite funding = (GrantLite) product.getFunding().getFirst();
        assertNull(funding.getFundingAgency().getIdentifiers());
        assertTrue(funding.getFundingAgency().getLocalIdentifier().startsWith("otf___"));
    }

    // datacite-dataset-funder-no-identifier-e449e75a.json: a real University of St Andrews
    // dataset (DOI 10.17630/e449e75a-1ee9-4490-909c-e3913052cce1) whose 3 funding references
    // (EPSRC x2, UK Research and Innovation x1) carry funderName/awardNumber/awardTitle but no
    // funderIdentifier/funderIdentifierType field at all - unlike
    // datacite-thesis-crossref-funder-id-4342.json above, this isn't a mutated non-DOI-shaped
    // type, it's the identifier being entirely absent from the source data. Also pins that the
    // same funder name reused across two grants resolves to the same otf funding_agency id.
    @Test
    void mapsFundingAgencyToOtfWhenFunderIdentifierIsEntirelyAbsent() throws IOException {
        Product product = mapFixture("datacite-dataset-funder-no-identifier-e449e75a.json");

        final int expectedFundingCount = 3;
        assertEquals(expectedFundingCount, product.getFunding().size());
        var epsrc1 = ((GrantLite) product.getFunding().getFirst()).getFundingAgency();
        var epsrc2 = ((GrantLite) product.getFunding().get(1)).getFundingAgency();
        var ukri = ((GrantLite) product.getFunding().get(2)).getFundingAgency();

        assertNull(epsrc1.getIdentifiers());
        assertNull(epsrc2.getIdentifiers());
        assertNull(ukri.getIdentifiers());
        assertTrue(epsrc1.getLocalIdentifier().startsWith("otf___"));
        assertEquals(epsrc1.getLocalIdentifier(), epsrc2.getLocalIdentifier());
        assertNotEquals(epsrc1.getLocalIdentifier(), ukri.getLocalIdentifier());
    }

    // datacite-zenodo-editor-21232199.json: a real Zenodo journal-article deposit whose
    // contributor carries contributorType "Editor" - unlike datacite-esrf-es-2210534378.json's
    // contributors ("DataCollector"/"ProjectManager", both of which fall back to author), this
    // is the first fixture to exercise the editor-role mapping.

    @Test
    void mapsEditorContributorTypeToEditorRole() throws IOException {
        Product product = mapFixture("datacite-zenodo-editor-21232199.json");

        // 1 creator (author) + 1 contributor (editor) + 1 publisher.
        final int expectedContributionCount = 3;
        assertEquals(expectedContributionCount, product.getContributions().size());
        ProductContribution editor = product.getContributions().stream()
                .filter(c -> c.getRole() == ProductContribution.RoleEnum.EDITOR)
                .findFirst()
                .orElseThrow();
        PersonLite editorBy = (PersonLite) editor.getBy();
        assertEquals("Dr. Ramesh V. Bhole", editorBy.getFamilyName());
        assertTrue(editorBy.getLocalIdentifier().startsWith("otf___"));
    }
}
