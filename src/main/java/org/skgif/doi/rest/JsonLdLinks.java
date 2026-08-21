package org.skgif.doi.rest;

import jakarta.ws.rs.core.UriInfo;

/**
 * Builds self/pagination/collection URLs for JSON-LD responses, shared by all four REST resource
 * classes: {@code DataCiteProductsResource}, {@code DataCiteGrantsResource}, {@code
 * CrossrefProductsResource}, and {@code CrossrefGrantsResource}.
 */
public final class JsonLdLinks {

    private JsonLdLinks() {
    }

    /**
     * Builds this API's own resolvable self URL for a single entity.
     *
     * @param uriInfo      the current request URI, used to derive this API's base URL
     * @param resourcePath the resource's own base path (e.g. {@code /datacite/products})
     * @param doi          the entity's DOI
     * @return the self URL, e.g. {@code <base>/datacite/products/<doi>}
     */
    public static String selfLink(UriInfo uriInfo, String resourcePath, String doi) {
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
