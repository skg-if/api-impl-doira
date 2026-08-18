package org.skgif.doi.crossref.mapper;

import java.util.ArrayList;
import java.util.List;
import org.skgif.doi.crossref.dto.CrossrefAffiliation;
import org.skgif.doi.crossref.dto.CrossrefContributor;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;
import org.skgif.doi.generated.model.ProductAllOfRelevantOrganisations;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductContributionBy;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.ExternalIdentifierUrls;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a Crossref work record's author/editor/publisher fields onto {@code
 * Product.contributions}. Split out of {@code CrossrefToSkgIfMapper} to keep that class down to
 * orchestration; also used by {@link CrossrefGrantMapper} for the equivalent grant-contribution
 * mapping (same ORCID/ROR/displayName conventions).
 */
final class CrossrefContributionMapper {

    private CrossrefContributionMapper() {
    }

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

    static ProductContributionBy personRef(String doi, String given, String family, String rawOrcid) {
        String bareOrcid = bareOrcid(rawOrcid);
        String name = displayName(given, family);
        List<PersonLiteAllOfIdentifiers> orcidIdentifiers = bareOrcid != null ?
                List.of(new PersonLiteAllOfIdentifiers().scheme("orcid").value(bareOrcid)) :
                null;
        return EntityRefs.personRef(doi, name, given, family, bareOrcid, orcidIdentifiers);
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
    private static ProductContributionBy organisationRef(String doi, String name) {
        return EntityRefs.organisationRef(doi, name, null);
    }

    /**
     * Crossref's ORCID field is already a full URL (http or https) - normalize both to bare.
     *
     * @param orcidUrl the full ORCID URL (http or https), or null
     * @return the bare ORCID id, or null if orcidUrl is null
     */
    static String bareOrcid(String orcidUrl) {
        if (orcidUrl == null) {
            return null;
        }
        if (orcidUrl.startsWith(ExternalIdentifierUrls.ORCID_BASE_URL)) {
            return orcidUrl.substring(ExternalIdentifierUrls.ORCID_BASE_URL.length());
        }
        if (orcidUrl.startsWith(ExternalIdentifierUrls.ORCID_HTTP_BASE_URL)) {
            return orcidUrl.substring(ExternalIdentifierUrls.ORCID_HTTP_BASE_URL.length());
        }
        return orcidUrl;
    }

    static String displayName(String given, String family) {
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
    static List<ProductAllOfRelevantOrganisations> affiliations(String doi, List<CrossrefAffiliation> affiliations) {
        if (affiliations == null || affiliations.isEmpty()) {
            return List.of();
        }
        List<ProductAllOfRelevantOrganisations> result = new ArrayList<>();
        for (CrossrefAffiliation affiliation : affiliations) {
            if (affiliation.name() == null) {
                continue;
            }
            result.add(EntityRefs.organisationRef(doi, affiliation.name(), firstRor(affiliation.id())));
        }
        return result;
    }

    static String firstRor(List<CrossrefIdEntry> ids) {
        if (ids == null) {
            return null;
        }
        return ids.stream()
                .filter(entry -> "ROR".equalsIgnoreCase(entry.idType()) && entry.id() != null)
                .map(entry -> MapperTextUtils.stripRorUrl(entry.id()))
                .findFirst()
                .orElse(null);
    }
}
