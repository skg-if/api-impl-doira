package org.skgif.doi.rest.datacite;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.DataCiteDoiFetcher;
import org.skgif.doi.datacite.ResourceTypeMapping;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.mapper.DataCiteToSkgIfMapper;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.rest.FilterQuerySyntax;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdEnvelopes;
import org.skgif.doi.rest.JsonLdErrors;
import org.skgif.doi.rest.JsonLdMeta;
import org.skgif.doi.rest.JsonLdSearchResponses;
import org.skgif.doi.rest.RequestPagination;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * SKG-IF Products endpoint, backed live by the DataCite REST API (no local storage). Serves
 * any DataCite DOI except {@code resourceTypeGeneral: "Award"} ones, which are grants, not
 * products - see {@link DataCiteGrantsResource}.
 *
 * <p>This does not implement the generated {@code ProductApi} interface: openapi-generator's
 * merge of the spec's {@code @context} anyOf (two fixed context URLs + an {@code @base} object)
 * collapses to a type that can only hold the {@code @base} object, dropping the two required
 * context URLs. The JSON-LD envelope (@context/meta/@graph) is therefore assembled by hand here
 * (via {@link JsonLdEnvelopes}) with Jackson, while the generated {@code Product}/{@code
 * MetaSingleEntity}/{@code MetaSearch}/{@code Error} models (which generated correctly) are
 * used for everything nested inside it.
 */
// The org.skgif.doi.rest.crossref/rest.datacite/rest.medra package split (added for
// ArchUnit-enforceable provider independence) means the shared JsonLd*/RequestPagination/
// FilterQuerySyntax helpers below need explicit imports instead of the same-package access this
// class previously got for free.
@SuppressWarnings("PMD.ExcessiveImports")
@Path("/datacite/products")
public class DataCiteProductsResource {

    /** This resource's own base path, used to build pagination/context links. */
    private static final String RESOURCE_PATH = "/datacite/products";

    /** The DataCite REST client used to fetch DOI records. */
    private final DataCiteClient dataCiteClient;
    /** Maps DataCite DOI records to SKG-IF Product records. */
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
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType") //ok https://quarkus.io/guides/config-reference
    @ConfigProperty(name = "datacite.prefix")
    Optional<String> dataCitePrefix;

    /** Page size used when a list request doesn't specify one. */
    @ConfigProperty(name = "skgif.default-page-size")
    int defaultPageSize;

    /**
     * Creates the resource with the collaborators shared by both of its endpoints.
     *
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
     * Serves the single-product endpoint, resolving one DOI to a JSON-LD envelope.
     *
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

        Optional<DataCiteDoiData> dataOpt = DataCiteDoiFetcher.fetchByDoi(dataCiteClient, doi);
        if (dataOpt.isEmpty()) {
            return notFound(localIdentifierParam);
        }
        DataCiteDoiData data = dataOpt.get();
        // A record with no attributes block carries nothing this API can map, so it is reported
        // as not-found rather than dereferenced - DataCite always sends one, but the DTO cannot
        // promise that.
        DataCiteAttributes attributes = data.attributes();
        if (attributes == null) {
            return notFound(localIdentifierParam);
        }
        if (ResourceTypeMapping.isAward(attributes)) {
            return JsonLdErrors.notFound("No product found for local_identifier '" + localIdentifierParam +
                    "' - this DOI is a grant award, see /datacite/grants/" + localIdentifierParam);
        }

        Product product = mapper.toProduct(attributes);

        String contextBase = JsonLdContextBase.contextBaseFor(data, sandboxBaseUrl, fallbackContextBase);
        return JsonLdEnvelopes.singleEntityResponse(objectMapper, contextBase,
                JsonLdMeta.singleEntityMeta(uriInfo, RESOURCE_PATH, doi), product);
    }

    /**
     * Serves the product search endpoint, translating SKG-IF filter syntax to DataCite's own.
     *
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

        Optional<String> query;
        try {
            query = DataCiteProductFilters.toDataCiteQuery(filter);
        } catch (FilterQuerySyntax.UnsupportedFilterException e) {
            return JsonLdErrors.invalidFilter(uriInfo, e.getMessage());
        }
        // Awards are grants, not products - never let them leak into /datacite/products results.
        String awardExclusion = "NOT " + DataCiteQueryField.DATACITE_FILTER_TYPES_RESOURCE_TYPE_GENERAL.value() +
                ":" + ResourceTypeMapping.AWARD;
        String finalQuery = query.map(q -> q + " AND " + awardExclusion).orElse(awardExclusion);

        int pageNumber = RequestPagination.parsePage(page);
        int size = pageSize != null && pageSize > 0 ? pageSize : defaultPageSize;
        String prefix = dataCitePrefix.filter(p -> !p.isBlank()).orElse(null);

        DataCiteDoiListResponse response = dataCiteClient.listDois(prefix, finalQuery, size, pageNumber);

        return DataCiteSearchResponses.build(
                new JsonLdSearchResponses.EnvelopeContext(objectMapper, sandboxBaseUrl, fallbackContextBase,
                        localIdentifiers),
                new JsonLdSearchResponses.ListRequest(uriInfo, RESOURCE_PATH, filter, pageNumber, size),
                response, mapper::toProduct);
    }

    private Response notFound(String requestedId) {
        return JsonLdErrors.notFound("No product found for local_identifier '" + requestedId + "'");
    }
}
