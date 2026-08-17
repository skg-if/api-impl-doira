package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
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
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;

/**
 * The Crossref-provider sibling of {@code DataCiteGrantsResourceTest}, at the separate {@code
 * /crossref/grants} path. Golden JSON-LD output regression tests live in
 * {@link GrantsGoldenTest}.
 */
@QuarkusTest
class CrossrefGrantsResourceTest {

    private static final String BASE = "/skg-if/api";

    @InjectMock
    @RestClient
    CrossrefClient crossrefClient;

    private CrossrefWorkResponse loadFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkResponse.class);
        }
    }

    @Test
    void getGrantById_returnsSkgIfEnvelope() throws IOException {
        when(crossrefClient.getWork("10.35802/218300")).thenReturn(loadFixture("crossref-grant.json"));

        given()
                .when().get(BASE + "/crossref/grants/10.35802/218300")
                .then()
                .statusCode(200)
                .body("@context", Matchers.hasSize(3))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.35802/218300"))
                .body("'@graph'[0].entity_type", Matchers.equalTo("grant"))
                .body("'@graph'[0].identifiers[0].scheme", Matchers.equalTo("doi"))
                .body("'@graph'[0].funding_agency.name", Matchers.equalTo("Wellcome Trust"));
    }

    @Test
    void getGrantById_notFound_returns404WithRfc7807Error() {
        when(crossrefClient.getWork(any())).thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/crossref/grants/10.9999/does-not-exist")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"))
                .body("title", Matchers.equalTo("NOT_FOUND"));
    }

    /**
     * A non-grant-type Crossref DOI (a product) requested via {@code /crossref/grants} must
     * 404, pointing the caller at {@code /crossref/products} instead.
     */
    @Test
    void getGrantById_productDoi_returns404PointingToProducts() throws IOException {
        when(crossrefClient.getWork("10.1038/nature12373"))
                .thenReturn(loadFixture("crossref-journal-article.json"));

        given()
                .when().get(BASE + "/crossref/grants/10.1038/nature12373")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"))
                .body("detail", Matchers.containsString("/crossref/products/10.1038/nature12373"));
    }

    @Test
    void getGrants_invalidFilter_returns422() {
        given()
                .when().get(BASE + "/crossref/grants?filter=bogus_filter:xyz")
                .then()
                .statusCode(422)
                .body("status", Matchers.equalTo("422"))
                .body("title", Matchers.equalTo("INVALID_FILTER"));
    }

    @Test
    void getGrants_onlyIncludesGrantTypeInCrossrefFilter() {
        CrossrefWorkListResponse listResponse =
                new CrossrefWorkListResponse(null, new CrossrefWorkListResponse.Message(0, List.of()));
        when(crossrefClient.listWorks(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(listResponse);

        given().when().get(BASE + "/crossref/grants").then().statusCode(200);

        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        verify(crossrefClient).listWorks(filterCaptor.capture(), any(), any(), anyInt(), anyInt(), any());
        org.junit.jupiter.api.Assertions.assertTrue(filterCaptor.getValue().contains("type:grant"));
    }

    @Test
    void getGrants_returnsSearchEnvelope() throws IOException {
        CrossrefWorkListResponse listResponse = new CrossrefWorkListResponse(null,
                new CrossrefWorkListResponse.Message(1, List.of(loadFixture("crossref-grant.json").message())));
        when(crossrefClient.listWorks(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/crossref/grants?page_size=5")
                .then()
                .statusCode(200)
                .body("meta.entity_type", Matchers.equalTo("search_result_page"))
                .body("meta.part_of.total_items", Matchers.equalTo(1))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.35802/218300"))
                .body("meta.api_items[0].local_identifier", Matchers.equalTo("https://doi.org/10.35802/218300"))
                .body("meta.api_items[0].urls[0].href",
                        Matchers.equalTo("http://localhost:8081/skg-if/api/crossref/grants/10.35802/218300"));
    }
}
