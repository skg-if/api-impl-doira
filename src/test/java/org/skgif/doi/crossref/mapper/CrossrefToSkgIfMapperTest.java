package org.skgif.doi.crossref.mapper;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;

final class CrossrefToSkgIfMapperTest extends CrossrefToSkgIfMapperTestBase {

    @Test
    void mapsCoreFieldsFromRealJournalArticle() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertThat(product.getLocalIdentifier()).isEqualTo("https://doi.org/10.1038/nature12373");
        assertThat(product.getEntityType()).isEqualTo("product");
        assertThat(product.getProductType()).isEqualTo(Product.ProductTypeEnum.LITERATURE);
        assertThat(product.getIdentifiers()).hasSize(1);
        assertThat(product.getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(product.getIdentifiers().getFirst().getValue()).isEqualTo("10.1038/nature12373");
    }

    @SuppressWarnings("unchecked")
    @Test
    void mapsTitles() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertThat(requireNonNull(titles.get("en")).getFirst()).contains("Nanometre-scale thermometry");
    }

    @MethodSource("resourceTypeToProductTypeFixtures")
    @ParameterizedTest
    void mapsResourceTypeToProductType(String fixtureName,
            Product.ProductTypeEnum expectedProductType) throws IOException {
        Product.ProductTypeEnum actualProductType = mapFixture(fixtureName).getProductType();

        assertThat(actualProductType).isEqualTo(expectedProductType);
    }

    private static Stream<Arguments> resourceTypeToProductTypeFixtures() {
        return Stream.of(
                arguments("crossref-journal-article.json", Product.ProductTypeEnum.LITERATURE),
                arguments("crossref-dataset.json", Product.ProductTypeEnum.RESEARCH_DATA));
    }

    @MethodSource("coreFieldsFromRealRecordsFixtures")
    @ParameterizedTest(name = "{0}")
    void mapsCoreFieldsFromReal(String label, String fixtureName, String expectedDoi) throws IOException {
        Product product = mapFixture(fixtureName);

        assertThat(product.getLocalIdentifier()).isEqualTo("https://doi.org/%s", expectedDoi);
        assertThat(product.getProductType()).isEqualTo(Product.ProductTypeEnum.LITERATURE);
        assertThat(product.getIdentifiers().getFirst().getValue()).isEqualTo(expectedDoi);
    }

    private static Stream<Arguments> coreFieldsFromRealRecordsFixtures() {
        return Stream.of(
                arguments("Article with ORCID Authors", "crossref-journal-article-with-orcid.json",
                        "10.1038/s41467-022-33468-6"),
                arguments("Book Chapter", "crossref-book-chapter.json", "10.1007/978-3-319-66787-4_9"),
                arguments("Proceedings Article", "crossref-proceedings-article.json", "10.17537/icmbb18.42"));
    }

    @Test
    void mapsAuthorsAsAuthorContributionsWithoutOrcidWhenAbsent() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertThat(product.getContributions()).isNotEmpty();
        ProductContribution first = product.getContributions().getFirst();
        assertThat(first.getRole()).isEqualTo(ProductContribution.RoleEnum.AUTHOR);
        assertThat(first.getRank()).isEqualTo(1);
        PersonLite by = (PersonLite) first.getBy();
        assertThat(by.getGivenName()).isEqualTo("G.");
        assertThat(by.getFamilyName()).isEqualTo("Kucsko");
        // This fixture's authors carry no ORCID - local_identifier falls back to an otf id.
        assertThat(by.getLocalIdentifier()).startsWith("otf___");
    }

    @Test
    void doesNotFabricateManifestationVersion() throws IOException {
        // Crossref has no software-versioning concept - left unset rather than guessed at.
        Product product = mapFixture("crossref-journal-article.json");

        assertThat(product.getManifestations().getFirst().getVersion()).isNull();
    }


    @Test
    void mapsAuthorsAsAuthorContributionsWithOrcidWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        ProductContribution first = product.getContributions().getFirst();
        PersonLite by = (PersonLite) first.getBy();
        assertThat(by.getGivenName()).isEqualTo("M. C.");
        assertThat(by.getFamilyName()).isEqualTo("Rahn");
        assertThat(by.getLocalIdentifier()).isEqualTo("https://orcid.org/0000-0001-7403-8288");
        assertThat(by.getIdentifiers().getFirst().getScheme()).isEqualTo("orcid");
        assertThat(by.getIdentifiers().getFirst().getValue()).isEqualTo("0000-0001-7403-8288");

        // This fixture also has authors without an ORCID (e.g. "A. Hariki") interspersed among
        // the ORCID-bearing ones - those must still fall back to an otf id, not be skipped.
        assertThat(product.getContributions())
                .anyMatch(c -> ((PersonLite) c.getBy()).getLocalIdentifier().startsWith("otf___"));
    }


    @Test
    void doesNotFabricateAccessRightsWhenNoLicensePresent() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        assertThat(product.getManifestations().getFirst().getAccessRights()).isNull();
        assertThat(product.getManifestations().getFirst().getLicence()).isNull();
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
        assertThat(((PersonLite) first.getBy()).getFamilyName()).isEqualTo("di Mauro");
        // This fixture's authors carry no ORCID at all - only their affiliations carry a ROR -
        // so the person's own local_identifier still falls back to an otf id.
        assertThat(((PersonLite) first.getBy()).getLocalIdentifier()).startsWith("otf___");

        Organisation affiliation = (Organisation) first.getDeclaredAffiliations().getFirst();
        assertThat(first.getDeclaredAffiliations()).hasSize(2);
        assertThat(affiliation.getLocalIdentifier()).isEqualTo("https://ror.org/00tmb7y09");
        assertThat(affiliation.getIdentifiers().getFirst().getScheme()).isEqualTo("ror");
        assertThat(affiliation.getIdentifiers().getFirst().getValue()).isEqualTo("00tmb7y09");
        assertThat(affiliation.getName()).isEqualTo("Laboratoire de Chimie Théorique");
    }

    @Test
    void mapsFundingWithFunderDoiAndMultipleAwardsForSameFunder() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        // 4 funder entries in the fixture, two of which are the same "Horizon 2020" funder with
        // two different award numbers - each award must surface as its own funding entry.
        final int expectedFundingCount = 4;
        assertThat(product.getFunding()).hasSize(expectedFundingCount);
        List<GrantLite> horizon2020Entries = product.getFunding().stream()
                .map(GrantLite.class::cast)
                .filter(f -> "Horizon 2020".equals(f.getFundingAgency().getName()))
                .toList();
        assertThat(horizon2020Entries).hasSize(2)
                .anyMatch(f -> "810367".equals(f.getGrantNumber()))
                .anyMatch(f -> "802533".equals(f.getGrantNumber()));

        // Unlike crossref-journal-article-with-funder.json's funder (no Funder Registry DOI at
        // all), this fixture's funders carry one directly on the top-level funder[] entry.
        var fundingAgency = horizon2020Entries.getFirst().getFundingAgency();
        assertThat(fundingAgency.getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(fundingAgency.getIdentifiers().getFirst().getValue()).isEqualTo("10.13039/501100007601");
    }


    @Test
    void mapsPublisherAsTrailingPublisherRoleContribution() throws IOException {
        Product product = mapFixture("crossref-book-chapter.json");

        // 6 authors, so the publisher contribution the mapper now appends must be the 7th,
        // ranked after every author.
        final int authorCount = 6;
        List<ProductContribution> contributions = product.getContributions();
        assertThat(contributions).hasSize(authorCount + 1);
        ProductContribution publisherContribution = contributions.get(authorCount);
        assertThat(publisherContribution.getRole()).isEqualTo(ProductContribution.RoleEnum.PUBLISHER);
        assertThat(publisherContribution.getRank()).isEqualTo(authorCount + 1);
        Organisation publisherBy = (Organisation) publisherContribution.getBy();
        assertThat(publisherBy.getName()).isEqualTo("Springer International Publishing");
        assertThat(publisherBy.getLocalIdentifier()).startsWith("otf___");
    }

    @Test
    void mapsAbstractStrippingJatsXmlTags() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        String abstractText = requireNonNull(abstracts.get("en")).getFirst();
        assertThat(abstractText).contains("Lissajous scanner").doesNotContain("<jats:p>");
    }

    @Test
    void mapsAccessRightsAsOpenFromCreativeCommonsLicence() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        assertThat(manifestation.getAccessRights().getStatus())
                .isEqualTo(ProductManifestationAccessRights.StatusEnum.OPEN);
        assertThat(manifestation.getLicence()).contains("creativecommons.org");
    }

    @Test
    void mapsFunderWithoutAwardNumberOrFunderDoi() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        assertThat(product.getFunding()).hasSize(1);
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertThat(grant.getFundingAgency().getName())
                .isEqualTo("Federal Ministries of Transport, Innovation and Technology");
        // No Funder Registry DOI on this fixture's funder - local_identifier falls back to otf.
        assertThat(grant.getFundingAgency().getIdentifiers()).isNull();
        assertThat(grant.getFundingAgency().getLocalIdentifier()).startsWith("otf___");
    }

    @Test
    void mapsDeclaredAffiliationsWithNameOnlyOtfFallbackWhenNoRor() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        var affiliations = product.getContributions().getFirst().getDeclaredAffiliations();
        assertThat(affiliations).hasSize(1);
        Organisation affiliation = (Organisation) affiliations.getFirst();
        // Unlike crossref-journal-article-with-ror-affiliation.json, this affiliation carries no
        // ROR at all - only a bare name - so it must fall back to an otf id instead.
        assertThat(affiliation.getName())
                .isEqualTo("Carinthian Tech Research AG, Europastrasse 12, 9524 Villach, Austria");
        assertThat(affiliation.getIdentifiers()).isNull();
        assertThat(affiliation.getLocalIdentifier()).startsWith("otf___");
    }
}
