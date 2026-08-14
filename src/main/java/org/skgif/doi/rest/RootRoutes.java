package org.skgif.doi.rest;

import io.vertx.ext.web.Router;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;

/**
 * Redirects the true site root ("/") to Swagger UI, standing in for Quarkus's default landing
 * page. This has to be a raw Vert.x route, not a JAX-RS resource - every {@code @Path} in this
 * project (including a bare "/") is prefixed by {@link RestApplication}'s
 * {@code @ApplicationPath("/skg-if/api")}, so JAX-RS alone can't reach the actual root. Quarkus
 * also refuses {@code quarkus.swagger-ui.path=/} outright (it would act as a catch-all and
 * block every other route), so this redirects to the actual (default) Swagger UI path instead.
 */
public class RootRoutes {

    void addRootRedirect(@Observes Router router) {
        router.get("/").handler(rc -> rc.response()
                .setStatusCode(Response.Status.SEE_OTHER.getStatusCode())
                .putHeader("Location", "/q/swagger-ui")
                .end());
    }
}
