package org.skgif.doi.datacite.mapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteRights;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.util.LicenceMapper;

/**
 * Maps a DataCite record's type/date/access-rights/licence fields onto {@code
 * Product.manifestations[]} (deferring the biblio portion to {@link DataCiteBiblioMapper} and the
 * date portion to {@link DataCiteManifestationDates}). Split out of {@code DataCiteToSkgIfMapper}
 * to keep that class down to orchestration.
 */
final class DataCiteManifestationMapper {

    /** URL of the XSD schema DataCite's {@code resourceType} XML attribute references. */
    private static final String DATACITE_RESOURCE_TYPE_SCHEMA_URL =
            "https://schema.datacite.org/meta/kernel-4.7/include/datacite-resourceType-v4.xsd";

    private DataCiteManifestationMapper() {
    }

    static ProductManifestation manifestation(DataCiteAttributes attributes) {
        return new ProductManifestation()
                .type(manifestationType(attributes).orElse(null))
                .dates(DataCiteManifestationDates.dates(attributes).orElse(null))
                .accessRights(accessRights(attributes).orElse(null))
                .licence(licence(attributes).orElse(null))
                .version(attributes.version())
                .biblio(DataCiteBiblioMapper.biblio(attributes).orElse(null));
    }

    private static Optional<ProductManifestationType> manifestationType(DataCiteAttributes attributes) {
        return resourceTypeGeneral(attributes).map(resourceType -> new ProductManifestationType()
                .definedIn(DATACITE_RESOURCE_TYPE_SCHEMA_URL)
                .labels(Map.of("en", resourceType)));
    }

    static Optional<String> resourceTypeGeneral(DataCiteAttributes attributes) {
        DataCiteAttributes.Types types = attributes.types();
        return Optional.ofNullable(types != null ? types.resourceTypeGeneral() : null);
    }

    private static Optional<ProductManifestationAccessRights> accessRights(DataCiteAttributes attributes) {
        return LicenceMapper.accessRights(licenceUrls(attributes));
    }

    private static Optional<String> licence(DataCiteAttributes attributes) {
        return LicenceMapper.licence(licenceUrls(attributes));
    }

    private static List<String> licenceUrls(DataCiteAttributes attributes) {
        List<DataCiteRights> rightsList = attributes.rightsList();
        return rightsList == null ? List.of() : rightsList.stream().map(DataCiteRights::rightsUri).toList();
    }
}
