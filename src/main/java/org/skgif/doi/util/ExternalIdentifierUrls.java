package org.skgif.doi.util;

/**
 * Single source of truth for the external identifier base URLs (ORCID, ROR, DOI) used to build
 * and strip full local_identifier/URL forms across every provider's mapper and filter classes,
 * so the two never drift apart.
 */
public final class ExternalIdentifierUrls {

    public static final String ORCID_BASE_URL = "https://orcid.org/";
    public static final String ORCID_HTTP_BASE_URL = "http://orcid.org/";
    public static final String ROR_BASE_URL = "https://ror.org/";
    public static final String DOI_BASE_URL = "https://doi.org/";
    public static final String DOI_HTTP_BASE_URL = "http://doi.org/";

    private ExternalIdentifierUrls() {
    }
}
