package org.skgif.doi.rest.datacite;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Function;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdSearchResponses;

/**
 * Adapts a DataCite {@code listDois} response to {@link JsonLdSearchResponses#build}, shared by
 * {@code DataCiteGrantsResource#getGrants}/{@code DataCiteProductsResource#getProducts} - the two
 * differ only in how a kept DOI record is mapped to its SKG-IF entity ({@code convert}); which
 * records to keep is already handled by the Lucene query itself (award vs. non-award), unlike
 * Crossref where it's a client-side filter.
 */
final class DataCiteSearchResponses {

    private DataCiteSearchResponses() {
    }

    /**
     * Assembles a DataCite list response into the shared JSON-LD search envelope.
     *
     * @param ctx      the calling resource's envelope-building dependencies
     * @param request  the per-request list parameters
     * @param response the raw DataCite list response to build the envelope from
     * @param convert  maps a kept DOI record's attributes to its SKG-IF entity
     * @param <T>      the SKG-IF entity type ({@code Grant} or {@code Product})
     * @return a 200 response with the assembled search-results envelope
     */
    static <T> Response build(JsonLdSearchResponses.EnvelopeContext ctx, JsonLdSearchResponses.ListRequest request,
            DataCiteDoiListResponse response, Function<DataCiteAttributes, T> convert) {
        List<DataCiteDoiData> items = response.data() != null ? response.data() : List.of();
        long total = response.meta() != null ? response.meta().total() : items.size();
        boolean hasNext = response.meta() != null && request.pageNumber() < response.meta().totalPages();
        String contextBase = JsonLdContextBase.contextBaseFor(response.data(), ctx.sandboxBaseUrl(),
                ctx.fallbackContextBase());
        return JsonLdSearchResponses.build(ctx, request, items,
                item -> item.attributes() != null ? item.attributes().doi() : null,
                item -> convert.apply(item.attributes()),
                new JsonLdSearchResponses.ProviderPage(total, hasNext, contextBase));
    }
}
