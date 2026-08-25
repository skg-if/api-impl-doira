package org.skgif.doi.crossref.mapper;

import static org.skgif.doi.util.SpotBugsSuppressions.BC_VACUOUS_INSTANCEOF;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.crossref.dto.CrossrefAmount;
import org.skgif.doi.crossref.dto.CrossrefDate;
import org.skgif.doi.crossref.dto.CrossrefFunder;
import org.skgif.doi.crossref.dto.CrossrefFunding;
import org.skgif.doi.crossref.dto.CrossrefProject;
import org.skgif.doi.crossref.dto.CrossrefProjectDescription;
import org.skgif.doi.crossref.dto.CrossrefProjectTitle;
import org.skgif.doi.crossref.dto.CrossrefResource;
import org.skgif.doi.crossref.dto.CrossrefResource.Primary;
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

    /** Resolves a grant's funder into an SKG-IF Organisation. */
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
        // Accessor-then-filter via method references rather than filter(p -> p.x() != null)
        // followed by flatMap(p -> p.x().stream()): the latter puts the guard in one synthetic
        // lambda and the dereference in another, which nullness analysis cannot connect.
        List<String> values = projects.stream()
                .map(CrossrefProject::projectTitle)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(CrossrefProjectTitle::title)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? Map.of() : Map.of("en", String.join(" ", values));
    }

    Map<String, String> grantAbstracts(List<CrossrefProject> projects) {
        List<String> values = projects.stream()
                .map(CrossrefProject::projectDescription)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(CrossrefProjectDescription::description)
                .filter(Objects::nonNull)
                .toList();
        return values.isEmpty() ? Map.of() : Map.of("en", String.join("\n\n", values));
    }

    Optional<Organisation> grantFundingAgency(@Nullable String doi, @Nullable CrossrefFunding primaryFunding,
            @Nullable List<CrossrefFunder> topLevelFunders) {
        return fundingMapper.grantFundingAgency(doi, primaryFunding, topLevelFunders);
    }

    Optional<Integer> fundedAmount(@Nullable CrossrefProject project, @Nullable CrossrefFunding funding) {
        // Optional.map already drops a null result, so mapping the accessor and the conversion
        // separately replaces the filter-then-dereference pair with the same behaviour.
        return awardAmount(project, funding)
                .map(CrossrefAmount::amount)
                .map(Double::intValue);
    }

    Optional<String> currency(@Nullable CrossrefProject project, @Nullable CrossrefFunding funding) {
        return awardAmount(project, funding).map(CrossrefAmount::currency);
    }

    private Optional<CrossrefAmount> awardAmount(@Nullable CrossrefProject project,
            @Nullable CrossrefFunding funding) {
        CrossrefAmount fundingAmount = funding != null ? funding.awardAmount() : null;
        if (fundingAmount != null) {
            return Optional.of(fundingAmount);
        }
        return Optional.ofNullable(project != null ? project.awardAmount() : null);
    }

    Optional<GrantAllOfDuration> duration(@Nullable CrossrefProject project) {
        if (project == null) {
            return Optional.empty();
        }
        CrossrefDate awardStart = project.awardStart();
        CrossrefDate awardEnd = project.awardEnd();
        String start = awardStart != null ? awardStart.toIsoDate().orElse(null) : null;
        String end = awardEnd != null ? awardEnd.toIsoDate().orElse(null) : null;
        return start == null && end == null ? Optional.empty() : Optional.of(new GrantAllOfDuration()
                .start(start).end(end));
    }

    @SuppressFBWarnings(value = BC_VACUOUS_INSTANCEOF, justification = "Record deconstruction pattern requires " +
            "naming the type at every nesting level even when statically redundant with the accessor's return " +
            "type - JEP 440/441 desugaring SpotBugs's bytecode analysis doesn't recognize")
    Optional<String> website(CrossrefWork work) {
        if (work.resource() instanceof CrossrefResource(Primary(String url))) {
            return Optional.of(url);
        }
        return Optional.empty();
    }
}
