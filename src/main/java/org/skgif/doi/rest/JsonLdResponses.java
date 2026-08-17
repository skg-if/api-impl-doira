package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Optional;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.Error;
import org.skgif.doi.generated.model.Link;

/**
 * JSON-LD envelope, pagination-link and RFC 7807 error helpers shared by {@link
 * DataCiteProductsResource} and {@link DataCiteGrantsResource}.
 *
 * <p>Both hand-assemble their {@code @context}/{@code meta}/{@code @graph} envelope with Jackson
 * rather than the generated {@code ProductApi}/{@code GrantApi} interfaces (see {@code
 * DataCiteProductsResource}'s javadoc for why), so the mechanics are identical across the two
 * resources.
 */
final class JsonLdResponses {

    private static final String CTX_DATA_MODEL = "https://w3id.org/skg-if/context/1.1.0/skg-if.json";
    private static final String CTX_API = "https://w3id.org/skg-if/context/1.0.0/skg-if-api.json";
    // 422 Unprocessable Entity has no jakarta.ws.rs.core.Response.Status constant.
    private static final int UNPROCESSABLE_ENTITY_STATUS = 422;

    private JsonLdResponses() {
    }

    static ObjectNode envelope(ObjectMapper objectMapper, String contextBase) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode context = objectMapper.createArrayNode();
        context.add(CTX_DATA_MODEL);
        context.add(CTX_API);
        ObjectNode base = objectMapper.createObjectNode();
        base.put("@base", contextBase);
        context.add(base);
        root.set("@context", context);
        return root;
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
     * @param data the single-item DataCite DOI record to derive a client namespace from
     * @param sandboxBaseUrl the base URL to namespace under (with the client id appended)
     * @param fallbackContextBase the {@code @base} to use when no client id can be derived
     * @return the namespaced {@code @base}, or fallbackContextBase if data carries no client id
     */
    static String contextBaseFor(DataCiteDoiData data, String sandboxBaseUrl, String fallbackContextBase) {
        String clientId = clientId(data);
        return clientId != null ? sandboxBaseUrl + clientId + "/" : fallbackContextBase;
    }

    /**
     * List-endpoint variant of {@link #contextBaseFor(DataCiteDoiData, String, String)}: a
     * single JSON-LD document can only declare one {@code @base}, so this namespaces to the
     * first result's DataCite client - in practice every result on a page shares the same
     * client, since {@code datacite.prefix} scopes a deployment to one organisation.
     *
     * @param items the page of DataCite DOI records to derive a client namespace from
     * @param sandboxBaseUrl the base URL to namespace under (with the client id appended)
     * @param fallbackContextBase the {@code @base} to use when no client id can be derived
     * @return the namespaced {@code @base}, or fallbackContextBase if no item carries a client id
     */
    static String contextBaseFor(List<DataCiteDoiData> items, String sandboxBaseUrl, String fallbackContextBase) {
        if (items == null) {
            return fallbackContextBase;
        }
        return items.stream()
                .map(JsonLdResponses::clientId)
                .filter(id -> id != null)
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
     * @param namespaceId the provider-specific namespace id, if any
     * @param sandboxBaseUrl the base URL to namespace under (with the namespace id appended)
     * @param fallbackContextBase the {@code @base} to use when namespaceId is absent/blank
     * @return the namespaced {@code @base}, or fallbackContextBase if namespaceId is absent/blank
     */
    static String contextBaseFor(Optional<String> namespaceId, String sandboxBaseUrl, String fallbackContextBase) {
        return namespaceId.filter(id -> !id.isBlank())
                .map(id -> sandboxBaseUrl + id + "/")
                .orElse(fallbackContextBase);
    }

    private static String clientId(DataCiteDoiData data) {
        if (data == null || data.relationships() == null || data.relationships().client() == null
                || data.relationships().client().data() == null) {
            return null;
        }
        String id = data.relationships().client().data().id();
        return id != null && !id.isBlank() ? id : null;
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

    /**
     * @param entityLocalIdentifier the entity's own local_identifier (matches the corresponding
     *     @graph[i].local_identifier) - per the spec's own worked examples, this is NOT the API
     *     URL; the API URL only appears in {@code urls[].href}.
     * @param apiSelfHref this API's own resolvable URL for the entity
     * @return an ApiItem referencing entityLocalIdentifier, with apiSelfHref as its self link
     */
    static ApiItem apiItem(String entityLocalIdentifier, String apiSelfHref) {
        return new ApiItem()
                .localIdentifier(entityLocalIdentifier)
                .urls(List.of(new Link().entityType("link").rel("self").href(apiSelfHref)));
    }

    static Response notFound(String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#NOT_FOUND")
                .title("NOT_FOUND")
                .status(String.valueOf(Response.Status.NOT_FOUND.getStatusCode()))
                .detail(detail);
        return Response.status(Response.Status.NOT_FOUND).entity(error).build();
    }

    static Response invalidFilter(UriInfo uriInfo, String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#INVALID_FILTER")
                .title("INVALID_FILTER")
                .status(String.valueOf(UNPROCESSABLE_ENTITY_STATUS))
                .detail(detail)
                .instance(uriInfo.getRequestUri().toString());
        return Response.status(UNPROCESSABLE_ENTITY_STATUS).entity(error).build();
    }
}
