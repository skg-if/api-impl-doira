package org.skgif.doi.util;

import static org.assertj.core.api.Assertions.assertThat;

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

        assertThat(hostingDataSource.getLocalIdentifier()).isEqualTo("otf___10-1234-abcd___example-publisher");
        assertThat(hostingDataSource.getName()).isEqualTo("Example Publisher");
        assertThat(hostingDataSource.getEntityType()).isEqualTo(DataSourceLite.EntityTypeEnum.DATASOURCE);
    }

    @Test
    void hostingDataSource_nullName_stillBuildsWithUnknownSlug() {
        DataSourceLite hostingDataSource =
                (DataSourceLite) EntityRefs.hostingDataSource("10.1234/abcd", null);

        assertThat(hostingDataSource.getLocalIdentifier()).isEqualTo("otf___10-1234-abcd___unknown");
        assertThat(hostingDataSource.getName()).isNull();
    }

    @Test
    void organisationRef_bareRorPresent_isRorIdentified() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example University", "01an7q238");

        assertThat(org.getLocalIdentifier()).isEqualTo("https://ror.org/01an7q238");
        assertThat(org.getName()).isEqualTo("Example University");
        assertThat(org.getEntityType()).isEqualTo("organisation");
        assertThat(org.getIdentifiers())
                .isEqualTo(List.of(new AgentAllOfIdentifiers().scheme("ror").value("01an7q238")));
    }

    @Test
    void organisationRef_noRor_fallsBackToOtfIdWithNoIdentifiers() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example University", null);

        assertThat(org.getLocalIdentifier()).isEqualTo("otf___10-1234-abcd___example-university");
        assertThat(org.getName()).isEqualTo("Example University");
        assertThat(org.getIdentifiers()).isNull();
    }

    @Test
    void organisationRef_funderRorPresent_isRorIdentifiedIgnoringDoi() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example Funder", "01an7q238",
                "https://doi.org/10.13039/501100000038", "10.13039/501100000038");

        assertThat(org.getLocalIdentifier()).isEqualTo("https://ror.org/01an7q238");
        assertThat(org.getIdentifiers())
                .isEqualTo(List.of(new AgentAllOfIdentifiers().scheme("ror").value("01an7q238")));
    }

    @Test
    void organisationRef_funderDoiPresentNoRor_isDoiIdentified() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example Funder", null,
                "https://doi.org/10.13039/501100000038", "10.13039/501100000038");

        assertThat(org.getLocalIdentifier()).isEqualTo("https://doi.org/10.13039/501100000038");
        assertThat(org.getName()).isEqualTo("Example Funder");
        assertThat(org.getIdentifiers())
                .isEqualTo(List.of(new AgentAllOfIdentifiers().scheme("doi").value("10.13039/501100000038")));
    }

    @Test
    void organisationRef_funderNeitherRorNorDoi_fallsBackToOtfIdWithNoIdentifiers() {
        Organisation org = EntityRefs.organisationRef("10.1234/abcd", "Example Funder", null, null, null);

        assertThat(org.getLocalIdentifier()).isEqualTo("otf___10-1234-abcd___example-funder");
        assertThat(org.getIdentifiers()).isNull();
    }

    @Test
    void personRef_bareOrcidPresent_isOrcidIdentified() {
        List<PersonLiteAllOfIdentifiers> orcidIdentifiers =
                List.of(new PersonLiteAllOfIdentifiers().scheme("orcid").value("0000-0001-2345-6789"));

        PersonLite person = EntityRefs.personRef("10.1234/abcd", "Jane Doe", "Jane", "Doe",
                "0000-0001-2345-6789", orcidIdentifiers);

        assertThat(person.getLocalIdentifier()).isEqualTo("https://orcid.org/0000-0001-2345-6789");
        assertThat(person.getName()).isEqualTo("Jane Doe");
        assertThat(person.getGivenName()).isEqualTo("Jane");
        assertThat(person.getFamilyName()).isEqualTo("Doe");
        assertThat(person.getEntityType()).isEqualTo("person");
        assertThat(person.getIdentifiers()).isEqualTo(orcidIdentifiers);
    }

    @Test
    void personRef_noOrcid_fallsBackToOtfIdWithNoIdentifiers() {
        PersonLite person = EntityRefs.personRef("10.1234/abcd", "Jane Doe", "Jane", "Doe", null, null);

        assertThat(person.getLocalIdentifier()).isEqualTo("otf___10-1234-abcd___jane-doe");
        assertThat(person.getIdentifiers()).isNull();
    }
}
