package org.skgif.doi.rest.crossref;

import static org.skgif.doi.util.SpotBugsSuppressions.EI_EXPOSE_REP2;
import static org.skgif.doi.util.SpotBugsSuppressions.JAXRS_ENDPOINT;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefTypeMapping;
import org.skgif.doi.crossref.CrossrefWorkFetcher;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.mapper.CrossrefToSkgIfMapper;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdEnvelopes;
import org.skgif.doi.rest.JsonLdErrors;
import org.skgif.doi.rest.JsonLdMeta;
import org.skgif.doi.rest.JsonLdSearchResponses;
import org.skgif.doi.rest.RequestPagination;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * SKG-IF Grants endpoint, backed live by the Crossref REST API - the Crossref-provider sibling
 * of {@code DataCiteGrantsResource}. Serves only Crossref DOIs with {@code type: "grant"}; every other
 * Crossref DOI is a product, see {@link CrossrefProductsResource}.
 */
// The org.skgif.doi.rest.crossref/rest.datacite/rest.medra package split (added for
// ArchUnit-enforceable provider independence) means the shared JsonLd*/RequestPagination/
// FilterQuerySyntax helpers below need explicit imports instead of the same-package access this
// class previously got for free.
@Path("/crossref/grants")
@SuppressWarnings("PMD.ExcessiveImports")
public class CrossrefGrantsResource {

    /** This resource's own base path, used to build pagination/context links. */
    private static final String RESOURCE_PATH = "/crossref/grants";

    /** The Crossref REST client used to fetch works by DOI. */
    private final CrossrefClient crossrefClient;
    /** Maps Crossref works to SKG-IF Grant records. */
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
    @ConfigProperty(name = "crossref.prefix") //ok https://quarkus.io/guides/config-reference
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<String> crossrefPrefix;

    /** Contact email for Crossref's polite-pool API access, if configured. */
    @ConfigProperty(name = "crossref.mailto")
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<String> crossrefMailto;

    /** Page size used when a list request doesn't specify one. */
    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    /**
     * Creates the resource with the collaborators shared by both of its endpoints.
     *
     * @param crossrefClient   the Crossref REST client used to fetch works by DOI
     * @param mapper           maps Crossref works to SKG-IF Grant records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper     used to assemble the JSON-LD response envelope
     */
    @Inject
    @SuppressFBWarnings(value = EI_EXPOSE_REP2, justification = "Standard CDI constructor injection - these " +
            "collaborators are shared, not independently mutated by this resource")
    public CrossrefGrantsResource(@RestClient CrossrefClient crossrefClient, CrossrefToSkgIfMapper mapper,
            LocalIdentifiers localIdentifiers, ObjectMapper objectMapper) {
        this.crossrefClient = crossrefClient;
        this.mapper = mapper;
        this.localIdentifiers = localIdentifiers;
        this.objectMapper = objectMapper;
    }

    /**
     * Serves the single-grant endpoint, resolving one DOI to a JSON-LD envelope.
     *
     * @param localIdentifierParam the DOI to look up (with or without the SKG base domain prefix)
     * @param uriInfo              the current request URI, used to build self/context links
     * @return the JSON-LD grant envelope, or a 404 error response if not found
     */
    @GET
    @Path("/{local_identifier: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressFBWarnings(value = JAXRS_ENDPOINT, justification = "Taint-source marker, not a finding - no " +
            "injection-family detector traced a dangerous sink from this endpoint's parameters")
    public Response getGrantById(
            @Parameter(description = "DOI to look up (with or without the SKG base domain prefix)",
                    examples = @ExampleObject(name = "grant",
                            value = "10.35802/218300")) @PathParam("local_identifier") String localIdentifierParam,
            @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        Optional<CrossrefWork> workOpt = CrossrefWorkFetcher.fetchByDoi(crossrefClient, doi);
        if (workOpt.isEmpty()) {
            return notFound(localIdentifierParam);
        }
        CrossrefWork work = workOpt.orElseThrow();
        if (!CrossrefTypeMapping.isGrant(work)) {
            return JsonLdErrors.notFound("No grant found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a product, see /crossref/products/" + localIdentifierParam);
        }

        Grant grant = mapper.toGrant(work);

        String contextBase = JsonLdContextBase.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl,
                fallbackContextBase);
        return JsonLdEnvelopes.singleEntityResponse(objectMapper, contextBase,
                JsonLdMeta.singleEntityMeta(uriInfo, RESOURCE_PATH, doi), grant);
    }

    /**
     * Serves the grant search endpoint, translating SKG-IF filter syntax to Crossref's own.
     *
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
            return JsonLdErrors.invalidFilter(uriInfo, e.getMessage());
        }

        int pageNumber = RequestPagination.parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        int offset = (pageNumber - 1) * size;
        String mailto = crossrefMailto.filter(m -> !m.isBlank()).orElse(null);

        // /crossref/grants only ever serves type:grant records - Crossref's own filter=, unlike
        // DataCite's Lucene query, has no negation operator, but a positive AND is trivial.
        String crossrefFilter = CrossrefFilters.withPrefix(crossrefPrefix, withGrantType(parsed.filter()));

        CrossrefWorkListResponse response = crossrefClient.listWorks(crossrefFilter, parsed.queryTitle(),
                parsed.queryBibliographic(), size, offset, mailto);

        return CrossrefSearchResponses.build(
                new JsonLdSearchResponses.EnvelopeContext(objectMapper, sandboxBaseUrl, fallbackContextBase,
                        localIdentifiers),
                new JsonLdSearchResponses.ListRequest(uriInfo, RESOURCE_PATH, filter, pageNumber, size), offset,
                response, CrossrefTypeMapping::isGrant, mapper::toGrant);
    }

    private String withGrantType(@Nullable String filter) {
        String clause = "type:" + CrossrefTypeMapping.GRANT;
        return filter == null ? clause : filter + "," + clause;
    }

    private Response notFound(String requestedId) {
        return JsonLdErrors.notFound("No grant found for local_identifier '" + requestedId + "'");
    }
}
