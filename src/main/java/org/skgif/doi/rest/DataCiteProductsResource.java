package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.MetaSearch;
import org.skgif.doi.generated.model.MetaSearchPartOf;
import org.skgif.doi.generated.model.MetaSingleEntity;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.SearchResultPage;
import org.skgif.doi.datacite.mapper.DataCiteToSkgIfMapper;
import org.skgif.doi.util.LocalIdentifiers;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SKG-IF Products endpoint, backed live by the DataCite REST API (no local storage). Serves
 * any DataCite DOI except {@code resourceTypeGeneral: "Award"} ones, which are grants, not
 * products - see {@link DataCiteGrantsResource}.
 *
 * <p>This does not implement the generated {@code ProductApi} interface: openapi-generator's
 * merge of the spec's {@code @context} anyOf (two fixed context URLs + an {@code @base} object)
 * collapses to a type that can only hold the {@code @base} object, dropping the two required
 * context URLs. The JSON-LD envelope (@context/meta/@graph) is therefore assembled by hand here
 * (via {@link JsonLdResponses}) with Jackson, while the generated {@code Product}/{@code
 * MetaSingleEntity}/{@code MetaSearch}/{@code Error} models (which generated correctly) are
 * used for everything nested inside it.
 */
@Path("/datacite/products")
public class DataCiteProductsResource {

    private static final String RESOURCE_PATH = "/datacite/products";
    private static final int FIRST_PAGE_NUMBER = 1;

    private final DataCiteClient dataCiteClient;
    private final DataCiteToSkgIfMapper mapper;
    private final LocalIdentifiers localIdentifiers;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "skgif.sandbox.base-url")
    String sandboxBaseUrl;

    @ConfigProperty(name = "skgif.context.base")
    String fallbackContextBase;

    // Optional<String>, not String: SmallRye Config treats a blank configured value as "no
    // value" for a plain String property, which throws at startup unless it's Optional (or
    // has a defaultValue) - and blank is exactly this property's own documented default.
    @ConfigProperty(name = "datacite.prefix")
    Optional<String> dataCitePrefix;

    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    /**
     * @param dataCiteClient   the DataCite REST client used to fetch DOI records
     * @param mapper           maps DataCite DOI records to SKG-IF Product records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper     used to assemble the JSON-LD response envelope
     */
    @Inject
    public DataCiteProductsResource(@RestClient DataCiteClient dataCiteClient, DataCiteToSkgIfMapper mapper,
                                    LocalIdentifiers localIdentifiers, ObjectMapper objectMapper) {
        this.dataCiteClient = dataCiteClient;
        this.mapper = mapper;
        this.localIdentifiers = localIdentifiers;
        this.objectMapper = objectMapper;
    }

    /**
     * @param localIdentifierParam the DOI to look up (with or without the SKG base domain prefix)
     * @param uriInfo              the current request URI, used to build self/context links
     * @return the JSON-LD product envelope, or a 404 error response if not found
     */
    @GET
    @Path("/{local_identifier: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductById(
                                   @Parameter(
                                           description = "DOI to look up (with or without the SKG base domain prefix)",
                                           examples = {
                                                   @ExampleObject(name = "dataset",
                                                           value = "10.15151/esrf-dc-2493599001"),
                                                   @ExampleObject(name = "dataset-2",
                                                           value = "10.15151/esrf-es-2210534378"),
                                                   @ExampleObject(name = "software", value = "10.5281/zenodo.21826016"),
                                                   @ExampleObject(name = "text", value = "10.5281/zenodo.20750072"),
                                                   @ExampleObject(name = "editor-contributor",
                                                           value = "10.5281/zenodo.21232199"),
                                                   @ExampleObject(name = "cites-references",
                                                           value = "10.5281/zenodo.21914195"),
                                                   @ExampleObject(name = "relations",
                                                           value = "10.5281/zenodo.21827103"),
                                                   @ExampleObject(name = "thesis-funder-id",
                                                           value = "10.82227/repository.uwtsd.ac.uk.00004342"),
                                                   @ExampleObject(name = "dataset-funder-no-identifier",
                                                           value = "10.17630/e449e75a-1ee9-4490-909c-e3913052cce1")
                                           }) @PathParam("local_identifier") String localIdentifierParam,
                                   @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        DataCiteDoiData data;
        try {
            DataCiteDoiResponse response = dataCiteClient.getDoi(doi);
            data = response != null ? response.data() : null;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return notFound(localIdentifierParam);
            }
            throw e;
        }
        if (data == null || data.attributes() == null) {
            return notFound(localIdentifierParam);
        }
        if (ResourceTypeMapping.isAward(data.attributes())) {
            return JsonLdResponses.notFound("No product found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a grant award, see /datacite/grants/" + localIdentifierParam);
        }

        Product product = mapper.toProduct(data.attributes());
        String selfHref = JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, doi);

        MetaSingleEntity meta = new MetaSingleEntity()
                .localIdentifier(selfHref)
                .entityType(MetaSingleEntity.EntityTypeEnum.SINGLE_ENTITY);

        String contextBase = JsonLdResponses.contextBaseFor(data, sandboxBaseUrl, fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        graph.add(objectMapper.valueToTree(product));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    /**
     * @param filter   the SKG-IF {@code filter} query string, translated to DataCite's own filter
     *                 syntax
     * @param page     the page cursor/number to fetch, or null for the first page
     * @param pageSize results per page, or null to use defaultPageSize
     * @param uriInfo  the current request URI, used to build pagination/context links
     * @return the JSON-LD product list envelope
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProducts(
                                @QueryParam("filter") String filter,
                                @QueryParam("page") String page,
                                @QueryParam("page_size") Integer pageSize,
                                @Context UriInfo uriInfo) {

        String query;
        try {
            query = DataCiteProductFilters.toDataCiteQuery(filter);
        } catch (FilterQuerySyntax.UnsupportedFilterException e) {
            return JsonLdResponses.invalidFilter(uriInfo, e.getMessage());
        }
        // Awards are grants, not products - never let them leak into /datacite/products results.
        String awardExclusion = "NOT types.resourceTypeGeneral:" + ResourceTypeMapping.AWARD;
        query = query == null ? awardExclusion : query + " AND " + awardExclusion;

        int pageNumber = parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        String prefix = dataCitePrefix.filter(p -> !p.isBlank()).orElse(null);

        DataCiteDoiListResponse response = dataCiteClient.listDois(prefix, query, size, pageNumber);

        List<Product> products = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        if (response.data() != null) {
            for (DataCiteDoiData item : response.data()) {
                if (item.attributes() == null) {
                    continue;
                }
                products.add(mapper.toProduct(item.attributes()));
                apiItems.add(JsonLdResponses.apiItem(localIdentifiers.toFullLocalIdentifier(item.attributes().doi()),
                        JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, item.attributes().doi())));
            }
        }

        long total = response.meta() != null ? response.meta().total() : products.size();
        String selfPageHref = JsonLdResponses.pageLink(uriInfo, RESOURCE_PATH, filter, pageNumber, size);

        MetaSearch meta = new MetaSearch()
                .localIdentifier(selfPageHref)
                .entityType(MetaSearch.EntityTypeEnum.SEARCH_RESULT_PAGE)
                .apiItems(apiItems);
        if (hasMorePages(response, pageNumber)) {
            meta.nextPage(new SearchResultPage()
                    .localIdentifier(JsonLdResponses.pageLink(uriInfo, RESOURCE_PATH, filter, pageNumber + 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        if (pageNumber > FIRST_PAGE_NUMBER) {
            meta.prevPage(new SearchResultPage()
                    .localIdentifier(JsonLdResponses.pageLink(uriInfo, RESOURCE_PATH, filter, pageNumber - 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        meta.partOf(new MetaSearchPartOf()
                .localIdentifier(JsonLdResponses.collectionLink(uriInfo, RESOURCE_PATH, filter))
                .entityType(MetaSearchPartOf.EntityTypeEnum.SEARCH_RESULT)
                .totalItems((int) total));

        String contextBase = JsonLdResponses.contextBaseFor(response.data(), sandboxBaseUrl, fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        products.forEach(p -> graph.add(objectMapper.valueToTree(p)));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    private boolean hasMorePages(DataCiteDoiListResponse response, int currentPage) {
        return response.meta() != null && currentPage < response.meta().totalPages();
    }

    private int parsePage(String page) {
        if (page == null) {
            return 1;
        }
        try {
            int parsed = Integer.parseInt(page);
            return parsed > 0 ? parsed : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Response notFound(String requestedId) {
        return JsonLdResponses.notFound("No product found for local_identifier '" + requestedId + "'");
    }
}
