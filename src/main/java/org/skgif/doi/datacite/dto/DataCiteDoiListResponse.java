package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteDoiListResponse {

    public List<DataCiteDoiData> data;
    public Meta meta;
    public Map<String, String> links;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        public long total;
        public int totalPages;
        public int page;
    }
}
