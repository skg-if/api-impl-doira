package org.skgif.doi.datacite.mapper;

import static org.skgif.doi.util.SpotBugsError.Code.IMPROPER_UNICODE;
import static org.skgif.doi.util.SpotBugsError.Code.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE;
import static org.skgif.doi.util.SpotBugsError.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
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
    /** DataCite's uppercase spelling of the ORCID scheme name (nameIdentifierScheme value). */
    private static final String SCHEME_ORCID_UPPER = "ORCID";
    /** DataCite's {@code contributorType} value identifying an editor contribution. */
    private static final String CONTRIBUTOR_TYPE_EDITOR = "Editor";

    private DataCiteContributionMapper() {
    }

    @SuppressFBWarnings(value = NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE, justification = "attributes.creators()/" +
            "attributes.contributors() misread as independently nullable per-call rather than a pure record " +
            "accessor guarded by the preceding null check - " + SPOTBUGS_REGISTER)
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
        return firstBareIdentifier(nameIdentifiers, SCHEME_ORCID_UPPER, ExternalIdentifierUrls::stripOrcidUrl);
    }

    /**
     * Picks the first declared identifier carrying a value under the requested scheme, in bare
     * (URL-stripped) form. Written as a loop rather than a filter/map stream pair so the {@code
     * != null} guard and the {@code bareForm} call it protects stay in one method body - a stream
     * pair splits them across two synthetic lambdas, which neither NullAway nor SpotBugs's
     * annotation-based suppression can connect.
     *
     * @param nameIdentifiers the creator/contributor's declared name identifiers, or null if it has none
     * @param scheme          the {@code nameIdentifierScheme} value to match, case-insensitively
     * @param bareForm        strips the scheme's URL prefix off a raw identifier value
     * @return the first matching identifier in bare form, or Optional.empty() if there is none
     */
    private static Optional<String> firstBareIdentifier(@Nullable List<DataCiteNameIdentifier> nameIdentifiers,
            String scheme, UnaryOperator<String> bareForm) {
        if (nameIdentifiers == null) {
            return Optional.empty();
        }
        for (DataCiteNameIdentifier nameIdentifier : nameIdentifiers) {
            String value = nameIdentifier.nameIdentifier();
            if (value != null && matchesScheme(scheme, nameIdentifier.nameIdentifierScheme())) {
                return Optional.of(bareForm.apply(value));
            }
        }
        return Optional.empty();
    }

    static List<PersonLiteAllOfIdentifiers> orcidIdentifiers(@Nullable List<DataCiteNameIdentifier> nameIdentifiers) {
        if (nameIdentifiers == null) {
            return List.of();
        }
        List<PersonLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        for (DataCiteNameIdentifier nameIdentifier : nameIdentifiers) {
            if (!matchesScheme(SCHEME_ORCID_UPPER, nameIdentifier.nameIdentifierScheme())) {
                continue;
            }
            // Held in a local rather than re-read via the accessor so the null check is directly
            // visible to the nullness checker at the stripOrcidUrl call, which takes a @NonNull value.
            String value = nameIdentifier.nameIdentifier();
            identifiers.add(new PersonLiteAllOfIdentifiers()
                    .scheme(IdentifierScheme.ORCID.value())
                    .value(value != null ? ExternalIdentifierUrls.stripOrcidUrl(value) : null));
        }
        return List.copyOf(identifiers);
    }

    static List<ProductAllOfRelevantOrganisations> affiliations(@Nullable String doi,
            @Nullable List<DataCiteAffiliation> affiliations) {
        if (affiliations == null) {
            return List.of();
        }
        List<ProductAllOfRelevantOrganisations> organisations = new ArrayList<>();
        for (DataCiteAffiliation affiliation : affiliations) {
            String name = affiliation.name();
            if (name == null) {
                continue;
            }
            // Held in a local rather than re-read via the accessor so the null check is directly
            // visible to the nullness checker at the stripRorUrl call, which takes a @NonNull value.
            String rorIdentifier = affiliation.affiliationIdentifier();
            String bareRor = rorIdentifier != null &&
                    matchesScheme(SCHEME_ROR_UPPER, affiliation.affiliationIdentifierScheme()) ?
                            ExternalIdentifierUrls.stripRorUrl(rorIdentifier) : null;
            organisations.add(EntityRefs.organisationRef(doi, name, bareRor));
        }
        return List.copyOf(organisations);
    }

    /**
     * Compares a DataCite scheme name case-insensitively, in one place so this package carries a
     * single {@code IMPROPER_UNICODE} suppression rather than one per calling method.
     *
     * @param expected the fixed uppercase vocabulary constant to match
     * @param actual   the scheme name as DataCite spelled it, or null if the record omits it
     * @return true if the record's scheme name matches, ignoring case
     */
    @SuppressFBWarnings(value = IMPROPER_UNICODE, justification = "equalsIgnoreCase against a fixed ASCII " +
            "vocabulary constant (\"ORCID\"/\"ROR\") - unconditionally flagged by design - " + SPOTBUGS_REGISTER)
    static boolean matchesScheme(String expected, @Nullable String actual) {
        return expected.equalsIgnoreCase(actual);
    }

    static Optional<String> firstRor(@Nullable List<DataCiteNameIdentifier> nameIdentifiers) {
        return firstBareIdentifier(nameIdentifiers, SCHEME_ROR_UPPER, ExternalIdentifierUrls::stripRorUrl);
    }
}
