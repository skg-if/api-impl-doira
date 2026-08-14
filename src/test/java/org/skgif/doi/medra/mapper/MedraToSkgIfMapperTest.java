package org.skgif.doi.medra.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.medra.xml.MedraOnixXmlParser;
import org.skgif.doi.util.LocalIdentifiers;

class MedraToSkgIfMapperTest {

    private final MedraToSkgIfMapper mapper = new MedraToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(parseFixture(resourceName));
    }

    private MedraWork parseFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MedraOnixXmlParser.parse(xml)
                    .orElseThrow(() -> new AssertionError("Fixture did not parse: " + resourceName));
        }
    }

    @Test
    void namesBeforeKeyAndKeyNamesTakePrecedenceWhenAllFourNameFieldsArePresent() throws IOException {
        Product product = mapFixture("medra-mixed-name-shapes.xml");

        assertEquals("https://doi.org/10.19276/plinius.2019.01004", product.getLocalIdentifier());
        List<ProductContribution> contributions = product.getContributions();
        assertEquals(1, contributions.size());
        PersonLite person = (PersonLite) contributions.get(0).getBy();
        assertEquals("Daniela D'Alessio", person.getName());
        assertEquals("Daniela", person.getGivenName());
        assertEquals("D'Alessio", person.getFamilyName());
        assertEquals(ProductContribution.RoleEnum.AUTHOR, contributions.get(0).getRole());
    }

    @Test
    void barePersonNameWithNoInvertedFormLeavesGivenAndFamilyUnset() throws IOException {
        Product product = mapFixture("medra-version-message-book-series.xml");

        List<ProductContribution> contributions = product.getContributions();
        assertEquals(2, contributions.size());
        PersonLite first = (PersonLite) contributions.get(0).getBy();
        assertEquals("Cotte M.", first.getName());
        assertNull(first.getGivenName());
        assertNull(first.getFamilyName());
        assertTrue(product.getAbstracts() != null);
    }

    @Test
    void personNameInvertedAloneIsSplitAndRecomposedInNaturalOrder() throws IOException {
        Product product = mapFixture("medra-personname-inverted-only.xml");

        List<ProductContribution> contributions = product.getContributions();
        assertEquals(1, contributions.size());
        PersonLite person = (PersonLite) contributions.get(0).getBy();
        assertEquals("Giovanna Fragneto", person.getName());
        assertEquals("Giovanna", person.getGivenName());
        assertEquals("Fragneto", person.getFamilyName());
    }

    @Test
    void mapsEmptyContributionsToNullWhenNoContributorExists() throws IOException {
        Product product = mapFixture("medra-no-contributors.xml");

        assertNull(product.getContributions());
    }

    @Test
    void groupsTitlesByLanguageInDocumentOrder() throws IOException {
        Product product = mapFixture("medra-multilang-titles.xml");

        Object titles = product.getTitles();
        @SuppressWarnings("unchecked")
        var titlesMap = (java.util.Map<String, List<String>>) titles;
        assertEquals(List.of("Transverse THz dynamics of phospholipid membranes: A neutron scattering study"),
                titlesMap.get("en"));
    }

    @Test
    void mapsPublicationDateOfVaryingPrecisionToIsoForm() throws IOException {
        // "medra-mixed-name-shapes.xml"'s PublicationDate is year-only ("2019").
        Product yearOnly = mapFixture("medra-mixed-name-shapes.xml");
        assertEquals(List.of("2019"), yearOnly.getManifestations().get(0).getDates().getPublication());

        // "medra-no-contributors.xml"'s PublicationDate is a full 8-digit date ("20210813").
        // "medra-version-message-book-series.xml" has no PublicationDate at all (only
        // JournalIssueDate, which is deliberately not mapped - see MedraToSkgIfMapper#isoDate).
        Product fullDate = mapFixture("medra-no-contributors.xml");
        assertEquals(List.of("2021-08-13"), fullDate.getManifestations().get(0).getDates().getPublication());
    }

    @Test
    void splitsMultiWordFamilyNameFromPersonNameInvertedOnAFirstCommaOnly() throws IOException {
        Product product = mapFixture("medra-multiple-product-identifiers.xml");

        List<ProductContribution> contributions = product.getContributions();
        assertEquals(1, contributions.size());
        PersonLite person = (PersonLite) contributions.get(0).getBy();
        assertEquals("Maria Helena Camara Bastos", person.getName());
        assertEquals("Maria Helena", person.getGivenName());
        assertEquals("Camara Bastos", person.getFamilyName());

        VenueLite venue = (VenueLite) product.getManifestations().get(0).getBiblio().getIn();
        assertEquals(List.of("19711131"), venue.getIdentifiers().stream()
                .map(org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers::getValue).toList());

        // No PublicationDate on this ContentItem at all - dates must be omitted, not fabricated
        // from the JournalIssueDate.
        assertNull(product.getManifestations().get(0).getDates());
    }

    @Test
    void mapsProductTypeAsLiteratureAndVenueFromJournalTitleAndIssn() throws IOException {
        Product product = mapFixture("medra-mixed-name-shapes.xml");

        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        VenueLite venue = (VenueLite) product.getManifestations().get(0).getBiblio().getIn();
        assertEquals("Plinius", venue.getName());
    }

    @Test
    void mapsManifestationTypeLabelFromTheRecordsOwnWrapperElementName() throws IOException {
        // WorkRegistrationMessage variant - wrapped in DOISerialArticleWork.
        Product workVariant = mapFixture("medra-mixed-name-shapes.xml");
        assertEquals("DOISerialArticleWork", manifestationTypeLabel(workVariant));

        // VersionRegistrationMessage variant - wrapped in DOISerialArticleVersion, not Work.
        Product versionVariant = mapFixture("medra-version-message-book-series.xml");
        assertEquals("DOISerialArticleVersion", manifestationTypeLabel(versionVariant));
    }

    @SuppressWarnings("unchecked")
    private String manifestationTypeLabel(Product product) {
        var labels = (java.util.Map<String, String>) product.getManifestations().get(0).getType().getLabels();
        return labels.get("en");
    }
}
