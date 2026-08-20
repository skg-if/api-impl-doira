package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.crossref.mapper.CrossrefToSkgIfMapper;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.Product;
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

    private final CrossrefClient crossrefClient;
    private final CrossrefVenueEnricher venueEnricher;
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
     * @param venueEnricher    fetches XML venue metadata to enrich single-product responses
     * @param mapper           maps Crossref works to SKG-IF Product records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper     used to assemble the JSON-LD response envelope
     */
    @Inject
    public CrossrefProductsResource(@RestClient CrossrefClient crossrefClient, CrossrefVenueEnricher venueEnricher,
            CrossrefToSkgIfMapper mapper, LocalIdentifiers localIdentifiers,
            ObjectMapper objectMapper) {
        this.crossrefClient = crossrefClient;
        this.venueEnricher = venueEnricher;
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
                            @ExampleObject(name = "journal-article",
                                    value = "10.1038/nature12373"),
                            @ExampleObject(name = "orcid", value = "10.1038/s41467-022-33468-6"),
                            @ExampleObject(name = "proceedings", value = "10.17537/icmbb18.42"),
                            @ExampleObject(name = "ror-affiliation",
                                    value = "10.1103/physrevb.110.174515"),
                            @ExampleObject(name = "book-chapter",
                                    value = "10.1007/978-3-319-66787-4_9"),
                            @ExampleObject(name = "proceedings-with-series",
                                    value = "10.2991/assehr.k.211222.032"),
                            @ExampleObject(name = "dataset", value = "10.17989/encsr154xia"),
                            @ExampleObject(name = "funder-without-identifier",
                                    value = "10.1155/2016/1353212"),
                            @ExampleObject(name = "standalone-book-chapter",
                                    value = "10.1007/978-1-4842-7310-4_15"),
                            @ExampleObject(name = "standalone-proceedings",
                                    value = "10.1109/freq.1998.717994"),
                            @ExampleObject(name = "is-supplemented-by",
                                    value = "10.1107/s2414314618016334")
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
        if (CrossrefTypeMapping.isGrant(work)) {
            return JsonLdResponses.notFound("No product found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a grant, see /crossref/grants/" + localIdentifierParam);
        }

        Product product = mapper.toProduct(work,
                CrossrefTypeMapping.isXmlVenueEnrichable(work) ? venueEnricher.fetchVenueMetadata(doi).orElse(null) :
                        null);
        String selfHref = JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, doi);

        String contextBase = JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl,
                fallbackContextBase);
        return JsonLdResponses.singleEntityResponse(objectMapper, contextBase,
                JsonLdResponses.singleEntityMeta(selfHref), product);
    }

    /**
     * @param filter   the SKG-IF {@code filter} query string, translated to Crossref's own filter
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

        CrossrefWorkListResponse response = crossrefClient.listWorks(withPrefix(parsed.filter()), parsed.queryTitle(),
                parsed.queryBibliographic(), size, offset, mailto);

        List<Product> products = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        long totalResults = 0;
        if (response.message() != null) {
            totalResults = response.message().totalResults();
            for (CrossrefWork work : Optional.ofNullable(response.message().items()).orElse(List.of())) {
                // Crossref's filter= has no negation operator (see CrossrefFilters), so
                // grant-type records are excluded here rather than in the query itself -
                // unlike DataCite's "NOT resourceTypeGeneral:Award" query clause.
                if (work.doi() == null || CrossrefTypeMapping.isGrant(work)) {
                    continue;
                }
                products.add(mapper.toProduct(work));
                apiItems.add(JsonLdResponses.apiItem(localIdentifiers.toFullLocalIdentifier(work.doi()),
                        JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, work.doi())));
            }
        }

        boolean hasNext = offset + size < totalResults;
        String contextBase = JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl,
                fallbackContextBase);
        return JsonLdResponses.searchResultsResponse(objectMapper, contextBase,
                JsonLdResponses.searchMeta(new JsonLdResponses.SearchPage(uriInfo, RESOURCE_PATH, filter, pageNumber,
                        size), totalResults, hasNext, apiItems),
                products);
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
