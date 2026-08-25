package org.skgif.doi.datacite.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDate;
import org.skgif.doi.generated.model.Product;

class DataCiteToSkgIfMapperDatesTest extends DataCiteToSkgIfMapperTestBase {

    private static DataCiteAttributes withLifecycleDates(DataCiteAttributes attributes, @Nullable String created,
            @Nullable String registered, @Nullable String updated, @Nullable String published) {
        return new DataCiteAttributes(
                attributes.doi(), attributes.titles(), attributes.creators(), attributes.contributors(),
                attributes.publisher(), attributes.publicationYear(), attributes.subjects(), attributes.dates(),
                created, registered, updated, published,
                attributes.language(), attributes.types(), attributes.rightsList(), attributes.descriptions(),
                attributes.relatedIdentifiers(), attributes.fundingReferences(), attributes.version(),
                attributes.url());
    }

    @Test
    void fallsBackToTopLevelAttributesWhenDatesArrayIsEmpty() throws IOException {
        // "dates": [] (not absent, but genuinely empty) - unlike every other fixture, which has
        // at least one date. Every DataCite record still carries the system-generated
        // created/registered/updated/published attributes though, and those are the only
        // real-world source for creation/deposit/modified/publication in practice: no fixture's
        // dates[] ever has a Created/Submitted/Updated entry.
        var attributes = readFixture("datacite-thesis-crossref-funder-id-4342.json");

        Product product = mapper.toProduct(attributes);

        var dates = product.getManifestations().getFirst().getDates();
        assertThat(dates.getCreation()).isEqualTo(List.of(attributes.created()));
        assertThat(dates.getDeposit()).isEqualTo(List.of(attributes.registered()));
        assertThat(dates.getModified()).isEqualTo(List.of(attributes.updated()));
        assertThat(dates.getPublication()).isEqualTo(List.of(attributes.published()));
    }

    @Test
    void doesNotFabricateManifestationDatesWhenNoDateSourceExistsAtAll() throws IOException {
        var attributes = withLifecycleDates(
                readFixture("datacite-thesis-crossref-funder-id-4342.json"), null, null, null, null);

        Product product = mapper.toProduct(attributes);

        assertThat(product.getManifestations().getFirst().getDates()).isNull();
    }

    @Test
    void explicitDatesEntryWinsOverTopLevelAttributeFallback() throws IOException {
        var attributes = readFixture("datacite-esrf-dc-2493599001.json");
        var created = new DataCiteDate("2020-01-01", "Created");
        Objects.requireNonNull(attributes.dates()).add(created);
        assertThat(created.date()).isNotEqualTo(attributes.created());

        Product product = mapper.toProduct(attributes);

        assertThat(product.getManifestations().getFirst().getDates().getCreation())
                .isEqualTo(List.of(created.date()));
    }

    @Test
    void dropsUnrecognizedDateTypesLikeCoverage() throws IOException {
        // "Coverage" is a real DataCite 4.7 dateType (the temporal span a resource's *content*
        // covers, not an event in the resource's own lifecycle) with no SKG-IF equivalent - it
        // must be silently dropped, same as "Other", and must not by itself trigger a non-null
        // dates object.
        var attributes = withLifecycleDates(
                readFixture("datacite-thesis-crossref-funder-id-4342.json"), null, null, null, null);
        var coverage = new DataCiteDate("1990/2000", "Coverage");
        Objects.requireNonNull(attributes.dates()).add(coverage);

        Product product = mapper.toProduct(attributes);

        assertThat(product.getManifestations().getFirst().getDates()).isNull();
    }

    @Test
    void mapsAvailableToEmbargoWhenItDiffersFromEveryOtherRecordDate() throws IOException {
        // datacite-esrf-es-2210534378.json is a genuine embargo case: Collected 2025-09-05,
        // Issued 2028, Available 2028-09-06 - none of those coincide, so Available is a real
        // embargo end date.
        Product product = mapFixture("datacite-esrf-es-2210534378.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertThat(dates.getEmbargo()).isEqualTo(List.of("2028-09-06"));
        assertThat(dates.getAccess()).isNull();
    }

    @Test
    void dropsAvailableWhenItMatchesAnotherRecordDateOnTheSameDay() throws IOException {
        // datacite-dataset-funder-no-identifier-e449e75a.json has a single Available date,
        // 2024-05-07, which is the same day as the top-level created/registered timestamps
        // (2024-05-07T10:07:27.000Z) - that's "published and immediately available," not an
        // embargo, so it must be dropped rather than surfacing as access or embargo.
        Product product = mapFixture("datacite-dataset-funder-no-identifier-e449e75a.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertThat(dates.getEmbargo()).isNull();
        assertThat(dates.getAccess()).isNull();
    }
}
