package org.skgif.doi.crossref.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossrefToSkgIfMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Left entirely unstubbed by default: listWorks(...) returns null, so the resolver degrades
    // to Optional.empty() for every ISSN - see CrossrefToSkgIfMapperVenueTest for the
    // resolver-hit/resolver-failure paths that actually stub this.
    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    private final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"),
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty()));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return response.message();
        }
    }

    @Test
    void mapsCoreFieldsFromRealJournalArticle() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertEquals("https://doi.org/10.1038/nature12373", product.getLocalIdentifier());
        assertEquals("product", product.getEntityType());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals(1, product.getIdentifiers().size());
        assertEquals("doi", product.getIdentifiers().getFirst().getScheme());
        assertEquals("10.1038/nature12373", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitles() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertTrue(titles.get("en").getFirst().contains("Nanometre-scale thermometry"));
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
        ProductContribution first = product.getContributions().getFirst();
        assertEquals(ProductContribution.RoleEnum.AUTHOR, first.getRole());
        assertEquals(1, first.getRank());
        PersonLite by = (PersonLite) first.getBy();
        assertEquals("G.", by.getGivenName());
        assertEquals("Kucsko", by.getFamilyName());
        // This fixture's authors carry no ORCID - local_identifier falls back to an otf id.
        assertTrue(by.getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void doesNotFabricateManifestationVersion() throws IOException {
        // Crossref has no software-versioning concept - left unset rather than guessed at.
        Product product = mapFixture("crossref-journal-article.json");

        assertNull(product.getManifestations().getFirst().getVersion());
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
        assertEquals("10.1038/s41467-022-33468-6", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    void mapsAuthorsAsAuthorContributionsWithOrcidWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        ProductContribution first = product.getContributions().getFirst();
        PersonLite by = (PersonLite) first.getBy();
        assertEquals("M. C.", by.getGivenName());
        assertEquals("Rahn", by.getFamilyName());
        assertEquals("https://orcid.org/0000-0001-7403-8288", by.getLocalIdentifier());
        assertEquals("orcid", by.getIdentifiers().getFirst().getScheme());
        assertEquals("0000-0001-7403-8288", by.getIdentifiers().getFirst().getValue());

        // This fixture also has authors without an ORCID (e.g. "A. Hariki") interspersed among
        // the ORCID-bearing ones - those must still fall back to an otf id, not be skipped.
        boolean hasOtfAuthor = product.getContributions().stream()
                .anyMatch(c -> ((PersonLite) c.getBy()).getLocalIdentifier().startsWith("otf___"));
        assertTrue(hasOtfAuthor);
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
        assertEquals("10.17537/icmbb18.42", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    void doesNotFabricateAccessRightsWhenNoLicensePresent() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        assertNull(product.getManifestations().getFirst().getAccessRights());
        assertNull(product.getManifestations().getFirst().getLicence());
    }

    // crossref-journal-article-with-ror-affiliation.json: a real Physical Review B article
    // (DOI 10.1103/physrevb.110.174515) whose author affiliations carry a ROR directly - unlike
    // every other journal-article fixture (name-only affiliations, or none at all). It also has
    // a funder with its own Funder Registry DOI and the same funder repeated with two different
    // award numbers, neither of which the other fixtures exercise.

    @Test
    void mapsDeclaredAffiliationsWithRorWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        ProductContribution first = product.getContributions().getFirst();
        assertEquals("di Mauro", ((PersonLite) first.getBy()).getFamilyName());
        // This fixture's authors carry no ORCID at all - only their affiliations carry a ROR -
        // so the person's own local_identifier still falls back to an otf id.
        assertTrue(((PersonLite) first.getBy()).getLocalIdentifier().startsWith("otf___"));

        Organisation affiliation = (Organisation) first.getDeclaredAffiliations().getFirst();
        assertEquals(2, first.getDeclaredAffiliations().size());
        assertEquals("https://ror.org/00tmb7y09", affiliation.getLocalIdentifier());
        assertEquals("ror", affiliation.getIdentifiers().getFirst().getScheme());
        assertEquals("00tmb7y09", affiliation.getIdentifiers().getFirst().getValue());
        assertEquals("Laboratoire de Chimie Théorique", affiliation.getName());
    }

    @Test
    void mapsFundingWithFunderDoiAndMultipleAwardsForSameFunder() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        // 4 funder entries in the fixture, two of which are the same "Horizon 2020" funder with
        // two different award numbers - each award must surface as its own funding entry.
        final int expectedFundingCount = 4;
        assertEquals(expectedFundingCount, product.getFunding().size());
        List<GrantLite> horizon2020Entries = product.getFunding().stream()
                .map(f -> (GrantLite) f)
                .filter(f -> "Horizon 2020".equals(f.getFundingAgency().getName()))
                .toList();
        assertEquals(2, horizon2020Entries.size());
        assertTrue(horizon2020Entries.stream().anyMatch(f -> "810367".equals(f.getGrantNumber())));
        assertTrue(horizon2020Entries.stream().anyMatch(f -> "802533".equals(f.getGrantNumber())));

        // Unlike crossref-journal-article-with-funder.json's funder (no Funder Registry DOI at
        // all), this fixture's funders carry one directly on the top-level funder[] entry.
        var fundingAgency = horizon2020Entries.getFirst().getFundingAgency();
        assertEquals("doi", fundingAgency.getIdentifiers().getFirst().getScheme());
        assertEquals("10.13039/501100007601", fundingAgency.getIdentifiers().getFirst().getValue());
    }

    // crossref-book-chapter.json: a real book chapter (DOI 10.1007/978-3-319-66787-4_9,
    // type: "book-chapter") - unlike every other fixture, its container-title[] has two
    // entries (the LNCS series title, then the actual proceedings/book title), and it's the
    // first fixture to exercise the mapper's new publisher-as-contribution behaviour.

    @Test
    void mapsCoreFieldsFromRealBookChapter() throws IOException {
        Product product = mapFixture("crossref-book-chapter.json");

        assertEquals("https://doi.org/10.1007/978-3-319-66787-4_9", product.getLocalIdentifier());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals("10.1007/978-3-319-66787-4_9", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    void mapsPublisherAsTrailingPublisherRoleContribution() throws IOException {
        Product product = mapFixture("crossref-book-chapter.json");

        // 6 authors, so the publisher contribution the mapper now appends must be the 7th,
        // ranked after every author.
        final int authorCount = 6;
        List<ProductContribution> contributions = product.getContributions();
        assertEquals(authorCount + 1, contributions.size());
        ProductContribution publisherContribution = contributions.get(authorCount);
        assertEquals(ProductContribution.RoleEnum.PUBLISHER, publisherContribution.getRole());
        assertEquals(authorCount + 1, publisherContribution.getRank());
        Organisation publisherBy = (Organisation) publisherContribution.getBy();
        assertEquals("Springer International Publishing", publisherBy.getName());
        assertTrue(publisherBy.getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void mapsAbstractStrippingJatsXmlTags() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        String abstractText = abstracts.get("en").getFirst();
        assertTrue(abstractText.contains("Lissajous scanner"));
        assertFalse(abstractText.contains("<jats:p>"));
    }

    @Test
    void mapsAccessRightsAsOpenFromCreativeCommonsLicence() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, manifestation.getAccessRights().getStatus());
        assertTrue(manifestation.getLicence().contains("creativecommons.org"));
    }

    @Test
    void mapsFunderWithoutAwardNumberOrFunderDoi() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        assertEquals(1, product.getFunding().size());
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertEquals("Federal Ministries of Transport, Innovation and Technology", grant.getFundingAgency().getName());
        // No Funder Registry DOI on this fixture's funder - local_identifier falls back to otf.
        assertNull(grant.getFundingAgency().getIdentifiers());
        assertTrue(grant.getFundingAgency().getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void mapsDeclaredAffiliationsWithNameOnlyOtfFallbackWhenNoRor() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        var affiliations = product.getContributions().getFirst().getDeclaredAffiliations();
        assertEquals(1, affiliations.size());
        Organisation affiliation = (Organisation) affiliations.getFirst();
        // Unlike crossref-journal-article-with-ror-affiliation.json, this affiliation carries no
        // ROR at all - only a bare name - so it must fall back to an otf id instead.
        assertEquals("Carinthian Tech Research AG, Europastrasse 12, 9524 Villach, Austria",
                affiliation.getName());
        assertNull(affiliation.getIdentifiers());
        assertTrue(affiliation.getLocalIdentifier().startsWith("otf___"));
    }
}
