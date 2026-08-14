package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.CrossrefXmlTransformClient;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.crossref.mapper.CrossrefToSkgIfMapper;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadataXmlParser;
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
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SKG-IF Products endpoint, backed live by the Crossref REST API (no local storage) - the
 * Crossref-provider sibling of {@link DataCiteProductsResource}, see that class's javadoc for why the
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
    @RestClient
    CrossrefXmlTransformClient crossrefXmlTransformClient;

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
    public Response getProductById(
            @Parameter(description = "DOI to look up (with or without the SKG base domain prefix)", examples = {
                    @ExampleObject(name = "journal-article", value = "10.1038/nature12373"),
                    @ExampleObject(name = "orcid", value = "10.1038/s41467-022-33468-6"),
                    @ExampleObject(name = "proceedings", value = "10.17537/icmbb18.42"),
                    @ExampleObject(name = "ror-affiliation", value = "10.1103/physrevb.110.174515"),
                    @ExampleObject(name = "book-chapter", value = "10.1007/978-3-319-66787-4_9"),
                    @ExampleObject(name = "proceedings-with-series", value = "10.2991/assehr.k.211222.032"),
                    @ExampleObject(name = "dataset", value = "10.17989/encsr154xia"),
                    @ExampleObject(name = "funder-without-identifier", value = "10.1155/2016/1353212"),
                    @ExampleObject(name = "standalone-book-chapter", value = "10.1007/978-1-4842-7310-4_15"),
                    @ExampleObject(name = "standalone-proceedings", value = "10.1109/freq.1998.717994"),
                    @ExampleObject(name = "is-supplemented-by", value = "10.1107/s2414314618016334")
            }) @PathParam("local_identifier") String localIdentifierParam,
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

        CrossrefVenueMetadata venueMetadata = null;
        if (CrossrefTypeMapping.isXmlVenueEnrichable(work)) {
            venueMetadata = fetchVenueMetadata(doi);
        }
        Product product = mapper.toProduct(work, venueMetadata);
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

    /**
     * Fetches and parses Crossref's XML transform for a chapter-in-a-book or paper-in-proceedings
     * record (see {@code CrossrefTypeMapping#isXmlVenueEnrichable}), used to build an accurate
     * Venue - see {@code CrossrefToSkgIfMapper#venue}. Only called from the single-item {@code
     * getProductById} endpoint, not the list endpoint below (which would otherwise mean N extra
     * Crossref HTTP calls per page). Any failure - non-200 response, network/timeout error, or a
     * shape the parser doesn't recognize - degrades to {@code null}, so the caller falls back to
     * the existing {@code container-title[0]} venue rather than failing the whole product
     * response over an enrichment call.
     *
     * @param doi the DOI to fetch XML venue metadata for
     * @return the parsed venue metadata, or null if the fetch/parse fails or finds nothing
     */
    private CrossrefVenueMetadata fetchVenueMetadata(String doi) {
        try (Response response = crossrefXmlTransformClient.getXmlTransform(doi)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                return null;
            }
            return CrossrefVenueMetadataXmlParser.parse(response.readEntity(String.class)).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
