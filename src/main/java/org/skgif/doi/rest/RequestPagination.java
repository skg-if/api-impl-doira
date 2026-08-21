package org.skgif.doi.rest;

/**
 * Query-param parsing shared by every provider's list endpoint (Crossref/DataCite
 * grants/products).
 */
final class RequestPagination {

    private RequestPagination() {
    }

    /**
     * @param page the {@code page} query param, or null for the first page
     * @return the parsed 1-based page number, defaulting to 1 for a null/non-positive/unparseable value
     */
    static int parsePage(String page) {
        if (page == null) {
            return 1;
        }
        try {
            int parsed = Integer.parseInt(page);
            return parsed > 0 ? parsed : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
