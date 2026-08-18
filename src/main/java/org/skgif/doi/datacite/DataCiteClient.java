package org.skgif.doi.datacite;

import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "datacite-api")
@Path("/dois")
public interface DataCiteClient {

    /**
     * Fetches a single DataCite DOI record.
     *
     * @param doi the DOI to fetch
     * @return the DataCite DOI response
     */
    @GET
    @Path("/{doi}")
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
                                     @QueryParam("prefix") String prefix,
                                     @QueryParam("query") String query,
                                     @QueryParam("page[size]") Integer pageSize,
                                     @QueryParam("page[number]") Integer pageNumber);
}
