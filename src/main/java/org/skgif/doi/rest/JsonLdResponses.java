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
import org.skgif.doi.generated.model.MetaSearch;
import org.skgif.doi.generated.model.MetaSearchPartOf;
import org.skgif.doi.generated.model.MetaSingleEntity;
import org.skgif.doi.generated.model.SearchResultPage;

/**
 * JSON-LD envelope, pagination-link and RFC 7807 error helpers shared by all four REST resource
 * classes: {@link DataCiteProductsResource}, {@link DataCiteGrantsResource}, {@link
 * CrossrefProductsResource}, and {@link CrossrefGrantsResource}.
 *
 * <p>All four hand-assemble their {@code @context}/{@code meta}/{@code @graph} envelope with
 * Jackson rather than the generated {@code ProductApi}/{@code GrantApi} interfaces (see {@code
 * DataCiteProductsResource}'s javadoc for why), so the mechanics are identical across all of them.
 */
final class JsonLdResponses {

    private static final String CTX_DATA_MODEL = "https://w3id.org/skg-if/context/1.1.0/skg-if.json";
    private static final String CTX_API = "https://w3id.org/skg-if/context/1.0.0/skg-if-api.json";
    // 422 Unprocessable Entity has no jakarta.ws.rs.core.Response.Status constant.
    private static final int UNPROCESSABLE_ENTITY_STATUS = 422;
    private static final int FIRST_PAGE_NUMBER = 1;

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
                .map(JsonLdResponses::clientId)
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
     *                              @graph[i].local_identifier) - per the spec's own worked examples, this is NOT the
     *                              API
     *                              URL; the API URL only appears in {@code urls[].href}.
     * @param apiSelfHref           this API's own resolvable URL for the entity
     * @return an ApiItem referencing entityLocalIdentifier, with apiSelfHref as its self link
     */
    static ApiItem apiItem(String entityLocalIdentifier, String apiSelfHref) {
        return new ApiItem()
                .localIdentifier(entityLocalIdentifier)
                .urls(List.of(new Link().entityType("link").rel("self").href(apiSelfHref)));
    }

    /**
     * Builds the single-entity meta block - the shared shape of every provider's {@code
     * get*ById} endpoint.
     *
     * @param selfHref this API's own resolvable URL for the entity
     * @return the single-entity meta block
     */
    static MetaSingleEntity singleEntityMeta(String selfHref) {
        return new MetaSingleEntity()
                .localIdentifier(selfHref)
                .entityType(MetaSingleEntity.EntityTypeEnum.SINGLE_ENTITY);
    }

    /**
     * The page-identifying context a search-results meta block is built from - bundled into one
     * record since {@link #searchMeta} would otherwise take more parameters than checkstyle's
     * ParameterNumber limit allows.
     *
     * @param uriInfo      the current request URI, used to build pagination/context links
     * @param resourcePath the resource's own base path (e.g. {@code /datacite/products})
     * @param filter       the SKG-IF {@code filter} query string this page was fetched with
     * @param pageNumber   the page number this meta block describes
     * @param size         results per page
     */
    record SearchPage(
            UriInfo uriInfo,
            String resourcePath,
            String filter,
            int pageNumber,
            int size) {
    }

    /**
     * Builds the search-results meta block (self link, {@code apiItems}, and conditional
     * next/prev-page links) - the shared shape of every provider's {@code get*s} list endpoint.
     * Only the "is there a next page" check differs by provider's own pagination shape (Crossref:
     * {@code offset + size < totalResults}; DataCite: {@code pageNumber < response.meta().totalPages()}),
     * so callers compute {@code hasNext} themselves and pass it in.
     *
     * @param page         the page-identifying context (request URI, resource path, filter, page number/size)
     * @param totalResults the total number of matching results across all pages
     * @param hasNext      whether a next page exists
     * @param apiItems     the page's {@code apiItems}, one per returned entity
     * @return the search-results meta block
     */
    static MetaSearch searchMeta(SearchPage page, long totalResults, boolean hasNext, List<ApiItem> apiItems) {
        UriInfo uriInfo = page.uriInfo();
        String resourcePath = page.resourcePath();
        String filter = page.filter();
        int pageNumber = page.pageNumber();
        int size = page.size();
        String selfPageHref = pageLink(uriInfo, resourcePath, filter, pageNumber, size);
        MetaSearch meta = new MetaSearch()
                .localIdentifier(selfPageHref)
                .entityType(MetaSearch.EntityTypeEnum.SEARCH_RESULT_PAGE)
                .apiItems(apiItems);
        if (hasNext) {
            meta.nextPage(new SearchResultPage()
                    .localIdentifier(pageLink(uriInfo, resourcePath, filter, pageNumber + 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        if (pageNumber > FIRST_PAGE_NUMBER) {
            meta.prevPage(new SearchResultPage()
                    .localIdentifier(pageLink(uriInfo, resourcePath, filter, pageNumber - 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        meta.partOf(new MetaSearchPartOf()
                .localIdentifier(collectionLink(uriInfo, resourcePath, filter))
                .entityType(MetaSearchPartOf.EntityTypeEnum.SEARCH_RESULT)
                .totalItems((int) totalResults));
        return meta;
    }

    /**
     * Builds the full JSON-LD envelope for a single-entity response (@context/meta/@graph with one
     * item) and wraps it in a 200 response - the shared shape of every provider's
     * {@code get*ById} endpoint.
     *
     * @param objectMapper used to serialize meta/entity into the envelope
     * @param contextBase  the {@code @base} to namespace this envelope under
     * @param meta         the single-entity meta block
     * @param entity       the SKG-IF entity (e.g. {@code Product}/{@code Grant}) to place in {@code @graph}
     * @return a 200 response with the assembled envelope
     */
    static Response singleEntityResponse(ObjectMapper objectMapper, String contextBase, MetaSingleEntity meta,
            Object entity) {
        ObjectNode root = envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        graph.add(objectMapper.valueToTree(entity));
        root.set("@graph", graph);
        return Response.ok(root).build();
    }

    /**
     * Builds the full JSON-LD envelope for a search-results response (@context/meta/@graph with a
     * page of items) and wraps it in a 200 response - the shared shape of every provider's
     * {@code get*s} list endpoint.
     *
     * @param objectMapper used to serialize meta/items into the envelope
     * @param contextBase  the {@code @base} to namespace this envelope under
     * @param meta         the search-results meta block
     * @param items        the page of SKG-IF entities (e.g. {@code Product}/{@code Grant}) to place in {@code @graph}
     * @return a 200 response with the assembled envelope
     */
    static Response searchResultsResponse(ObjectMapper objectMapper, String contextBase, MetaSearch meta,
            List<?> items) {
        ObjectNode root = envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        items.forEach(item -> graph.add(objectMapper.valueToTree(item)));
        root.set("@graph", graph);
        return Response.ok(root).build();
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
