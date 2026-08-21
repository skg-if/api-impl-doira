package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Builds the JSON-LD search-results envelope from an already-paginated list of raw provider
 * items - the shared shape of every provider's list endpoint (Crossref's {@code getGrants}/{@code
 * getProducts}, DataCite's equivalents, ...), once each provider has extracted its own item list
 * and computed its own {@code total}/{@code hasNext}/{@code contextBase} from its own response
 * type. Provider-specific adapters (e.g. {@code CrossrefSearchResponses},
 * {@code DataCiteSearchResponses}) live in their own provider sub-package and delegate here.
 */
public final class JsonLdSearchResponses {

    private JsonLdSearchResponses() {
    }

    /**
     * The calling resource's own JSON-LD-envelope-building dependencies - identical across every
     * call from a given resource instance, unlike {@link ListRequest}.
     *
     * @param objectMapper        used to assemble the JSON-LD response envelope
     * @param sandboxBaseUrl      base URL of the sandbox environment, surfaced in error responses
     * @param fallbackContextBase fallback {@code @context} base URL used when not overridden per-request
     * @param localIdentifiers    resolves local identifiers to/from DOIs
     */
    public record EnvelopeContext(
            ObjectMapper objectMapper,
            String sandboxBaseUrl,
            String fallbackContextBase,
            LocalIdentifiers localIdentifiers) {
    }

    /**
     * The per-request list parameters, distinct per call unlike {@link EnvelopeContext}.
     *
     * @param uriInfo      the current request URI, used to build pagination/context links
     * @param resourcePath the calling resource's own base path (e.g. {@code /datacite/grants})
     * @param filter       the original SKG-IF {@code filter} query string, echoed into pagination links
     * @param pageNumber   the page number being served
     * @param size         the page size being served
     */
    public record ListRequest(
            UriInfo uriInfo,
            String resourcePath,
            String filter,
            int pageNumber,
            int size) {
    }

    /**
     * @param ctx         the calling resource's envelope-building dependencies
     * @param request     the per-request list parameters
     * @param items       the raw provider items already extracted from this page of the provider's response
     * @param doiOf       the item's DOI, or null to exclude it from the page (folds in each provider's own
     *                    "missing attributes"/"wrong type" skip check)
     * @param convert     maps a kept item to its SKG-IF entity
     * @param total       total number of matching results across all pages, per the provider's own response
     * @param hasNext     whether a further page exists, per the provider's own response
     * @param contextBase the {@code @context} base URL for this page, per the provider's own response
     * @param <I>         the raw provider item type (e.g. {@code CrossrefWork}, {@code DataCiteDoiData})
     * @param <T>         the SKG-IF entity type ({@code Grant} or {@code Product})
     * @return a 200 response with the assembled search-results envelope
     */
    public static <I, T> Response build(EnvelopeContext ctx, ListRequest request, List<I> items,
            Function<I, String> doiOf, Function<I, T> convert, long total, boolean hasNext, String contextBase) {
        List<T> entities = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        for (I item : items) {
            String doi = doiOf.apply(item);
            if (doi == null) {
                continue;
            }
            entities.add(convert.apply(item));
            apiItems.add(JsonLdMeta.apiItem(ctx.localIdentifiers().toFullLocalIdentifier(doi),
                    JsonLdLinks.selfLink(request.uriInfo(), request.resourcePath(), doi)));
        }
        return JsonLdEnvelopes.searchResultsResponse(ctx.objectMapper(), contextBase,
                JsonLdMeta.searchMeta(new JsonLdMeta.SearchPage(request.uriInfo(), request.resourcePath(),
                        request.filter(), request.pageNumber(), request.size()), total, hasNext, apiItems),
                entities);
    }
}
