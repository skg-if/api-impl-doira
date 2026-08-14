package org.skgif.doi.medra.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.skgif.doi.medra.dto.MedraContributor;
import org.skgif.doi.medra.dto.MedraTitle;
import org.skgif.doi.medra.dto.MedraWork;

class MedraOnixXmlParserTest {

    private MedraWork parseFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return MedraOnixXmlParser.parse(xml)
                    .orElseThrow(() -> new AssertionError("Fixture did not parse: " + resourceName));
        }
    }

    @Test
    void parsesAllFourContributorNameFieldsWhenAllArePresent() throws IOException {
        MedraWork work = parseFixture("medra-mixed-name-shapes.xml");

        assertEquals("10.19276/plinius.2019.01004", work.doi());
        assertEquals(1, work.contributors().size());
        MedraContributor contributor = work.contributors().get(0);
        assertEquals("A01", contributor.role());
        assertEquals("Daniela", contributor.namesBeforeKey());
        assertEquals("D'Alessio", contributor.keyNames());
        assertEquals("Daniela D'Alessio", contributor.personName());
        assertEquals("D'Alessio, Daniela", contributor.personNameInverted());

        assertEquals(1, work.titles().size());
        assertEquals("Synthesis, phase transitions, degassing behaviour of melanophlogite (type I clathrate)",
                work.titles().get(0).text());
        assertEquals("Plinius", work.journalTitle());
        assertEquals(java.util.List.of("1972-1366"), work.issns());
        assertNull(work.abstractText());
        assertEquals("2019", work.publicationDate());
        assertEquals("DOISerialArticleWork", work.workElementName());
    }

    @Test
    void parsesPersonNameOnlyContributorsWithNoInvertedForm() throws IOException {
        MedraWork work = parseFixture("medra-version-message-book-series.xml");

        assertEquals("10.3254/978-1-61499-732-0-119", work.doi());
        assertEquals(2, work.contributors().size());
        MedraContributor first = work.contributors().get(0);
        assertEquals("Cotte M.", first.personName());
        assertNull(first.personNameInverted());
        assertNull(first.namesBeforeKey());
        assertNull(first.keyNames());

        assertTrue(work.abstractText() != null && work.abstractText().startsWith("Synchrotron radiation"));
        assertEquals(java.util.List.of("0074-784X"), work.issns());
        // ...VersionRegistrationMessage variant - wraps its fields in DOISerialArticleVersion,
        // not DOISerialArticleWork (see medra-mixed-name-shapes.xml's assertion above).
        assertEquals("DOISerialArticleVersion", work.workElementName());
    }

    @Test
    void parsesNamesBeforeKeyAndKeyNamesForManyAuthors() throws IOException {
        MedraWork work = parseFixture("medra-many-authors.xml");

        assertEquals(23, work.contributors().size());
        MedraContributor first = work.contributors().get(0);
        assertEquals("L.", first.namesBeforeKey());
        assertEquals("Baldesi", first.keyNames());
        assertNull(first.personName());
        assertNull(first.personNameInverted());
        assertTrue(work.abstractText() != null && work.abstractText().startsWith("This study investigates"));
    }

    @Test
    void parsesEmptyContributorListWhenNoContributorElementExists() throws IOException {
        MedraWork work = parseFixture("medra-no-contributors.xml");

        assertEquals("10.1393/ncc/i2021-21084-7", work.doi());
        assertTrue(work.contributors().isEmpty());
    }

    @Test
    void distinguishesArticleTitleFromJournalLevelTitlesAndPicksFirstFullJournalTitle() throws IOException {
        MedraWork work = parseFixture("medra-multilang-titles.xml");

        assertEquals(1, work.titles().size());
        MedraTitle articleTitle = work.titles().get(0);
        assertEquals("Transverse THz dynamics of phospholipid membranes: A neutron scattering study",
                articleTitle.text());
        assertNull(articleTitle.language());

        // Journal level has 4 Title entries (2 languages x 2 TitleTypes) - first TitleType "01" in
        // document order is the Italian one.
        assertEquals("Atti della Accademia Perloritana dei Pericolanti. Classe di Scienze Fisiche, Matematiche"
                + " e Naturali", work.journalTitle());
        assertEquals(java.util.List.of("18251242"), work.issns());
        assertEquals(8, work.contributors().size());
    }

    @Test
    void splitsOffOnlyPersonNameInvertedWhenNoOtherNameFieldIsPresent() throws IOException {
        MedraWork work = parseFixture("medra-personname-inverted-only.xml");

        assertEquals(1, work.contributors().size());
        MedraContributor contributor = work.contributors().get(0);
        assertEquals("Fragneto, Giovanna", contributor.personNameInverted());
        assertNull(contributor.personName());
        assertNull(contributor.namesBeforeKey());
        assertNull(contributor.keyNames());

        // SerialVersion's ProductIdentifier here is coded ProductIDType "06" (DOI), not "07"
        // (ISSN) - the parser must not misread it as an ISSN.
        assertTrue(work.issns().isEmpty());
        assertEquals("Sapere 4/2018", work.journalTitle());
    }

    @Test
    void picksOnlyTheIssnTypedIdentifierWhenAProprietaryIdSharesTheSameSerialVersion() throws IOException {
        MedraWork work = parseFixture("medra-multiple-product-identifiers.xml");

        assertEquals("10.1400/255846", work.doi());
        // SerialVersion here carries two ProductIdentifier siblings - ProductIDType "01"
        // (proprietary, "4242485") and "07" (ISSN, "19711131") - the parser must pick only the
        // ISSN one, not the proprietary id alongside it.
        assertEquals(java.util.List.of("19711131"), work.issns());
        assertEquals("History of Education and Children's Literature", work.journalTitle());

        // This ContentItem has no PublicationDate at all (only a JournalIssueDate, which is
        // deliberately not mapped - see MedraToSkgIfMapper#isoDate).
        assertNull(work.publicationDate());

        assertEquals(1, work.contributors().size());
        MedraContributor contributor = work.contributors().get(0);
        assertEquals("Camara Bastos, Maria Helena", contributor.personNameInverted());
        assertNull(contributor.personName());
    }

    @Test
    void returnsEmptyWhenXmlIsBlank() {
        assertTrue(MedraOnixXmlParser.parse("").isEmpty());
        assertTrue(MedraOnixXmlParser.parse(null).isEmpty());
    }

    @Test
    void returnsEmptyWhenXmlIsUnparseable() {
        assertTrue(MedraOnixXmlParser.parse("<not-well-formed-xml").isEmpty());
    }
}
