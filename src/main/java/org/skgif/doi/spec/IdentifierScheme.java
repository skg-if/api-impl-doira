package org.skgif.doi.spec;

/**
 * The SKG-IF external identifier {@code scheme} values documented at
 * https://skg-if.github.io/interoperability-framework/#external-identifiers-of-entities - single
 * source of truth so every provider's mapper and REST filter uses the same scheme strings.
 */
public enum IdentifierScheme {

    ARXIV("arxiv"),
    BIBCODE("bibcode"),
    CROSSREF("crossref"),
    DOI("doi"),
    EISSN("eissn"),
    HANDLE("handle"),
    ISBN("isbn"),
    ISSN("issn"),
    IVOID("ivoid"),
    LISSN("lissn"),
    OMID("omid"),
    OPENALEX("openalex"),
    OPENDOAR("opendoar"),
    ORCID("orcid"),
    PMCID("pmcid"),
    PMID("pmid"),
    ROR("ror"),
    SPASE("spase"),
    URL("url"),
    URN("urn"),
    VIAF("viaf"),
    W3ID("w3id");

    // Field intentionally shares its name with its accessor below, same idiom
    // EntityTypes' field already does this for.
    /** The constant's underlying SKG-IF {@code identifiers[].scheme} string. */
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    private final String value;

    IdentifierScheme(String value) {
        this.value = value;
    }

    /**
     * @return the SKG-IF {@code identifiers[].scheme} string this constant represents
     */
    public String value() {
        return value;
    }
}
