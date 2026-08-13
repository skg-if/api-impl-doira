package org.skgif.doi.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Declares the API's root path, {@code /skg-if/api} (matching the OpenAPI spec's
 * {@code servers[].variables.skg_if_api_path} default). openapi-generator auto-generated this
 * exact class up through 7.10.0, but as of 7.24.0 it only does so when {@code interfaceOnly} is
 * {@code false} (see {@code JavaJAXRSSpecServerCodegen#processOpts}) - this project sets
 * {@code interfaceOnly=true} since every resource is hand-written, so upgrading the generator
 * silently drops the root path unless it's maintained here instead.
 */
@ApplicationPath("/skg-if/api")
public class RestApplication extends Application {
}
