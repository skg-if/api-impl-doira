package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A single Crossref {@code works} record ({@code message} in the API envelope). Unlike
 * DataCite's JSON:API response (camelCase keys, matched by Jackson without annotations),
 * Crossref's own JSON uses kebab-case/all-caps keys, so every non-matching field needs an
 * explicit {@link JsonProperty}.
 *
 * <p>{@code award} and {@code project} are only present on grant-type records ({@code
 * type: "grant"} - see {@code CrossrefTypeMapping#isGrant}); every other field is a regular
 * work (article, dataset, book, etc).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefWork {

    @JsonProperty("DOI")
    public String doi;
    @JsonProperty("URL")
    public String url;
    public String type;
    public String publisher;
    public List<String> title;
    public List<String> subtitle;
    @JsonProperty("container-title")
    public List<String> containerTitle;
    public String page;
    public String volume;
    public String issue;
    @JsonProperty("abstract")
    public String abstractText;
    public List<String> subject;
    @JsonProperty("ISSN")
    public List<String> issn;
    public List<CrossrefContributor> author;
    public List<CrossrefContributor> editor;
    public List<CrossrefFunder> funder;
    public List<CrossrefLicense> license;
    public List<CrossrefReference> reference;

    public CrossrefDate issued;
    public CrossrefDate created;
    public CrossrefDate deposited;
    @JsonProperty("published-print")
    public CrossrefDate publishedPrint;
    @JsonProperty("published-online")
    public CrossrefDate publishedOnline;
    public CrossrefDate posted;
    public CrossrefDate accepted;
    @JsonProperty("update-to")
    public List<CrossrefUpdateTo> updateTo;

    // Grant-type records only.
    public String award;
    public List<CrossrefProject> project;
    public CrossrefResource resource;
}
