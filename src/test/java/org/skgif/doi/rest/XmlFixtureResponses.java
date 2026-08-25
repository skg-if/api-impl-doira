package org.skgif.doi.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

/**
 * Shared classpath-XML-fixture helpers for REST resource tests that mock a Crossref
 * XML-transform/mEDRA ONIX {@link Response}.
 */
public final class XmlFixtureResponses {

    /** HTTP 200 OK status code. */
    private static final int HTTP_OK = 200;

    private XmlFixtureResponses() {
    }

    /**
     * Reads a classpath resource's raw content as a UTF-8 string.
     *
     * @param testClass    the calling test class, used to resolve the resource via its classloader
     * @param resourceName classpath resource name to load
     * @return the resource's raw content
     * @throws IOException if the resource cannot be read
     */
    public static String loadRawResource(Class<?> testClass, String resourceName) throws IOException {
        try (InputStream in = testClass.getClassLoader().getResourceAsStream(resourceName)) {
            requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            return new String(in.readAllBytes(), UTF_8);
        }
    }

    /**
     * A 200 {@link Response} mock wrapping the given XML fixture's raw content. Must be built as
     * its own statement, assigned to a local variable, before being passed to {@code
     * when(...).thenReturn(...)} elsewhere - it opens its own when()/thenReturn() stubs
     * internally, and evaluating it inline as another still-open when(...).thenReturn(...)'s
     * argument corrupts Mockito's single ongoing-stubbing state
     * ({@code UnfinishedStubbingException}).
     *
     * @param testClass       the calling test class, used to resolve the fixture via its classloader
     * @param xmlResourceName classpath resource name of the XML fixture to load
     * @return a mocked 200 {@code Response} whose body is the fixture's raw XML
     * @throws IOException if the fixture resource cannot be read
     */
    public static Response okXmlResponse(Class<?> testClass, String xmlResourceName) throws IOException {
        Response response = mock();
        when(response.getStatus()).thenReturn(HTTP_OK);
        when(response.readEntity(String.class)).thenReturn(loadRawResource(testClass, xmlResourceName));
        return response;
    }
}
