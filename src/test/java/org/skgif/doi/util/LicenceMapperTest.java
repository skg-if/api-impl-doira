package org.skgif.doi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;

class LicenceMapperTest {

    @Test
    void accessRights_nullList_returnsNull() {
        assertNull(LicenceMapper.accessRights(null));
    }

    @Test
    void accessRights_emptyList_returnsNull() {
        assertNull(LicenceMapper.accessRights(List.of()));
    }

    @Test
    void accessRights_singleCreativeCommonsUrl_isOpen() {
        ProductManifestationAccessRights accessRights =
                LicenceMapper.accessRights(List.of("https://creativecommons.org/licenses/by/4.0/"));
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, accessRights.getStatus());
    }

    @Test
    void accessRights_singleNonCreativeCommonsUrl_hasNullStatus() {
        ProductManifestationAccessRights accessRights =
                LicenceMapper.accessRights(List.of("https://example.org/proprietary-licence"));
        assertNull(accessRights.getStatus());
    }

    /**
     * accessRights() tolerates a null URL in any position and still finds an open licence later
     * in the list - this must keep working after sharing the logic.
     */
    @Test
    void accessRights_nullFirstEntryWithOpenLicenceLater_isOpen() {
        ProductManifestationAccessRights accessRights =
                LicenceMapper.accessRights(Arrays.asList(null, "https://creativecommons.org/licenses/by/4.0/"));
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, accessRights.getStatus());
    }

    @Test
    void licence_nullList_returnsNull() {
        assertNull(LicenceMapper.licence(null));
    }

    @Test
    void licence_emptyList_returnsNull() {
        assertNull(LicenceMapper.licence(List.of()));
    }

    @Test
    void licence_returnsFirstEntryVerbatim() {
        assertEquals("https://creativecommons.org/licenses/by/4.0/",
                LicenceMapper.licence(
                        List.of("https://creativecommons.org/licenses/by/4.0/", "https://example.org/other")));
    }

    /**
     * licence() deliberately does NOT skip forward to a later non-null entry when the first
     * entry's URL is itself null - unlike accessRights(), which scans every entry. This
     * asymmetry is intentional and must survive the shared extraction.
     */
    @Test
    void licence_nullFirstEntryWithNonNullLater_returnsNull() {
        assertNull(LicenceMapper.licence(Arrays.asList(null, "https://example.org/other")));
    }
}
