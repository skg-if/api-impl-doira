package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.Error;
import org.skgif.doi.generated.model.Link;

/**
 * JSON-LD envelope, pagination-link and RFC 7807 error helpers shared by {@link
 * ProductsResource} and {@link GrantsResource} - both hand-assemble their {@code
 * @context}/{@code meta}/{@code @graph} envelope with Jackson rather than the generated
 * {@code ProductApi}/{@code GrantApi} interfaces (see {@code ProductsResource}'s javadoc for
 * why), so the mechanics are identical across the two resources.
 */
final class JsonLdResponses {

    private static final String CTX_DATA_MODEL = "https://w3id.org/skg-if/context/1.1.0/skg-if.json";
    private static final String CTX_API = "https://w3id.org/skg-if/context/1.0.0/skg-if-api.json";

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

    static String selfLink(UriInfo uriInfo, String resourcePath, String doi) {
        return baseUri(uriInfo) + resourcePath + "/" + doi;
    }

    static String pageLink(UriInfo uriInfo, String resourcePath, String filter, int pageNumber, int pageSize) {
        StringBuilder sb = new StringBuilder(baseUri(uriInfo) + resourcePath + "?");
        if (filter != null && !filter.isBlank()) {
            sb.append("filter=").append(filter).append('&');
        }
        sb.append("page=").append(pageNumber).append("&page_size=").append(pageSize);
        return sb.toString();
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
                .status("404")
                .detail(detail);
        return Response.status(Response.Status.NOT_FOUND).entity(error).build();
    }

    static Response invalidFilter(UriInfo uriInfo, String detail) {
        Error error = new Error()
                .type("https://skg-if.github.io/api/errors#INVALID_FILTER")
                .title("INVALID_FILTER")
                .status("422")
                .detail(detail)
                .instance(uriInfo.getRequestUri().toString());
        return Response.status(422).entity(error).build();
    }
}
