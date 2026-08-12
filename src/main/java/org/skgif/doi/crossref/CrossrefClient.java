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

    @GET
    @Path("/{doi}")
    CrossrefWorkResponse getWork(@PathParam("doi") String doi);

    @GET
    CrossrefWorkListResponse listWorks(
            @QueryParam("filter") String filter,
            @QueryParam("query.title") String queryTitle,
            @QueryParam("query.bibliographic") String queryBibliographic,
            @QueryParam("rows") Integer rows,
            @QueryParam("offset") Integer offset,
            @QueryParam("mailto") String mailto);
}
