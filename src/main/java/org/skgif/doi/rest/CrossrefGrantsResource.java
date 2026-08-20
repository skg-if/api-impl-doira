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
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SKG-IF Grants endpoint, backed live by the Crossref REST API - the Crossref-provider sibling
 * of {@link DataCiteGrantsResource}. Serves only Crossref DOIs with {@code type: "grant"}; every other
 * Crossref DOI is a product, see {@link CrossrefProductsResource}.
 */
@Path("/crossref/grants")
public class CrossrefGrantsResource {

    private static final String RESOURCE_PATH = "/crossref/grants";
    private static final int FIRST_PAGE_NUMBER = 1;

    private final CrossrefClient crossrefClient;
    private final CrossrefToSkgIfMapper mapper;
    private final LocalIdentifiers localIdentifiers;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "skgif.sandbox.base-url")
    String sandboxBaseUrl;

    @ConfigProperty(name = "skgif.context.base")
    String fallbackContextBase;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") //ok https://quarkus.io/guides/config-reference
    @ConfigProperty(name = "crossref.prefix")
    Optional<String> crossrefPrefix;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ConfigProperty(name = "crossref.mailto")
    Optional<String> crossrefMailto;

    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    /**
     * @param crossrefClient   the Crossref REST client used to fetch works by DOI
     * @param mapper           maps Crossref works to SKG-IF Grant records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper     used to assemble the JSON-LD response envelope
     */
    @Inject
    public CrossrefGrantsResource(@RestClient CrossrefClient crossrefClient, CrossrefToSkgIfMapper mapper,
            LocalIdentifiers localIdentifiers, ObjectMapper objectMapper) {
        this.crossrefClient = crossrefClient;
        this.mapper = mapper;
        this.localIdentifiers = localIdentifiers;
        this.objectMapper = objectMapper;
    }

    /**
     * @param localIdentifierParam the DOI to look up (with or without the SKG base domain prefix)
     * @param uriInfo              the current request URI, used to build self/context links
     * @return the JSON-LD grant envelope, or a 404 error response if not found
     */
    @GET
    @Path("/{local_identifier: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGrantById(
            @Parameter(description = "DOI to look up (with or without the SKG base domain prefix)",
                    examples = {
                            @ExampleObject(name = "grant", value = "10.35802/218300")
                    }) @PathParam("local_identifier") String localIdentifierParam,
            @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        CrossrefWork work = null;
        try {
            CrossrefWorkResponse response = crossrefClient.getWork(doi);
            if (response != null) {
                work = response.message();
            }
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return notFound(localIdentifierParam);
            }
            throw e;
        }
        if (work == null || work.doi() == null) {
            return notFound(localIdentifierParam);
        }
        if (!CrossrefTypeMapping.isGrant(work)) {
            return JsonLdResponses.notFound("No grant found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a product, see /crossref/products/" + localIdentifierParam);
        }

        Grant grant = mapper.toGrant(work);
        String selfHref = JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, doi);

        MetaSingleEntity meta = new MetaSingleEntity()
                .localIdentifier(selfHref)
                .entityType(MetaSingleEntity.EntityTypeEnum.SINGLE_ENTITY);

        String contextBase = JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl,
                fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        graph.add(objectMapper.valueToTree(grant));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    /**
     * @param filter   the SKG-IF {@code filter} query string, translated to Crossref's own filter
     *                 syntax
     * @param page     the page cursor/number to fetch, or null for the first page
     * @param pageSize results per page, or null to use defaultPageSize
     * @param uriInfo  the current request URI, used to build pagination/context links
     * @return the JSON-LD grant list envelope
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGrants(
            @QueryParam("filter") String filter,
            @QueryParam("page") String page,
            @QueryParam("page_size") Integer pageSize,
            @Context UriInfo uriInfo) {

        CrossrefFilters.ParsedFilter parsed;
        try {
            parsed = CrossrefFilters.toGrantsQuery(filter);
        } catch (FilterQuerySyntax.UnsupportedFilterException e) {
            return JsonLdResponses.invalidFilter(uriInfo, e.getMessage());
        }

        int pageNumber = parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        int offset = (pageNumber - 1) * size;
        String mailto = crossrefMailto.filter(m -> !m.isBlank()).orElse(null);

        // /crossref/grants only ever serves type:grant records - Crossref's own filter=, unlike
        // DataCite's Lucene query, has no negation operator, but a positive AND is trivial.
        String crossrefFilter = withPrefix(withGrantType(parsed.filter()));

        CrossrefWorkListResponse response = crossrefClient.listWorks(crossrefFilter, parsed.queryTitle(),
                parsed.queryBibliographic(), size, offset, mailto);

        List<Grant> grants = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        long totalResults = 0;
        if (response.message() != null) {
            totalResults = response.message().totalResults();
            for (CrossrefWork work : Optional.ofNullable(response.message().items()).orElse(List.of())) {
                if (work.doi() == null || !CrossrefTypeMapping.isGrant(work)) {
                    continue;
                }
                grants.add(mapper.toGrant(work));
                apiItems.add(JsonLdResponses.apiItem(localIdentifiers.toFullLocalIdentifier(work.doi()),
                        JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, work.doi())));
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
        if (pageNumber > FIRST_PAGE_NUMBER) {
            meta.prevPage(new SearchResultPage()
                    .localIdentifier(JsonLdResponses.pageLink(uriInfo, RESOURCE_PATH, filter, pageNumber - 1, size))
                    .entityType(SearchResultPage.EntityTypeEnum.SEARCH_RESULT_PAGE));
        }
        meta.partOf(new MetaSearchPartOf()
                .localIdentifier(JsonLdResponses.collectionLink(uriInfo, RESOURCE_PATH, filter))
                .entityType(MetaSearchPartOf.EntityTypeEnum.SEARCH_RESULT)
                .totalItems((int) totalResults));

        String contextBase = JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl,
                fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        grants.forEach(g -> graph.add(objectMapper.valueToTree(g)));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    private String withGrantType(String filter) {
        String clause = "type:" + CrossrefTypeMapping.GRANT;
        return filter == null ? clause : filter + "," + clause;
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
        return JsonLdResponses.notFound("No grant found for local_identifier '" + requestedId + "'");
    }
}
