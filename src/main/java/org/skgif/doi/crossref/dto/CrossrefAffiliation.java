package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Always a plain object in Crossref (unlike DataCite, which can send either a bare string or
 * an object for the same field - see {@code DataCiteAffiliationDeserializer} - so no custom
 * deserializer is needed here). On product/author affiliations, {@code id} is normally absent
 * (name-only); on grant investigator affiliations it's frequently present (usually a ROR).
 *
 * @param name the affiliation name
 * @param country the affiliation's country
 * @param id structured external identifiers for the affiliation (usually a ROR)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefAffiliation(String name, String country, List<CrossrefIdEntry> id) {
}
