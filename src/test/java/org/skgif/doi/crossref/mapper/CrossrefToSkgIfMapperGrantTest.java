package org.skgif.doi.crossref.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossrefToSkgIfMapperGrantTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    private final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"),
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty()));

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
    }

    private CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return response.message();
        }
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
        assertEquals("doi", grant.getIdentifiers().getFirst().getScheme());
        assertEquals("10.35802/218300", grant.getIdentifiers().getFirst().getValue());
        assertEquals("218300", grant.getGrantNumber());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstractsFromProject() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertTrue(titles.get("en").contains("Biocontainment Level 2"));

        // Two project-description entries in the fixture, concatenated into one string since
        // Grant.abstracts (unlike Product.abstracts) is a plain string per language.
        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertTrue(abstracts.get("en").contains("Provision of cutting edge cell-sorter"));
        assertTrue(abstracts.get("en").contains("Our bodies are composed of trillions"));
    }

    @Test
    void toGrant_mapsFundingAgencyAmountCurrencyAndDurationExplicitly() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertEquals("Wellcome Trust", grant.getFundingAgency().getName());
        assertEquals("doi", grant.getFundingAgency().getIdentifiers().getFirst().getScheme());
        assertEquals("10.13039/100010269", grant.getFundingAgency().getIdentifiers().getFirst().getValue());
        final int expectedFundedAmount = 479450;
        assertEquals(expectedFundedAmount, grant.getFundedAmount());
        assertEquals("GBP", grant.getCurrency());
        assertEquals("2019-11-01", grant.getDuration().getStart());
        assertEquals("2024-10-31", grant.getDuration().getEnd());
    }

    @Test
    void toGrant_mapsLeadAndCoInvestigatorsAsContributionsWithRoles() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        final int expectedInvestigatorCount = 9;
        assertEquals(expectedInvestigatorCount, grant.getContributions().size());
        GrantContribution lead = (GrantContribution) grant.getContributions().stream()
                .filter(c -> "Halim".equals(((PersonLite) ((GrantContribution) c).getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertEquals(Collections.singletonList(GrantContribution.RolesEnum.LEAD_APPLICANT), lead.getRoles());
        PersonLite leadBy = (PersonLite) lead.getBy();
        assertEquals("orcid", leadBy.getIdentifiers().getFirst().getScheme());
        assertEquals("0000-0001-9773-0023", leadBy.getIdentifiers().getFirst().getValue());

        GrantContribution coApplicant = (GrantContribution) grant.getContributions().stream()
                .filter(c -> "Caldas".equals(((PersonLite) ((GrantContribution) c).getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertEquals(Collections.singletonList(GrantContribution.RolesEnum.CO_APPLICANT), coApplicant.getRoles());
        Organisation coApplicantAffiliation = (Organisation) coApplicant.getDeclaredAffiliations().getFirst();
        assertEquals("ror", coApplicantAffiliation.getIdentifiers().getFirst().getScheme());
        assertEquals("013meh722", coApplicantAffiliation.getIdentifiers().getFirst().getValue());
    }

    @Test
    void toGrant_dedupesBeneficiariesFromInvestigatorAffiliations() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        // 3 distinct institutions appear across 9 investigators (University of Cambridge
        // repeats 6 times) - beneficiaries must be deduped by name.
        final int expectedDistinctInstitutionCount = 3;
        assertEquals(expectedDistinctInstitutionCount, grant.getBeneficiaries().size());
        assertTrue(grant.getBeneficiaries().stream()
                .anyMatch(b -> "University of Cambridge".equals(((Organisation) b).getName())));
    }

    @Test
    void toGrant_doesNotFabricateAcronym() throws IOException {
        // No Crossref grant-schema field found for this - left unset rather than guessed at.
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertNull(grant.getAcronym());
    }
}
