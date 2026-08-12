package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataCiteContributor {

    public String name;
    public String givenName;
    public String familyName;
    public String nameType;
    public String contributorType;
    public List<DataCiteNameIdentifier> nameIdentifiers;
    public List<DataCiteAffiliation> affiliation;
}
