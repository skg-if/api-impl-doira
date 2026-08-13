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

        // 1 creator + 2 contributors (DataCollector, ProjectManager) + 1 publisher
        assertEquals(4, product.getContributions().size());

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

    // datacite-zenodo-editor-21232199.json: a real Zenodo journal-article deposit whose
    // contributor carries contributorType "Editor" - unlike datacite-esrf-es-2210534378.json's
    // contributors ("DataCollector"/"ProjectManager", both of which fall back to author), this
    // is the first fixture to exercise the editor-role mapping. It also has relatedIdentifiers
    // of types the mapper doesn't model ("HasVersion", "IsPartOf") and no "Cites"/"IsCitedBy" -
    // related_products must stay unset rather than surfacing either of them.

    @Test
    void mapsEditorContributorTypeToEditorRole() throws IOException {
        Product product = mapFixture("datacite-zenodo-editor-21232199.json");

        // 1 creator (author) + 1 contributor (editor) + 1 publisher.
        assertEquals(3, product.getContributions().size());
        ProductContribution editor = product.getContributions().stream()
                .filter(c -> c.getRole() == ProductContribution.RoleEnum.EDITOR)
                .findFirst()
                .orElseThrow();
        assertEquals("Dr. Ramesh V. Bhole", editor.getBy().getFamilyName());
        assertTrue(editor.getBy().getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void surfacesIsPartOfButNotUnmodeledHasVersion() throws IOException {
        // relatedIdentifiers has a "HasVersion" DOI (unmodeled - stays out entirely) and an
        // "IsPartOf" ISSN, which now does surface as related_products.is_part_of.
        Product product = mapFixture("datacite-zenodo-editor-21232199.json");

        assertNull(product.getRelatedProducts().getCites());
        assertEquals(1, product.getRelatedProducts().getIsPartOf().size());
        assertEquals("issn", product.getRelatedProducts().getIsPartOf().get(0).getIdentifiers().get(0).getScheme());
        assertEquals("2230-9578", product.getRelatedProducts().getIsPartOf().get(0).getIdentifiers().get(0).getValue());
    }

    // datacite-zenodo-cites-references-21914195.json: a real Zenodo deposit (DOI
    // 10.5281/zenodo.21914195) whose relatedIdentifiers mix DataCite's two citation-like
    // relation types - "Cites" and "References" - alongside "IsPartOf"/"IsDocumentedBy" (now
    // modeled too, into their own fields - see the zenodo.21827103 block below) and
    // "IsDerivedFrom"/"HasVersion" (still unmodeled). Both citation types must land in the
    // same related_products.cites array, since SKG-IF has no separate field for "References".

    @Test
    void mapsBothCitesAndReferencesRelationTypesIntoTheSameCitesArray() throws IOException {
        Product product = mapFixture("datacite-zenodo-cites-references-21914195.json");

        // 2 "Cites" entries + 1 "References" entry = 3 cites; "IsDerivedFrom"/"HasVersion"
        // (still unmodeled) must not add any more, and "IsPartOf"/"IsDocumentedBy" land in
        // their own fields rather than here.
        assertEquals(3, product.getRelatedProducts().getCites().size());
        boolean hasReferencesEntry = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "https://doi.org/10.5281/zenodo.21913675".equals(c.getLocalIdentifier()));
        assertTrue(hasReferencesEntry);
    }

    @Test
    void mapsNonDoiRelatedIdentifierToOtfId() throws IOException {
        // The "Cites" entry with relatedIdentifierType "ISBN" (978-963-281-509-1) has no DOI,
        // so it must fall back to an otf id rather than being dropped or mis-typed as a DOI.
        Product product = mapFixture("datacite-zenodo-cites-references-21914195.json");

        var isbnCite = product.getRelatedProducts().getCites().stream()
                .filter(c -> c.getLocalIdentifier().startsWith("otf___"))
                .findFirst()
                .orElseThrow();
        assertEquals("isbn", isbnCite.getIdentifiers().get(0).getScheme());
        assertEquals("978-963-281-509-1", isbnCite.getIdentifiers().get(0).getValue());
    }

    // datacite-zenodo-relations-21827103.json: a real Zenodo dataset (DOI
    // 10.5281/zenodo.21827103) whose relatedIdentifiers exercise 4 relation types the mapper
    // didn't previously model - "IsSupplementedBy", "IsDocumentedBy", "IsNewVersionOf", and
    // "IsPartOf" - each landing in its own related_products field rather than "cites". It also
    // carries a decoy, "IsSupplementTo" (the inverse of "IsSupplementedBy", easy to confuse by
    // name), and "HasVersion", neither of which the mapper models - both must stay excluded.

    @Test
    void mapsIsSupplementedByIsDocumentedByAndIsNewVersionOf() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        assertEquals(1, related.getIsSupplementedBy().size());
        assertEquals("url", related.getIsSupplementedBy().get(0).getIdentifiers().get(0).getScheme());
        assertEquals("https://github.com/vicgos/MICRO", related.getIsSupplementedBy().get(0).getIdentifiers().get(0).getValue());

        assertEquals(1, related.getIsDocumentedBy().size());
        assertEquals("handle", related.getIsDocumentedBy().get(0).getIdentifiers().get(0).getScheme());

        assertEquals(2, related.getIsNewVersionOf().size());
        boolean hasNsdVersion = related.getIsNewVersionOf().stream()
                .anyMatch(r -> "10.18712/NSD-NSD2457-V3".equals(r.getIdentifiers().get(0).getValue()));
        assertTrue(hasNsdVersion);
    }

    @Test
    void mapsIsPartOfWithFullDoiLocalIdentifier() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        assertEquals(2, related.getIsPartOf().size());
        boolean hasKnownPart = related.getIsPartOf().stream()
                .anyMatch(r -> "https://doi.org/10.5281/zenodo.21827101".equals(r.getLocalIdentifier()));
        assertTrue(hasKnownPart);
    }

    @Test
    void doesNotConfuseIsSupplementToWithIsSupplementedByOrSurfaceHasVersion() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        // "IsSupplementTo" and "IsSupplementedBy" both target the same identifier (10852/56047)
        // in this fixture, so a substring/prefix mixup would silently double it into
        // is_supplemented_by - it must appear there exactly once, from "IsSupplementedBy" only.
        assertEquals(1, related.getIsSupplementedBy().size());
        // Neither "IsSupplementTo" nor "HasVersion" have a related_products field at all -
        // cites/citedBy stay null, not just empty.
        assertNull(related.getCites());
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
