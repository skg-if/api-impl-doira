package org.skgif.doi.medra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Fetches a mEDRA-registered DOI's ONIX-for-DOI 2.0 XML record from {@code
 * https://api.medra.org/metadata/{doi}} - a DOI-keyed metadata lookup, not a search/list API (no
 * mEDRA equivalent of Crossref's {@code filter=}/DataCite's list query was found), which is why
 * there is no {@code listMetadata}-style method here and no {@code /medra/products} list
 * endpoint in {@code MedraProductsResource}.
 *
 * <p>Returns a raw {@link Response} rather than a typed entity - like {@code
 * CrossrefXmlTransformClient}, there is no registered {@code MessageBodyReader} for ONIX XML, so
 * the caller reads {@code response.readEntity(String.class)} itself and hands it to {@code
 * MedraOnixXmlParser}.
 */
@RegisterRestClient(configKey = "medra-api")
public interface MedraClient {

    @GET
    @Path("/metadata/{doi}")
    Response getMetadata(@PathParam("doi") String doi);
}
