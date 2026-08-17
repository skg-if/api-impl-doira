package org.skgif.doi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.skgif.doi.generated.model.AgentAllOfIdentifiers;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.PersonLiteAllOfIdentifiers;

class EntityRefsTest {

    @Test
    void hostingDataSource_buildsOtfIdentifiedDataSource() {
        DataSourceLite hostingDataSource =
                (DataSourceLite) EntityRefs.hostingDataSource("10.1234/abcd", "Example Publisher");

        assertEquals("otf___10-1234-abcd___example-publisher", hostingDataSource.getLocalIdentifier());
        assertEquals("Example Publisher", hostingDataSource.getName());
        assertEquals(DataSourceLite.EntityTypeEnum.DATASOURCE, hostingDataSource.getEntityType());
    }

    @Test
    void hostingDataSource_nullName_stillBuildsWithUnknownSlug() {
        DataSourceLite hostingDataSource =
                (DataSourceLite) EntityRefs.hostingDataSource("10.1234/abcd", null);

        assertEquals("otf___10-1234-abcd___unknown", hostingDataSource.getLocalIdentifier());
        assertNull(hostingDataSource.getName());
    }

    @Test
    void organisationRef_bareRorPresent_isRorIdentified() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example University", "01an7q238");

        assertEquals("https://ror.org/01an7q238", org.getLocalIdentifier());
        assertEquals("Example University", org.getName());
        assertEquals("organisation", org.getEntityType());
        assertEquals(List.of(new AgentAllOfIdentifiers().scheme("ror").value("01an7q238")), org.getIdentifiers());
    }

    @Test
    void organisationRef_noRor_fallsBackToOtfIdWithNoIdentifiers() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example University", null);

        assertEquals("otf___10-1234-abcd___example-university", org.getLocalIdentifier());
        assertEquals("Example University", org.getName());
        assertNull(org.getIdentifiers());
    }

    @Test
    void organisationRef_funderRorPresent_isRorIdentifiedIgnoringDoi() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example Funder", "01an7q238",
                "https://doi.org/10.13039/501100000038", "10.13039/501100000038");

        assertEquals("https://ror.org/01an7q238", org.getLocalIdentifier());
        assertEquals(List.of(new AgentAllOfIdentifiers().scheme("ror").value("01an7q238")), org.getIdentifiers());
    }

    @Test
    void organisationRef_funderDoiPresentNoRor_isDoiIdentified() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example Funder", null,
                "https://doi.org/10.13039/501100000038", "10.13039/501100000038");

        assertEquals("https://doi.org/10.13039/501100000038", org.getLocalIdentifier());
        assertEquals("Example Funder", org.getName());
        assertEquals(List.of(new AgentAllOfIdentifiers().scheme("doi").value("10.13039/501100000038")),
                org.getIdentifiers());
    }

    @Test
    void organisationRef_funderNeitherRorNorDoi_fallsBackToOtfIdWithNoIdentifiers() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example Funder", null, null, null);

        assertEquals("otf___10-1234-abcd___example-funder", org.getLocalIdentifier());
        assertNull(org.getIdentifiers());
    }

    @Test
    void personRef_bareOrcidPresent_isOrcidIdentified() {
        List<PersonLiteAllOfIdentifiers> orcidIdentifiers =
                List.of(new PersonLiteAllOfIdentifiers().scheme("orcid").value("0000-0001-2345-6789"));

        PersonLite person = EntityRefs.personRef("10.1234/abcd", "Jane Doe", "Jane", "Doe",
                "0000-0001-2345-6789", orcidIdentifiers);

        assertEquals("https://orcid.org/0000-0001-2345-6789", person.getLocalIdentifier());
        assertEquals("Jane Doe", person.getName());
        assertEquals("Jane", person.getGivenName());
        assertEquals("Doe", person.getFamilyName());
        assertEquals("person", person.getEntityType());
        assertEquals(orcidIdentifiers, person.getIdentifiers());
    }

    @Test
    void personRef_noOrcid_fallsBackToOtfIdWithNoIdentifiers() {
        PersonLite person = EntityRefs.personRef("10.1234/abcd", "Jane Doe", "Jane", "Doe", null, null);

        assertEquals("otf___10-1234-abcd___jane-doe", person.getLocalIdentifier());
        assertNull(person.getIdentifiers());
    }
}
