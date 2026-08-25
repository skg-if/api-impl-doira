package org.skgif.doi.rest.crossref;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.spec.GrantFilterKeys;
import org.skgif.doi.spec.ProductFilterKeys;
import org.skgif.doi.util.ExternalIdentifierUrls;

final class CrossrefFiltersTest {

    /** Expected Crossref query clause guaranteed to match no record. */
    private static final String NO_MATCH_CLAUSE = "doi:__no_match__";

    @MethodSource("productsFilterClauseCases")
    @ParameterizedTest(name = "{0}")
    void toProductsQuery_matchesExpectedFilterClause(String label, String input, String expectedClause) {
        assertThat(CrossrefFilters.toProductsQuery(input).filter()).isEqualTo(expectedClause);
    }

    static Stream<Arguments> productsFilterClauseCases() {
        String orcidUrl = ExternalIdentifierUrls.ORCID_BASE_URL + "0000-0002-1008-0687";
        String doiUrl = "https://doi.org/10.15151/esrf-dc-2493599001";
        return Stream.of(
                arguments("null filter has no clause", null, null),
                arguments("blank filter has no clause", "   ", null),
                arguments("product type research data maps to dataset", ProductFilterKeys.PRODUCT_TYPE.key() +
                        ":research data", "type:dataset"),
                arguments("product type unrecognized value has no match", ProductFilterKeys.PRODUCT_TYPE.key() +
                        ":bogus", NO_MATCH_CLAUSE),
                // Crossref has no software-specific type at all (see CrossrefTypeMapping's class
                // javadoc) - research software can never match any Crossref record via this filter.
                arguments("product type research software has no match on crossref", ProductFilterKeys.PRODUCT_TYPE
                        .key() + ":research software", NO_MATCH_CLAUSE),
                arguments("identifiers id strips full doi url prefix", ProductFilterKeys.IDENTIFIERS_ID.key() + ":" +
                        doiUrl, "doi:10.15151/esrf-dc-2493599001"),
                arguments("identifiers id passes bare doi through unchanged", ProductFilterKeys.IDENTIFIERS_ID.key() +
                        ":10.15151/esrf-dc-2493599001", "doi:10.15151/esrf-dc-2493599001"),
                arguments("identifiers scheme is a no-op for doi", ProductFilterKeys.IDENTIFIERS_SCHEME.key() + ":doi",
                        null),
                arguments("identifiers scheme zero match for other scheme", ProductFilterKeys.IDENTIFIERS_SCHEME.key() +
                        ":pmid", NO_MATCH_CLAUSE),
                arguments("contributions by identifiers id adds orcid prefix and strips orcid url",
                        ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_ID.key() + ":" + orcidUrl,
                        "orcid:0000-0002-1008-0687"),
                arguments("cf contributions orcid produces the same clause as identifiers id",
                        ProductFilterKeys.CF_CONTRIBUTIONS_ORCID.key() + ":0000-0002-1008-0687",
                        "orcid:0000-0002-1008-0687"),
                arguments("contributions by identifiers scheme is a no-op for orcid",
                        ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key() + ":orcid", null),
                arguments("contributions by identifiers scheme zero match for other scheme",
                        ProductFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key() + ":isni", NO_MATCH_CLAUSE),
                arguments("funding grant number", ProductFilterKeys.FUNDING_GRANT_NUMBER.key() + ":MX-2738",
                        "award.number:MX-2738"),
                arguments("combines multiple filters with a comma", ProductFilterKeys.IDENTIFIERS_ID.key() +
                        ":10.15151/esrf-dc-2493599001," + ProductFilterKeys.PRODUCT_TYPE.key() + ":research data",
                        "doi:10.15151/esrf-dc-2493599001,type:dataset"));
    }

    @Test
    void toProductsQuery_searchTitle_setsQueryTitleNotFilter() {
        CrossrefFilters.ParsedFilter parsed =
                CrossrefFilters.toProductsQuery(ProductFilterKeys.CF_SEARCH_TITLE.key() + ":gravitational waves");

        assertThat(parsed.filter()).isNull();
        assertThat(parsed.queryTitle()).isEqualTo("gravitational waves");
        assertThat(parsed.queryBibliographic()).isNull();
    }

    @Test
    void toProductsQuery_searchTitleAbstract_setsQueryBibliographicNotFilter() {
        CrossrefFilters.ParsedFilter parsed = CrossrefFilters
                .toProductsQuery(ProductFilterKeys.CF_SEARCH_TITLE_ABSTRACT.key() + ":neutron stars");

        assertThat(parsed.filter()).isNull();
        assertThat(parsed.queryBibliographic()).isEqualTo("neutron stars");
        assertThat(parsed.queryTitle()).isNull();
    }

    @Test
    void toProductsQuery_unsupportedFilter_throws() {
        // contributions.by.family_name is supported on the DataCite side (see
        // DataCiteProductFiltersTest) but Crossref has no equivalent facet - a stable choice for
        // an "unsupported here" filter key.
        assertThatThrownBy(() -> CrossrefFilters
                .toProductsQuery(ProductFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME.key() + ":Choiniere"))
                .isInstanceOf(FilterQuerySyntax.UnsupportedFilterException.class)
                .hasMessageContaining(ProductFilterKeys.CONTRIBUTIONS_BY_FAMILY_NAME.key());
    }

    @MethodSource("grantsFilterClauseCases")
    @ParameterizedTest(name = "{0}")
    void toGrantsQuery_matchesExpectedFilterClause(String label, String input, String expectedClause) {
        assertThat(CrossrefFilters.toGrantsQuery(input).filter()).isEqualTo(expectedClause);
    }

    static Stream<Arguments> grantsFilterClauseCases() {
        String orcidUrl = ExternalIdentifierUrls.ORCID_BASE_URL + "0000-0001-9773-0023";
        return Stream.of(
                arguments("null filter has no clause", null, null),
                arguments("identifiers value strips full doi url prefix", GrantFilterKeys.IDENTIFIERS_VALUE.key() +
                        ":https://doi.org/10.35802/218300", "doi:10.35802/218300"),
                arguments("identifiers scheme is a no-op for doi", GrantFilterKeys.IDENTIFIERS_SCHEME.key() + ":doi",
                        null),
                arguments("identifiers scheme zero match for other scheme", GrantFilterKeys.IDENTIFIERS_SCHEME.key() +
                        ":isni", NO_MATCH_CLAUSE),
                arguments("contributions by identifiers value strips orcid url",
                        GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_VALUE.key() + ":" + orcidUrl,
                        "orcid:0000-0001-9773-0023"),
                // Grant contributions can be organisational (ror) too, but Crossref's "orcid"
                // filter only ever matches a person - see CrossrefFilters.toGrantClause.
                arguments("contributions by identifiers scheme no-op for orcid",
                        GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key() + ":orcid", null),
                arguments("contributions by identifiers scheme no-op for ror too",
                        GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key() + ":ror", null),
                arguments("contributions by identifiers scheme zero match for any other scheme",
                        GrantFilterKeys.CONTRIBUTIONS_BY_IDENTIFIERS_SCHEME.key() + ":isni", NO_MATCH_CLAUSE),
                arguments("funding agency identifiers value", GrantFilterKeys.FUNDING_AGENCY_IDENTIFIERS_VALUE.key() +
                        ":10.13039/100010269", "award.funder:10.13039/100010269"));
    }

    @Test
    void toGrantsQuery_searchTitle_setsQueryTitleNotFilter() {
        CrossrefFilters.ParsedFilter parsed =
                CrossrefFilters.toGrantsQuery(GrantFilterKeys.CF_SEARCH_TITLE.key() + ":biocontainment");

        assertThat(parsed.filter()).isNull();
        assertThat(parsed.queryTitle()).isEqualTo("biocontainment");
    }

    @Test
    void toGrantsQuery_searchTitleAbstract_setsQueryBibliographicNotFilter() {
        CrossrefFilters.ParsedFilter parsed =
                CrossrefFilters.toGrantsQuery(GrantFilterKeys.CF_SEARCH_TITLE_ABSTRACT.key() + ":cell sorter");

        assertThat(parsed.filter()).isNull();
        assertThat(parsed.queryBibliographic()).isEqualTo("cell sorter");
        assertThat(parsed.queryTitle()).isNull();
    }

    @Test
    void toGrantsQuery_unsupportedFilter_throws() {
        assertThatThrownBy(
                () -> CrossrefFilters.toGrantsQuery(GrantFilterKeys.BENEFICIARIES_NAME.key() + ":Cambridge"))
                .isInstanceOf(FilterQuerySyntax.UnsupportedFilterException.class)
                .hasMessageContaining(GrantFilterKeys.BENEFICIARIES_NAME.key());
    }

    @Test
    void productClauseBuilders_matchSupportedProductFilterKeys() {
        Set<ProductFilterKeys> expected = CrossrefFilters.PRODUCT_SUPPORTED.stream()
                .map(ProductFilterKeys::fromKey)
                .collect(toUnmodifiableSet());

        assertThat(expected).hasSameElementsAs(CrossrefFilters.PRODUCT_CLAUSE_BUILDERS.keySet());
    }

    @Test
    void grantClauseBuilders_matchSupportedGrantFilterKeys() {
        Set<GrantFilterKeys> expected = CrossrefFilters.GRANT_SUPPORTED.stream()
                .map(GrantFilterKeys::fromKey)
                .collect(toUnmodifiableSet());

        assertThat(expected).hasSameElementsAs(CrossrefFilters.GRANT_CLAUSE_BUILDERS.keySet());
    }
}
