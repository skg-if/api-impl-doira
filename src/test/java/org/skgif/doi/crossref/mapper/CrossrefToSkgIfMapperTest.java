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
    void mapsReferenceWithoutDoiToOtfIdInsteadOfExcludingIt() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        // This fixture's reference[] has 30 entries, one of which (key BFnature12373_CR17)
        // carries no DOI - it must still surface as a cites entry, via an otf id, not be dropped.
        assertEquals(30, product.getRelatedProducts().getCites().size());
        boolean hasOtfReference = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> c.getLocalIdentifier().startsWith("otf___"));
        assertTrue(hasOtfReference);
    }

    @Test
    void doesNotFabricateManifestationVersion() throws IOException {
        // Crossref has no software-versioning concept - left unset rather than guessed at.
        Product product = mapFixture("crossref-journal-article.json");

        assertNull(product.getManifestations().get(0).getVersion());
    }

    // crossref-journal-article-with-orcid.json: a real Nature Communications article (DOI
    // 10.1038/s41467-022-33468-6) - unlike crossref-journal-article.json, its authors carry
    // ORCIDs and it has no "page" field (only "article-number"), so it exercises paths the
    // other journal-article fixtures don't.

    @Test
    void mapsCoreFieldsFromRealArticleWithOrcidAuthors() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        assertEquals("https://doi.org/10.1038/s41467-022-33468-6", product.getLocalIdentifier());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals("10.1038/s41467-022-33468-6", product.getIdentifiers().get(0).getValue());
    }

    @Test
    void mapsAuthorsAsAuthorContributionsWithOrcidWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        ProductContribution first = product.getContributions().get(0);
        assertEquals("M. C.", first.getBy().getGivenName());
        assertEquals("Rahn", first.getBy().getFamilyName());
        assertEquals("https://orcid.org/0000-0001-7403-8288", first.getBy().getLocalIdentifier());
        assertEquals("orcid", first.getBy().getIdentifiers().get(0).getScheme());
        assertEquals("0000-0001-7403-8288", first.getBy().getIdentifiers().get(0).getValue());

        // This fixture also has authors without an ORCID (e.g. "A. Hariki") interspersed among
        // the ORCID-bearing ones - those must still fall back to an otf id, not be skipped.
        boolean hasOtfAuthor = product.getContributions().stream()
                .anyMatch(c -> c.getBy().getLocalIdentifier().startsWith("otf___"));
        assertTrue(hasOtfAuthor);
    }

    @Test
    void doesNotFabricatePagesWhenOnlyArticleNumberIsPresent() throws IOException {
        // This fixture has no "page" field (Nature Communications uses "article-number"
        // instead, which the mapper has no SKG-IF field to carry) - pages must stay unset
        // rather than being guessed at, even though issue/volume/venue are all present.
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        ProductManifestation manifestation = product.getManifestations().get(0);
        assertNull(manifestation.getBiblio().getPages());
        assertEquals("13", manifestation.getBiblio().getVolume());
        assertEquals("1", manifestation.getBiblio().getIssue());
        assertEquals("Nature Communications", manifestation.getBiblio().getIn().getName());
    }

    // crossref-proceedings-article.json: a real conference-proceedings record (DOI
    // 10.17537/icmbb18.42, type: "proceedings-article") - no license, no funder, and one
    // reference (key "ref3") with neither a DOI nor unstructured text, exercising the
    // otf-id-from-key fallback that the other journal-article fixtures never hit.

    @Test
    void mapsCoreFieldsFromRealProceedingsArticle() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        assertEquals("https://doi.org/10.17537/icmbb18.42", product.getLocalIdentifier());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals("10.17537/icmbb18.42", product.getIdentifiers().get(0).getValue());
    }

    @Test
    void mapsReferenceWithNeitherDoiNorUnstructuredToOtfIdFromKey() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        // reference key "ref3" carries no DOI and no unstructured text - the otf id must
        // fall back to the reference key itself rather than being dropped.
        assertEquals(5, product.getRelatedProducts().getCites().size());
        assertTrue(product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> c.getLocalIdentifier().equals("otf___10-17537-icmbb18-42___ref3")));
    }

    @Test
    void doesNotFabricateAccessRightsWhenNoLicensePresent() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        assertNull(product.getManifestations().get(0).getAccessRights());
        assertNull(product.getManifestations().get(0).getLicence());
    }

    // crossref-journal-article-with-ror-affiliation.json: a real Physical Review B article
    // (DOI 10.1103/physrevb.110.174515) whose author affiliations carry a ROR directly - unlike
    // every other journal-article fixture (name-only affiliations, or none at all). It also has
    // a funder with its own Funder Registry DOI and the same funder repeated with two different
    // award numbers, neither of which the other fixtures exercise.

    @Test
    void mapsDeclaredAffiliationsWithRorWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        ProductContribution first = product.getContributions().get(0);
        assertEquals("di Mauro", first.getBy().getFamilyName());
        // This fixture's authors carry no ORCID at all - only their affiliations carry a ROR -
        // so the person's own local_identifier still falls back to an otf id.
        assertTrue(first.getBy().getLocalIdentifier().startsWith("otf___"));

        var affiliations = first.getDeclaredAffiliations();
        assertEquals(2, affiliations.size());
        assertEquals("https://ror.org/00tmb7y09", affiliations.get(0).getLocalIdentifier());
        assertEquals("ror", affiliations.get(0).getIdentifiers().get(0).getScheme());
        assertEquals("00tmb7y09", affiliations.get(0).getIdentifiers().get(0).getValue());
        assertEquals("Laboratoire de Chimie Théorique", affiliations.get(0).getName());
    }

    @Test
    void mapsFundingWithFunderDoiAndMultipleAwardsForSameFunder() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        // 4 funder entries in the fixture, two of which are the same "Horizon 2020" funder with
        // two different award numbers - each award must surface as its own funding entry.
        assertEquals(4, product.getFunding().size());
        var horizon2020Entries = product.getFunding().stream()
                .filter(f -> "Horizon 2020".equals(f.getFundingAgency().getName()))
                .toList();
        assertEquals(2, horizon2020Entries.size());
        assertTrue(horizon2020Entries.stream().anyMatch(f -> "810367".equals(f.getGrantNumber())));
        assertTrue(horizon2020Entries.stream().anyMatch(f -> "802533".equals(f.getGrantNumber())));

        // Unlike crossref-journal-article-with-funder.json's funder (no Funder Registry DOI at
        // all), this fixture's funders carry one directly on the top-level funder[] entry.
        var fundingAgency = horizon2020Entries.get(0).getFundingAgency();
        assertEquals("doi", fundingAgency.getIdentifiers().get(0).getScheme());
        assertEquals("10.13039/501100007601", fundingAgency.getIdentifiers().get(0).getValue());
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
