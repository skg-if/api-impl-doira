package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.skgif.doi.medra.MedraClient;

/**
 * The mEDRA-provider sibling of {@code CrossrefProductsResourceTest}/{@code
 * DataCiteProductsResourceTest}, at the separate {@code /medra/products} path - see {@code
 * MedraProductsResource}'s javadoc for why there is no list-endpoint counterpart here. Golden
 * JSON-LD output regression tests live in {@link ProductsGoldenTest}.
 */
@QuarkusTest
class MedraProductsResourceTest {

    private static final String BASE = "/skg-if/api";

    @InjectMock
    @RestClient
    MedraClient medraClient;

    private String loadRawResource(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** See {@code CrossrefProductsResourceTest#okXmlResponse}'s javadoc for why this must be built as its own statement. */
    private Response okXmlResponse(String xmlResourceName) throws IOException {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn(loadRawResource(xmlResourceName));
        return response;
    }

    @Test
    void getProductById_returnsSkgIfEnvelope() throws IOException {
        Response xmlResponse = okXmlResponse("medra-mixed-name-shapes.xml");
        when(medraClient.getMetadata("10.19276/plinius.2019.01004")).thenReturn(xmlResponse);

        given()
                .when().get(BASE + "/medra/products/10.19276/plinius.2019.01004")
                .then()
                .statusCode(200)
                .body("@context", Matchers.hasSize(3))
                .body("'@graph'[0].local_identifier",
                        Matchers.equalTo("https://doi.org/10.19276/plinius.2019.01004"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].identifiers[0].scheme", Matchers.equalTo("doi"))
                .body("'@graph'[0].contributions[0].by.name", Matchers.equalTo("Daniela D'Alessio"));
    }

    @Test
    void getProductById_noContributors_returnsProductWithNoContributions() throws IOException {
        Response xmlResponse = okXmlResponse("medra-no-contributors.xml");
        when(medraClient.getMetadata("10.1393/ncc/i2021-21084-7")).thenReturn(xmlResponse);

        given()
                .when().get(BASE + "/medra/products/10.1393/ncc/i2021-21084-7")
                .then()
                .statusCode(200)
                .body("'@graph'[0].local_identifier",
                        Matchers.equalTo("https://doi.org/10.1393/ncc/i2021-21084-7"))
                .body("'@graph'[0]", Matchers.not(Matchers.hasKey("contributions")));
    }

    @Test
    void getProductById_nonOkStatusFromMedra_returns404WithRfc7807Error() {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(404);
        when(medraClient.getMetadata("10.9999/does-not-exist")).thenReturn(response);

        given()
                .when().get(BASE + "/medra/products/10.9999/does-not-exist")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"))
                .body("title", Matchers.equalTo("NOT_FOUND"));
    }

    @Test
    void getProductById_unparseableXml_returns404WithRfc7807Error() {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn("<not-well-formed-onix");
        when(medraClient.getMetadata("10.19276/plinius.2019.01004")).thenReturn(response);

        given()
                .when().get(BASE + "/medra/products/10.19276/plinius.2019.01004")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"));
    }

    @Test
    void getProductById_clientThrows_returns404WithRfc7807Error() {
        when(medraClient.getMetadata("10.9999/does-not-exist"))
                .thenThrow(new RuntimeException("connection refused"));

        given()
                .when().get(BASE + "/medra/products/10.9999/does-not-exist")
                .then()
                .statusCode(404)
                .body("status", Matchers.equalTo("404"));
    }
}
