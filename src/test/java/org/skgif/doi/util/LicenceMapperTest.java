package org.skgif.doi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationAccessRights.StatusEnum;

final class LicenceMapperTest {

    private static Stream<Arguments> accessRightsCases() {
        return Stream.of(
                arguments("null list returns null", null, true, null),
                arguments("empty list returns null", List.of(), true, null),
                arguments("single Creative Commons URL is open", List.of(
                        "https://creativecommons.org/licenses/by/4.0/"), false, StatusEnum.OPEN),
                arguments("single non-Creative Commons URL has null status", List.of(
                        "https://example.org/proprietary-licence"), false, null),
                arguments("null first entry with open licence later is open", Arrays.asList(null,
                        "https://creativecommons.org/licenses/by/4.0/"), false, StatusEnum.OPEN));
    }

    // accessRights() tolerates a null URL in any position and still finds an open licence later
    // in the list - this must keep working after sharing the logic.
    @MethodSource("accessRightsCases")
    @ParameterizedTest(name = "{0}")
    void accessRights(String label, List<String> licences, boolean expectNullResult, StatusEnum expectedStatus) {
        if (expectNullResult) {
            assertThat(LicenceMapper.accessRights(licences)).isEmpty();
        } else {
            ProductManifestationAccessRights accessRights = LicenceMapper.accessRights(licences).orElseThrow();
            if (expectedStatus == null) {
                assertThat(accessRights.getStatus()).isNull();
            } else {
                assertThat(accessRights.getStatus()).isEqualTo(expectedStatus);
            }
        }
    }

    private static Stream<Arguments> licenceCases() {
        return Stream.of(
                arguments("null list returns null", null, null),
                arguments("empty list returns null", List.of(), null),
                arguments("returns first entry verbatim", List.of("https://creativecommons.org/licenses/by/4.0/",
                        "https://example.org/other"), "https://creativecommons.org/licenses/by/4.0/"),
                arguments("null first entry with non-null later returns null", Arrays.asList(null,
                        "https://example.org/other"), null));
    }

    // licence() deliberately does NOT skip forward to a later non-null entry when the first
    // entry's URL is itself null - unlike accessRights(), which scans every entry. This asymmetry
    // is intentional and must survive the shared extraction.
    @MethodSource("licenceCases")
    @ParameterizedTest(name = "{0}")
    void licence(String label, List<String> licences, String expected) {
        if (expected == null) {
            assertThat(LicenceMapper.licence(licences)).isEmpty();
        } else {
            assertThat(LicenceMapper.licence(licences)).hasValue(expected);
        }
    }
}
