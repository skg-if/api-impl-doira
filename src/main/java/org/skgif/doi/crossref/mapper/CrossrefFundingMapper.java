package org.skgif.doi.crossref.mapper;

import java.util.ArrayList;
import java.util.List;
import org.skgif.doi.crossref.dto.CrossrefFunder;
import org.skgif.doi.crossref.dto.CrossrefFunding;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.ProductAllOfFunding;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.LocalIdentifiers;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a Crossref work record's {@code funder[]} onto {@code Product.funding}, and resolves a
 * funder into an SKG-IF Organisation ({@link #fundingAgencyOrg}) - reused by {@link
 * CrossrefGrantMapper#grantFundingAgency} for the equivalent grant-record lookup. Split out of
 * {@code CrossrefToSkgIfMapper} to keep that class down to orchestration. Needs {@link
 * LocalIdentifiers} to resolve a funder's Funder Registry DOI into a real local_identifier, so -
 * unlike the purely-static title/contribution helpers - this is an instance collaborator,
 * constructed once by the facade.
 */
final class CrossrefFundingMapper {

    private static final String SCHEME_DOI = "doi";

    private final LocalIdentifiers localIdentifiers;

    CrossrefFundingMapper(LocalIdentifiers localIdentifiers) {
        this.localIdentifiers = localIdentifiers;
    }

    List<ProductAllOfFunding> funding(CrossrefWork work) {
        if (work.funder == null || work.funder.isEmpty()) {
            return null;
        }
        List<ProductAllOfFunding> result = new ArrayList<>();
        for (CrossrefFunder funder : work.funder) {
            if (funder.name == null) {
                continue;
            }
            List<String> awards = funder.award != null ? funder.award : List.of();
            if (awards.isEmpty()) {
                result.add(fundingEntry(work.doi, funder, null));
            } else {
                for (String award : awards) {
                    result.add(fundingEntry(work.doi, funder, award));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private ProductAllOfFunding fundingEntry(String doi, CrossrefFunder funder, String awardNumber) {
        String label = awardNumber != null ? awardNumber : funder.name;
        return new GrantLite()
                .localIdentifier(MapperTextUtils.otf(doi, label))
                .entityType(GrantLite.EntityTypeEnum.GRANT)
                .grantNumber(awardNumber)
                .fundingAgency(fundingAgencyOrg(doi, funder));
    }

    /**
     * The Funder Registry DOI plays the role DataCite's ROR-typed {@code funderIdentifier} plays
     * - note the identifier scheme emitted here is {@code doi}, not {@code ror}, since that's
     * genuinely what Crossref gives (a funder's Funder Registry DOI, not its ROR).
     *
     * @param doi the owning record's DOI, used to build a deterministic otf id when funder has
     *     no Funder Registry DOI
     * @param funder the Crossref funder record
     * @return an Organisation for funder, identified by its Funder Registry DOI when present
     */
    Organisation fundingAgencyOrg(String doi, CrossrefFunder funder) {
        String funderDoi = funderDoi(funder);
        Organisation agency = new Organisation()
                .localIdentifier(funderDoi != null
                        ? localIdentifiers.toFullLocalIdentifier(funderDoi)
                        : MapperTextUtils.otf(doi, funder.name))
                .name(funder.name)
                .entityType(EntityTypes.ORGANISATION);
        if (funderDoi != null) {
            agency.identifiers(List.of(new AgentAllOfIdentifiers().scheme(SCHEME_DOI).value(funderDoi)));
        }
        return agency;
    }

    /**
     * A top-level {@code work.funder[]} entry carries the Funder Registry DOI directly as {@code
     * DOI}; a grant record's {@code project[].funding[].funder} only has it inside {@code id[]}
     * (verified live against a real grant record) - check both.
     *
     * @param funder the Crossref funder record to read a Funder Registry DOI from
     * @return the funder's Funder Registry DOI, or null if it has none
     */
    private String funderDoi(CrossrefFunder funder) {
        if (funder.doi != null) {
            return funder.doi;
        }
        if (funder.id == null) {
            return null;
        }
        return funder.id.stream()
                .filter(entry -> "DOI".equalsIgnoreCase(entry.idType) && entry.id != null)
                .map(entry -> entry.id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves the funding agency for a grant record: the primary funding entry's funder, or -
     * when Crossref's grant DOI carries no {@code project[].funding[]} entry at all - the first
     * top-level {@code work.funder[]} entry.
     *
     * @param doi the owning grant DOI, used to build a deterministic otf id when funder has no
     *     Funder Registry DOI
     * @param primaryFunding the grant's first project's first funding entry, or null
     * @param topLevelFunders the grant record's top-level funder[], or null
     * @return the mapped Organisation, or null if no funder name is available
     */
    Organisation grantFundingAgency(String doi, CrossrefFunding primaryFunding, List<CrossrefFunder> topLevelFunders) {
        CrossrefFunder funder = primaryFunding != null ? primaryFunding.funder : null;
        if (funder == null && topLevelFunders != null && !topLevelFunders.isEmpty()) {
            funder = topLevelFunders.get(0);
        }
        if (funder == null || funder.name == null) {
            return null;
        }
        return fundingAgencyOrg(doi, funder);
    }
}
