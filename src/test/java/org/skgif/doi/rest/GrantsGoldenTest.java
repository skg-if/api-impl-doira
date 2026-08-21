package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;

/**
 * Full JSON-LD output regression tests for {@code /datacite/grants} and {@code
 * /crossref/grants}: the actual response body must exactly match (structurally) the
 * corresponding checked-in document under {@code src/test/resources/expected/}.
 *
 * <p>Regenerate the golden JSON-LD files instead of asserting against them - e.g. after an
 * intentional mapper change: {@code mvn test -Dtest=GrantsGoldenTest -Dgolden.regenerate=true}
 * Then {@code git diff} the {@code expected/} files to review exactly what changed before
 * committing.
 */
@QuarkusTest
class GrantsGoldenTest {

    /** Base path this API is served under. */
    private static final String BASE = "/skg-if/api";

    /** Whether to overwrite the checked-in golden files instead of asserting against them. */
    private static final boolean REGENERATE_GOLDEN = Boolean.getBoolean("golden.regenerate");

    /** HTTP 200 OK status code. */
    private static final int HTTP_OK = 200;

    /** Mocked DataCite REST client, stubbed per test case. */
    @InjectMock
    @RestClient
    DataCiteClient dataCiteClient;

    /** Mocked Crossref REST client, stubbed per test case. */
    @InjectMock
    @RestClient
    CrossrefClient crossrefClient;

    private DataCiteDoiResponse loadDataCiteFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, DataCiteDoiResponse.class);
        }
    }

    private CrossrefWorkResponse loadCrossrefFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkResponse.class);
        }
    }

    /**
     * The actual response body must exactly match (structurally) the checked-in document under
     * {@code expected/datacite-award-r3sy-7371-out.json} - a real Award DOI from The Navigation
     * Fund, chosen because it exercises both contribution shapes in one real-world record: a
     * ROR-bearing organisational contributor (Code for Science & Society, also a beneficiary) and
     * an ORCID-bearing personal contributor (the project leader).
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getGrantById_matchesExpectedJsonLd_r3sy7371() throws IOException {
        when(dataCiteClient.getDoi("10.71707/r3sy-7371"))
                .thenReturn(loadDataCiteFixture("datacite-award-r3sy-7371.json"));

        String actualBody = given()
                .when().get(BASE + "/datacite/grants/10.71707/r3sy-7371")
                .then()
                .statusCode(HTTP_OK)
                .extract().asString();

        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), "expected/datacite-award-r3sy-7371-out.json");
    }

    @Test
    void getGrantById_matchesExpectedJsonLd_wellcomeGrant() throws IOException {
        when(crossrefClient.getWork("10.35802/218300")).thenReturn(loadCrossrefFixture("crossref-grant.json"));

        String actualBody = given()
                .when().get(BASE + "/crossref/grants/10.35802/218300")
                .then()
                .statusCode(HTTP_OK)
                .extract().asString();

        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), "expected/crossref-grant-out.json");
    }

    private void compareOrWriteGolden(JsonNode actual, String expectedResource) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        if (REGENERATE_GOLDEN) {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("src/test/resources/" + expectedResource), actual);
            return;
        }

        var expected = objectMapper.readTree(getClass().getClassLoader().getResourceAsStream(expectedResource));

        assertThat(actual)
                .as("Actual JSON-LD output no longer matches " + expectedResource +
                        ". If this change is intentional: mvn test -Dtest=GrantsGoldenTest" +
                        " -Dgolden.regenerate=true, then review the diff before committing.")
                .isEqualTo(expected);
    }
}
