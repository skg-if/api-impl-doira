package org.skgif.doi.crossref.mapper;

import static org.skgif.doi.util.SpotBugsError.Code.IMPROPER_UNICODE;
import static org.skgif.doi.util.SpotBugsError.Code.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE;
import static org.skgif.doi.util.SpotBugsError.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.crossref.dto.CrossrefAffiliation;
import org.skgif.doi.crossref.dto.CrossrefContributor;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductAllOfRelevantOrganisations;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.spec.IdentifierScheme;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.ExternalIdentifierUrls;

/**
 * Maps a Crossref work record's author/editor/publisher fields onto {@code
 * Product.contributions}. Split out of {@code CrossrefToSkgIfMapper} to keep that class down to
 * orchestration; also used by {@link CrossrefGrantMapper} for the equivalent grant-contribution
 * mapping (same ORCID/ROR/displayName conventions).
 */
final class CrossrefContributionMapper {

    private CrossrefContributionMapper() {
    }

    @SuppressFBWarnings(value = NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE,
            justification = "work.author()/work.editor() misread as independently nullable per-call rather than " +
                    "a pure record accessor guarded by the preceding null check - " + SPOTBUGS_REGISTER)
    static List<ProductContribution> contributions(CrossrefWork work) {
        List<ProductContribution> contributions = new ArrayList<>();
        int rank = 1;
        if (work.author() != null) {
            for (CrossrefContributor author : work.author()) {
                contributions.add(new ProductContribution()
                        .by(personRef(work.doi(), author.given(), author.family(), author.orcid()))
                        .declaredAffiliations(affiliations(work.doi(), author.affiliation()))
                        .rank(rank++)
                        .role(ProductContribution.RoleEnum.AUTHOR));
            }
        }
        if (work.editor() != null) {
            for (CrossrefContributor editor : work.editor()) {
                contributions.add(new ProductContribution()
                        .by(personRef(work.doi(), editor.given(), editor.family(), editor.orcid()))
                        .declaredAffiliations(affiliations(work.doi(), editor.affiliation()))
                        .rank(rank++)
                        .role(ProductContribution.RoleEnum.EDITOR));
            }
        }
        if (work.publisher() != null) {
            contributions.add(new ProductContribution()
                    .by(organisationRef(work.doi(), work.publisher()))
                    .rank(rank)
                    .role(ProductContribution.RoleEnum.PUBLISHER));
        }
        return contributions;
    }

    static ProductContributionBy personRef(@Nullable String doi, @Nullable String given, @Nullable String family,
            @Nullable String rawOrcid) {
        Optional<String> bareOrcid = bareOrcid(rawOrcid);
        String name = displayName(given, family);
        List<PersonLiteAllOfIdentifiers> orcidIdentifiers = bareOrcid
                .map(orcid -> List.of(new PersonLiteAllOfIdentifiers().scheme(IdentifierScheme.ORCID.value()).value(
                        orcid)))
                .orElse(null);
        return EntityRefs.personRef(doi, name, given, family, bareOrcid.orElse(null), orcidIdentifiers);
    }

    /**
     * Crossref's top-level {@code publisher} is a bare string with no external identifier
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

    /**
     * Crossref's ORCID field is already a full URL (http or https) - normalize both to bare.
     *
     * @param orcidUrl the full ORCID URL (http or https), or null
     * @return the bare ORCID id, or Optional.empty() if orcidUrl is null
     */
    static Optional<String> bareOrcid(@Nullable String orcidUrl) {
        return orcidUrl == null ? Optional.empty() : Optional.of(ExternalIdentifierUrls.stripOrcidUrl(orcidUrl));
    }

    static @Nullable String displayName(@Nullable String given, @Nullable String family) {
        if (given == null) {
            return family;
        }
        if (family == null) {
            return given;
        }
        return given + " " + family;
    }

    /**
     * Crossref author/editor affiliations are usually name-only, but some publishers (e.g. APS)
     * do assert a ROR on them directly - same occasional-ROR situation as DataCite creator
     * affiliations, so this checks for one before falling back to an otf id.
     *
     * @param doi          the owning record's DOI, used to build a deterministic otf id
     * @param affiliations the author/editor's declared affiliations
     * @return the mapped affiliations, or an empty list if affiliations is null/empty
     */
    static List<ProductAllOfRelevantOrganisations> affiliations(@Nullable String doi,
            @Nullable List<CrossrefAffiliation> affiliations) {
        if (affiliations == null) {
            return List.of();
        }
        return affiliations.stream()
                .filter(affiliation -> affiliation.name() != null)
                .<ProductAllOfRelevantOrganisations>map(affiliation -> EntityRefs.organisationRef(doi,
                        affiliation.name(), firstRor(affiliation.id()).orElse(null)))
                .toList();
    }

    /**
     * Picks the first ROR-typed identifier off a Crossref affiliation's {@code id} list. Written as
     * a loop rather than a stream so the {@code id != null} guard sits in the same method as the
     * dereference - a filter/map stream pair puts the guard in one synthetic lambda and the
     * dereference in another, which neither NullAway nor SpotBugs can connect.
     *
     * @param ids the affiliation's declared identifiers, or null if it asserts none
     * @return the bare ROR id, or Optional.empty() if no ROR-typed entry carries a value
     */
    static Optional<String> firstRor(@Nullable List<CrossrefIdEntry> ids) {
        if (ids == null) {
            return Optional.empty();
        }
        for (CrossrefIdEntry entry : ids) {
            String id = entry.id();
            if (id != null && isRorType(entry.idType())) {
                return Optional.of(ExternalIdentifierUrls.stripRorUrl(id));
            }
        }
        return Optional.empty();
    }

    @SuppressFBWarnings(value = IMPROPER_UNICODE, justification = "equalsIgnoreCase against a fixed ASCII " +
            "vocabulary constant (\"ROR\") - unconditionally flagged by design - " + SPOTBUGS_REGISTER)
    private static boolean isRorType(@Nullable String idType) {
        return "ROR".equalsIgnoreCase(idType);
    }
}
