package org.skgif.doi.rest;

import java.util.List;
import java.util.Optional;
import org.skgif.doi.datacite.dto.DataCiteDoiData;

/**
 * Derives the JSON-LD {@code @context}'s {@code @base} namespace, shared by all four REST
 * resource classes: {@link DataCiteProductsResource}, {@link DataCiteGrantsResource}, {@link
 * CrossrefProductsResource}, and {@link CrossrefGrantsResource}.
 */
final class JsonLdContextBase {

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
    static String contextBaseFor(DataCiteDoiData data, String sandboxBaseUrl, String fallbackContextBase) {
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
    static String contextBaseFor(List<DataCiteDoiData> items, String sandboxBaseUrl, String fallbackContextBase) {
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
    static String contextBaseFor(Optional<String> namespaceId, String sandboxBaseUrl, String fallbackContextBase) {
        return namespaceId.filter(id -> !id.isBlank())
                .map(id -> sandboxBaseUrl + id + "/")
                .orElse(fallbackContextBase);
    }

    private static Optional<String> clientId(DataCiteDoiData data) {
        if (data == null || data.relationships() == null || data.relationships().client() == null ||
                data.relationships().client().data() == null) {
            return Optional.empty();
        }
        String id = data.relationships().client().data().id();
        return Optional.ofNullable(id != null && !id.isBlank() ? id : null);
    }
}
