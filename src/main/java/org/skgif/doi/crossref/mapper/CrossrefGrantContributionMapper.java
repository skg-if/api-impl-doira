package org.skgif.doi.crossref.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.skgif.doi.crossref.dto.CrossrefAffiliation;
import org.skgif.doi.crossref.dto.CrossrefInvestigator;
import org.skgif.doi.crossref.dto.CrossrefProject;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.GrantAllOfBeneficiaries;
import org.skgif.doi.generated.model.GrantAllOfContributions;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.ExternalIdentifierUrls;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a Crossref {@code type: "grant"} work record's {@code project[].leadInvestigator}/{@code
 * investigator} entries onto the SKG-IF {@code Grant} entity's contribution/beneficiary fields.
 * Split out of {@link CrossrefGrantMapper} (itself split out of {@code CrossrefToSkgIfMapper}) to
 * keep each class down to one cohesive concern. Reuses {@link CrossrefContributionMapper}'s
 * ORCID/ROR/displayName helpers - none of these methods need {@code LocalIdentifiers} or {@link
 * CrossrefFundingMapper}, so this class is purely static.
 */
final class CrossrefGrantContributionMapper {

    private CrossrefGrantContributionMapper() {
    }

    static List<GrantAllOfContributions> grantContributions(String doi, List<CrossrefProject> projects) {
        List<GrantAllOfContributions> result = new ArrayList<>();
        for (CrossrefProject project : projects) {
            if (project.leadInvestigator != null) {
                for (CrossrefInvestigator investigator : project.leadInvestigator) {
                    result.add(investigatorContribution(doi, investigator, GrantContribution.RolesEnum.LEAD_APPLICANT));
                }
            }
            if (project.investigator != null) {
                for (CrossrefInvestigator investigator : project.investigator) {
                    result.add(investigatorContribution(doi, investigator, GrantContribution.RolesEnum.CO_APPLICANT));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static GrantAllOfContributions investigatorContribution(String doi, CrossrefInvestigator investigator,
            GrantContribution.RolesEnum role) {
        String bareOrcid = CrossrefContributionMapper.bareOrcid(investigator.orcid);
        String name = CrossrefContributionMapper.displayName(investigator.given, investigator.family);
        PersonLite by = new PersonLite()
                .localIdentifier(bareOrcid != null
                        ? ExternalIdentifierUrls.ORCID_BASE_URL + bareOrcid
                        : MapperTextUtils.otf(doi, name))
                .name(name)
                .givenName(investigator.given)
                .familyName(investigator.family)
                .entityType(EntityTypes.PERSON);
        if (bareOrcid != null) {
            by.identifiers(List.of(new PersonLiteAllOfIdentifiers().scheme("orcid").value(bareOrcid)));
        }
        return new GrantContribution()
                .by(by)
                .declaredAffiliations(grantAffiliations(doi, investigator.affiliation))
                .roles(List.of(role));
    }

    static List<GrantAllOfBeneficiaries> grantAffiliations(String doi, List<CrossrefAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return null;
        }
        List<GrantAllOfBeneficiaries> result = new ArrayList<>();
        for (CrossrefAffiliation affiliation : affiliations) {
            if (affiliation.name == null) {
                continue;
            }
            String ror = CrossrefContributionMapper.firstRor(affiliation.id);
            Organisation org = new Organisation()
                    .localIdentifier(ror != null
                            ? ExternalIdentifierUrls.ROR_BASE_URL + ror
                            : MapperTextUtils.otf(doi, affiliation.name))
                    .name(affiliation.name)
                    .entityType(EntityTypes.ORGANISATION);
            if (ror != null) {
                org.identifiers(List.of(new AgentAllOfIdentifiers().scheme("ror").value(ror)));
            }
            result.add(org);
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Crossref grant records have no separate "organisational contributor" concept the way
     * DataCite Awards do - this reuses the investigators' own declared affiliations as
     * beneficiaries (deduped by name), the closest available analogue. Same documented judgment
     * call as {@code DataCiteGrantMapper#grantBeneficiaries}.
     *
     * @param doi the owning grant DOI, used to build a deterministic otf id
     * @param projects the grant DOI's project entries
     * @return the deduped beneficiary organisations, or null if none have a declared affiliation
     */
    static List<GrantAllOfBeneficiaries> grantBeneficiaries(String doi, List<CrossrefProject> projects) {
        Map<String, CrossrefAffiliation> byName = new LinkedHashMap<>();
        for (CrossrefProject project : projects) {
            collectAffiliations(byName, project.leadInvestigator);
            collectAffiliations(byName, project.investigator);
        }
        return grantAffiliations(doi, new ArrayList<>(byName.values()));
    }

    private static void collectAffiliations(Map<String, CrossrefAffiliation> byName,
            List<CrossrefInvestigator> investigators) {
        if (investigators == null) {
            return;
        }
        for (CrossrefInvestigator investigator : investigators) {
            if (investigator.affiliation == null) {
                continue;
            }
            for (CrossrefAffiliation affiliation : investigator.affiliation) {
                if (affiliation.name != null) {
                    byName.putIfAbsent(affiliation.name, affiliation);
                }
            }
        }
    }
}
