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

    @GET
    @Path("/{doi}")
    DataCiteDoiResponse getDoi(@PathParam("doi") String doi);

    @GET
    DataCiteDoiListResponse listDois(
            @QueryParam("prefix") String prefix,
            @QueryParam("query") String query,
            @QueryParam("page[size]") Integer pageSize,
            @QueryParam("page[number]") Integer pageNumber);
}
