package org.skgif.doi.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.skgif.doi.crossref.CrossrefXmlTransformClient;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadataXmlParser;

/**
 * Fetches and parses Crossref's XML transform for a chapter-in-a-book or paper-in-proceedings
 * record (see {@code CrossrefTypeMapping#isXmlVenueEnrichable}), used by {@link
 * CrossrefProductsResource} to build an accurate Venue - see {@code CrossrefBiblioMapper#venue}.
 * Only called from the single-item {@code getProductById} endpoint, not the list endpoint (which
 * would otherwise mean N extra Crossref HTTP calls per page). Any failure - non-200 response,
 * network/timeout error, or a shape the parser doesn't recognize - degrades to {@code null}, so
 * the caller falls back to the existing {@code container-title[0]} venue rather than failing the
 * whole product response over an enrichment call.
 */
@ApplicationScoped
public class CrossrefVenueEnricher {

    /** The Crossref XML-transform REST client used for venue enrichment. */
    private final CrossrefXmlTransformClient crossrefXmlTransformClient;

    /**
     * @param crossrefXmlTransformClient the Crossref XML-transform REST client used for venue enrichment
     */
    @Inject
    public CrossrefVenueEnricher(@RestClient CrossrefXmlTransformClient crossrefXmlTransformClient) {
        this.crossrefXmlTransformClient = crossrefXmlTransformClient;
    }

    /**
     * @param doi the DOI to fetch XML venue metadata for
     * @return the parsed venue metadata, or Optional.empty() if the fetch/parse fails or finds
     *         nothing
     */
    public Optional<CrossrefVenueMetadata> fetchVenueMetadata(String doi) {
        try (Response response = crossrefXmlTransformClient.getXmlTransform(doi)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                return Optional.empty();
            }
            return CrossrefVenueMetadataXmlParser.parse(response.readEntity(String.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
