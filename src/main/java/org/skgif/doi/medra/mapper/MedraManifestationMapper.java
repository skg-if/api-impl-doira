package org.skgif.doi.medra.mapper;

import java.util.Map;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.medra.dto.MedraWork;

/**
 * Maps a mEDRA ONIX-for-DOI record's type/date fields onto {@code Product.manifestations[]}
 * (deferring the biblio/venue portion to {@link MedraBiblioMapper}). Split out of {@code
 * MedraToSkgIfMapper} to keep that class down to orchestration.
 */
final class MedraManifestationMapper {

    private static final String ONIX_SERIAL_ARTICLE_SPEC_URL =
            "https://www.medra.org/stdoc/ONIX_DOI_Serial_Article_2.0_v.2.pdf";
    private static final int YEAR_LENGTH = 4;
    private static final int YEAR_MONTH_LENGTH = 6;
    private static final int FULL_DATE_LENGTH = 8;

    private MedraManifestationMapper() {
    }

    static ProductManifestation manifestation(MedraWork work) {
        return new ProductManifestation()
                .type(manifestationType(work))
                .dates(dates(work))
                .biblio(MedraBiblioMapper.biblio(work));
    }

    /**
     * The label is the record's own {@code workElementName} ({@code DOISerialArticleWork} or
     * {@code DOISerialArticleVersion}, whichever ONIX-DOI message variant registered it) rather
     * than a fixed string - read straight off the document instead of hardcoded, unlike {@code
     * product_type} (always {@code literature}, since only this one schema family is handled).
     *
     * @param work the mEDRA record to read the manifestation type label from
     * @return the mapped ProductManifestationType, or null if work.workElementName() is null
     */
    private static ProductManifestationType manifestationType(MedraWork work) {
        if (work.workElementName() == null) {
            return null;
        }
        return new ProductManifestationType()
                .definedIn(ONIX_SERIAL_ARTICLE_SPEC_URL)
                .labels(Map.of("en", work.workElementName()));
    }

    private static ProductManifestationDates dates(MedraWork work) {
        String iso = isoDate(work.publicationDate());
        return iso == null ? null : new ProductManifestationDates().addPublicationItem(iso);
    }

    /**
     * mEDRA's {@code PublicationDate} is a bare digit string with no format marker (unlike {@code
     * JournalIssueDate}, which carries an explicit {@code DateFormat} code and has no clean
     * SKG-IF home of its own - see SKG_IF_DOI_MAPPING_DATES.md) - length alone distinguishes
     * year-only ("2019"), year+month ("202103"), and full-date ("20210813") forms, confirmed
     * across all 6 fixtures.
     *
     * @param raw the raw mEDRA PublicationDate digit string, or null
     * @return the ISO-normalized date (year, year-month, or full date), or null if unrecognized
     */
    private static String isoDate(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.length()) {
            case YEAR_LENGTH -> raw;
            case YEAR_MONTH_LENGTH ->
                raw.substring(0, YEAR_LENGTH) + "-" + raw.substring(YEAR_LENGTH, YEAR_MONTH_LENGTH);
            case FULL_DATE_LENGTH ->
                raw.substring(0, YEAR_LENGTH) + "-" + raw.substring(YEAR_LENGTH, YEAR_MONTH_LENGTH) +
                        "-" + raw.substring(YEAR_MONTH_LENGTH, FULL_DATE_LENGTH);
            default -> null;
        };
    }
}
