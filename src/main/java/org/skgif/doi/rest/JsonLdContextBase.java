package org.skgif.doi.rest;

import static org.skgif.doi.util.SpotBugsSuppressions.BC_VACUOUS_INSTANCEOF;
import static org.skgif.doi.util.SpotBugsSuppressions.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE;
import static org.skgif.doi.util.SpotBugsSuppressions.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteRelationships;
import org.skgif.doi.datacite.dto.DataCiteRelationships.ClientData;
import org.skgif.doi.datacite.dto.DataCiteRelationships.ClientRelationship;

/**
 * Derives the JSON-LD {@code @context}'s {@code @base} namespace, shared by all four REST
 * resource classes: {@code DataCiteProductsResource}, {@code DataCiteGrantsResource}, {@code
 * CrossrefProductsResource}, and {@code CrossrefGrantsResource}.
 */
public final class JsonLdContextBase {

    private JsonLdContextBase() {
    }

    /**
     * Namespaces the JSON-LD {@code @base} to the DataCite client that registered the DOI (e.g.
     * {@code relationships.client.data.id == "inist.esrf"} becomes {@code
     * <sandboxBaseUrl>inist.esrf/}), so on-the-fly identifiers minted for entities without a
     * stable id of their own (see {@code MapperTextUtils#otf}) resolve into that client's
     * own namespace rather than always the deployment's default. Falls back to {@code
     * fallbackContextBase} when the DOI carries no client relationship (e.g. malformed/partial
     * DataCite data).
     *
     * @param data                the single-item DataCite DOI record to derive a client namespace from
     * @param sandboxBaseUrl      the base URL to namespace under (with the client id appended)
     * @param fallbackContextBase the {@code @base} to use when no client id can be derived
     * @return the namespaced {@code @base}, or fallbackContextBase if data carries no client id
     */
    public static String contextBaseFor(DataCiteDoiData data, String sandboxBaseUrl, String fallbackContextBase) {
        return clientId(data).map(id -> sandboxBaseUrl + id + "/").orElse(fallbackContextBase);
    }

    /**
     * List-endpoint variant of {@link #contextBaseFor(DataCiteDoiData, String, String)}: a
     * single JSON-LD document can only declare one {@code @base}, so this namespaces to the
     * first result's DataCite client - in practice every result on a page shares the same
     * client, since {@code datacite.prefix} scopes a deployment to one organisation.
     *
     * @param items               the page of DataCite DOI records to derive a client namespace from
     * @param sandboxBaseUrl      the base URL to namespace under (with the client id appended)
     * @param fallbackContextBase the {@code @base} to use when no client id can be derived
     * @return the namespaced {@code @base}, or fallbackContextBase if no item carries a client id
     */
    public static String contextBaseFor(@Nullable List<DataCiteDoiData> items, String sandboxBaseUrl,
            String fallbackContextBase) {
        if (items == null) {
            return fallbackContextBase;
        }
        return items.stream()
                .map(JsonLdContextBase::clientId)
                .flatMap(Optional::stream)
                .findFirst()
                .map(id -> sandboxBaseUrl + id + "/")
                .orElse(fallbackContextBase);
    }

    /**
     * Provider-agnostic variant of {@link #contextBaseFor(DataCiteDoiData, String, String)},
     * for providers with no DataCite-shaped namespace concept of their own (e.g. Crossref, which
     * has no equivalent to {@code relationships.client.data.id} mapped yet - see {@code
     * CrossrefProductsResource}/{@code CrossrefGrantsResource}, which always pass {@code
     * Optional.empty()} here).
     *
     * @param namespaceId         the provider-specific namespace id, if any
     * @param sandboxBaseUrl      the base URL to namespace under (with the namespace id appended)
     * @param fallbackContextBase the {@code @base} to use when namespaceId is absent/blank
     * @return the namespaced {@code @base}, or fallbackContextBase if namespaceId is absent/blank
     */
    public static String contextBaseFor(Optional<String> namespaceId, String sandboxBaseUrl,
            String fallbackContextBase) {
        return namespaceId.filter(id -> !id.isBlank())
                .map(id -> sandboxBaseUrl + id + "/")
                .orElse(fallbackContextBase);
    }

    @SuppressFBWarnings(value = {BC_VACUOUS_INSTANCEOF, NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE},
            justification = "Record deconstruction pattern requires naming the type at every nesting level even " +
                    "when statically redundant (JEP 440/441 desugaring SpotBugs's bytecode analysis doesn't " +
                    "recognize) - " + SPOTBUGS_REGISTER)
    private static Optional<String> clientId(DataCiteDoiData data) {
        if (data == null) {
            return Optional.empty();
        }
        // Nested record pattern instead of a null check per level: a record pattern does not
        // match a null component, so relationships/client/data being absent - and id itself
        // being null - all fall through to the empty result without naming each case.
        if (data.relationships() instanceof DataCiteRelationships(ClientRelationship(ClientData(String id))) &&
                !id.isBlank()) {
            return Optional.of(id);
        }
        return Optional.empty();
    }
}
