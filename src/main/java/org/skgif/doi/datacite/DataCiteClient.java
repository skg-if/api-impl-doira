package org.skgif.doi.datacite;

import static org.skgif.doi.util.SpotBugsSuppressions.JAXRS_ENDPOINT;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;

/** MicroProfile REST client for the DataCite REST API's {@code dois} endpoints. */
@Path("/dois")
@RegisterRestClient(configKey = "datacite-api")
public interface DataCiteClient {

    /**
     * Fetches a single DataCite DOI record.
     *
     * @param doi the DOI to fetch
     * @return the DataCite DOI response
     */
    @GET
    @Path("/{doi}")
    @SuppressFBWarnings(value = JAXRS_ENDPOINT, justification = "Taint-source marker, not a finding - no " +
            "injection-family detector traced a dangerous sink from this endpoint's parameters")
    DataCiteDoiResponse getDoi(@PathParam("doi") String doi);

    /**
     * Lists/searches DataCite DOIs matching the given query parameters.
     *
     * @param prefix     DataCite DOI prefix to filter by
     * @param query      DataCite {@code query} parameter (Elasticsearch query string)
     * @param pageSize   maximum number of results to return
     * @param pageNumber page number to fetch
     * @return the matching DataCite DOI list response
     */
    @GET
    DataCiteDoiListResponse listDois(
            @QueryParam("prefix") @Nullable String prefix,
            @QueryParam("query") @Nullable String query,
            @QueryParam("page[size]") @Nullable Integer pageSize,
            @QueryParam("page[number]") @Nullable Integer pageNumber);
}
