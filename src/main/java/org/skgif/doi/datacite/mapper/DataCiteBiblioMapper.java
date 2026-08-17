package org.skgif.doi.datacite.mapper;

import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.generated.model.ProductManifestationBiblio;
import org.skgif.doi.generated.model.ProductManifestationBiblioHostingDataSource;
import org.skgif.doi.util.EntityRefs;

/**
 * Maps a DataCite record's publisher field onto {@code Product.manifestations[].biblio}. Split
 * out of {@code DataCiteToSkgIfMapper} to keep that class down to orchestration. Unlike Crossref's
 * equivalent, DataCite never populates {@code biblio.in} (no container-title/ISSN source), so
 * there's no venue resolution here and no need for {@code LocalIdentifiers}.
 */
final class DataCiteBiblioMapper {

    private DataCiteBiblioMapper() {
    }

    static ProductManifestationBiblio biblio(DataCiteAttributes attributes) {
        if (attributes.publisher() == null) {
            return null;
        }
        return new ProductManifestationBiblio().hostingDataSource(hostingDataSource(attributes));
    }

    /**
     * DataCite's own {@code publisher} field is the closest generic equivalent of "where this
     * record is hosted" - unlike an organisation's ROR, there's no external identifier system
     * for an arbitrary publisher string, so this always gets an otf id.
     *
     * @param attributes the DataCite record to derive a hosting data source from
     * @return a DataSourceLite for attributes.publisher, with an otf local_identifier
     */
    private static ProductManifestationBiblioHostingDataSource hostingDataSource(DataCiteAttributes attributes) {
        return EntityRefs.hostingDataSource(attributes.doi(), attributes.publisher());
    }
}
