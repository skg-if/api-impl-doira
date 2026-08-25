package org.skgif.doi.crossref;

import static org.skgif.doi.util.SpotBugsSuppressions.JAXRS_ENDPOINT;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
// Single abstract method by coincidence, not a lambda target: Quarkus generates the implementation
// from @RegisterRestClient, so @FunctionalInterface would advertise a use that never happens.
@Path("/works")
@RegisterRestClient(configKey = "crossref-xml-transform-api")
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface CrossrefXmlTransformClient {

    /**
     * Fetches a work's Crossref XML transform document, which carries venue metadata the REST API omits.
     *
     * @param doi the work's DOI
     * @return the raw XML transform response
     */
    @GET
    @Path("/{doi}/transform/application/vnd.crossref.unixsd+xml")
    @SuppressFBWarnings(value = JAXRS_ENDPOINT, justification = "Taint-source marker, not a finding - no " +
            "injection-family detector traced a dangerous sink from this endpoint's parameters")
    Response getXmlTransform(@PathParam("doi") String doi);
}
