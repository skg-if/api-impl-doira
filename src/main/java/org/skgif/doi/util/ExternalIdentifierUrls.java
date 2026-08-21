package org.skgif.doi.util;

/**
 * Single source of truth for the external identifier base URLs (ORCID, ROR, DOI) used to build
 * and strip full local_identifier/URL forms across every provider's mapper and filter classes,
 * so the two never drift apart.
 */
public final class ExternalIdentifierUrls {

    /** Base URL an ORCID identifier is resolved against (https). */
    public static final String ORCID_BASE_URL = "https://orcid.org/";
    /** Base URL an ORCID identifier is resolved against (http). */
    public static final String ORCID_HTTP_BASE_URL = "http://orcid.org/";
    /** Base URL a ROR identifier is resolved against. */
    public static final String ROR_BASE_URL = "https://ror.org/";
    /** Base URL a DOI is resolved against (https). */
    public static final String DOI_BASE_URL = "https://doi.org/";
    /** Base URL a DOI is resolved against (http). */
    public static final String DOI_HTTP_BASE_URL = "http://doi.org/";

    private ExternalIdentifierUrls() {
    }

    /**
     * Strips a leading {@link #ORCID_BASE_URL}/{@link #ORCID_HTTP_BASE_URL} prefix if present.
     *
     * @param value the ORCID value, bare or as a full https/http URL
     * @return the bare ORCID id, or value unchanged if it isn't a recognized ORCID URL
     */
    public static String stripOrcidUrl(String value) {
        if (value.startsWith(ORCID_BASE_URL)) {
            return value.substring(ORCID_BASE_URL.length());
        }
        if (value.startsWith(ORCID_HTTP_BASE_URL)) {
            return value.substring(ORCID_HTTP_BASE_URL.length());
        }
        return value;
    }

    /**
     * Strips a leading {@link #DOI_BASE_URL}/{@link #DOI_HTTP_BASE_URL} prefix if present.
     *
     * @param value the DOI value, bare or as a full https/http URL
     * @return the bare DOI, or value unchanged if it isn't a recognized DOI URL
     */
    public static String stripDoiUrl(String value) {
        if (value.startsWith(DOI_BASE_URL)) {
            return value.substring(DOI_BASE_URL.length());
        }
        if (value.startsWith(DOI_HTTP_BASE_URL)) {
            return value.substring(DOI_HTTP_BASE_URL.length());
        }
        return value;
    }

    /**
     * Strips a leading {@link #ROR_BASE_URL} prefix if present (ROR has no http variant in
     * practice).
     *
     * @param value the ROR value, bare or as a full https URL
     * @return the bare ROR id, or value unchanged if it isn't a recognized ROR URL
     */
    public static String stripRorUrl(String value) {
        return value.startsWith(ROR_BASE_URL) ? value.substring(ROR_BASE_URL.length()) : value;
    }
}
