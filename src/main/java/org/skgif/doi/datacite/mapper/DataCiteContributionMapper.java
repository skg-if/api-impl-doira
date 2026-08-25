package org.skgif.doi.datacite.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.datacite.dto.DataCiteAffiliation;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteContributor;
import org.skgif.doi.datacite.dto.DataCiteCreator;
import org.skgif.doi.datacite.dto.DataCiteNameIdentifier;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductAllOfRelevantOrganisations;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.spec.IdentifierScheme;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Maps a DataCite record's creator/contributor/publisher fields onto {@code
 * Product.contributions}. Split out of {@code DataCiteToSkgIfMapper} to keep that class down to
 * orchestration; also used by {@link DataCiteGrantMapper} for the equivalent grant-contribution
 * mapping (same ORCID/ROR conventions).
 */
final class DataCiteContributionMapper {

    /** DataCite's uppercase spelling of the ROR scheme name (nameIdentifierScheme value). */
    private static final String SCHEME_ROR_UPPER = "ROR";
    /** DataCite's {@code contributorType} value identifying an editor contribution. */
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

    private static ProductContribution.RoleEnum contributorRole(@Nullable String dataCiteContributorType) {
        if (CONTRIBUTOR_TYPE_EDITOR.equals(dataCiteContributorType)) {
            return ProductContribution.RoleEnum.EDITOR;
        }
        return ProductContribution.RoleEnum.AUTHOR;
    }

    static ProductContributionBy personRef(@Nullable String doi, @Nullable String name, @Nullable String givenName,
            @Nullable String familyName, @Nullable List<DataCiteNameIdentifier> nameIdentifiers) {
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
    private static ProductContributionBy organisationRef(@Nullable String doi, @Nullable String name) {
        return EntityRefs.organisationRef(doi, name, null);
    }

    static Optional<String> firstOrcid(@Nullable List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return Optional.empty();
        }
        return nameIdentifiers.stream()
                .filter(ni -> "ORCID".equalsIgnoreCase(ni.nameIdentifierScheme()) && ni.nameIdentifier() != null)
                .map(ni -> ExternalIdentifierUrls.stripOrcidUrl(ni.nameIdentifier()))
                .findFirst();
    }

    static List<PersonLiteAllOfIdentifiers> orcidIdentifiers(@Nullable List<DataCiteNameIdentifier> nameIdentifiers) {
        return Optional.ofNullable(nameIdentifiers)
                .orElseGet(List::of)
                .stream()
                .filter(ni -> "ORCID".equalsIgnoreCase(ni.nameIdentifierScheme()))
                .map(ni -> new PersonLiteAllOfIdentifiers()
                        .scheme(IdentifierScheme.ORCID.value())
                        .value(ni.nameIdentifier() != null ? ExternalIdentifierUrls.stripOrcidUrl(ni.nameIdentifier()) :
                                null))
                .toList();
    }

    static List<ProductAllOfRelevantOrganisations> affiliations(@Nullable String doi,
            @Nullable List<DataCiteAffiliation> affiliations) {
        return Optional.ofNullable(affiliations)
                .orElseGet(List::of)
                .stream()
                .filter(affiliation -> affiliation.name() != null)
                .<ProductAllOfRelevantOrganisations>map(affiliation -> {
                    // Held in a local rather than re-read via the accessor so the null check is
                    // directly visible to the nullness checker at the stripRorUrl call below,
                    // which takes a @NonNull value.
                    String rorIdentifier = affiliation.affiliationIdentifier();
                    String bareRor = rorIdentifier != null &&
                            SCHEME_ROR_UPPER.equalsIgnoreCase(affiliation.affiliationIdentifierScheme()) ?
                                    ExternalIdentifierUrls.stripRorUrl(rorIdentifier) : null;
                    return EntityRefs.organisationRef(doi, affiliation.name(), bareRor);
                })
                .toList();
    }

    static Optional<String> firstRor(@Nullable List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return Optional.empty();
        }
        return nameIdentifiers.stream()
                .filter(ni -> SCHEME_ROR_UPPER.equalsIgnoreCase(ni.nameIdentifierScheme()) &&
                        ni.nameIdentifier() != null)
                .map(ni -> ExternalIdentifierUrls.stripRorUrl(ni.nameIdentifier()))
                .findFirst();
    }
}
