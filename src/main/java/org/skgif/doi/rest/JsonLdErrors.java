package org.skgif.doi.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.skgif.doi.generated.model.Error;

/**
 * Builds RFC 7807 error responses, shared by all four REST resource classes: {@code
 * DataCiteProductsResource}, {@code DataCiteGrantsResource}, {@code CrossrefProductsResource}, and
 * {@code CrossrefGrantsResource}.
 */
public final class JsonLdErrors {

    // 422 Unprocessable Entity has no jakarta.ws.rs.core.Response.Status constant.
    /** HTTP status code for 422 Unprocessable Entity. */
    private static final int UNPROCESSABLE_ENTITY_STATUS = 422;

    private JsonLdErrors() {
    }

    /**
     * Builds a 404 RFC 7807 error response for an entity that couldn't be found.
     *
     * @param detail human-readable detail explaining what wasn't found
     * @return a 404 response with an RFC 7807 {@code NOT_FOUND} error body
     */
    public static Response notFound(String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#NOT_FOUND")
                .title("NOT_FOUND")
                .status(String.valueOf(Response.Status.NOT_FOUND.getStatusCode()))
                .detail(detail);
        return Response.status(Response.Status.NOT_FOUND).entity(error).build();
    }

    /**
     * Builds a 422 RFC 7807 error response for a {@code filter} query string this API can't parse.
     *
     * @param uriInfo the current request URI, used as the error's {@code instance}
     * @param detail  human-readable detail explaining what was wrong with the filter
     * @return a 422 response with an RFC 7807 {@code INVALID_FILTER} error body
     */
    public static Response invalidFilter(UriInfo uriInfo, String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#INVALID_FILTER")
                .title("INVALID_FILTER")
                .status(String.valueOf(UNPROCESSABLE_ENTITY_STATUS))
                .detail(detail)
                .instance(uriInfo.getRequestUri().toString());
        return Response.status(UNPROCESSABLE_ENTITY_STATUS).entity(error).build();
    }
}
