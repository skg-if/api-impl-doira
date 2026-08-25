package org.skgif.doi.crossref;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;

/** MicroProfile REST client for the Crossref REST API's {@code works} endpoints. */
@Path("/works")
@RegisterRestClient(configKey = "crossref-api")
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
     * <p>A null argument omits that query parameter from the outgoing request, which is the
     * standard MicroProfile REST Client behaviour - so the {@code @Nullable} parameters below are
     * the ones Crossref treats as genuinely optional, not merely ones some caller happens to
     * leave unset.
     *
     * @param filter             Crossref {@code filter} query parameter, comma-joined sub-clauses
     * @param queryTitle         Crossref {@code query.title} query parameter, or null to omit
     * @param queryBibliographic Crossref {@code query.bibliographic} parameter, or null to omit
     * @param rows               maximum number of results to return
     * @param offset             number of results to skip, or null to omit
     * @param mailto             contact email for Crossref's polite-pool API access, or null if
     *                           none is configured
     * @return the matching Crossref work list response
     */
    // Each parameter maps 1:1 to its own @QueryParam - the standard MicroProfile REST Client
    // interface shape; bundling them into a container object would need a @BeanParam DTO for no
    // real clarity gain over six named, individually-documented query parameters.
    @GET
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    CrossrefWorkListResponse listWorks(
            @QueryParam("filter") @Nullable String filter,
            @QueryParam("query.title") @Nullable String queryTitle,
            @QueryParam("query.bibliographic") @Nullable String queryBibliographic,
            @QueryParam("rows") Integer rows,
            @QueryParam("offset") @Nullable Integer offset,
            @QueryParam("mailto") @Nullable String mailto);
}
