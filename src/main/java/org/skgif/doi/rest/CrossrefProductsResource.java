package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.crossref.mapper.CrossrefToSkgIfMapper;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.MetaSearch;
import org.skgif.doi.generated.model.MetaSearchPartOf;
import org.skgif.doi.generated.model.MetaSingleEntity;
import org.skgif.doi.generated.model.Product;
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
 * SKG-IF Products endpoint, backed live by the Crossref REST API (no local storage) - the
 * Crossref-provider sibling of {@link ProductsResource}, see that class's javadoc for why the
 * JSON-LD envelope is hand-assembled via {@link JsonLdResponses}. Provider selection is by URL
 * path rather than auto-detected: this only ever serves Crossref-registered DOIs, at {@code
 * /crossref/products} rather than {@code /datacite/products}.
 */
@Path("/crossref/products")
public class CrossrefProductsResource {

    private static final String RESOURCE_PATH = "/crossref/products";

    @Inject
    @RestClient
    CrossrefClient crossrefClient;

    @Inject
    CrossrefToSkgIfMapper mapper;

    @Inject
    LocalIdentifiers localIdentifiers;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "skgif.sandbox.base-url")
    String sandboxBaseUrl;

    @ConfigProperty(name = "skgif.context.base")
    String fallbackContextBase;

    @ConfigProperty(name = "crossref.prefix")
    Optional<String> crossrefPrefix;

    @ConfigProperty(name = "crossref.mailto")
    Optional<String> crossrefMailto;

    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    @GET
    @Path("/{local_identifier: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductById(@PathParam("local_identifier") String localIdentifierParam,
            @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        CrossrefWork work;
        try {
            CrossrefWorkResponse response = crossrefClient.getWork(doi);
            work = response != null ? response.message : null;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return notFound(localIdentifierParam);
            }
            throw e;
        }
        if (work == null || work.doi == null) {
            return notFound(localIdentifierParam);
        }
        if (CrossrefTypeMapping.isGrant(work)) {
            return JsonLdResponses.notFound("No product found for local_identifier '" + localIdentifierParam
                    + "' - this DOI is a grant, see /crossref/grants/" + localIdentifierParam);
        }

        Product product = mapper.toProduct(work);
        String selfHref = JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, doi);

        MetaSingleEntity meta = new MetaSingleEntity()
                .localIdentifier(selfHref)
                .entityType(MetaSingleEntity.EntityTypeEnum.SINGLE_ENTITY);

        String contextBase = JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl, fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        graph.add(objectMapper.valueToTree(product));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProducts(
            @QueryParam("filter") String filter,
            @QueryParam("page") String page,
            @QueryParam("page_size") Integer pageSize,
            @Context UriInfo uriInfo) {

        CrossrefFilters.ParsedFilter parsed;
        try {
            parsed = CrossrefFilters.toProductsQuery(filter);
        } catch (FilterQuerySyntax.UnsupportedFilterException e) {
            return JsonLdResponses.invalidFilter(uriInfo, e.getMessage());
        }

        int pageNumber = parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        int offset = (pageNumber - 1) * size;
        String mailto = crossrefMailto.filter(m -> !m.isBlank()).orElse(null);

        CrossrefWorkListResponse response = crossrefClient.listWorks(withPrefix(parsed.filter), parsed.queryTitle,
                parsed.queryBibliographic, size, offset, mailto);

        List<Product> products = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        long totalResults = 0;
        if (response.message != null) {
            totalResults = response.message.totalResults;
            if (response.message.items != null) {
                for (CrossrefWork work : response.message.items) {
                    // Crossref's filter= has no negation operator (see CrossrefFilters), so
                    // grant-type records are excluded here rather than in the query itself -
                    // unlike DataCite's "NOT resourceTypeGeneral:Award" query clause.
                    if (work.doi == null || CrossrefTypeMapping.isGrant(work)) {
                        continue;
                    }
                    products.add(mapper.toProduct(work));
                    apiItems.add(JsonLdResponses.apiItem(localIdentifiers.toFullLocalIdentifier(work.doi),
                            JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, work.doi)));
                }
            }
        }

        String selfPageHref = JsonLdResponses.pageLink(uriInfo, RESOURCE_PATH, filter, pageNumber, size);

        MetaSearch meta = new MetaSearch()
                .localIdentifier(selfPageHref)
                .entityType(MetaSearch.EntityTypeEnum.SEARCH_RESULT_PAGE)
                .apiItems(apiItems);
        if (offset + size < totalResults) {
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
                .totalItems((int) totalResults));

        String contextBase = JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl, fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        products.forEach(p -> graph.add(objectMapper.valueToTree(p)));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    private String withPrefix(String filter) {
        String prefix = crossrefPrefix.filter(p -> !p.isBlank()).orElse(null);
        if (prefix == null) {
            return filter;
        }
        String prefixClause = "prefix:" + prefix;
        return filter == null ? prefixClause : filter + "," + prefixClause;
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
