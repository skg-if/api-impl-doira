package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

// Top-level Jackson DTO for an entire DataCite work/DOI record - necessarily has many
// independent optional fields; splitting it wouldn't help since Jackson needs one class
// matching the source JSON's shape.
/**
 * A DataCite DOI record's {@code attributes} block - the bulk of the metadata this API maps.
 *
 * @param doi                the record's own DOI
 * @param titles             the record's titles, one per language/type
 * @param creators           the primary authors
 * @param contributors       the non-author contributors, each typed by {@code contributorType}
 * @param publisher          the publishing organisation
 * @param publicationYear    the publication year
 * @param subjects           the subject keywords
 * @param dates              the researcher-asserted dates, each typed by {@code dateType}
 * @param created            the system-generated record creation timestamp
 * @param registered         the system-generated DOI registration timestamp
 * @param published          the system-generated publication timestamp
 * @param updated            the system-generated last-update timestamp
 * @param language           the record's language tag
 * @param types              the resource type block that decides the SKG-IF product_type
 * @param rightsList         the licence/rights statements
 * @param descriptions       the descriptions, including the abstract
 * @param relatedIdentifiers links to other DOIs, each typed by {@code relationType}
 * @param fundingReferences  the grants that funded the work
 * @param version            the version string
 * @param url                the landing-page URL the DOI resolves to
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteAttributes(
        @Nullable String doi,
        @Nullable List<Title> titles,
        @Nullable List<DataCiteCreator> creators,
        @Nullable List<DataCiteContributor> contributors,
        @Nullable String publisher,
        @Nullable Integer publicationYear,
        @Nullable List<DataCiteSubject> subjects,
        @Nullable List<DataCiteDate> dates,
        // System-generated record-lifecycle timestamps, distinct from the researcher-asserted
        // dates[] array above - used by the mapper only as a fallback when dates[] has no
        // Created/Submitted/Updated/Issued entry (see DataCiteManifestationMapper#dates).
        @Nullable String created,
        @Nullable String registered,
        @Nullable String published,
        @Nullable String updated,
        @Nullable String language,
        @Nullable Types types,
        @Nullable List<DataCiteRights> rightsList,
        @Nullable List<DataCiteDescription> descriptions,
        @Nullable List<DataCiteRelatedIdentifier> relatedIdentifiers,
        @Nullable List<DataCiteFundingReference> fundingReferences,
        @Nullable String version,
        @Nullable String url) {

    /**
     * One {@code titles[]} entry, with its optional language tag.
     *
     * @param title the title text
     * @param lang  the title's language tag, if given
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Title(
            @Nullable String title,
            @Nullable String lang) {
    }

    /**
     * The {@code types} block; only {@code resourceTypeGeneral}/{@code resourceType} are modelled.
     *
     * @param resourceTypeGeneral DataCite's controlled vocabulary value, mapped to a product_type
     * @param resourceType        the depositor's free-text refinement of that value
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Types(
            @Nullable String resourceTypeGeneral,
            @Nullable String resourceType) {
    }
}
