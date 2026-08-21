package org.skgif.doi.rest.datacite;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.DataCiteDoiFetcher;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.mapper.DataCiteToSkgIfMapper;
import org.skgif.doi.generated.model.ApiItem;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdEnvelopes;
import org.skgif.doi.rest.JsonLdErrors;
import org.skgif.doi.rest.JsonLdLinks;
import org.skgif.doi.rest.JsonLdMeta;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SKG-IF Grants endpoint, backed live by the DataCite REST API (no local storage). Serves only
 * DataCite DOIs with {@code resourceTypeGeneral: "Award"} - every other DOI is a product, see
 * {@link DataCiteProductsResource}. See that class's javadoc for why the JSON-LD envelope is
 * hand-assembled (via {@link JsonLdEnvelopes}) rather than implementing the generated {@code
 * GrantApi} interface directly.
 */
// The org.skgif.doi.rest.crossref/rest.datacite/rest.medra package split (added for
// ArchUnit-enforceable provider independence) means the shared JsonLd*/RequestPagination/
// FilterQuerySyntax helpers below need explicit imports instead of the same-package access this
// class previously got for free.
@SuppressWarnings("PMD.ExcessiveImports")
@Path("/datacite/grants")
public class DataCiteGrantsResource {

    /** This resource's own base path, used to build pagination/context links. */
    private static final String RESOURCE_PATH = "/datacite/grants";

    /** The DataCite REST client used to fetch DOI records. */
    private final DataCiteClient dataCiteClient;
    /** Maps DataCite DOI records to SKG-IF Grant records. */
    private final DataCiteToSkgIfMapper mapper;
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

    // Optional<String>, not String: SmallRye Config treats a blank configured value as "no
    // value" for a plain String property, which throws at startup unless it's Optional (or
    // has a defaultValue) - and blank is exactly this property's own documented default.
    /** DataCite DOI prefix this deployment is restricted to, if configured. */
    @ConfigProperty(name = "datacite.prefix")
    Optional<String> dataCitePrefix;

    /** Page size used when a list request doesn't specify one. */
    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    /**
     * @param dataCiteClient   the DataCite REST client used to fetch DOI records
     * @param mapper           maps DataCite DOI records to SKG-IF Grant records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper     used to assemble the JSON-LD response envelope
     */
    @Inject
    public DataCiteGrantsResource(@RestClient DataCiteClient dataCiteClient, DataCiteToSkgIfMapper mapper,
            LocalIdentifiers localIdentifiers, ObjectMapper objectMapper) {
        this.dataCiteClient = dataCiteClient;
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
                            @ExampleObject(name = "award", value = "10.71707/r3sy-7371")
                    }) @PathParam("local_identifier") String localIdentifierParam,
            @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        Optional<DataCiteDoiData> dataOpt = DataCiteDoiFetcher.fetchByDoi(dataCiteClient, doi);
        if (dataOpt.isEmpty()) {
            return notFound(localIdentifierParam);
        }
        DataCiteDoiData data = dataOpt.get();
        if (!ResourceTypeMapping.isAward(data.attributes())) {
            return JsonLdErrors.notFound("No grant found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a product, see /datacite/products/" + localIdentifierParam);
        }

        Grant grant = mapper.toGrant(data.attributes());

        String contextBase = JsonLdContextBase.contextBaseFor(data, sandboxBaseUrl, fallbackContextBase);
        return JsonLdEnvelopes.singleEntityResponse(objectMapper, contextBase,
                JsonLdMeta.singleEntityMeta(uriInfo, RESOURCE_PATH, doi), grant);
    }

    /**
     * @param filter   the SKG-IF {@code filter} query string, translated to DataCite's own filter
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

        Optional<String> query;
        try {
            query = DataCiteGrantFilters.toDataCiteQuery(filter);
        } catch (FilterQuerySyntax.UnsupportedFilterException e) {
            return JsonLdErrors.invalidFilter(uriInfo, e.getMessage());
        }
        // /datacite/grants only ever serves Award-type DOIs.
        String awardInclusion = DataCiteQueryField.DATACITE_FILTER_TYPES_RESOURCE_TYPE_GENERAL.value() + ":" +
                ResourceTypeMapping.AWARD;
        String finalQuery = query.map(q -> q + " AND " + awardInclusion).orElse(awardInclusion);

        int pageNumber = RequestPagination.parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        String prefix = dataCitePrefix.filter(p -> !p.isBlank()).orElse(null);

        DataCiteDoiListResponse response = dataCiteClient.listDois(prefix, finalQuery, size, pageNumber);

        List<Grant> grants = new ArrayList<>();
        List<ApiItem> apiItems = new ArrayList<>();
        if (response.data() != null) {
            for (DataCiteDoiData item : response.data()) {
                if (item.attributes() == null) {
                    continue;
                }
                grants.add(mapper.toGrant(item.attributes()));
                apiItems.add(JsonLdMeta.apiItem(localIdentifiers.toFullLocalIdentifier(item.attributes().doi()),
                        JsonLdLinks.selfLink(uriInfo, RESOURCE_PATH, item.attributes().doi())));
            }
        }

        long total = response.meta() != null ? response.meta().total() : grants.size();
        boolean hasNext = hasMorePages(response, pageNumber);
        String contextBase = JsonLdContextBase.contextBaseFor(response.data(), sandboxBaseUrl, fallbackContextBase);
        return JsonLdEnvelopes.searchResultsResponse(objectMapper, contextBase,
                JsonLdMeta.searchMeta(new JsonLdMeta.SearchPage(uriInfo, RESOURCE_PATH, filter, pageNumber,
                        size), total, hasNext, apiItems),
                grants);
    }

    private boolean hasMorePages(DataCiteDoiListResponse response, int currentPage) {
        return response.meta() != null && currentPage < response.meta().totalPages();
    }

    private Response notFound(String requestedId) {
        return JsonLdErrors.notFound("No grant found for local_identifier '" + requestedId + "'");
    }
}
