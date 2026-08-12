package org.skgif.doi.crossref.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
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

class CrossrefToSkgIfMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
    }

    private CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return response.message;
        }
    }

    @Test
    void mapsCoreFieldsFromRealJournalArticle() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertEquals("https://doi.org/10.1038/nature12373", product.getLocalIdentifier());
        assertEquals("product", product.getEntityType());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals(1, product.getIdentifiers().size());
        assertEquals("doi", product.getIdentifiers().get(0).getScheme());
        assertEquals("10.1038/nature12373", product.getIdentifiers().get(0).getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitles() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertTrue(titles.get("en").get(0).contains("Nanometre-scale thermometry"));
    }

    @Test
    void mapsResourceTypeToProductType() throws IOException {
        assertEquals(Product.ProductTypeEnum.LITERATURE, mapFixture("crossref-journal-article.json").getProductType());
        assertEquals(Product.ProductTypeEnum.RESEARCH_DATA, mapFixture("crossref-dataset.json").getProductType());
    }

    @Test
    void mapsAuthorsAsAuthorContributionsWithoutOrcidWhenAbsent() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertFalse(product.getContributions().isEmpty());
        ProductContribution first = product.getContributions().get(0);
        assertEquals(ProductContribution.RoleEnum.AUTHOR, first.getRole());
        assertEquals(1, first.getRank());
        assertEquals("G.", first.getBy().getGivenName());
        assertEquals("Kucsko", first.getBy().getFamilyName());
        // This fixture's authors carry no ORCID - local_identifier falls back to an otf id.
        assertTrue(first.getBy().getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void mapsBiblioFromContainerTitleIssnVolumeIssuePages() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        ProductManifestation manifestation = product.getManifestations().get(0);
        assertEquals("Nature", manifestation.getBiblio().getIn().getName());
        assertEquals("issn", manifestation.getBiblio().getIn().getIdentifiers().get(0).getScheme());
        assertEquals("0028-0836", manifestation.getBiblio().getIn().getIdentifiers().get(0).getValue());
        assertEquals("500", manifestation.getBiblio().getVolume());
        assertEquals("7460", manifestation.getBiblio().getIssue());
        assertEquals("54", manifestation.getBiblio().getPages().getFirst());
        assertEquals("58", manifestation.getBiblio().getPages().getLast());
        assertEquals("Springer Science and Business Media LLC", manifestation.getBiblio().getHostingDataSource().getName());
    }

    @Test
    void mapsRelatedProductsFromReferenceListDois() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertFalse(product.getRelatedProducts().getCites().isEmpty());
        boolean hasKnownReference = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "https://doi.org/10.1038/nature03509".equals(c.getLocalIdentifier()));
        assertTrue(hasKnownReference);
    }

    @Test
    void doesNotFabricateManifestationVersion() throws IOException {
        // Crossref has no software-versioning concept - left unset rather than guessed at.
        Product product = mapFixture("crossref-journal-article.json");

        assertNull(product.getManifestations().get(0).getVersion());
    }

    @Test
    void mapsAbstractStrippingJatsXmlTags() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        String abstractText = abstracts.get("en").get(0);
        assertTrue(abstractText.contains("Lissajous scanner"));
        assertFalse(abstractText.contains("<jats:p>"));
    }

    @Test
    void mapsAccessRightsAsOpenFromCreativeCommonsLicence() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        ProductManifestation manifestation = product.getManifestations().get(0);
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, manifestation.getAccessRights().getStatus());
        assertTrue(manifestation.getLicence().contains("creativecommons.org"));
    }

    @Test
    void mapsFunderWithoutAwardNumberOrFunderDoi() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        assertEquals(1, product.getFunding().size());
        var grant = product.getFunding().get(0);
        assertEquals("Federal Ministries of Transport, Innovation and Technology", grant.getFundingAgency().getName());
        // No Funder Registry DOI on this fixture's funder - local_identifier falls back to otf.
        assertNull(grant.getFundingAgency().getIdentifiers());
        assertTrue(grant.getFundingAgency().getLocalIdentifier().startsWith("otf___"));
    }

    // crossref-grant.json: a real Crossref grant record (type: "grant") - a Wellcome Trust
    // award, with explicit funder/amount/duration fields Crossref gives directly (unlike
    // DataCite Awards, which need the ROR-bearing-creator heuristic).

    @Test
    void toGrant_mapsCoreFieldsFromRealGrantRecord() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertEquals("https://doi.org/10.35802/218300", grant.getLocalIdentifier());
        assertEquals("grant", grant.getEntityType().toString());
        assertEquals(1, grant.getIdentifiers().size());
        assertEquals("doi", grant.getIdentifiers().get(0).getScheme());
        assertEquals("10.35802/218300", grant.getIdentifiers().get(0).getValue());
        assertEquals("218300", grant.getGrantNumber());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstractsFromProject() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) grant.getTitles();
        assertTrue(titles.get("en").get(0).contains("Biocontainment Level 2"));

        Map<String, List<String>> abstracts = (Map<String, List<String>>) grant.getAbstracts();
        assertEquals(2, abstracts.get("en").size());
    }

    @Test
    void toGrant_mapsFundingAgencyAmountCurrencyAndDurationExplicitly() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertEquals("Wellcome Trust", grant.getFundingAgency().getName());
        assertEquals("doi", grant.getFundingAgency().getIdentifiers().get(0).getScheme());
        assertEquals("10.13039/100010269", grant.getFundingAgency().getIdentifiers().get(0).getValue());
        assertEquals(479450, grant.getFundedAmount());
        assertEquals("GBP", grant.getCurrency());
        assertEquals("2019-11-01", grant.getDuration().getStart());
        assertEquals("2024-10-31", grant.getDuration().getEnd());
    }

    @Test
    void toGrant_mapsLeadAndCoInvestigatorsAsContributionsWithRoles() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertEquals(9, grant.getContributions().size());
        var lead = grant.getContributions().stream()
                .filter(c -> "Halim".equals(c.getBy().getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(org.skgif.doi.generated.model.GrantAllOfContributions.RolesEnum.LEAD_APPLICANT), lead.getRoles());
        assertEquals("orcid", lead.getBy().getIdentifiers().get(0).getScheme());
        assertEquals("0000-0001-9773-0023", lead.getBy().getIdentifiers().get(0).getValue());

        var coApplicant = grant.getContributions().stream()
                .filter(c -> "Caldas".equals(c.getBy().getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(org.skgif.doi.generated.model.GrantAllOfContributions.RolesEnum.CO_APPLICANT), coApplicant.getRoles());
        assertEquals("ror", coApplicant.getDeclaredAffiliations().get(0).getIdentifiers().get(0).getScheme());
        assertEquals("013meh722", coApplicant.getDeclaredAffiliations().get(0).getIdentifiers().get(0).getValue());
    }

    @Test
    void toGrant_dedupesBeneficiariesFromInvestigatorAffiliations() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        // 3 distinct institutions appear across 9 investigators (University of Cambridge
        // repeats 6 times) - beneficiaries must be deduped by name.
        assertEquals(3, grant.getBeneficiaries().size());
        assertTrue(grant.getBeneficiaries().stream().anyMatch(b -> "University of Cambridge".equals(b.getName())));
    }

    @Test
    void toGrant_doesNotFabricateAcronym() throws IOException {
        // No Crossref grant-schema field found for this - left unset rather than guessed at.
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertNull(grant.getAcronym());
    }
}
