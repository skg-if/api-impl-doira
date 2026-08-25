package org.skgif.doi.rest.datacite;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;

/**
 * Golden JSON-LD output regression tests live in {@code GrantsGoldenTest}.
 */
@QuarkusTest
class DataCiteGrantsResourceTest {

    /** Base path this API is served under. */
    private static final String BASE = "/skg-if/api";

    /** HTTP 200 OK status code. */
    private static final int HTTP_OK = 200;
    /** HTTP 404 Not Found status code. */
    private static final int HTTP_NOT_FOUND = 404;
    /** HTTP 422 Unprocessable Entity status code. */
    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;
    /** Expected {@code @context} array size for a single-entity response. */
    private static final int EXPECTED_CONTEXT_SIZE = 3;

    /** Mocked DataCite REST client, stubbed per test case. */
    @InjectMock
    @RestClient
    DataCiteClient dataCiteClient;

    private DataCiteDoiResponse loadFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            return objectMapper.readValue(in, DataCiteDoiResponse.class);
        }
    }

    @Test
    void getGrantById_returnsSkgIfEnvelope() throws IOException {
        when(dataCiteClient.getDoi("10.71707/r3sy-7371"))
                .thenReturn(loadFixture("datacite-award-r3sy-7371.json"));

        given()
                .when().get(BASE + "/datacite/grants/10.71707/r3sy-7371")
                .then()
                .statusCode(HTTP_OK)
                .body("@context", org.hamcrest.Matchers.hasSize(EXPECTED_CONTEXT_SIZE))
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.71707/r3sy-7371"))
                .body("'@graph'[0].entity_type", equalTo("grant"))
                .body("'@graph'[0].identifiers[0].scheme", equalTo("doi"))
                .body("'@graph'[0].funding_agency.name", equalTo("The Navigation Fund"))
                .body("'@graph'[0].beneficiaries[0].name", equalTo("Code for Science & Society"))
                // Guards against the generator's polymorphic @JsonTypeInfo on GrantContributionBy
                // overriding this with its @JsonTypeName ("GrantContribution_by") instead - see
                // SkgIfObjectMapperCustomizer's javadoc.
                .body("'@graph'[0].contributions[1].by.entity_type", equalTo("organisation"));
    }

    @Test
    void getGrantById_notFound_returns404WithRfc7807Error() {
        when(dataCiteClient.getDoi(any())).thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/datacite/grants/10.9999/does-not-exist")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", equalTo("404"))
                .body("title", equalTo("NOT_FOUND"));
    }

    /**
     * A non-Award DOI (a product) requested via {@code /datacite/grants} must 404, pointing the
     * caller at {@code /datacite/products} instead - the inverse of {@code
     * DataCiteProductsResourceTest#getProductById_awardDoi_returns404PointingToGrants}.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getGrantById_productDoi_returns404PointingToProducts() throws IOException {
        when(dataCiteClient.getDoi("10.15151/esrf-dc-2493599001"))
                .thenReturn(loadFixture("datacite-esrf-dc-2493599001.json"));

        given()
                .when().get(BASE + "/datacite/grants/10.15151/esrf-dc-2493599001")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", equalTo("404"))
                .body("detail", containsString("/datacite/products/10.15151/esrf-dc-2493599001"));
    }

    @Test
    void getGrants_invalidFilter_returns422() {
        given()
                .when().get(BASE + "/datacite/grants?filter=bogus_filter:xyz")
                .then()
                .statusCode(HTTP_UNPROCESSABLE_ENTITY)
                .body("status", equalTo("422"))
                .body("title", equalTo("INVALID_FILTER"));
    }

    @Test
    void getGrants_onlyIncludesAwardsInDataCiteQuery() {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse(
                java.util.List.of(), new DataCiteDoiListResponse.Meta(0, 0, 1), null);
        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        given().when().get(BASE + "/datacite/grants").then().statusCode(HTTP_OK);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataCiteClient).listDois(any(), queryCaptor.capture(), anyInt(), anyInt());
        assertThat(queryCaptor.getValue()).contains("types.resourceTypeGeneral:Award");
    }

    @Test
    void getGrants_returnsSearchEnvelope() throws IOException {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse(
                java.util.List.of(loadFixture("datacite-award-r3sy-7371.json").data()),
                new DataCiteDoiListResponse.Meta(1, 1, 1), null);

        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/datacite/grants?page_size=5")
                .then()
                .statusCode(HTTP_OK)
                .body("meta.entity_type", equalTo("search_result_page"))
                .body("meta.part_of.total_items", equalTo(1))
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.71707/r3sy-7371"))
                .body("meta.api_items[0].local_identifier", equalTo("https://doi.org/10.71707/r3sy-7371"))
                .body("meta.api_items[0].urls[0].href",
                        equalTo("http://localhost:8081/skg-if/api/datacite/grants/10.71707/r3sy-7371"));
    }
}
