package org.skgif.doi.rest;

import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.Link;
import org.skgif.doi.generated.model.MetaSearch;
import org.skgif.doi.generated.model.MetaSearchPartOf;
import org.skgif.doi.generated.model.MetaSingleEntity;
import org.skgif.doi.generated.model.SearchResultPage;

/**
 * Builds the {@code meta} block for JSON-LD responses (single-entity and search-results shapes),
 * shared by all four REST resource classes: {@link DataCiteProductsResource}, {@link
 * DataCiteGrantsResource}, {@link CrossrefProductsResource}, and {@link CrossrefGrantsResource}.
 */
final class JsonLdMeta {

    private static final int FIRST_PAGE_NUMBER = 1;

    private JsonLdMeta() {
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
        String selfPageHref = JsonLdLinks.pageLink(uriInfo, resourcePath, filter, pageNumber, size);
        MetaSearch meta = new MetaSearch()
                .localIdentifier(selfPageHref)
                .entityType(MetaSearch.EntityTypeEnum.SEARCH_RESULT_PAGE)
                .apiItems(apiItems);
        if (hasNext) {
            meta.nextPage(new SearchResultPage()
                    .localIdentifier(JsonLdLinks.pageLink(uriInfo, resourcePath, filter, pageNumber + 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        if (pageNumber > FIRST_PAGE_NUMBER) {
            meta.prevPage(new SearchResultPage()
                    .localIdentifier(JsonLdLinks.pageLink(uriInfo, resourcePath, filter, pageNumber - 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        meta.partOf(new MetaSearchPartOf()
                .localIdentifier(JsonLdLinks.collectionLink(uriInfo, resourcePath, filter))
                .entityType(MetaSearchPartOf.EntityTypeEnum.SEARCH_RESULT)
                .totalItems((int) totalResults));
        return meta;
    }
}
