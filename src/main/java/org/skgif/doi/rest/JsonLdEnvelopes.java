package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.skgif.doi.generated.model.MetaSearch;
import org.skgif.doi.generated.model.MetaSingleEntity;

/**
 * Assembles the {@code @context}/{@code meta}/{@code @graph} JSON-LD envelope and wraps it in a
 * REST response, shared by all four REST resource classes: {@code DataCiteProductsResource},
 * {@code DataCiteGrantsResource}, {@code CrossrefProductsResource}, and {@code
 * CrossrefGrantsResource}.
 */
public final class JsonLdEnvelopes {

    /** {@code @context} entry for the SKG-IF data model vocabulary. */
    private static final String CTX_DATA_MODEL = "https://w3id.org/skg-if/context/1.1.0/skg-if.json";
    /** {@code @context} entry for the SKG-IF API-specific vocabulary. */
    private static final String CTX_API = "https://w3id.org/skg-if/context/1.0.0/skg-if-api.json";

    private JsonLdEnvelopes() {
    }

    static ObjectNode envelope(ObjectMapper objectMapper, String contextBase) {
        ArrayNode context = objectMapper.createArrayNode();
        context.add(CTX_DATA_MODEL);
        context.add(CTX_API);
        ObjectNode base = objectMapper.createObjectNode();
        base.put("@base", contextBase);
        context.add(base);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("@context", context);
        return root;
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
    public static Response singleEntityResponse(ObjectMapper objectMapper, String contextBase, MetaSingleEntity meta,
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
    public static Response searchResultsResponse(ObjectMapper objectMapper, String contextBase, MetaSearch meta,
            List<?> items) {
        ObjectNode root = envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        items.forEach(item -> graph.add(objectMapper.valueToTree(item)));
        root.set("@graph", graph);
        return Response.ok(root).build();
    }
}
