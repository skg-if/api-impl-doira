package org.skgif.doi.rest.datacite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.spec.GrantFilterKeys;

final class DataCiteGrantFiltersTest {

    @Test
    void clauseBuilders_coverEveryGrantFilterKey() {
        assertThat(DataCiteGrantFilters.CLAUSE_BUILDERS.keySet()).containsExactlyInAnyOrder(GrantFilterKeys.values());
    }

    @MethodSource("equalityAndNullCases")
    @ParameterizedTest(name = "{0}")
    void toDataCiteQuery_returnsExpectedOrNull(String label, String input, String expected) {
        assertThat(DataCiteGrantFilters.toDataCiteQuery(input)).isEqualTo(Optional.ofNullable(expected));
    }

    private static Stream<Arguments> equalityAndNullCases() {
        return Stream.of(
                arguments("null returns null", null, null),
                arguments("blank returns null", "  ", null),
                arguments("identifiers value maps to doi clause", "identifiers.value:10.71707/r3sy-7371",
                        "doi:\"10.71707/r3sy-7371\""),
                arguments("identifiers scheme no-op for doi", "identifiers.scheme:doi", null),
                arguments("identifiers scheme zero match for other scheme", "identifiers.scheme:handle",
                        "doi:\"__no_match__\""),
                arguments("by family name searches both creators and contributors",
                        "contributions.by.family_name:Smith",
                        "(creators.familyName:\"Smith\" OR contributors.familyName:\"Smith\")"),
                arguments("by identifiers scheme orcid is no-op", "contributions.by.identifiers.scheme:orcid", null),
                arguments("by identifiers scheme ror is no-op", "contributions.by.identifiers.scheme:ror", null),
                arguments("by identifiers scheme zero match for other scheme",
                        "contributions.by.identifiers.scheme:isni", "doi:\"__no_match__\""),
                arguments("by identifiers value ORs orcid and ror forms across both roles",
                        "contributions.by.identifiers.value:018n2ja79",
                        "(creators.nameIdentifiers.nameIdentifier:\"https://orcid.org/018n2ja79\"" +
                                " OR contributors.nameIdentifiers.nameIdentifier:\"https://orcid.org/018n2ja79\"" +
                                " OR creators.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\"" +
                                " OR contributors.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\")"),
                arguments("declared affiliations name searches both roles",
                        "contributions.declared_affiliations.name:Brown University",
                        "(creators.affiliation.name:\"Brown University\" " +
                                "OR contributors.affiliation.name:\"Brown University\")"),
                arguments("beneficiaries name matches contributors name",
                        "beneficiaries.name:Atlas of Living Australia",
                        "contributors.name:\"Atlas of Living Australia\""),
                arguments("beneficiaries identifiers value adds ror prefix",
                        "beneficiaries.identifiers.value:018n2ja79",
                        "contributors.nameIdentifiers.nameIdentifier:\"https://ror.org/018n2ja79\""),
                arguments("beneficiaries identifiers scheme no-op for ror", "beneficiaries.identifiers.scheme:ror",
                        null),
                arguments("funding agency name matches creators name",
                        "funding_agency.name:Australian Research Data Commons",
                        "creators.name:\"Australian Research Data Commons\""),
                arguments("funding agency identifiers value adds ror prefix",
                        "funding_agency.identifiers.value:038sjwq14",
                        "creators.nameIdentifiers.nameIdentifier:\"https://ror.org/038sjwq14\""),
                arguments("funding agency identifiers scheme zero match for other scheme",
                        "funding_agency.identifiers.scheme:isni", "doi:\"__no_match__\""),
                arguments("search title passes through escaped", "cf.search.title:GraspOS", "GraspOS"));
    }

    @Test
    void toDataCiteQuery_unsupportedKey_throws() {
        // grant_number is deliberately NOT implemented - see DataCiteGrantFilters' class javadoc.
        assertThatThrownBy(() -> DataCiteGrantFilters.toDataCiteQuery("grant_number:101095129"))
                .isInstanceOf(FilterQuerySyntax.UnsupportedFilterException.class)
                .hasMessageContaining("grant_number");
    }

    @Test
    void toDataCiteQuery_malformedSegment_throws() {
        assertThatThrownBy(() -> DataCiteGrantFilters.toDataCiteQuery("contributions.by.family_name"))
                .isInstanceOf(FilterQuerySyntax.UnsupportedFilterException.class);
    }
}
