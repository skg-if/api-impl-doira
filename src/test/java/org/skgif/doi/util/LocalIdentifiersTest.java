package org.skgif.doi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocalIdentifiersTest {

    private final LocalIdentifiers localIdentifiers = new LocalIdentifiers("https://doi.org/");

    @Test
    void toDoi_bareDoi_returnsUnchanged() {
        assertEquals("10.15151/esrf-es-2210534378", localIdentifiers.toDoi("10.15151/esrf-es-2210534378"));
    }

    @Test
    void toDoi_canonicalFullLocalIdentifier_stripsBaseUrl() {
        assertEquals("10.15151/esrf-es-2210534378",
                localIdentifiers.toDoi("https://doi.org/10.15151/esrf-es-2210534378"));
    }

    /**
     * Reproduces the value @PathParam actually receives when a client sends the full
     * local_identifier URL as a raw, unescaped path segment: Vert.x's own HTTP routing
     * collapses the literal "//" in the raw request path down to a single "/" before path-param
     * binding runs, so toDoi() sees "https:/doi.org/..." (one slash), not "https://doi.org/...".
     */
    @Test
    void toDoi_vertxCollapsedSingleSlashForm_stripsBaseUrl() {
        assertEquals("10.15151/esrf-es-2210534378",
                localIdentifiers.toDoi("https:/doi.org/10.15151/esrf-es-2210534378"));
    }

    @Test
    void toDoi_unrelatedValue_passesThroughUnchanged() {
        assertEquals("not-a-doi-or-url", localIdentifiers.toDoi("not-a-doi-or-url"));
    }

    @Test
    void toDoi_differentSchemeIsNotTolerated() {
        // Only the exact configured scheme (https) is tolerated - http is a different, valid
        // URL, not a collapsed variant of the configured https base URL, and must not be
        // silently treated as equivalent.
        String httpUrl = "http://doi.org/10.15151/esrf-es-2210534378";
        assertEquals(httpUrl, localIdentifiers.toDoi(httpUrl));
    }

    @Test
    void toFullLocalIdentifier_unaffectedByFix() {
        assertEquals("https://doi.org/10.15151/esrf-es-2210534378",
                localIdentifiers.toFullLocalIdentifier("10.15151/esrf-es-2210534378"));
    }
}
