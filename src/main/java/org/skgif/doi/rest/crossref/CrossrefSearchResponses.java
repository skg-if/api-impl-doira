package org.skgif.doi.rest.crossref;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdSearchResponses;

/**
 * Adapts a Crossref {@code listWorks} response to {@link JsonLdSearchResponses#build}, shared by
 * {@code CrossrefGrantsResource#getGrants}/{@code CrossrefProductsResource#getProducts} - the two
 * differ only in which works to keep ({@code include}) and how to map a kept work to its SKG-IF
 * entity ({@code convert}).
 */
final class CrossrefSearchResponses {

    private CrossrefSearchResponses() {
    }

    /**
     * Assembles a Crossref list response into the shared JSON-LD search envelope.
     *
     * @param ctx      the calling resource's envelope-building dependencies
     * @param request  the per-request list parameters
     * @param offset   the zero-based offset of the first item on this page
     * @param response the raw Crossref list response to build the envelope from
     * @param include  which works from {@code response} to keep on this page
     * @param convert  maps a kept work to its SKG-IF entity
     * @param <T>      the SKG-IF entity type ({@code Grant} or {@code Product})
     * @return a 200 response with the assembled search-results envelope
     */
    static <T> Response build(JsonLdSearchResponses.EnvelopeContext ctx, JsonLdSearchResponses.ListRequest request,
            int offset, CrossrefWorkListResponse response, Predicate<CrossrefWork> include,
            Function<CrossrefWork, T> convert) {
        List<CrossrefWork> items = response.message() != null ?
                Optional.ofNullable(response.message().items()).orElseGet(List::of) :
                List.of();
        long totalResults = response.message() != null ? response.message().totalResults() : 0;
        boolean hasNext = offset + request.size() < totalResults;
        String contextBase = JsonLdContextBase.contextBaseFor(Optional.<String>empty(), ctx.sandboxBaseUrl(),
                ctx.fallbackContextBase());
        return JsonLdSearchResponses.build(ctx, request, items,
                work -> work.doi() != null && include.test(work) ? work.doi() : null, convert,
                new JsonLdSearchResponses.ProviderPage(totalResults, hasNext, contextBase));
    }
}
