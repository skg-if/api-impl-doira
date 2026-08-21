package org.skgif.doi.crossref.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossrefToSkgIfMapperDatesTest {

    /** Used to read the JSON fixture files this test maps. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Mocked Crossref REST client, unused directly but required by {@link #mapper}'s dependencies. */
    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    /** The mapper under test. */
    private final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"),
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty()));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return response.message();
        }
    }

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
