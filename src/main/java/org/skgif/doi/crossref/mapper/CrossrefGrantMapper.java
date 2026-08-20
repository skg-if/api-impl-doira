package org.skgif.doi.crossref.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.skgif.doi.crossref.dto.CrossrefAmount;
import org.skgif.doi.crossref.dto.CrossrefFunder;
import org.skgif.doi.crossref.dto.CrossrefFunding;
import org.skgif.doi.crossref.dto.CrossrefProject;
import org.skgif.doi.crossref.dto.CrossrefProjectDescription;
import org.skgif.doi.crossref.dto.CrossrefProjectTitle;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.GrantAllOfDuration;
import org.skgif.doi.generated.model.Organisation;

/**
 * Maps a Crossref {@code type: "grant"} work record's {@code project[]} entries onto the SKG-IF
 * {@code Grant} entity's title/abstract/funding/amount/duration/website fields. Split out of
 * {@code CrossrefToSkgIfMapper} to keep that class down to orchestration; the equivalent
 * contribution/beneficiary mapping lives in {@link CrossrefGrantContributionMapper} (a separate
 * class specifically so each stays a single cohesive concern). Delegates funder resolution to
 * {@link CrossrefFundingMapper} (same Funder-Registry-DOI convention as {@code Product.funding}).
 */
final class CrossrefGrantMapper {

    private final CrossrefFundingMapper fundingMapper;

    CrossrefGrantMapper(CrossrefFundingMapper fundingMapper) {
        this.fundingMapper = fundingMapper;
    }

    /**
     * Unlike {@code Product.titles}/{@code abstracts} (array of strings per language),
     * {@code Grant.titles}/{@code abstracts} are a plain string per language - so titles/
     * descriptions from multiple {@code project[]} entries are concatenated into one string.
     *
     * @param projects the grant DOI's project entries
     * @return the concatenated titles keyed by "en", or an empty map if none carry a title
     */
    Map<String, String> grantTitles(List<CrossrefProject> projects) {
        List<String> values = projects.stream()
                .filter(p -> p.projectTitle() != null)
                .flatMap(p -> p.projectTitle().stream())
                .map(CrossrefProjectTitle::title)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? Map.of() : Map.of("en", String.join(" ", values));
    }

    Map<String, String> grantAbstracts(List<CrossrefProject> projects) {
        List<String> values = projects.stream()
                .filter(p -> p.projectDescription() != null)
                .flatMap(p -> p.projectDescription().stream())
                .map(CrossrefProjectDescription::description)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? Map.of() : Map.of("en", String.join("\n\n", values));
    }

    Optional<Organisation> grantFundingAgency(String doi, CrossrefFunding primaryFunding,
            List<CrossrefFunder> topLevelFunders) {
        return fundingMapper.grantFundingAgency(doi, primaryFunding, topLevelFunders);
    }

    Optional<Integer> fundedAmount(CrossrefProject project, CrossrefFunding funding) {
        return awardAmount(project, funding)
                .filter(amount -> amount.amount() != null)
                .map(amount -> amount.amount().intValue());
    }

    Optional<String> currency(CrossrefProject project, CrossrefFunding funding) {
        return awardAmount(project, funding).map(CrossrefAmount::currency);
    }

    private Optional<CrossrefAmount> awardAmount(CrossrefProject project, CrossrefFunding funding) {
        if (funding != null && funding.awardAmount() != null) {
            return Optional.of(funding.awardAmount());
        }
        return Optional.ofNullable(project != null ? project.awardAmount() : null);
    }

    Optional<GrantAllOfDuration> duration(CrossrefProject project) {
        if (project == null) {
            return Optional.empty();
        }
        String start = project.awardStart() != null ? project.awardStart().toIsoDate().orElse(null) : null;
        String end = project.awardEnd() != null ? project.awardEnd().toIsoDate().orElse(null) : null;
        return start == null && end == null ? Optional.empty() : Optional.of(new GrantAllOfDuration()
                .start(start).end(end));
    }

    Optional<String> website(CrossrefWork work) {
        return Optional.ofNullable(
                work.resource() != null && work.resource().primary() != null ? work.resource().primary().url() :
                        null);
    }
}
