package org.skgif.doi.datacite;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.skgif.doi.datacite.dto.DataCiteDoiData;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;

/**
 * Fetches a single {@link DataCiteDoiData} record by DOI, shared by {@code
 * DataCiteGrantsResource}/{@code DataCiteProductsResource}'s {@code get*ById} endpoints.
 */
public final class DataCiteDoiFetcher {

    private DataCiteDoiFetcher() {
    }

    /**
     * @param dataCiteClient the DataCite REST client to fetch the record from
     * @param doi            the DOI to look up
     * @return the fetched record, or empty if not found (a 404 response, or a null/attributes-less
     *         body) - any other {@link WebApplicationException} status is rethrown
     */
    public static Optional<DataCiteDoiData> fetchByDoi(DataCiteClient dataCiteClient, String doi) {
        DataCiteDoiData data = null;
        try {
            DataCiteDoiResponse response = dataCiteClient.getDoi(doi);
            if (response != null) {
                data = response.data();
            }
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return Optional.empty();
            }
            throw e;
        }
        return data == null || data.attributes() == null ? Optional.empty() : Optional.of(data);
    }
}
