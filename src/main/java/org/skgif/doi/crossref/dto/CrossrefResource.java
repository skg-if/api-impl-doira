package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefResource {

    public Primary primary;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Primary {
        @JsonProperty("URL")
        public String url;
    }
}
