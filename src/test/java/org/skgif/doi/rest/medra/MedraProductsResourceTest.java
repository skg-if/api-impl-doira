package org.skgif.doi.rest.medra;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.skgif.doi.medra.MedraClient;
import org.skgif.doi.rest.XmlFixtureResponses;

/**
 * The mEDRA-provider sibling of {@code CrossrefProductsResourceTest}/{@code
 * DataCiteProductsResourceTest}, at the separate {@code /medra/products} path - see {@code
 * MedraProductsResource}'s javadoc for why there is no list-endpoint counterpart here. Golden
 * JSON-LD output regression tests live in {@code ProductsGoldenTest}.
 */
@QuarkusTest
final class MedraProductsResourceTest {

    /** Base path this API is served under. */
    private static final String BASE = "/skg-if/api";

    /** HTTP 200 OK status code. */
    private static final int HTTP_OK = 200;
    /** HTTP 404 Not Found status code. */
    private static final int HTTP_NOT_FOUND = 404;
    /** Expected {@code @context} array size for a single-entity response. */
    private static final int EXPECTED_CONTEXT_SIZE = 3;

    /** Mocked mEDRA REST client, stubbed per test case. */
    @InjectMock
    @RestClient
    MedraClient medraClient;

    private Response okXmlResponse(String xmlResourceName) throws IOException {
        return XmlFixtureResponses.okXmlResponse(getClass(), xmlResourceName);
    }

    @Test
    void getProductById_returnsSkgIfEnvelope() throws IOException {
        Response xmlResponse = okXmlResponse("medra-mixed-name-shapes.xml");
        when(medraClient.getMetadata("10.19276/plinius.2019.01004")).thenReturn(xmlResponse);

        given()
                .when().get(BASE + "/medra/products/10.19276/plinius.2019.01004")
                .then()
                .statusCode(HTTP_OK)
                .body("@context", hasSize(EXPECTED_CONTEXT_SIZE))
                .body("'@graph'[0].local_identifier",
                        equalTo("https://doi.org/10.19276/plinius.2019.01004"))
                .body("'@graph'[0].product_type", equalTo("literature"))
                .body("'@graph'[0].identifiers[0].scheme", equalTo("doi"))
                .body("'@graph'[0].contributions[0].by.name", equalTo("Daniela D'Alessio"));
    }

    @Test
    void getProductById_noContributors_returnsProductWithNoContributions() throws IOException {
        Response xmlResponse = okXmlResponse("medra-no-contributors.xml");
        when(medraClient.getMetadata("10.1393/ncc/i2021-21084-7")).thenReturn(xmlResponse);

        given()
                .when().get(BASE + "/medra/products/10.1393/ncc/i2021-21084-7")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].local_identifier",
                        equalTo("https://doi.org/10.1393/ncc/i2021-21084-7"))
                .body("'@graph'[0]", not(hasKey("contributions")));
    }

    @Test
    void getProductById_nonOkStatusFromMedra_returns404WithRfc7807Error() {
        Response response = mock();
        when(response.getStatus()).thenReturn(HTTP_NOT_FOUND);
        when(medraClient.getMetadata("10.9999/does-not-exist")).thenReturn(response);

        given()
                .when().get(BASE + "/medra/products/10.9999/does-not-exist")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", equalTo("404"))
                .body("title", equalTo("NOT_FOUND"));
    }

    @Test
    void getProductById_unparseableXml_returns404WithRfc7807Error() {
        Response response = mock();
        when(response.getStatus()).thenReturn(HTTP_OK);
        when(response.readEntity(String.class)).thenReturn("<not-well-formed-onix");
        when(medraClient.getMetadata("10.19276/plinius.2019.01004")).thenReturn(response);

        given()
                .when().get(BASE + "/medra/products/10.19276/plinius.2019.01004")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", equalTo("404"));
    }

    @Test
    void getProductById_clientThrows_returns404WithRfc7807Error() {
        when(medraClient.getMetadata("10.9999/does-not-exist"))
                .thenThrow(new RuntimeException("connection refused"));

        given()
                .when().get(BASE + "/medra/products/10.9999/does-not-exist")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", equalTo("404"));
    }
}
