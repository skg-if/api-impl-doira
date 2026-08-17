package org.skgif.doi.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.skgif.doi.generated.model.MetaSingleEntity;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.medra.MedraClient;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.medra.mapper.MedraToSkgIfMapper;
import org.skgif.doi.medra.xml.MedraOnixXmlParser;
import org.skgif.doi.util.LocalIdentifiers;

/**
 * SKG-IF Products endpoint, backed live by mEDRA's ONIX-for-DOI metadata API (no local storage) -
 * the mEDRA-provider sibling of {@link DataCiteProductsResource}/{@link CrossrefProductsResource}, see
 * {@code DataCiteProductsResource}'s javadoc for why the JSON-LD envelope is hand-assembled via {@link
 * JsonLdResponses}. Provider selection is by URL path, not auto-detected: this only ever serves
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

    private static final String RESOURCE_PATH = "/medra/products";

    private final MedraClient medraClient;
    private final MedraToSkgIfMapper mapper;
    private final LocalIdentifiers localIdentifiers;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "skgif.sandbox.base-url")
    String sandboxBaseUrl;

    @ConfigProperty(name = "skgif.context.base")
    String fallbackContextBase;

    /**
     * @param medraClient the mEDRA REST client used to fetch ONIX-for-DOI metadata
     * @param mapper maps mEDRA works to SKG-IF Product records
     * @param localIdentifiers resolves local identifiers to/from DOIs
     * @param objectMapper used to assemble the JSON-LD response envelope
     */
    @Inject
    public MedraProductsResource(@RestClient MedraClient medraClient, MedraToSkgIfMapper mapper,
            LocalIdentifiers localIdentifiers, ObjectMapper objectMapper) {
        this.medraClient = medraClient;
        this.mapper = mapper;
        this.localIdentifiers = localIdentifiers;
        this.objectMapper = objectMapper;
    }

    /**
     * @param localIdentifierParam the DOI to look up (with or without the SKG base domain prefix)
     * @param uriInfo the current request URI, used to build self/context links
     * @return the JSON-LD product envelope, or a 404 error response if not found
     */
    @GET
    @Path("/{local_identifier: .+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProductById(
            @Parameter(description = "DOI to look up (with or without the SKG base domain prefix)", examples = {
                    @ExampleObject(name = "plinius", value = "10.19276/plinius.2019.01004"),
                    @ExampleObject(name = "ncc-2021", value = "10.1393/ncc/i2021-21084-7"),
                    @ExampleObject(name = "ncc-2025", value = "10.1393/ncc/i2025-25069-2"),
                    @ExampleObject(name = "aapp", value = "10.1478/AAPP.98S1A9"),
                    @ExampleObject(name = "sapere", value = "10.12919/sapere.2018.04.3"),
                    @ExampleObject(name = "ecai", value = "10.3254/978-1-61499-732-0-119"),
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
        } catch (RuntimeException e) {
            return notFound(localIdentifierParam);
        }

        Optional<MedraWork> work = MedraOnixXmlParser.parse(xml);
        if (work.isEmpty()) {
            return notFound(localIdentifierParam);
        }

        Product product = mapper.toProduct(work.get());
        String selfHref = JsonLdResponses.selfLink(uriInfo, RESOURCE_PATH, doi);

        MetaSingleEntity meta = new MetaSingleEntity()
                .localIdentifier(selfHref)
                .entityType(MetaSingleEntity.EntityTypeEnum.SINGLE_ENTITY);

        String contextBase =
                JsonLdResponses.contextBaseFor(Optional.<String>empty(), sandboxBaseUrl, fallbackContextBase);
        ObjectNode root = JsonLdResponses.envelope(objectMapper, contextBase);
        root.set("meta", objectMapper.valueToTree(meta));
        ArrayNode graph = objectMapper.createArrayNode();
        graph.add(objectMapper.valueToTree(product));
        root.set("@graph", graph);

        return Response.ok(root).build();
    }

    private Response notFound(String requestedId) {
        return JsonLdResponses.notFound("No product found for local_identifier '" + requestedId + "'");
    }
}
