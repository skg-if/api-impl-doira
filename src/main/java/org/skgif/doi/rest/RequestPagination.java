package org.skgif.doi.rest;

/**
 * Query-param parsing shared by every provider's list endpoint (Crossref/DataCite
 * grants/products).
 */
public final class RequestPagination {

    private RequestPagination() {
    }

    /**
     * Parses the {@code page} query param defensively, so a bad value falls back to the first page.
     *
     * @param page the {@code page} query param, or null for the first page
     * @return the parsed 1-based page number, defaulting to 1 for a null/non-positive/unparseable value
     */
    public static int parsePage(String page) {
        if (page == null) {
            return 1;
        }
        try {
            int parsed = Integer.parseInt(page);
            return parsed > 0 ? parsed : 1;
        } catch (NumberFormatException _) {
            return 1;
        }
    }
}
