package org.skgif.doi.datacite.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.skgif.doi.datacite.dto.DataCiteAffiliation;
import org.skgif.doi.datacite.dto.DataCiteContributor;
import org.skgif.doi.datacite.dto.DataCiteCreator;
import org.skgif.doi.datacite.dto.DataCiteNameIdentifier;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.GrantAllOfBeneficiaries;
import org.skgif.doi.generated.model.GrantAllOfContributions;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.GrantContributionBy;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.ExternalIdentifierUrls;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a DataCite Award record's creators/contributors onto the SKG-IF {@code Grant} entity's
 * fundingAgency/contribution/beneficiary fields. Split out of {@code DataCiteToSkgIfMapper} to
 * keep that class down to orchestration. Reuses {@link DataCiteContributionMapper}'s ORCID/ROR
 * helpers - none of these methods need {@code LocalIdentifiers} (unlike {@code Product.funding}'s
 * DOI-shaped-identifier resolution), so this class is purely static.
 */
final class DataCiteGrantMapper {

    private static final String SCHEME_ROR_UPPER = "ROR";
    private static final String SCHEME_ROR = "ror";
    private static final String NAME_TYPE_ORGANIZATIONAL = "Organizational";

    private DataCiteGrantMapper() {
    }

    static Organisation grantFundingAgency(String doi, Optional<DataCiteCreator> fundingAgencyCreator,
                                           String publisher) {
        if (fundingAgencyCreator.isPresent()) {
            DataCiteCreator creator = fundingAgencyCreator.get();
            String ror = DataCiteContributionMapper.firstRor(creator.nameIdentifiers());
            return new Organisation()
                    .localIdentifier(ExternalIdentifierUrls.ROR_BASE_URL + ror)
                    .name(creator.name())
                    .entityType(EntityTypes.ORGANISATION)
                    .identifiers(List.of(new AgentAllOfIdentifiers().scheme(SCHEME_ROR).value(ror)));
        }
        // No ROR-bearing creator to identify the funder - fall back to the record's own
        // publisher, same convention used for Product.manifestations[].biblio.hosting_data_source.
        if (publisher == null) {
            return null;
        }
        return new Organisation()
                .localIdentifier(MapperTextUtils.otf(doi, publisher))
                .name(publisher)
                .entityType(EntityTypes.ORGANISATION);
    }

    static List<GrantAllOfContributions> grantContributions(String doi, List<DataCiteCreator> creators,
                                                            List<DataCiteContributor> contributors, Optional<
                                                                    DataCiteCreator> fundingAgencyCreator) {
        List<GrantAllOfContributions> result = new ArrayList<>();
        for (DataCiteCreator creator : creators) {
            if (fundingAgencyCreator.isPresent() && fundingAgencyCreator.get() == creator) {
                continue;
            }
            boolean organizational = NAME_TYPE_ORGANIZATIONAL.equals(creator.nameType());
            result.add(new GrantContribution()
                    .by(grantContributionBy(doi, creator.name(), creator.givenName(), creator.familyName(),
                            creator.nameIdentifiers(), organizational))
                    .declaredAffiliations(grantAffiliations(doi, creator.affiliation())));
        }
        for (DataCiteContributor contributor : contributors) {
            boolean organizational = NAME_TYPE_ORGANIZATIONAL.equals(contributor.nameType());
            result.add(new GrantContribution()
                    .by(grantContributionBy(doi, contributor.name(), contributor.givenName(), contributor.familyName(),
                            contributor.nameIdentifiers(), organizational))
                    .declaredAffiliations(grantAffiliations(doi, contributor.affiliation())));
        }
        return result;
    }

    private static GrantContributionBy grantContributionBy(String doi, String name, String givenName,
                                                           String familyName, List<
                                                                   DataCiteNameIdentifier> nameIdentifiers,
                                                           boolean organizational) {
        if (organizational) {
            String ror = DataCiteContributionMapper.firstRor(nameIdentifiers);
            Organisation by = new Organisation()
                    .localIdentifier(ror != null ?
                            ExternalIdentifierUrls.ROR_BASE_URL + ror :
                            MapperTextUtils.otf(doi, name))
                    .name(name)
                    .entityType(EntityTypes.ORGANISATION);
            if (ror != null) {
                by.identifiers(List.of(new AgentAllOfIdentifiers().scheme(SCHEME_ROR).value(ror)));
            }
            return by;
        }
        String orcid = DataCiteContributionMapper.firstOrcid(nameIdentifiers);
        List<PersonLiteAllOfIdentifiers> identifiers = DataCiteContributionMapper.orcidIdentifiers(nameIdentifiers);
        return EntityRefs.personRef(doi, name, givenName, familyName, orcid, identifiers);
    }

    static List<GrantAllOfBeneficiaries> grantAffiliations(String doi, List<DataCiteAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return List.of();
        }
        List<GrantAllOfBeneficiaries> result = new ArrayList<>();
        for (DataCiteAffiliation affiliation : affiliations) {
            if (affiliation.name() == null) {
                continue;
            }
            boolean hasRor = affiliation.affiliationIdentifier() != null &&
                    SCHEME_ROR_UPPER.equalsIgnoreCase(affiliation.affiliationIdentifierScheme());
            String bareRor = hasRor ? MapperTextUtils.stripRorUrl(affiliation.affiliationIdentifier()) : null;
            result.add(EntityRefs.organisationRef(doi, affiliation.name(), bareRor));
        }
        return result;
    }

    /**
     * Organisational contributors (DataCite {@code nameType: "Organizational"}) are also listed
     * as the grant's beneficiaries, alongside appearing in {@code contributions} - both are
     * legitimate per the spec's own worked example (GraspOS: Brown University is both a
     * contribution's declared affiliation and a top-level beneficiary).
     *
     * @param doi          the owning record's DOI, used to build a deterministic otf id
     * @param contributors the record's contributors
     * @return the organisational contributors mapped as beneficiaries, or an empty list if there
     *         are none
     */
    static List<GrantAllOfBeneficiaries> grantBeneficiaries(String doi, List<DataCiteContributor> contributors) {
        List<DataCiteAffiliation> organizationalContributors = new ArrayList<>();
        for (DataCiteContributor contributor : contributors) {
            if (!NAME_TYPE_ORGANIZATIONAL.equals(contributor.nameType()) || contributor.name() == null) {
                continue;
            }
            String ror = DataCiteContributionMapper.firstRor(contributor.nameIdentifiers());
            DataCiteAffiliation asAffiliation = ror != null ?
                    new DataCiteAffiliation(contributor.name(), ExternalIdentifierUrls.ROR_BASE_URL + ror,
                            SCHEME_ROR_UPPER) :
                    new DataCiteAffiliation(contributor.name(), null, null);
            organizationalContributors.add(asAffiliation);
        }
        return grantAffiliations(doi, organizationalContributors);
    }
}
