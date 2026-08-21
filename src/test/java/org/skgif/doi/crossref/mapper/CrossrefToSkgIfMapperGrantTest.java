package org.skgif.doi.crossref.mapper;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Used to read the JSON fixture files this test maps. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Mocked Crossref REST client, unused directly but required by {@link #mapper}'s dependencies. */
    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    /** The mapper under test. */
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

        assertThat(grant.getLocalIdentifier()).isEqualTo("https://doi.org/10.35802/218300");
        assertThat(grant.getEntityType()).hasToString("grant");
        assertThat(grant.getIdentifiers()).hasSize(1);
        assertThat(grant.getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(grant.getIdentifiers().getFirst().getValue()).isEqualTo("10.35802/218300");
        assertThat(grant.getGrantNumber()).isEqualTo("218300");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstractsFromProject() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertThat(titles.get("en")).contains("Biocontainment Level 2");

        // Two project-description entries in the fixture, concatenated into one string since
        // Grant.abstracts (unlike Product.abstracts) is a plain string per language.
        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertThat(abstracts.get("en")).contains("Provision of cutting edge cell-sorter");
        assertThat(abstracts.get("en")).contains("Our bodies are composed of trillions");
    }

    @Test
    void toGrant_mapsFundingAgencyAmountCurrencyAndDurationExplicitly() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertThat(grant.getFundingAgency().getName()).isEqualTo("Wellcome Trust");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getScheme()).isEqualTo("doi");
        assertThat(grant.getFundingAgency().getIdentifiers().getFirst().getValue())
                .isEqualTo("10.13039/100010269");
        final int expectedFundedAmount = 479450;
        assertThat(grant.getFundedAmount()).isEqualTo(expectedFundedAmount);
        assertThat(grant.getCurrency()).isEqualTo("GBP");
        assertThat(grant.getDuration().getStart()).isEqualTo("2019-11-01");
        assertThat(grant.getDuration().getEnd()).isEqualTo("2024-10-31");
    }

    @Test
    void toGrant_mapsLeadAndCoInvestigatorsAsContributionsWithRoles() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        final int expectedInvestigatorCount = 9;
        assertThat(grant.getContributions()).hasSize(expectedInvestigatorCount);
        GrantContribution lead = (GrantContribution) grant.getContributions().stream()
                .filter(c -> "Halim".equals(((PersonLite) ((GrantContribution) c).getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertThat(lead.getRoles()).isEqualTo(Collections.singletonList(GrantContribution.RolesEnum.LEAD_APPLICANT));
        PersonLite leadBy = (PersonLite) lead.getBy();
        assertThat(leadBy.getIdentifiers().getFirst().getScheme()).isEqualTo("orcid");
        assertThat(leadBy.getIdentifiers().getFirst().getValue()).isEqualTo("0000-0001-9773-0023");

        GrantContribution coApplicant = (GrantContribution) grant.getContributions().stream()
                .filter(c -> "Caldas".equals(((PersonLite) ((GrantContribution) c).getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertThat(coApplicant.getRoles())
                .isEqualTo(Collections.singletonList(GrantContribution.RolesEnum.CO_APPLICANT));
        Organisation coApplicantAffiliation = (Organisation) coApplicant.getDeclaredAffiliations().getFirst();
        assertThat(coApplicantAffiliation.getIdentifiers().getFirst().getScheme()).isEqualTo("ror");
        assertThat(coApplicantAffiliation.getIdentifiers().getFirst().getValue()).isEqualTo("013meh722");
    }

    @Test
    void toGrant_dedupesBeneficiariesFromInvestigatorAffiliations() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        // 3 distinct institutions appear across 9 investigators (University of Cambridge
        // repeats 6 times) - beneficiaries must be deduped by name.
        final int expectedDistinctInstitutionCount = 3;
        assertThat(grant.getBeneficiaries()).hasSize(expectedDistinctInstitutionCount);
        assertThat(grant.getBeneficiaries())
                .anyMatch(b -> "University of Cambridge".equals(((Organisation) b).getName()));
    }

    @Test
    void toGrant_doesNotFabricateAcronym() throws IOException {
        // No Crossref grant-schema field found for this - left unset rather than guessed at.
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertThat(grant.getAcronym()).isNull();
    }
}
