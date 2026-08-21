package org.skgif.doi.crossref;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;

/**
 * Fetches a single {@link CrossrefWork} by DOI, shared by {@code CrossrefGrantsResource}/{@code
 * CrossrefProductsResource}'s {@code get*ById} endpoints.
 */
public final class CrossrefWorkFetcher {

    private CrossrefWorkFetcher() {
    }

    /**
     * @param crossrefClient the Crossref REST client to fetch the work from
     * @param doi            the DOI to look up
     * @return the fetched work, or empty if not found (a 404 response, or a null/DOI-less body) -
     *         any other {@link WebApplicationException} status is rethrown
     */
    public static Optional<CrossrefWork> fetchByDoi(CrossrefClient crossrefClient, String doi) {
        CrossrefWork work = null;
        try {
            CrossrefWorkResponse response = crossrefClient.getWork(doi);
            if (response != null) {
                work = response.message();
            }
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return Optional.empty();
            }
            throw e;
        }
        return work == null || work.doi() == null ? Optional.empty() : Optional.of(work);
    }
}
