package org.skgif.doi.medra.xml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.skgif.doi.medra.dto.MedraContributor;
import org.skgif.doi.medra.dto.MedraTitle;
import org.skgif.doi.medra.dto.MedraWork;

class MedraOnixXmlParserTest {

    private MedraWork parseFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            Objects.requireNonNull(in, "Fixture not found on classpath: " + resourceName);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MedraOnixXmlParser.parse(xml)
                    .orElseThrow(() -> new AssertionError("Fixture did not parse: " + resourceName));
        }
    }

    @Test
    void parsesAllFourContributorNameFieldsWhenAllArePresent() throws IOException {
        MedraWork work = parseFixture("medra-mixed-name-shapes.xml");

        assertThat(work.doi()).isEqualTo("10.19276/plinius.2019.01004");
        assertThat(work.contributors()).hasSize(1);
        MedraContributor contributor = Objects.requireNonNull(work.contributors()).getFirst();
        assertThat(contributor.role()).isEqualTo("A01");
        assertThat(contributor.namesBeforeKey()).isEqualTo("Daniela");
        assertThat(contributor.keyNames()).isEqualTo("D'Alessio");
        assertThat(contributor.personName()).isEqualTo("Daniela D'Alessio");
        assertThat(contributor.personNameInverted()).isEqualTo("D'Alessio, Daniela");

        assertThat(work.titles()).hasSize(1);
        assertThat(Objects.requireNonNull(work.titles()).getFirst().text())
                .isEqualTo("Synthesis, phase transitions, degassing behaviour of melanophlogite (type I clathrate)");
        assertThat(work.journalTitle()).isEqualTo("Plinius");
        assertThat(work.issns()).isEqualTo(java.util.List.of("1972-1366"));
        assertThat(work.abstractText()).isNull();
        assertThat(work.publicationDate()).isEqualTo("2019");
        assertThat(work.workElementName()).isEqualTo("DOISerialArticleWork");
    }

    @Test
    void parsesPersonNameOnlyContributorsWithNoInvertedForm() throws IOException {
        MedraWork work = parseFixture("medra-version-message-book-series.xml");

        assertThat(work.doi()).isEqualTo("10.3254/978-1-61499-732-0-119");
        assertThat(work.contributors()).hasSize(2);
        MedraContributor first = Objects.requireNonNull(work.contributors()).getFirst();
        assertThat(first.personName()).isEqualTo("Cotte M.");
        assertThat(first.personNameInverted()).isNull();
        assertThat(first.namesBeforeKey()).isNull();
        assertThat(first.keyNames()).isNull();

        assertThat(work.abstractText()).startsWith("Synchrotron radiation");
        assertThat(work.issns()).isEqualTo(java.util.List.of("0074-784X"));
        // ...VersionRegistrationMessage variant - wraps its fields in DOISerialArticleVersion,
        // not DOISerialArticleWork (see medra-mixed-name-shapes.xml's assertion above).
        assertThat(work.workElementName()).isEqualTo("DOISerialArticleVersion");
    }

    @Test
    void parsesNamesBeforeKeyAndKeyNamesForManyAuthors() throws IOException {
        MedraWork work = parseFixture("medra-many-authors.xml");

        final int expectedContributorCount = 23;
        assertThat(work.contributors()).hasSize(expectedContributorCount);
        MedraContributor first = Objects.requireNonNull(work.contributors()).getFirst();
        assertThat(first.namesBeforeKey()).isEqualTo("L.");
        assertThat(first.keyNames()).isEqualTo("Baldesi");
        assertThat(first.personName()).isNull();
        assertThat(first.personNameInverted()).isNull();
        assertThat(work.abstractText()).startsWith("This study investigates");
    }

    @Test
    void parsesEmptyContributorListWhenNoContributorElementExists() throws IOException {
        MedraWork work = parseFixture("medra-no-contributors.xml");

        assertThat(work.doi()).isEqualTo("10.1393/ncc/i2021-21084-7");
        assertThat(work.contributors()).isEmpty();
    }

    @Test
    void distinguishesArticleTitleFromJournalLevelTitlesAndPicksFirstFullJournalTitle() throws IOException {
        MedraWork work = parseFixture("medra-multilang-titles.xml");

        assertThat(work.titles()).hasSize(1);
        MedraTitle articleTitle = Objects.requireNonNull(work.titles()).getFirst();
        assertThat(articleTitle.text())
                .isEqualTo("Transverse THz dynamics of phospholipid membranes: A neutron scattering study");
        assertThat(articleTitle.language()).isNull();

        // Journal level has 4 Title entries (2 languages x 2 TitleTypes) - first TitleType "01" in
        // document order is the Italian one.
        assertThat(work.journalTitle()).isEqualTo("Atti della Accademia Perloritana dei Pericolanti. Classe di" +
                " Scienze Fisiche, Matematiche e Naturali");
        assertThat(work.issns()).isEqualTo(java.util.List.of("18251242"));
        final int expectedContributorCount = 8;
        assertThat(work.contributors()).hasSize(expectedContributorCount);
    }

    @Test
    void splitsOffOnlyPersonNameInvertedWhenNoOtherNameFieldIsPresent() throws IOException {
        MedraWork work = parseFixture("medra-personname-inverted-only.xml");

        assertThat(work.contributors()).hasSize(1);
        MedraContributor contributor = Objects.requireNonNull(work.contributors()).getFirst();
        assertThat(contributor.personNameInverted()).isEqualTo("Fragneto, Giovanna");
        assertThat(contributor.personName()).isNull();
        assertThat(contributor.namesBeforeKey()).isNull();
        assertThat(contributor.keyNames()).isNull();

        // SerialVersion's ProductIdentifier here is coded ProductIDType "06" (DOI), not "07"
        // (ISSN) - the parser must not misread it as an ISSN.
        assertThat(work.issns()).isEmpty();
        assertThat(work.journalTitle()).isEqualTo("Sapere 4/2018");
    }

    @Test
    void picksOnlyTheIssnTypedIdentifierWhenAProprietaryIdSharesTheSameSerialVersion() throws IOException {
        MedraWork work = parseFixture("medra-multiple-product-identifiers.xml");

        assertThat(work.doi()).isEqualTo("10.1400/255846");
        // SerialVersion here carries two ProductIdentifier siblings - ProductIDType "01"
        // (proprietary, "4242485") and "07" (ISSN, "19711131") - the parser must pick only the
        // ISSN one, not the proprietary id alongside it.
        assertThat(work.issns()).isEqualTo(java.util.List.of("19711131"));
        assertThat(work.journalTitle()).isEqualTo("History of Education and Children's Literature");

        // This ContentItem has no PublicationDate at all (only a JournalIssueDate, which is
        // deliberately not mapped - see MedraManifestationMapper#isoDate).
        assertThat(work.publicationDate()).isNull();

        assertThat(work.contributors()).hasSize(1);
        MedraContributor contributor = Objects.requireNonNull(work.contributors()).getFirst();
        assertThat(contributor.personNameInverted()).isEqualTo("Camara Bastos, Maria Helena");
        assertThat(contributor.personName()).isNull();
    }

    @Test
    void returnsEmptyWhenXmlIsBlank() {
        assertThat(MedraOnixXmlParser.parse("")).isEmpty();
        assertThat(MedraOnixXmlParser.parse(null)).isEmpty();
    }

    @Test
    void returnsEmptyWhenXmlIsUnparseable() {
        assertThat(MedraOnixXmlParser.parse("<not-well-formed-xml")).isEmpty();
    }
}
