package org.skgif.doi.util;

import java.util.List;
import java.util.Optional;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;

/**
 * Access-rights/licence detection shared by every provider's manifestation mapper ({@code
 * CrossrefManifestationMapper}, {@code DataCiteManifestationMapper}) - byte-identical logic
 * (including the same "creativecommons.org" substring check) that was previously duplicated once
 * per mapper class. Operates on each provider's already-extracted list of licence/rights URLs, so
 * no DTO-specific adapter is needed here.
 */
public final class LicenceMapper {

    /** Substring identifying a licence URL as a Creative Commons licence. */
    private static final String CREATIVE_COMMONS_MARKER = "creativecommons.org";

    private LicenceMapper() {
    }

    /**
     * @param licenceUrls the record's licence/rights URLs, in provider order, or null
     * @return an OPEN access-rights status if any URL (nulls tolerated per-entry) is a Creative
     *         Commons licence, else Optional.empty() if licenceUrls is null/empty
     */
    public static Optional<ProductManifestationAccessRights> accessRights(List<String> licenceUrls) {
        if (licenceUrls == null || licenceUrls.isEmpty()) {
            return Optional.empty();
        }
        boolean open = licenceUrls.stream().anyMatch(LicenceMapper::isOpenLicence);
        return Optional.of(new ProductManifestationAccessRights()
                .status(open ? ProductManifestationAccessRights.StatusEnum.OPEN : null));
    }

    // Sole call site is accessRights' `licenceUrls.stream().anyMatch(LicenceMapper::isOpenLicence)`
    // above - PMD's symbol table doesn't reliably trace a private method through a method
    // reference passed as a Predicate<String> argument once the generated OpenAPI sources are on
    // the compile classpath, so it misreports this method as unused.
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private static boolean isOpenLicence(String licenceUrl) {
        return licenceUrl != null && licenceUrl.contains(CREATIVE_COMMONS_MARKER);
    }

    /**
     * @param licenceUrls the record's licence/rights URLs, in provider order, or null
     * @return the first entry verbatim, or Optional.empty() if licenceUrls is null/empty, or if
     *         its first entry is itself null. Deliberately does not skip forward to a later
     *         non-null entry: only the first-listed licence is ever reported here.
     */
    public static Optional<String> licence(List<String> licenceUrls) {
        if (licenceUrls == null || licenceUrls.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(licenceUrls.getFirst());
    }
}
