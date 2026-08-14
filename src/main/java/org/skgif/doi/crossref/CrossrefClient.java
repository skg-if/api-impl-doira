package org.skgif.doi.crossref;

import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "crossref-api")
@Path("/works")
public interface CrossrefClient {

    /**
     * Fetches a single Crossref work record by DOI.
     *
     * @param doi the work's DOI
     * @return the Crossref work response
     */
    @GET
    @Path("/{doi}")
    CrossrefWorkResponse getWork(@PathParam("doi") String doi);

    /**
     * Lists/searches Crossref works matching the given query parameters.
     *
     * @param filter Crossref {@code filter} query parameter, comma-joined sub-clauses
     * @param queryTitle Crossref {@code query.title} query parameter
     * @param queryBibliographic Crossref {@code query.bibliographic} query parameter
     * @param rows maximum number of results to return
     * @param offset number of results to skip
     * @param mailto contact email for Crossref's polite-pool API access
     * @return the matching Crossref work list response
     */
    // Each parameter maps 1:1 to its own @QueryParam - the standard MicroProfile REST Client
    // interface shape; bundling them into a container object would need a @BeanParam DTO for no
    // real clarity gain over six named, individually-documented query parameters.
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    @GET
    CrossrefWorkListResponse listWorks(
            @QueryParam("filter") String filter,
            @QueryParam("query.title") String queryTitle,
            @QueryParam("query.bibliographic") String queryBibliographic,
            @QueryParam("rows") Integer rows,
            @QueryParam("offset") Integer offset,
            @QueryParam("mailto") String mailto);
}
