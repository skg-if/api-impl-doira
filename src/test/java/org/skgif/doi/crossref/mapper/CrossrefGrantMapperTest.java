package org.skgif.doi.crossref.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.dto.CrossrefAmount;
import org.skgif.doi.crossref.dto.CrossrefDate;
import org.skgif.doi.crossref.dto.CrossrefFunding;
import org.skgif.doi.crossref.dto.CrossrefProject;
import org.skgif.doi.crossref.dto.CrossrefProjectDescription;
import org.skgif.doi.crossref.dto.CrossrefProjectTitle;
import org.skgif.doi.crossref.dto.CrossrefResource;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.GrantAllOfDuration;
import org.skgif.doi.util.LocalIdentifiers;

class CrossrefGrantMapperTest {

    /** The mapper under test. */
    private final CrossrefGrantMapper mapper =
            new CrossrefGrantMapper(new CrossrefFundingMapper(new LocalIdentifiers("https://doi.org/")));

    @Test
    void grantTitles_emptyProjectsReturnsEmptyMap() {
        assertThat(mapper.grantTitles(List.of())).isEmpty();
    }

    @Test
    void grantTitles_ignoresProjectWithNoProjectTitle() {
        assertThat(mapper.grantTitles(List.of(project(null, null)))).isEmpty();
    }

    @Test
    void grantTitles_concatenatesTitlesFromMultipleProjects() {
        CrossrefProject first = project(List.of(new CrossrefProjectTitle("Biocontainment")), null);
        CrossrefProject second = project(List.of(new CrossrefProjectTitle("Level 2")), null);

        assertThat(mapper.grantTitles(List.of(first, second))).isEqualTo(Map.of("en", "Biocontainment Level 2"));
    }

    @Test
    void grantAbstracts_emptyProjectsReturnsEmptyMap() {
        assertThat(mapper.grantAbstracts(List.of())).isEmpty();
    }

    @Test
    void grantAbstracts_ignoresProjectWithNoProjectDescription() {
        assertThat(mapper.grantAbstracts(List.of(project(null, null)))).isEmpty();
    }

    @Test
    void grantAbstracts_joinsMultipleProjectsWithBlankLine() {
        CrossrefProject first = project(null, List.of(new CrossrefProjectDescription("First part.")));
        CrossrefProject second = project(null, List.of(new CrossrefProjectDescription("Second part.")));

        assertThat(mapper.grantAbstracts(List.of(first, second)))
                .isEqualTo(Map.of("en", "First part.\n\nSecond part."));
    }

    @Test
    void fundedAmount_prefersFundingAwardAmountOverProject() {
        CrossrefProject projectWithAmount = amountProject(new CrossrefAmount(1.0, "EUR"));
        CrossrefFunding funding = new CrossrefFunding(null, new CrossrefAmount(2.0, "GBP"), null);

        final int expected = 2;
        assertThat(mapper.fundedAmount(projectWithAmount, funding)).contains(expected);
        assertThat(mapper.currency(projectWithAmount, funding)).contains("GBP");
    }

    @Test
    void fundedAmount_fallsBackToProjectAwardAmountWhenFundingHasNone() {
        final double projectAwardAmount = 3.0;
        final int expectedFundedAmount = 3;
        CrossrefProject projectWithAmount = amountProject(new CrossrefAmount(projectAwardAmount, "USD"));

        assertThat(mapper.fundedAmount(projectWithAmount, null)).contains(expectedFundedAmount);
        assertThat(mapper.currency(projectWithAmount, null)).contains("USD");
    }

    @Test
    void fundedAmount_nullWhenNeitherProjectNorFundingHasAnAmount() {
        CrossrefProject projectWithoutAmount = amountProject(null);

        assertThat(mapper.fundedAmount(projectWithoutAmount, null)).isEmpty();
        assertThat(mapper.currency(projectWithoutAmount, null)).isEmpty();
    }

    @Test
    void fundedAmount_nullWhenProjectIsNullAndFundingIsNull() {
        assertThat(mapper.fundedAmount(null, null)).isEmpty();
        assertThat(mapper.currency(null, null)).isEmpty();
    }

    @Test
    void fundedAmount_fallsBackToProjectWhenFundingHasNoAwardAmount() {
        final double projectAwardAmount = 3.0;
        final int expectedFundedAmount = 3;
        CrossrefProject projectWithAmount = amountProject(new CrossrefAmount(projectAwardAmount, "USD"));
        CrossrefFunding fundingWithoutAmount = new CrossrefFunding("scheme-x", null, null);

        assertThat(mapper.fundedAmount(projectWithAmount, fundingWithoutAmount)).contains(expectedFundedAmount);
        assertThat(mapper.currency(projectWithAmount, fundingWithoutAmount)).contains("USD");
    }

    @Test
    void duration_nullWhenProjectIsNull() {
        assertThat(mapper.duration(null)).isEmpty();
    }

    @Test
    void duration_nullWhenNeitherStartNorEndIsPresent() {
        assertThat(mapper.duration(durationProject(null, null))).isEmpty();
    }

    @Test
    void duration_onlyStartPresent() {
        GrantAllOfDuration duration = mapper.duration(durationProject(isoDate("2019-11-01"), null)).orElseThrow();

        assertThat(duration.getStart()).isEqualTo("2019-11-01");
        assertThat(duration.getEnd()).isNull();
    }

    @Test
    void duration_onlyEndPresent() {
        GrantAllOfDuration duration = mapper.duration(durationProject(null, isoDate("2024-10-31"))).orElseThrow();

        assertThat(duration.getStart()).isNull();
        assertThat(duration.getEnd()).isEqualTo("2024-10-31");
    }

    @Test
    void duration_bothStartAndEndPresent() {
        GrantAllOfDuration duration =
                mapper.duration(durationProject(isoDate("2019-11-01"), isoDate("2024-10-31"))).orElseThrow();

        assertThat(duration.getStart()).isEqualTo("2019-11-01");
        assertThat(duration.getEnd()).isEqualTo("2024-10-31");
    }

    @Test
    void website_nullWhenWorkHasNoResource() {
        assertThat(mapper.website(workWithResource(null))).isEmpty();
    }

    @Test
    void website_nullWhenResourceHasNoPrimary() {
        assertThat(mapper.website(workWithResource(new CrossrefResource(null)))).isEmpty();
    }

    @Test
    void website_returnsPrimaryResourceUrl() {
        CrossrefResource resource = new CrossrefResource(new CrossrefResource.Primary("https://example.org/grant"));

        assertThat(mapper.website(workWithResource(resource))).contains("https://example.org/grant");
    }

    private static CrossrefProject project(@Nullable List<CrossrefProjectTitle> titles,
            @Nullable List<CrossrefProjectDescription> descriptions) {
        return new CrossrefProject(titles, descriptions, null, null, null, null, null, null);
    }

    private static CrossrefProject amountProject(@Nullable CrossrefAmount awardAmount) {
        return new CrossrefProject(null, null, null, null, awardAmount, null, null, null);
    }

    private static CrossrefProject durationProject(@Nullable CrossrefDate awardStart,
            @Nullable CrossrefDate awardEnd) {
        return new CrossrefProject(null, null, null, null, null, awardStart, awardEnd, null);
    }

    private static CrossrefDate isoDate(String iso) {
        List<Integer> parts = Arrays.stream(iso.split("-")).map(Integer::parseInt).toList();
        return new CrossrefDate(List.of(parts));
    }

    private static CrossrefWork workWithResource(@Nullable CrossrefResource resource) {
        return new CrossrefWork(
                null, // doi
                null, // url
                null, // type
                null, // publisher
                null, // title
                null, // subtitle
                null, // containerTitle
                null, // page
                null, // volume
                null, // issue
                null, // abstractText
                null, // subject
                null, // issn
                null, // author
                null, // editor
                null, // funder
                null, // license
                null, // reference
                null, // relation
                null, // issued
                null, // created
                null, // deposited
                null, // publishedPrint
                null, // publishedOnline
                null, // accepted
                null, // updateTo
                null, // award
                null, // project
                resource);
    }
}
