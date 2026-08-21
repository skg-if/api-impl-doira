package org.skgif.doi.crossref.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.skgif.doi.generated.model.Product;

class CrossrefToSkgIfMapperDatesTest extends CrossrefToSkgIfMapperTestBase {

    @Test
    void mapsDepositedIntoBothDepositAndModified() throws IOException {
        // Crossref documents `deposited` as "date on which the work metadata was most recently
        // updated" - the same field feeds both SKG-IF dates, not just `deposit`.
        Product product = mapFixture("crossref-journal-article.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertThat(dates.getDeposit()).isEqualTo(List.of("2023-05-18"));
        assertThat(dates.getModified()).isEqualTo(List.of("2023-05-18"));
    }

    @Test
    void mapsUpdateToCorrectionAndRetractionDates() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-update-to.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertThat(dates.getCorrection()).isEqualTo(List.of("2021-03-05"));
        assertThat(dates.getRetraction()).isEqualTo(List.of("2022-06-20"));
    }

    @Test
    void ignoresUnrecognizedUpdateToType() throws IOException {
        // The fixture also carries an "erratum" entry - Crossref's update-to[].type isn't
        // exhaustively documented (only "correction"/"retraction" are confirmed), so any other
        // value must be ignored rather than guessed at.
        Product product = mapFixture("crossref-journal-article-with-update-to.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertThat(dates.getCorrection()).hasSize(1);
        assertThat(dates.getRetraction()).hasSize(1);
    }
}
