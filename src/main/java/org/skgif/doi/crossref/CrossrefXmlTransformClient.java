package org.skgif.doi.crossref;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Fetches Crossref's XML "transform" representation of a work
 * ({@code application/vnd.crossref.unixsd+xml}), used only for chapter-in-a-book and
 * paper-in-proceedings records (see {@link CrossrefTypeMapping#isXmlVenueEnrichable}) to get an
 * unambiguous container title/DOI/ISBN/volume that the REST JSON's {@code container-title[]}
 * array doesn't reliably give - see {@code org.skgif.doi.crossref.xml.CrossrefVenueMetadataXmlParser}.
 *
 * <p>The method returns a raw {@link Response} rather than a typed entity - the vendor media type
 * {@code application/vnd.crossref.unixsd+xml} has no registered {@code MessageBodyReader}, so the
 * caller reads {@code response.readEntity(String.class)} itself instead of relying on JAX-RS
 * content-type negotiation.
 */
@RegisterRestClient(configKey = "crossref-xml-transform-api")
@Path("/works")
public interface CrossrefXmlTransformClient {

    @GET
    @Path("/{doi}/transform/application/vnd.crossref.unixsd+xml")
    Response getXmlTransform(@PathParam("doi") String doi);
}
