package org.skgif.doi.rest.crossref;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdEnvelopes;
import org.skgif.doi.rest.JsonLdLinks;
import org.skgif.doi.rest.JsonLdMeta;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * Builds the JSON-LD search-results envelope for a Crossref {@code listWorks} response, shared by
 * {@code CrossrefGrantsResource#getGrants}/{@code CrossrefProductsResource#getProducts} - the two
 * differ only in which works to keep ({@code include}) and how to map a kept work to its SKG-IF
 * entity ({@code convert}).
 */
final class CrossrefSearchResponses {

    private CrossrefSearchResponses() {
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
    record EnvelopeContext(
            ObjectMapper objectMapper,
            String sandboxBaseUrl,
            String fallbackContextBase,
            LocalIdentifiers localIdentifiers) {
    }

    /**
     * The per-request list parameters, distinct per call unlike {@link EnvelopeContext}.
     *
     * @param uriInfo      the current request URI, used to build pagination/context links
     * @param resourcePath the calling resource's own base path (e.g. {@code /crossref/grants})
     * @param filter       the original SKG-IF {@code filter} query string, echoed into pagination links
     * @param pageNumber   the page number being served
     * @param size         the page size being served
     * @param offset       the zero-based offset of the first item on this page
     */
    record ListRequest(
            UriInfo uriInfo,
            String resourcePath,
            String filter,
            int pageNumber,
            int size,
            int offset) {
    }

    /**
     * @param ctx      the calling resource's envelope-building dependencies
     * @param request  the per-request list parameters
     * @param response the raw Crossref list response to build the envelope from
     * @param include  which works from {@code response} to keep on this page
     * @param convert  maps a kept work to its SKG-IF entity
     * @param <T>      the SKG-IF entity type ({@code Grant} or {@code Product})
     * @return a 200 response with the assembled search-results envelope
     */
    static <T> Response build(EnvelopeContext ctx, ListRequest request, CrossrefWorkListResponse response,
            Predicate<CrossrefWork> include, Function<CrossrefWork, T> convert) {
        List<T> entities = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        long totalResults = 0;
        if (response.message() != null) {
            totalResults = response.message().totalResults();
            for (CrossrefWork work : Optional.ofNullable(response.message().items()).orElse(List.of())) {
                if (work.doi() == null || !include.test(work)) {
                    continue;
                }
                entities.add(convert.apply(work));
                apiItems.add(JsonLdMeta.apiItem(ctx.localIdentifiers().toFullLocalIdentifier(work.doi()),
                        JsonLdLinks.selfLink(request.uriInfo(), request.resourcePath(), work.doi())));
            }
        }

        boolean hasNext = request.offset() + request.size() < totalResults;
        String contextBase = JsonLdContextBase.contextBaseFor(Optional.<String>empty(), ctx.sandboxBaseUrl(),
                ctx.fallbackContextBase());
        return JsonLdEnvelopes.searchResultsResponse(ctx.objectMapper(), contextBase,
                JsonLdMeta.searchMeta(new JsonLdMeta.SearchPage(request.uriInfo(), request.resourcePath(),
                        request.filter(), request.pageNumber(), request.size()), totalResults, hasNext, apiItems),
                entities);
    }
}
