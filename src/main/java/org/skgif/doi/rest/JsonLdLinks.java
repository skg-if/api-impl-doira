package org.skgif.doi.rest;

import jakarta.ws.rs.core.UriInfo;

/**
 * Builds self/pagination/collection URLs for JSON-LD responses, shared by all four REST resource
 * classes: {@link DataCiteProductsResource}, {@link DataCiteGrantsResource}, {@link
 * CrossrefProductsResource}, and {@link CrossrefGrantsResource}.
 */
final class JsonLdLinks {

    private JsonLdLinks() {
    }

    static String selfLink(UriInfo uriInfo, String resourcePath, String doi) {
        return baseUri(uriInfo) + resourcePath + "/" + doi;
    }

    static String pageLink(UriInfo uriInfo, String resourcePath, String filter, int pageNumber, int pageSize) {
        String base = baseUri(uriInfo) + resourcePath + "?";
        if (filter != null && !filter.isBlank()) {
            base += "filter=" + filter + "&";
        }
        return base + "page=" + pageNumber + "&page_size=" + pageSize;
    }

    static String collectionLink(UriInfo uriInfo, String resourcePath, String filter) {
        String base = baseUri(uriInfo) + resourcePath;
        return (filter != null && !filter.isBlank()) ? base + "?filter=" + filter : base;
    }

    private static String baseUri(UriInfo uriInfo) {
        return uriInfo.getBaseUri().toString().replaceAll("/$", "");
    }
}
