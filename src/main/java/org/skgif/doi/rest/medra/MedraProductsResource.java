package org.skgif.doi.rest.medra;

import static org.skgif.doi.util.SpotBugsError.Code.EI_EXPOSE_REP2;
import static org.skgif.doi.util.SpotBugsError.Code.JAXRS_ENDPOINT;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.medra.MedraClient;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.medra.mapper.MedraToSkgIfMapper;
import org.skgif.doi.medra.xml.MedraOnixXmlParser;
import org.skgif.doi.rest.JsonLdContextBase;
import org.skgif.doi.rest.JsonLdEnvelopes;
import org.skgif.doi.rest.JsonLdErrors;
import org.skgif.doi.rest.JsonLdMeta;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * SKG-IF Products endpoint, backed live by mEDRA's ONIX-for-DOI metadata API (no local storage) -
 * the mEDRA-provider sibling of {@code DataCiteProductsResource}/{@code CrossrefProductsResource}, see
 * {@code DataCiteProductsResource}'s javadoc for why the JSON-LD envelope is hand-assembled via {@link
 * JsonLdEnvelopes}. Provider selection is by URL path, not auto-detected: this only ever serves
 * mEDRA-registered DOIs, at {@code /medra/products}.
 *
 * <p>Single-item lookup only - unlike {@code DataCiteProductsResource}/{@code CrossrefProductsResource},
 * there is no bare {@code GET /medra/products} list endpoint here. {@code
 * api.medra.org/metadata/{doi}} is a DOI-keyed metadata lookup, not a search/list API (no mEDRA
 * equivalent of Crossref's {@code filter=}/DataCite's list query was found), so there is no query
 * to back a list endpoint with. See SKG_IF_DOI_MAPPING_LIMITATIONS.md.
 */
@Path("/medra/products")
public class MedraProductsResource {

    /** This resource's own base path. */
    private static final String RESOURCE_PATH = "/medra/products";

    /** The mEDRA REST client used to fetch ONIX-for-DOI metadata. */
    private final MedraClient medraClient;
    /** Maps mEDRA works to SKG-IF Product records. */
    private final MedraToSkgIfMapper mapper;
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

    /**
     * Creates the resource with the collaborators its endpoint needs.
     *
     * @param medraClient      the mEDRA REST client used to fetch ONIX-for-DOI metadata
     * @param mapper           maps mEDRA works to SKG-IF Product records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper     used to assemble the JSON-LD response envelope
     */
    @Inject
    @SuppressFBWarnings(value = EI_EXPOSE_REP2, justification = "Standard CDI constructor injection - these " +
            "collaborators are shared, not independently mutated by this resource")
    public MedraProductsResource(@RestClient MedraClient medraClient, MedraToSkgIfMapper mapper,
            LocalIdentifiers localIdentifiers, ObjectMapper objectMapper) {
        this.medraClient = medraClient;
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
    @SuppressFBWarnings(value = JAXRS_ENDPOINT, justification = "Taint-source marker, not a finding - no " +
            "injection-family detector traced a dangerous sink from this endpoint's parameters")
    public Response getProductById(
            @Parameter(
                    description = "DOI to look up (with or without the SKG base domain prefix)",
                    examples = {
                            @ExampleObject(name = "plinius",
                                    value = "10.19276/plinius.2019.01004"),
                            @ExampleObject(name = "ncc-2021",
                                    value = "10.1393/ncc/i2021-21084-7"),
                            @ExampleObject(name = "ncc-2025",
                                    value = "10.1393/ncc/i2025-25069-2"),
                            @ExampleObject(name = "aapp", value = "10.1478/AAPP.98S1A9"),
                            @ExampleObject(name = "sapere", value = "10.12919/sapere.2018.04.3"),
                            @ExampleObject(name = "ecai",
                                    value = "10.3254/978-1-61499-732-0-119"),
                            @ExampleObject(name = "il-nuovo-cimento", value = "10.1400/255846")
                    }) @PathParam("local_identifier") String localIdentifierParam,
            @Context UriInfo uriInfo) {
        String doi = localIdentifiers.toDoi(localIdentifierParam);

        String xml;
        try (Response response = medraClient.getMetadata(doi)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                return notFound(localIdentifierParam);
            }
            xml = response.readEntity(String.class);
        } catch (RuntimeException _) {
            return notFound(localIdentifierParam);
        }

        Optional<MedraWork> work = MedraOnixXmlParser.parse(xml);
        if (work.isEmpty()) {
            return notFound(localIdentifierParam);
        }

        Product product = mapper.toProduct(work.orElseThrow());

        String contextBase =
                JsonLdContextBase.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl, fallbackContextBase);
        return JsonLdEnvelopes.singleEntityResponse(objectMapper, contextBase,
                JsonLdMeta.singleEntityMeta(uriInfo, RESOURCE_PATH, doi), product);
    }

    private Response notFound(String requestedId) {
        return JsonLdErrors.notFound("No product found for local_identifier '" + requestedId + "'");
    }
}
