package org.skgif.doi.medra.mapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.medra.xml.MedraOnixXmlParser;
import org.skgif.doi.util.LocalIdentifiers;

final class MedraToSkgIfMapperTest {

    /** The mapper under test. */
    private final MedraToSkgIfMapper mapper = new MedraToSkgIfMapper(new LocalIdentifiers("https://doi.org/"));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(parseFixture(resourceName));
    }

    private MedraWork parseFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            String xml = new String(in.readAllBytes(), UTF_8);
            return MedraOnixXmlParser.parse(xml)
                    .orElseThrow(() -> new AssertionError("Fixture did not parse: " + resourceName));
        }
    }

    @Test
    void namesBeforeKeyAndKeyNamesTakePrecedenceWhenAllFourNameFieldsArePresent() throws IOException {
        Product product = mapFixture("medra-mixed-name-shapes.xml");

        assertThat(product.getLocalIdentifier()).isEqualTo("https://doi.org/10.19276/plinius.2019.01004");
        List<ProductContribution> contributions = product.getContributions();
        assertThat(contributions).hasSize(1);
        PersonLite person = (PersonLite) contributions.getFirst().getBy();
        assertThat(person.getName()).isEqualTo("Daniela D'Alessio");
        assertThat(person.getGivenName()).isEqualTo("Daniela");
        assertThat(person.getFamilyName()).isEqualTo("D'Alessio");
        assertThat(contributions.getFirst().getRole()).isEqualTo(ProductContribution.RoleEnum.AUTHOR);
    }

    @Test
    void barePersonNameWithNoInvertedFormLeavesGivenAndFamilyUnset() throws IOException {
        Product product = mapFixture("medra-version-message-book-series.xml");

        List<ProductContribution> contributions = product.getContributions();
        assertThat(contributions).hasSize(2);
        PersonLite first = (PersonLite) contributions.getFirst().getBy();
        assertThat(first.getName()).isEqualTo("Cotte M.");
        assertThat(first.getGivenName()).isNull();
        assertThat(first.getFamilyName()).isNull();
        assertThat(product.getAbstracts()).isNotNull();
    }

    @Test
    void personNameInvertedAloneIsSplitAndRecomposedInNaturalOrder() throws IOException {
        Product product = mapFixture("medra-personname-inverted-only.xml");

        List<ProductContribution> contributions = product.getContributions();
        assertThat(contributions).hasSize(1);
        PersonLite person = (PersonLite) contributions.getFirst().getBy();
        assertThat(person.getName()).isEqualTo("Giovanna Fragneto");
        assertThat(person.getGivenName()).isEqualTo("Giovanna");
        assertThat(person.getFamilyName()).isEqualTo("Fragneto");
    }

    @Test
    void mapsEmptyContributionsToEmptyListWhenNoContributorExists() throws IOException {
        Product product = mapFixture("medra-no-contributors.xml");

        assertThat(product.getContributions()).isEmpty();
    }

    @Test
    void groupsTitlesByLanguageInDocumentOrder() throws IOException {
        Product product = mapFixture("medra-multilang-titles.xml");

        Object titles = product.getTitles();
        @SuppressWarnings("unchecked") var titlesMap = (java.util.Map<String, List<String>>) titles;
        assertThat(titlesMap).containsEntry("en",
                List.of("Transverse THz dynamics of phospholipid membranes: A neutron scattering study"));
    }

    @Test
    void mapsPublicationDateOfVaryingPrecisionToIsoForm() throws IOException {
        // "medra-mixed-name-shapes.xml"'s PublicationDate is year-only ("2019").
        Product yearOnly = mapFixture("medra-mixed-name-shapes.xml");
        assertThat(yearOnly.getManifestations().getFirst().getDates().getPublication()).containsExactly("2019");

        // "medra-no-contributors.xml"'s PublicationDate is a full 8-digit date ("20210813").
        // "medra-version-message-book-series.xml" has no PublicationDate at all (only
        // JournalIssueDate, which is deliberately not mapped - see MedraManifestationMapper#isoDate).
        Product fullDate = mapFixture("medra-no-contributors.xml");
        assertThat(fullDate.getManifestations().getFirst().getDates().getPublication()).containsExactly("2021-08-13");
    }

    @Test
    void splitsMultiWordFamilyNameFromPersonNameInvertedOnAFirstCommaOnly() throws IOException {
        Product product = mapFixture("medra-multiple-product-identifiers.xml");

        List<ProductContribution> contributions = product.getContributions();
        assertThat(contributions).hasSize(1);
        PersonLite person = (PersonLite) contributions.getFirst().getBy();
        assertThat(person.getName()).isEqualTo("Maria Helena Camara Bastos");
        assertThat(person.getGivenName()).isEqualTo("Maria Helena");
        assertThat(person.getFamilyName()).isEqualTo("Camara Bastos");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertThat(venue.getIdentifiers().stream()
                .map(org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers::getValue).toList())
                .containsExactly("19711131");

        // No PublicationDate on this ContentItem at all - dates must be omitted, not fabricated
        // from the JournalIssueDate.
        assertThat(product.getManifestations().getFirst().getDates()).isNull();
    }

    @Test
    void mapsProductTypeAsLiteratureAndVenueFromJournalTitleAndIssn() throws IOException {
        Product product = mapFixture("medra-mixed-name-shapes.xml");

        assertThat(product.getProductType()).isEqualTo(Product.ProductTypeEnum.LITERATURE);
        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertThat(venue.getName()).isEqualTo("Plinius");
    }

    @Test
    void mapsManifestationTypeLabelFromTheRecordsOwnWrapperElementName() throws IOException {
        // WorkRegistrationMessage variant - wrapped in DOISerialArticleWork.
        Product workVariant = mapFixture("medra-mixed-name-shapes.xml");
        assertThat(manifestationTypeLabel(workVariant)).isEqualTo("DOISerialArticleWork");

        // VersionRegistrationMessage variant - wrapped in DOISerialArticleVersion, not Work.
        Product versionVariant = mapFixture("medra-version-message-book-series.xml");
        assertThat(manifestationTypeLabel(versionVariant)).isEqualTo("DOISerialArticleVersion");
    }

    @SuppressWarnings("unchecked")
    private @Nullable String manifestationTypeLabel(Product product) {
        var labels = (java.util.Map<String, String>) product.getManifestations().getFirst().getType().getLabels();
        return labels.get("en");
    }
}
