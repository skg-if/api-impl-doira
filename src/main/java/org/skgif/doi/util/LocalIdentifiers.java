package org.skgif.doi.util;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * SKG-IF local_identifier scheme for products and grants: every entity this API serves
 * already has a permanent DataCite DOI, so per the SKG-IF API doc's "DOI-based" option, the
 * DOI itself is used as local_identifier - no separate slug/id mapping. The bare form is the
 * DOI, the full form is the DOI resolver URL ({@code https://doi.org/<doi>}).
 */
@ApplicationScoped
public class LocalIdentifiers {

    private static final String SCHEME_SEPARATOR = "://";

    private final String baseUrl;

    /**
     * Tolerates the configured {@code baseUrl} with the "://" after its scheme collapsed to a
     * single slash. Vert.x's own HTTP routing normalizes the raw request path - collapsing any
     * run of consecutive slashes down to exactly one - before {@code @PathParam} binding ever
     * sees the value (see {@code io.vertx.core.http.impl.HttpUtils#removeDots}). So a client
     * that sends the full local_identifier as a raw, unescaped path segment (e.g. pasted into a
     * browser or curl without percent-encoding) has "https://doi.org/..." arrive here as
     * "https:/doi.org/...". Derived from {@code baseUrl} itself (not hardcoded to doi.org) so it
     * tracks {@code skgif.local-identifier.base-url} if that config ever changes. Deliberately
     * does NOT generalize beyond the configured scheme (no case-insensitivity, no tolerating
     * "http://" when the config says "https://") - only the exact configured scheme is ever
     * accepted, just with 1-or-more slashes instead of a hardcoded two.
     */
    private final Pattern collapsedBaseUrlPrefix;

    /**
     * @param baseUrl the configured local_identifier base URL (e.g. {@code https://doi.org/})
     */
    public LocalIdentifiers(@ConfigProperty(name = "skgif.local-identifier.base-url") String baseUrl) {
        this.baseUrl = baseUrl;
        this.collapsedBaseUrlPrefix = buildCollapsedBaseUrlPrefix(baseUrl);
    }

    private static Pattern buildCollapsedBaseUrlPrefix(String baseUrl) {
        int schemeEnd = baseUrl.indexOf(SCHEME_SEPARATOR);
        if (schemeEnd == -1) {
            return null;
        }
        String scheme = baseUrl.substring(0, schemeEnd + 1); // e.g. "https:"
        String afterAuthoritySlashes = baseUrl.substring(schemeEnd + SCHEME_SEPARATOR.length()); // e.g. "doi.org/"
        return Pattern.compile(Pattern.quote(scheme) + "/{1,}" + Pattern.quote(afterAuthoritySlashes));
    }

    /**
     * Turns an incoming {@code local_identifier} path parameter - either the full
     * {@code https://doi.org/...} form (MUST resolve) or the bare DOI (SHOULD resolve) -
     * into the plain DOI used to call DataCite.
     *
     * @param pathParam the incoming local_identifier path parameter, full or bare form
     * @return the plain DOI
     */
    public String toDoi(String pathParam) {
        if (pathParam.startsWith(baseUrl)) {
            return pathParam.substring(baseUrl.length());
        }
        if (collapsedBaseUrlPrefix != null) {
            Matcher matcher = collapsedBaseUrlPrefix.matcher(pathParam);
            if (matcher.lookingAt()) {
                return pathParam.substring(matcher.end());
            }
        }
        return pathParam;
    }

    /**
     * The full, dereferenceable local_identifier form for a given DOI.
     *
     * @param doi the plain DOI
     * @return the full local_identifier ({@code baseUrl + doi})
     */
    public String toFullLocalIdentifier(String doi) {
        return baseUrl + doi;
    }
}
