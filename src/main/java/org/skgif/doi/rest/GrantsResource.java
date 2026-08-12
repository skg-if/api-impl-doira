package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.datacite.mapper.DataCiteToSkgIfMapper;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.MetaSearch;
import org.skgif.doi.generated.model.MetaSearchPartOf;
import org.skgif.doi.generated.model.MetaSingleEntity;
import org.skgif.doi.generated.model.SearchResultPage;
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
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SKG-IF Grants endpoint, backed live by the DataCite REST API (no local storage). Serves only
 * DataCite DOIs with {@code resourceTypeGeneral: "Award"} - every other DOI is a product, see
 * {@link ProductsResource}. See that class's javadoc for why the JSON-LD envelope is
 * hand-assembled (via {@link JsonLdResponses}) rather than implementing the generated {@code
 * GrantApi} interface directly.
 */
@Path("/grants")
public class GrantsResource {

    private static final String RESOURCE_PATH = "/grants";

    @Inject
    @RestClient
    DataCiteClient dataCiteClient;

    @Inject
    DataCiteToSkgIfMapper mapper;

    @Inject
    LocalIdentifiers localIdentifiers;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "skgif.context.base")
    String contextBase;

    // Optional<String>, not String: SmallRye Config treats a blank configured value as "no
    // value" for a plain String property, which throws at startup unless it's Optional (or
    // has a defaultValue) - and blank is exactly this property's own documented default.
    @ConfigProperty(name = "datacite.prefix")
    Optional<String> dataCitePrefix;

    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    @GET
    @Path("/{local_identifier: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGrantById(@PathParam("local_identifier") String localIdentifierParam,
            @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        DataCiteDoiData data;
        try {
            DataCiteDoiResponse response = dataCiteClient.getDoi(doi);
            data = response != null ? response.data : null;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound(localIdentifierParam);
            }
            throw e;
        }
        if (data == null || data.attributes == null) {
            return notFound(localIdentifierParam);
        }
        if (!ResourceTypeMapping.isAward(data.attributes)) {
            return JsonLdResponses.notFound("No grant found for local_identifier '" + localIdentifierParam
                    + "' - this DOI is a product, see /products/" + localIdentifierParam);
        }

        Grant grant = mapper.toGrant(data.attributes);
        String selfHref = JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, doi);

        MetaSingleEntity meta = new MetaSingleEntity()
                .localIdentifier(selfHref)
                .entityType(MetaSingleEntity.EntityTypeEnum.SINGLE_ENTITY);

        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        graph.add(objectMapper.valueToTree(grant));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGrants(
            @QueryParam("filter") String filter,
            @QueryParam("page") String page,
            @QueryParam("page_size") Integer pageSize,
            @Context UriInfo uriInfo) {

        String query;
        try {
            query = GrantFilters.toDataCiteQuery(filter);
        } catch (FilterQuerySyntax.UnsupportedFilterException e) {
            return JsonLdResponses.invalidFilter(uriInfo, e.getMessage());
        }
        // /grants only ever serves Award-type DOIs.
        String awardInclusion = "types.resourceTypeGeneral:" + ResourceTypeMapping.AWARD;
        query = query == null ? awardInclusion : query + " AND " + awardInclusion;

        int pageNumber = parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        String prefix = dataCitePrefix.filter(p -> !p.isBlank()).orElse(null);

        DataCiteDoiListResponse response = dataCiteClient.listDois(prefix, query, size, pageNumber);

        List<Grant> grants = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        if (response.data != null) {
            for (DataCiteDoiData item : response.data) {
                if (item.attributes == null) {
                    continue;
                }
                grants.add(mapper.toGrant(item.attributes));
                apiItems.add(JsonLdResponses.apiItem(localIdentifiers.toFullLocalIdentifier(item.attributes.doi),
                        JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, item.attributes.doi)));
            }
        }

        long total = response.meta != null ? response.meta.total : grants.size();
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
        if (pageNumber > 1) {
            meta.prevPage(new SearchResultPage()
                    .localIdentifier(JsonLdResponses.pageLink(uriInfo, RESOURCE_PATH, filter, pageNumber - 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        meta.partOf(new MetaSearchPartOf()
                .localIdentifier(JsonLdResponses.collectionLink(uriInfo, RESOURCE_PATH, filter))
                .entityType(MetaSearchPartOf.EntityTypeEnum.SEARCH_RESULT)
                .totalItems((int) total));

        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        grants.forEach(g -> graph.add(objectMapper.valueToTree(g)));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    private boolean hasMorePages(DataCiteDoiListResponse response, int currentPage) {
        return response.meta != null && currentPage < response.meta.totalPages;
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
        return JsonLdResponses.notFound("No grant found for local_identifier '" + requestedId + "'");
    }
}
