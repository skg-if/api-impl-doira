package org.skgif.doi.datacite.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDate;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.datacite.dto.DataCiteFundingReference;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductsRelatedItem;
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

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
    }

    private DataCiteAttributes readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            DataCiteDoiResponse response = objectMapper.readValue(in, DataCiteDoiResponse.class);
            return response.data().attributes();
        }
    }

    private static DataCiteAttributes withLifecycleDates(DataCiteAttributes attributes, String created,
            String registered, String updated, String published) {
        return new DataCiteAttributes(
                attributes.doi(), attributes.titles(), attributes.creators(), attributes.contributors(),
                attributes.publisher(), attributes.publicationYear(), attributes.subjects(), attributes.dates(),
                created, registered, updated, published,
                attributes.language(), attributes.types(), attributes.rightsList(), attributes.descriptions(),
                attributes.relatedIdentifiers(), attributes.fundingReferences(), attributes.version(),
                attributes.url());
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
        PersonLite by = (PersonLite) first.getBy();
        assertEquals("Jonah", by.getGivenName());
        assertEquals("Choiniere", by.getFamilyName());
        assertEquals("orcid", by.getIdentifiers().get(0).getScheme());
        assertEquals("0000-0002-1008-0687", by.getIdentifiers().get(0).getValue());
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

        ProductContribution first = product.getContributions().get(0);
        assertEquals("Pojer", ((PersonLite) first.getBy()).getFamilyName());
        assertFalse(first.getDeclaredAffiliations().isEmpty());
        Organisation affiliation = (Organisation) first.getDeclaredAffiliations().get(0);
        assertEquals(
                "EPFL - PTPSP, Protein Production and Structure Core Facilit, EPFL SV PTECH PTPSP, "
                        + "Station 19, Ch-1015 Lausanne, Switzerland",
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
                ((Organisation) dataCollector.getDeclaredAffiliations().get(0)).getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsFundingReferenceWithNormalizedRorOnFundingAgency() throws IOException {
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        assertEquals(1, product.getFunding().size());
        GrantLite grant = (GrantLite) product.getFunding().get(0);
        assertEquals("MX-2738", grant.getGrantNumber());
        assertTrue(((Map<String, String>) grant.getTitles()).get("en").contains("Swiss consortium"));
        assertEquals("European Synchrotron Radiation Facility", grant.getFundingAgency().getName());
        assertEquals("ror", grant.getFundingAgency().getIdentifiers().get(0).getScheme());
        // DataCite gives the full https://ror.org/... URL - the mapper normalizes to the bare id,
        // consistent with how ESRF's own ROR is stored elsewhere (relevant_organisations).
        assertEquals("02550n020", grant.getFundingAgency().getIdentifiers().get(0).getValue());
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
        GrantLite grant = (GrantLite) product.getFunding().get(0);
        assertEquals("UWTSD", ((Map<String, String>) grant.getTitles()).get("en"));
        assertEquals("University of Wales Trinity Saint David", grant.getFundingAgency().getName());
        assertEquals("https://doi.org/10.13039/100010038", grant.getFundingAgency().getLocalIdentifier());
        assertEquals("doi", grant.getFundingAgency().getIdentifiers().get(0).getScheme());
        assertEquals("10.13039/100010038", grant.getFundingAgency().getIdentifiers().get(0).getValue());
    }

    @Test
    void doesNotExtractDoiFromNonDoiShapedFunderIdentifier() throws IOException {
        // A funderIdentifier that isn't ROR and isn't DOI-shaped (e.g. a bare GRID id) must still
        // fall back to an otf id rather than being mis-parsed.
        var attributes = readFixture("datacite-thesis-crossref-funder-id-4342.json");
        DataCiteFundingReference original = attributes.fundingReferences().get(0);
        attributes.fundingReferences().set(0, new DataCiteFundingReference(
                original.funderName(), "grid.451003.6", "GRID",
                original.awardNumber(), original.awardTitle(), original.awardUri()));

        Product product = mapper.toProduct(attributes);

        GrantLite funding = (GrantLite) product.getFunding().get(0);
        assertNull(funding.getFundingAgency().getIdentifiers());
        assertTrue(funding.getFundingAgency().getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void fallsBackToTopLevelAttributesWhenDatesArrayIsEmpty() throws IOException {
        // "dates": [] (not absent, but genuinely empty) - unlike every other fixture, which has
        // at least one date. Every DataCite record still carries the system-generated
        // created/registered/updated/published attributes though, and those are the only
        // real-world source for creation/deposit/modified/publication in practice: no fixture's
        // dates[] ever has a Created/Submitted/Updated entry.
        var attributes = readFixture("datacite-thesis-crossref-funder-id-4342.json");

        Product product = mapper.toProduct(attributes);

        var dates = product.getManifestations().get(0).getDates();
        assertEquals(List.of(attributes.created()), dates.getCreation());
        assertEquals(List.of(attributes.registered()), dates.getDeposit());
        assertEquals(List.of(attributes.updated()), dates.getModified());
        assertEquals(List.of(attributes.published()), dates.getPublication());
    }

    @Test
    void doesNotFabricateManifestationDatesWhenNoDateSourceExistsAtAll() throws IOException {
        var attributes = withLifecycleDates(
                readFixture("datacite-thesis-crossref-funder-id-4342.json"), null, null, null, null);

        Product product = mapper.toProduct(attributes);

        assertNull(product.getManifestations().get(0).getDates());
    }

    @Test
    void explicitDatesEntryWinsOverTopLevelAttributeFallback() throws IOException {
        var attributes = readFixture("datacite-esrf-dc-2493599001.json");
        var created = new DataCiteDate("2020-01-01", "Created");
        attributes.dates().add(created);
        assertNotEquals(created.date(), attributes.created());

        Product product = mapper.toProduct(attributes);

        assertEquals(List.of(created.date()), product.getManifestations().get(0).getDates().getCreation());
    }

    @Test
    void dropsUnrecognizedDateTypesLikeCoverage() throws IOException {
        // "Coverage" is a real DataCite 4.7 dateType (the temporal span a resource's *content*
        // covers, not an event in the resource's own lifecycle) with no SKG-IF equivalent - it
        // must be silently dropped, same as "Other", and must not by itself trigger a non-null
        // dates object.
        var attributes = withLifecycleDates(
                readFixture("datacite-thesis-crossref-funder-id-4342.json"), null, null, null, null);
        var coverage = new DataCiteDate("1990/2000", "Coverage");
        attributes.dates().add(coverage);

        Product product = mapper.toProduct(attributes);

        assertNull(product.getManifestations().get(0).getDates());
    }

    @Test
    void mapsAvailableToEmbargoWhenItDiffersFromEveryOtherRecordDate() throws IOException {
        // datacite-esrf-es-2210534378.json is a genuine embargo case: Collected 2025-09-05,
        // Issued 2028, Available 2028-09-06 - none of those coincide, so Available is a real
        // embargo end date.
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        var dates = product.getManifestations().get(0).getDates();
        assertEquals(List.of("2028-09-06"), dates.getEmbargo());
        assertNull(dates.getAccess());
    }

    @Test
    void dropsAvailableWhenItMatchesAnotherRecordDateOnTheSameDay() throws IOException {
        // datacite-dataset-funder-no-identifier-e449e75a.json has a single Available date,
        // 2024-05-07, which is the same day as the top-level created/registered timestamps
        // (2024-05-07T10:07:27.000Z) - that's "published and immediately available," not an
        // embargo, so it must be dropped rather than surfacing as access or embargo.
        Product product = mapFixture("datacite-dataset-funder-no-identifier-e449e75a.json");

        var dates = product.getManifestations().get(0).getDates();
        assertNull(dates.getEmbargo());
        assertNull(dates.getAccess());
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
        var epsrc1 = ((GrantLite) product.getFunding().get(0)).getFundingAgency();
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
    // is the first fixture to exercise the editor-role mapping. It also has relatedIdentifiers
    // of types the mapper doesn't model ("HasVersion", "IsPartOf") and no "Cites"/"IsCitedBy" -
    // related_products must stay unset rather than surfacing either of them.

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

    @Test
    void surfacesIsPartOfButNotUnmodeledHasVersion() throws IOException {
        // relatedIdentifiers has a "HasVersion" DOI (unmodeled - stays out entirely) and an
        // "IsPartOf" ISSN, which now does surface as related_products.is_part_of.
        Product product = mapFixture("datacite-zenodo-editor-21232199.json");

        assertNull(product.getRelatedProducts().getCites());
        assertEquals(1, product.getRelatedProducts().getIsPartOf().size());
        ProductsRelatedItem isPartOf = (ProductsRelatedItem) product.getRelatedProducts().getIsPartOf().get(0);
        assertEquals("issn", isPartOf.getIdentifiers().get(0).getScheme());
        assertEquals("2230-9578", isPartOf.getIdentifiers().get(0).getValue());
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
        final int expectedCitesCount = 3;
        assertEquals(expectedCitesCount, product.getRelatedProducts().getCites().size());
        boolean hasReferencesEntry = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "https://doi.org/10.5281/zenodo.21913675"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
        assertTrue(hasReferencesEntry);
    }

    @Test
    void mapsNonDoiRelatedIdentifierToOtfId() throws IOException {
        // The "Cites" entry with relatedIdentifierType "ISBN" (978-963-281-509-1) has no DOI,
        // so it must fall back to an otf id rather than being dropped or mis-typed as a DOI.
        Product product = mapFixture("datacite-zenodo-cites-references-21914195.json");

        ProductsRelatedItem isbnCite = (ProductsRelatedItem) product.getRelatedProducts().getCites().stream()
                .filter(c -> ((ProductsRelatedItem) c).getLocalIdentifier().startsWith("otf___"))
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
        ProductsRelatedItem isSupplementedBy = (ProductsRelatedItem) related.getIsSupplementedBy().get(0);
        assertEquals("url", isSupplementedBy.getIdentifiers().get(0).getScheme());
        assertEquals("https://github.com/vicgos/MICRO", isSupplementedBy.getIdentifiers().get(0).getValue());

        assertEquals(1, related.getIsDocumentedBy().size());
        assertEquals("handle",
                ((ProductsRelatedItem) related.getIsDocumentedBy().get(0)).getIdentifiers().get(0).getScheme());

        assertEquals(2, related.getIsNewVersionOf().size());
        boolean hasNsdVersion = related.getIsNewVersionOf().stream()
                .anyMatch(r -> "10.18712/NSD-NSD2457-V3"
                        .equals(((ProductsRelatedItem) r).getIdentifiers().get(0).getValue()));
        assertTrue(hasNsdVersion);
    }

    @Test
    void mapsIsPartOfWithFullDoiLocalIdentifier() throws IOException {
        Product product = mapFixture("datacite-zenodo-relations-21827103.json");
        var related = product.getRelatedProducts();

        assertEquals(2, related.getIsPartOf().size());
        boolean hasKnownPart = related.getIsPartOf().stream()
                .anyMatch(r -> "https://doi.org/10.5281/zenodo.21827101"
                        .equals(((ProductsRelatedItem) r).getLocalIdentifier()));
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

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertTrue(titles.get("en").contains("2i2c"));

        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertTrue(abstracts.get("en").contains("open cloud service"));
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
        GrantContribution contribution = (GrantContribution) grant.getContributions().get(0);
        PersonLite by = (PersonLite) contribution.getBy();
        assertEquals("Holdgraf, Chris", by.getName());
        assertEquals("Chris", by.getGivenName());
        assertEquals("Holdgraf", by.getFamilyName());
        assertEquals("orcid", by.getIdentifiers().get(0).getScheme());
        assertEquals("0000-0002-9420-9301", by.getIdentifiers().get(0).getValue());
    }

    @Test
    void toGrant_mapsOrganisationalContributorAsContributionAndBeneficiary() throws IOException {
        Grant grant = mapGrantFixture("datacite-award-r3sy-7371.json");

        GrantContribution contribution = (GrantContribution) grant.getContributions().get(1);
        Organisation by = (Organisation) contribution.getBy();
        assertEquals("Code for Science & Society", by.getName());
        assertEquals("organisation", by.getEntityType());
        assertEquals("ror", by.getIdentifiers().get(0).getScheme());
        assertEquals("01dmavx46", by.getIdentifiers().get(0).getValue());

        assertEquals(1, grant.getBeneficiaries().size());
        Organisation beneficiary = (Organisation) grant.getBeneficiaries().get(0);
        assertEquals("Code for Science & Society", beneficiary.getName());
        assertEquals("01dmavx46", beneficiary.getIdentifiers().get(0).getValue());
    }
}
