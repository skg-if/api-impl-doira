package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;

/**
 * The Crossref-provider sibling of {@code ProductsResourceTest}, at the separate {@code
 * /crossref/products} path (see {@code CrossrefProductsResource}'s javadoc for why provider
 * selection is URL-driven rather than auto-detected).
 */
@QuarkusTest
class CrossrefProductsResourceTest {

    private static final String BASE = "/skg-if/api";

    private static final boolean REGENERATE_GOLDEN = Boolean.getBoolean("golden.regenerate");

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
    void getProductById_returnsSkgIfEnvelope() throws IOException {
        when(crossrefClient.getWork(eq("10.1038/nature12373")))
                .thenReturn(loadFixture("crossref-journal-article.json"));

        given()
                .when().get(BASE + "/crossref/products/10.1038/nature12373")
                .then()
                .statusCode(200)
                .body("@context", Matchers.hasSize(3))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].identifiers[0].scheme", Matchers.equalTo("doi"));
    }

    @Test
    void getProductById_notFound_returns404WithRfc7807Error() {
        when(crossrefClient.getWork(any())).thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/crossref/products/10.9999/does-not-exist")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"))
                .body("title", Matchers.equalTo("NOT_FOUND"));
    }

    /**
     * A Crossref {@code type: "grant"} DOI requested via {@code /crossref/products} must 404,
     * pointing the caller at {@code /crossref/grants} instead - mirrors {@code
     * ProductsResourceTest#getProductById_awardDoi_returns404PointingToGrants}.
     */
    @Test
    void getProductById_grantDoi_returns404PointingToGrants() throws IOException {
        when(crossrefClient.getWork(eq("10.35802/218300"))).thenReturn(loadFixture("crossref-grant.json"));

        given()
                .when().get(BASE + "/crossref/products/10.35802/218300")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"))
                .body("detail", Matchers.containsString("/crossref/grants/10.35802/218300"));
    }

    @Test
    void getProducts_invalidFilter_returns422() {
        given()
                .when().get(BASE + "/crossref/products?filter=bogus_filter:xyz")
                .then()
                .statusCode(422)
                .body("status", Matchers.equalTo("422"))
                .body("title", Matchers.equalTo("INVALID_FILTER"));
    }

    /**
     * Crossref's {@code filter=} has no negation operator (see {@code CrossrefFilters}), so
     * grant-type records are excluded from {@code /crossref/products} list results client-side
     * rather than via the query itself - this pins that exclusion.
     */
    @Test
    void getProducts_excludesGrantTypeRecordsFromResults() throws IOException {
        CrossrefWorkListResponse listResponse = new CrossrefWorkListResponse();
        listResponse.message = new CrossrefWorkListResponse.Message();
        listResponse.message.totalResults = 2;
        listResponse.message.items = List.of(
                loadFixture("crossref-journal-article.json").message,
                loadFixture("crossref-grant.json").message);
        when(crossrefClient.listWorks(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/crossref/products?page_size=5")
                .then()
                .statusCode(200)
                .body("'@graph'", Matchers.hasSize(1))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"));
    }

    @Test
    void getProducts_returnsSearchEnvelope() throws IOException {
        CrossrefWorkListResponse listResponse = new CrossrefWorkListResponse();
        listResponse.message = new CrossrefWorkListResponse.Message();
        listResponse.message.totalResults = 1;
        listResponse.message.items = List.of(loadFixture("crossref-journal-article.json").message);
        when(crossrefClient.listWorks(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/crossref/products?page_size=5")
                .then()
                .statusCode(200)
                .body("meta.entity_type", Matchers.equalTo("search_result_page"))
                .body("meta.part_of.total_items", Matchers.equalTo(1))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"))
                .body("meta.api_items[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"))
                .body("meta.api_items[0].urls[0].href",
                        Matchers.equalTo("http://localhost:8081/skg-if/api/crossref/products/10.1038/nature12373"));
    }

    /**
     * Full JSON-LD output regression test, mirroring {@code ProductsResourceTest}'s equivalent -
     * regenerate via {@code mvn test -Dtest=CrossrefProductsResourceTest -Dgolden.regenerate=true}
     * then review the diff before committing.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_natureArticle() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        when(crossrefClient.getWork(eq("10.1038/nature12373")))
                .thenReturn(loadFixture("crossref-journal-article.json"));

        String actualBody = given()
                .when().get(BASE + "/crossref/products/10.1038/nature12373")
                .then()
                .statusCode(200)
                .extract().asString();

        compareOrWriteGolden(objectMapper.readTree(actualBody), "expected/crossref-product-nature12373.json");
    }

    private void compareOrWriteGolden(JsonNode actual, String expectedResource) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        if (REGENERATE_GOLDEN) {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("src/test/resources/" + expectedResource), actual);
            return;
        }

        var expected = objectMapper.readTree(getClass().getClassLoader().getResourceAsStream(expectedResource));

        org.junit.jupiter.api.Assertions.assertEquals(expected, actual,
                "Actual JSON-LD output no longer matches " + expectedResource
                        + ". If this change is intentional: mvn test -Dtest=CrossrefProductsResourceTest"
                        + " -Dgolden.regenerate=true, then review the diff before committing.");
    }
}
