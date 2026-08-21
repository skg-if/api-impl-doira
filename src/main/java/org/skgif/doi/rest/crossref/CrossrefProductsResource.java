package org.skgif.doi.rest.crossref;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.CrossrefWorkFetcher;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.mapper.CrossrefToSkgIfMapper;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdEnvelopes;
import org.skgif.doi.rest.JsonLdErrors;
import org.skgif.doi.rest.JsonLdMeta;
import org.skgif.doi.rest.JsonLdSearchResponses;
import org.skgif.doi.rest.RequestPagination;
import org.skgif.doi.util.LocalIdentifiers;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

/**
 * SKG-IF Products endpoint, backed live by the Crossref REST API (no local storage) - the
 * Crossref-provider sibling of {@code DataCiteProductsResource}, see that class's javadoc for why the
 * JSON-LD envelope is hand-assembled via {@link JsonLdEnvelopes}. Provider selection is by URL
 * path rather than auto-detected: this only ever serves Crossref-registered DOIs, at {@code
 * /crossref/products} rather than {@code /datacite/products}.
 */
// The org.skgif.doi.rest.crossref/rest.datacite/rest.medra package split (added for
// ArchUnit-enforceable provider independence) means the shared JsonLd*/RequestPagination/
// FilterQuerySyntax helpers below need explicit imports instead of the same-package access this
// class previously got for free.
@SuppressWarnings("PMD.ExcessiveImports")
@Path("/crossref/products")
public class CrossrefProductsResource {

    /** This resource's own base path, used to build pagination/context links. */
    private static final String RESOURCE_PATH = "/crossref/products";

    /** The Crossref REST client used to fetch works by DOI. */
    private final CrossrefClient crossrefClient;
    /** Fetches XML venue metadata to enrich single-product responses. */
    private final CrossrefVenueEnricher venueEnricher;
    /** Maps Crossref works to SKG-IF Product records. */
    private final CrossrefToSkgIfMapper mapper;
    /** Resolves local identifiers to/from DOIs. */
    private final LocalIdentifiers localIdentifiers;
    /** Used to assemble the JSON-LD response envelope. */
    private final ObjectMapper objectMapper;

    /** Base URL of the sandbox environment, surfaced in error responses. */
    @ConfigProperty(name = "skgif.sandbox.base-url")
    String sandboxBaseUrl;

    /** Fallback {@code @context} base URL used when not overridden per-request. */
    @ConfigProperty(name = "skgif.context.base")
    String fallbackContextBase;

    /** Crossref DOI prefix this deployment is restricted to, if configured. */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") //ok https://quarkus.io/guides/config-reference
    @ConfigProperty(name = "crossref.prefix")
    Optional<String> crossrefPrefix;

    /** Contact email for Crossref's polite-pool API access, if configured. */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ConfigProperty(name = "crossref.mailto")
    Optional<String> crossrefMailto;

    /** Page size used when a list request doesn't specify one. */
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

        Optional<CrossrefWork> workOpt = CrossrefWorkFetcher.fetchByDoi(crossrefClient, doi);
        if (workOpt.isEmpty()) {
            return notFound(localIdentifierParam);
        }
        CrossrefWork work = workOpt.get();
        if (CrossrefTypeMapping.isGrant(work)) {
            return JsonLdErrors.notFound("No product found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a grant, see /crossref/grants/" + localIdentifierParam);
        }

        Product product = mapper.toProduct(work,
                CrossrefTypeMapping.isXmlVenueEnrichable(work) ? venueEnricher.fetchVenueMetadata(doi).orElse(null) :
                        null);

        String contextBase = JsonLdContextBase.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl,
                fallbackContextBase);
        return JsonLdEnvelopes.singleEntityResponse(objectMapper, contextBase,
                JsonLdMeta.singleEntityMeta(uriInfo, RESOURCE_PATH, doi), product);
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
            return JsonLdErrors.invalidFilter(uriInfo, e.getMessage());
        }

        int pageNumber = RequestPagination.parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        int offset = (pageNumber - 1) * size;
        String mailto = crossrefMailto.filter(m -> !m.isBlank()).orElse(null);

        CrossrefWorkListResponse response = crossrefClient.listWorks(
                CrossrefFilters.withPrefix(crossrefPrefix, parsed.filter()), parsed.queryTitle(),
                parsed.queryBibliographic(), size, offset, mailto);

        // Crossref's filter= has no negation operator (see CrossrefFilters), so grant-type
        // records are excluded here rather than in the query itself - unlike DataCite's
        // "NOT resourceTypeGeneral:Award" query clause.
        return CrossrefSearchResponses.build(
                new JsonLdSearchResponses.EnvelopeContext(objectMapper, sandboxBaseUrl, fallbackContextBase,
                        localIdentifiers),
                new JsonLdSearchResponses.ListRequest(uriInfo, RESOURCE_PATH, filter, pageNumber, size), offset,
                response, work -> !CrossrefTypeMapping.isGrant(work), mapper::toProduct);
    }

    private Response notFound(String requestedId) {
        return JsonLdErrors.notFound("No product found for local_identifier '" + requestedId + "'");
    }
}
