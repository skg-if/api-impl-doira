package org.skgif.doi.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared classpath-XML-fixture helpers for REST resource tests that mock a Crossref
 * XML-transform/mEDRA ONIX {@link Response}.
 */
final class XmlFixtureResponses {

    /** HTTP 200 OK status code. */
    private static final int HTTP_OK = 200;

    private XmlFixtureResponses() {
    }

    static String loadRawResource(Class<?> testClass, String resourceName) throws IOException {
        try (InputStream in = testClass.getClassLoader().getResourceAsStream(resourceName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
    static Response okXmlResponse(Class<?> testClass, String xmlResourceName) throws IOException {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HTTP_OK);
        when(response.readEntity(String.class)).thenReturn(loadRawResource(testClass, xmlResourceName));
        return response;
    }
}
