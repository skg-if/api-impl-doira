package org.skgif.doi.datacite.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

class DataCiteToSkgIfMapperTest extends DataCiteToSkgIfMapperTestBase {

    @Test
    void mapsCoreFieldsFromRealEsrfDataciteRecord() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertThat(product.getLocalIdentifier()).isEqualTo("https://doi.org/10.15151/esrf-dc-2493599001");
        assertThat(product.getEntityType()).isEqualTo("product");
        assertThat(product.getProductType()).isEqualTo(Product.ProductTypeEnum.RESEARCH_DATA);
        assertThat(product.getIdentifiers()).hasSize(1);
        assertThat(product.getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(product.getIdentifiers().getFirst().getValue()).isEqualTo("10.15151/esrf-dc-2493599001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitlesAndAbstracts() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertThat(Objects.requireNonNull(titles.get("en")).getFirst()).contains("Aleodon");

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        assertThat(Objects.requireNonNull(abstracts.get("en")).getFirst()).contains("Aleodon");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsMultipleFrenchTitlesUnderTheirOwnLanguageKey() throws IOException {
        Product product = mapFixture("datacite-french-titles-16o9y.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertThat(titles).doesNotContainKey("en");
        assertThat(titles.get("fr")).containsExactly(
                "Doctorants, panels et données d'enquêtes en sciences sociales", "Rencontre annuelle ELIPSS#3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitleWithNoLangKeyUnderEnglishByDefault() throws IOException {
        // Real DataCite records often omit "lang" entirely on a title object (rather than
        // setting it to "en" explicitly) - titleLanguage() must default a missing key the same
        // way it defaults a null/blank value.
        Product product = mapFixture("datacite-title-no-lang-zenodo-19729005.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertThat(titles.keySet()).containsExactly("en");
        assertThat(titles.get("en")).containsExactly("Impact of Assistive Technologies on Reading Skills Among " +
                "Children with Specific Learning Disabilities: Bridging Policy and Practice under NEP 2020");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsAbstractsInDifferentLanguagesUnderTheirOwnKeys() throws IOException {
        Product product = mapFixture("datacite-multilang-abstracts-swp-2026-29.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertThat(titles.keySet()).containsExactly("en");

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        assertThat(Objects.requireNonNull(abstracts.get("en")).getFirst()).contains(
                "large heterogeneity in the cyclicality");
        assertThat(Objects.requireNonNull(abstracts.get("fr")).getFirst()).contains(
                "grande hétérogénéité dans la cyclicité");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsMixedTwoAndThreeLetterLanguageCodesSeparately() throws IOException {
        // DataCite's lang isn't restricted to ISO 639-1 two-letter codes - a real record can mix
        // a 3-letter ISO 639-2 code (e.g. "eng") with a 2-letter one. This is passed through
        // unnormalized rather than guessed at - see SKG_IF_DOI_MAPPING_LIMITATIONS.md.
        Product product = mapFixture("datacite-mixed-lang-titles-eng-fr.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertThat(titles.get("eng")).containsExactly(
                "Hub Location for Waterborne Transportation Networks in the Context of the Physical Internet");
        assertThat(titles.get("fr")).containsExactly(
                "École Nationale Supérieure Mines-Télécom Atlantique Bretagne Pays de la Loire (IMT Atlantique)");
    }

    @Test
    void mapsCreatorsAsAuthorContributionsWithOrcid() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertThat(product.getContributions()).isNotEmpty();
        ProductContribution first = product.getContributions().getFirst();
        assertThat(first.getRole()).isEqualTo(ProductContribution.RoleEnum.AUTHOR);
        assertThat(first.getRank()).isEqualTo(1);
        PersonLite by = (PersonLite) first.getBy();
        assertThat(by.getGivenName()).isEqualTo("Jonah");
        assertThat(by.getFamilyName()).isEqualTo("Choiniere");
        assertThat(by.getIdentifiers().getFirst().getScheme()).isEqualTo("orcid");
        assertThat(by.getIdentifiers().getFirst().getValue()).isEqualTo("0000-0002-1008-0687");
    }

    @Test
    void mapsManifestationAccessRightsAndLicenceFromRightsList() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        assertThat(manifestation.getAccessRights().getStatus())
                .isEqualTo(ProductManifestationAccessRights.StatusEnum.OPEN);
        assertThat(manifestation.getLicence()).contains("creativecommons.org");
        assertThat(manifestation.getVersion()).isEqualTo("1");
        // hosting_data_source comes generically from the DataCite record's own "publisher"
        // field, not a hardcoded organisation - this fixture's publisher happens to be ESRF.
        assertThat(((DataSourceLite) manifestation.getBiblio().getHostingDataSource()).getName())
                .isEqualTo("European Synchrotron Radiation Facility");
    }

    @Test
    void doesNotFabricateRelevantOrganisations() throws IOException {
        // No generic, reliable DataCite field identifies "the organisation behind this
        // product" - affiliation data is already captured under contributions[].declared_
        // affiliations, so relevant_organisations is left unset rather than guessed at.
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertThat(product.getRelevantOrganisations()).isNull();
    }

    @ParameterizedTest
    @MethodSource("resourceTypeGeneralFixtures")
    void mapsResourceTypeGeneralToProductType(String fixtureName,
            Product.ProductTypeEnum expectedProductType) throws IOException {
        Product product = mapFixture(fixtureName);

        assertThat(product.getProductType()).isEqualTo(expectedProductType);
    }

    private static Stream<Arguments> resourceTypeGeneralFixtures() {
        return Stream.of(
                Arguments.of("datacite-esrf-dc-2493599001.json", Product.ProductTypeEnum.RESEARCH_DATA),
                Arguments.of("datacite-zenodo-software-21826016.json", Product.ProductTypeEnum.RESEARCH_SOFTWARE),
                Arguments.of("datacite-zenodo-text-20750072.json", Product.ProductTypeEnum.LITERATURE));
    }

    @Test
    void mapsSubjectsAsTopics() throws IOException {
        Product product = mapFixture("datacite-esrf-dc-2493599001.json");

        assertThat(product.getTopics()).isNotEmpty();
        assertThat(product.getTopics())
                .anyMatch(topic -> Map.of("en", "fossil").equals(((Topic) topic.getTerm()).getLabels()));
    }

    // datacite-esrf-es-2210534378.json: unlike the dataset above, this record's creators/
    // contributors carry plain-string affiliations and it has a funder/grant reference -
    // both empty in the fixture used by the tests above.

    @Test
    void mapsCreatorDeclaredAffiliationFromPlainStringForm() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        ProductContribution first = product.getContributions().getFirst();
        assertThat(((PersonLite) first.getBy()).getFamilyName()).isEqualTo("Pojer");
        assertThat(first.getDeclaredAffiliations()).isNotEmpty();
        Organisation affiliation = (Organisation) first.getDeclaredAffiliations().getFirst();
        assertThat(affiliation.getName()).isEqualTo(
                "EPFL - PTPSP, Protein Production and Structure Core Facilit, EPFL SV PTECH PTPSP, " +
                        "Station 19, Ch-1015 Lausanne, Switzerland");
        // DataCite gave a plain string, not a structured affiliation object, so there's no
        // external identifier to carry over.
        assertThat(affiliation.getIdentifiers()).isNull();
    }

    @Test
    void mapsContributorsWithRolesAndAffiliations() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        // 1 creator + 2 contributors (DataCollector, ProjectManager) + 1 publisher
        final int expectedContributionCount = 4;
        assertThat(product.getContributions()).hasSize(expectedContributionCount);

        ProductContribution dataCollector = product.getContributions().stream()
                .filter(c -> "De Sanctis".equals(((PersonLite) c.getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        // "DataCollector" doesn't map to editor/publisher, so it falls back to author.
        assertThat(dataCollector.getRole()).isEqualTo(ProductContribution.RoleEnum.AUTHOR);
        assertThat(((Organisation) dataCollector.getDeclaredAffiliations().getFirst()).getName())
                .isEqualTo("ESRF, 71 avenue des Martyrs, CS 40220, 38043 Grenoble Cedex 9, France");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsFundingReferenceWithNormalizedRorOnFundingAgency() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        assertThat(product.getFunding()).hasSize(1);
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertThat(grant.getGrantNumber()).isEqualTo("MX-2738");
        assertThat(((Map<String, String>) grant.getTitles()).get("en")).contains("Swiss consortium");
        assertThat(grant.getFundingAgency().getName()).isEqualTo("European Synchrotron Radiation Facility");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getScheme()).isEqualTo("ror");
        // DataCite gives the full https://ror.org/... URL - the mapper normalizes to the bare id,
        // consistent with how ESRF's own ROR is stored elsewhere (relevant_organisations).
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getValue()).isEqualTo("02550n020");
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

        assertThat(product.getFunding()).hasSize(1);
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertThat((Map<String, String>) grant.getTitles()).containsEntry("en", "UWTSD");
        assertThat(grant.getFundingAgency().getName()).isEqualTo("University of Wales Trinity Saint David");
        assertThat(grant.getFundingAgency().getLocalIdentifier())
                .isEqualTo("https://doi.org/10.13039/100010038");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getValue()).isEqualTo("10.13039/100010038");
    }

    // datacite-dataset-multiple-crossref-funder-ids-15047595.json: a real Zenodo dataset (DOI
    // 10.5281/zenodo.15047595) with two funding references, both typed "Crossref Funder ID" -
    // unlike datacite-thesis-crossref-funder-id-4342.json above, funderIdentifier here is a bare
    // DOI ("10.13039/100000001") with no "https://doi.org/" prefix, and there are two distinct
    // funders on the same record rather than one.
    @Test
    @SuppressWarnings("unchecked")
    void mapsMultipleFundingReferencesWithBareDoiCrossrefFunderIds() throws IOException {
        Product product = mapFixture("datacite-dataset-multiple-crossref-funder-ids-15047595.json");

        assertThat(product.getFunding()).hasSize(2);
        GrantLite nsfGrant = (GrantLite) product.getFunding().getFirst();
        assertThat(nsfGrant.getGrantNumber()).isEqualTo("2022070");
        assertThat((Map<String, String>) nsfGrant.getTitles())
                .containsEntry("en", "BII-Implementation: The EMERGE Institute: Identifying EMergent Ecosystem " +
                        "Responses through Genes-to-Ecosystems Integration");
        assertThat(nsfGrant.getFundingAgency().getName()).isEqualTo("U.S. National Science Foundation");
        assertThat(nsfGrant.getFundingAgency().getLocalIdentifier()).isEqualTo("https://doi.org/10.13039/100000001");
        assertThat(nsfGrant.getFundingAgency().getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(nsfGrant.getFundingAgency().getIdentifiers().getFirst().getValue())
                .isEqualTo("10.13039/100000001");

        GrantLite nasaGrant = (GrantLite) product.getFunding().get(1);
        assertThat(nasaGrant.getGrantNumber()).isEqualTo("NNX17AK10G");
        assertThat(nasaGrant.getFundingAgency().getName()).isEqualTo("National Aeronautics and Space Administration");
        assertThat(nasaGrant.getFundingAgency().getLocalIdentifier())
                .isEqualTo("https://doi.org/10.13039/100000104");
        assertThat(nasaGrant.getFundingAgency().getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(nasaGrant.getFundingAgency().getIdentifiers().getFirst().getValue())
                .isEqualTo("10.13039/100000104");
    }

    @Test
    void doesNotExtractDoiFromNonDoiShapedFunderIdentifier() throws IOException {
        // A funderIdentifier that isn't ROR and isn't DOI-shaped (e.g. a bare GRID id) must still
        // fall back to an otf id rather than being mis-parsed.
        var attributes = readFixture("datacite-thesis-crossref-funder-id-4342.json");
        DataCiteFundingReference original = Objects.requireNonNull(attributes.fundingReferences()).getFirst();
        Objects.requireNonNull(attributes.fundingReferences()).set(0, new DataCiteFundingReference(
                original.funderName(), "grid.451003.6", "GRID",
                original.awardNumber(), original.awardTitle(), original.awardUri()));

        Product product = mapper.toProduct(attributes);

        GrantLite funding = (GrantLite) product.getFunding().getFirst();
        assertThat(funding.getFundingAgency().getIdentifiers()).isNull();
        assertThat(funding.getFundingAgency().getLocalIdentifier()).startsWith("otf___");
    }

    @Test
    void fallsBackToOtfForFunderIdentifierTypeNotInDataCitesDocumentedVocabulary() throws IOException {
        // DataCite has added values to its funderIdentifierType controlled vocabulary before and
        // may do so again - an unrecognized type (simulated here as "Wikidata", not part of the
        // documented Crossref Funder ID/GRID/ISNI/ROR/Other list) must be treated like any other
        // non-ROR type rather than making the mapping fail.
        var attributes = readFixture("datacite-thesis-crossref-funder-id-4342.json");
        DataCiteFundingReference original = Objects.requireNonNull(attributes.fundingReferences()).getFirst();
        Objects.requireNonNull(attributes.fundingReferences()).set(0, new DataCiteFundingReference(
                original.funderName(), "Q1234567", "Wikidata",
                original.awardNumber(), original.awardTitle(), original.awardUri()));

        Product product = mapper.toProduct(attributes);

        GrantLite funding = (GrantLite) product.getFunding().getFirst();
        assertThat(funding.getFundingAgency().getIdentifiers()).isNull();
        assertThat(funding.getFundingAgency().getLocalIdentifier()).startsWith("otf___");
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
        assertThat(product.getFunding()).hasSize(expectedFundingCount);
        var epsrc1 = ((GrantLite) product.getFunding().getFirst()).getFundingAgency();
        var epsrc2 = ((GrantLite) product.getFunding().get(1)).getFundingAgency();
        var ukri = ((GrantLite) product.getFunding().get(2)).getFundingAgency();

        assertThat(epsrc1.getIdentifiers()).isNull();
        assertThat(epsrc2.getIdentifiers()).isNull();
        assertThat(ukri.getIdentifiers()).isNull();
        assertThat(epsrc1.getLocalIdentifier()).startsWith("otf___");
        assertThat(epsrc1.getLocalIdentifier()).isEqualTo(epsrc2.getLocalIdentifier());
        assertThat(epsrc1.getLocalIdentifier()).isNotEqualTo(ukri.getLocalIdentifier());
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
        assertThat(product.getContributions()).hasSize(expectedContributionCount);
        ProductContribution editor = product.getContributions().stream()
                .filter(c -> c.getRole() == ProductContribution.RoleEnum.EDITOR)
                .findFirst()
                .orElseThrow();
        PersonLite editorBy = (PersonLite) editor.getBy();
        assertThat(editorBy.getFamilyName()).isEqualTo("Dr. Ramesh V. Bhole");
        assertThat(editorBy.getLocalIdentifier()).startsWith("otf___");
    }
}
