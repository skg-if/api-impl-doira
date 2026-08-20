package org.skgif.doi.datacite.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.skgif.doi.datacite.dto.DataCiteAffiliation;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteContributor;
import org.skgif.doi.datacite.dto.DataCiteCreator;
import org.skgif.doi.datacite.dto.DataCiteNameIdentifier;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductAllOfRelevantOrganisations;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.ExternalIdentifierUrls;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a DataCite record's creator/contributor/publisher fields onto {@code
 * Product.contributions}. Split out of {@code DataCiteToSkgIfMapper} to keep that class down to
 * orchestration; also used by {@link DataCiteGrantMapper} for the equivalent grant-contribution
 * mapping (same ORCID/ROR conventions).
 */
final class DataCiteContributionMapper {

    private static final String SCHEME_ROR_UPPER = "ROR";
    private static final String CONTRIBUTOR_TYPE_EDITOR = "Editor";

    private DataCiteContributionMapper() {
    }

    static List<ProductContribution> contributions(DataCiteAttributes attributes) {
        List<ProductContribution> contributions = new ArrayList<>();
        int rank = 1;
        if (attributes.creators() != null) {
            for (DataCiteCreator creator : attributes.creators()) {
                contributions.add(new ProductContribution()
                        .by(personRef(attributes.doi(), creator.name(), creator.givenName(), creator.familyName(),
                                creator.nameIdentifiers()))
                        .declaredAffiliations(affiliations(attributes.doi(), creator.affiliation()))
                        .rank(rank++)
                        .role(ProductContribution.RoleEnum.AUTHOR));
            }
        }
        if (attributes.contributors() != null) {
            for (DataCiteContributor contributor : attributes.contributors()) {
                contributions.add(new ProductContribution()
                        .by(personRef(attributes.doi(), contributor.name(), contributor.givenName(),
                                contributor.familyName(), contributor.nameIdentifiers()))
                        .declaredAffiliations(affiliations(attributes.doi(), contributor.affiliation()))
                        .rank(rank++)
                        .role(contributorRole(contributor.contributorType())));
            }
        }
        if (attributes.publisher() != null) {
            contributions.add(new ProductContribution()
                    .by(organisationRef(attributes.doi(), attributes.publisher()))
                    .rank(rank)
                    .role(ProductContribution.RoleEnum.PUBLISHER));
        }
        return contributions;
    }

    private static ProductContribution.RoleEnum contributorRole(String dataCiteContributorType) {
        if (CONTRIBUTOR_TYPE_EDITOR.equals(dataCiteContributorType)) {
            return ProductContribution.RoleEnum.EDITOR;
        }
        return ProductContribution.RoleEnum.AUTHOR;
    }

    static ProductContributionBy personRef(String doi, String name, String givenName, String familyName,
            List<DataCiteNameIdentifier> nameIdentifiers) {
        return EntityRefs.personRef(doi, name, givenName, familyName, firstOrcid(nameIdentifiers).orElse(null),
                orcidIdentifiers(nameIdentifiers));
    }

    /**
     * DataCite's top-level {@code publisher} is a bare string with no external identifier
     * system behind it, so - like the {@code hosting_data_source} use of the same field - this
     * always gets an otf id.
     *
     * @param doi  the owning record's DOI, used to build a deterministic otf id
     * @param name the organisation's name
     * @return an Organisation reference with an otf local_identifier
     */
    private static ProductContributionBy organisationRef(String doi, String name) {
        return EntityRefs.organisationRef(doi, name, null);
    }

    static Optional<String> firstOrcid(List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return Optional.empty();
        }
        return nameIdentifiers.stream()
                .filter(ni -> "ORCID".equalsIgnoreCase(ni.nameIdentifierScheme()) && ni.nameIdentifier() != null)
                .map(ni -> ni.nameIdentifier().startsWith(ExternalIdentifierUrls.ORCID_BASE_URL) ?
                        ni.nameIdentifier().substring(ExternalIdentifierUrls.ORCID_BASE_URL.length()) :
                        ni.nameIdentifier())
                .findFirst();
    }

    static List<PersonLiteAllOfIdentifiers> orcidIdentifiers(List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null || nameIdentifiers.isEmpty()) {
            return List.of();
        }
        List<PersonLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        for (DataCiteNameIdentifier ni : nameIdentifiers) {
            if (!"ORCID".equalsIgnoreCase(ni.nameIdentifierScheme())) {
                continue;
            }
            String orcid = ni.nameIdentifier();
            if (orcid != null && orcid.startsWith(ExternalIdentifierUrls.ORCID_BASE_URL)) {
                orcid = orcid.substring(ExternalIdentifierUrls.ORCID_BASE_URL.length());
            }
            identifiers.add(new PersonLiteAllOfIdentifiers()
                    .scheme("orcid")
                    .value(orcid));
        }
        return identifiers;
    }

    static List<ProductAllOfRelevantOrganisations> affiliations(String doi, List<DataCiteAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return List.of();
        }
        List<ProductAllOfRelevantOrganisations> result = new ArrayList<>();
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

    static Optional<String> firstRor(List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return Optional.empty();
        }
        return nameIdentifiers.stream()
                .filter(ni -> SCHEME_ROR_UPPER.equalsIgnoreCase(ni.nameIdentifierScheme()) &&
                        ni.nameIdentifier() != null)
                .map(ni -> MapperTextUtils.stripRorUrl(ni.nameIdentifier()))
                .findFirst();
    }
}
