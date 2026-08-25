package org.skgif.doi.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ExternalIdentifierUrlsTest {

    @Test
    void stripOrcidUrl_stripsHttpsPrefix() {
        assertThat(ExternalIdentifierUrls.stripOrcidUrl(ExternalIdentifierUrls.ORCID_BASE_URL + "0000-0002-1008-0687"))
                .isEqualTo("0000-0002-1008-0687");
    }

    @Test
    void stripOrcidUrl_stripsHttpPrefix() {
        assertThat(
                ExternalIdentifierUrls.stripOrcidUrl(ExternalIdentifierUrls.ORCID_HTTP_BASE_URL +
                        "0000-0002-1008-0687"))
                .isEqualTo("0000-0002-1008-0687");
    }

    @Test
    void stripOrcidUrl_passesBareValueThrough() {
        assertThat(ExternalIdentifierUrls.stripOrcidUrl("0000-0002-1008-0687")).isEqualTo("0000-0002-1008-0687");
    }

    @Test
    void stripDoiUrl_stripsHttpsPrefix() {
        assertThat(ExternalIdentifierUrls.stripDoiUrl(ExternalIdentifierUrls.DOI_BASE_URL + "10.1234/abc"))
                .isEqualTo("10.1234/abc");
    }

    @Test
    void stripDoiUrl_stripsHttpPrefix() {
        assertThat(ExternalIdentifierUrls.stripDoiUrl(ExternalIdentifierUrls.DOI_HTTP_BASE_URL + "10.1234/abc"))
                .isEqualTo("10.1234/abc");
    }

    @Test
    void stripDoiUrl_passesBareValueThrough() {
        assertThat(ExternalIdentifierUrls.stripDoiUrl("10.1234/abc")).isEqualTo("10.1234/abc");
    }

    @Test
    void stripRorUrl_stripsHttpsPrefix() {
        assertThat(ExternalIdentifierUrls.stripRorUrl(ExternalIdentifierUrls.ROR_BASE_URL + "02550n020"))
                .isEqualTo("02550n020");
    }

    @Test
    void stripRorUrl_passesBareValueThrough() {
        assertThat(ExternalIdentifierUrls.stripRorUrl("02550n020")).isEqualTo("02550n020");
    }
}
