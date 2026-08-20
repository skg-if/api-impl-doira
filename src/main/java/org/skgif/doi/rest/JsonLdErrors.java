package org.skgif.doi.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.skgif.doi.generated.model.Error;

/**
 * Builds RFC 7807 error responses, shared by all four REST resource classes: {@link
 * DataCiteProductsResource}, {@link DataCiteGrantsResource}, {@link CrossrefProductsResource}, and
 * {@link CrossrefGrantsResource}.
 */
final class JsonLdErrors {

    // 422 Unprocessable Entity has no jakarta.ws.rs.core.Response.Status constant.
    private static final int UNPROCESSABLE_ENTITY_STATUS = 422;

    private JsonLdErrors() {
    }

    static Response notFound(String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#NOT_FOUND")
                .title("NOT_FOUND")
                .status(String.valueOf(Response.Status.NOT_FOUND.getStatusCode()))
                .detail(detail);
        return Response.status(Response.Status.NOT_FOUND).entity(error).build();
    }

    static Response invalidFilter(UriInfo uriInfo, String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#INVALID_FILTER")
                .title("INVALID_FILTER")
                .status(String.valueOf(UNPROCESSABLE_ENTITY_STATUS))
                .detail(detail)
                .instance(uriInfo.getRequestUri().toString());
        return Response.status(UNPROCESSABLE_ENTITY_STATUS).entity(error).build();
    }
}
