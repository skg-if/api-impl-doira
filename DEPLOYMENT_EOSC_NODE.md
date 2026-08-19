# Deploying to the EOSC EU Node Tools Hub

This documents how to run `puma-skg-if-api` for free on the
[EOSC EU Node](https://open-science-cloud.ec.europa.eu/) **Tools Hub**, on top of its
**Cloud Container Platform** (OKD) service, per the
*EOSC EU Node Tools Hub User Guide v2.1*.

CI now automatically builds and publishes `ghcr.io/skg-if/api-impl-doira:latest` on every push to
`main` (see [README.md](README.md#docker-image)) - useful if you just want to `docker pull` and run
the app, and it's also the image OKD pulls for the Tools Hub deployment below, since it's already
public on GitHub Container Registry. Getting it deployed on EOSC's Tools Hub specifically still
needs the manual sequence of steps below in the Tools Hub web UI - there is no API to script that
part against, so it remains a runbook, not something that can be automated from this repo.

## Prerequisites

- An EOSC EU Node account with **Cloud Container Platform** resources already allocated
  to a Personal or Group project (Resource Hub -> allocate before deploying, per guide
  section 2.1).
- No registry setup is needed: `ghcr.io/skg-if/api-impl-doira` is public, so OKD can pull it
  directly with no image pull credential.

## Tools Hub runbook

1. **Get a TOSCA baseline.** In *Tools Hub -> All Tools*, find an existing published tool
   whose target infrastructure is "Container" on OKD - the guide lists **GeoServer**
   ("Deploy a containerized instance of a GeoServer platform on OKD") and **MLflow**
   ("Deploy a containerized instance of a MLflow platform") as examples. Click **Copy to
   My Tools** on one of them. This opens the Create Tool wizard pre-filled with a working
   TOSCA template - far safer than authoring INDIGO TOSCA container syntax from scratch.

2. **Edit metadata** (wizard step 1 - Metadata):
   - Name: `Puma SKG-IF API`
   - Description: summary from [README.md](README.md) ("A SKG-IF REST API exposing metadata
     for any DataCite DOI, sourced live from DataCite...")
   - Resource Organisation: `SKG-IF RDA WG`
   - Keywords: `skg-if`, `datacite`, `crossref`
   - License: project's actual license (none currently committed to the repo - pick
     whatever is appropriate, or leave as internal/proprietary)

3. **Edit the TOSCA template** (wizard step 2 - Content):
   - Replace the container image reference with `ghcr.io/skg-if/api-impl-doira:latest`
     (or a pinned version tag, e.g. `ghcr.io/skg-if/api-impl-doira:1.2.3` - see
     [README.md](README.md#docker-image)).
   - Set the exposed/target port to `8080`.
   - No environment variables or secrets are required for a basic deployment -
     `datacite.api.base-url` and friends all have working defaults in
     [application.properties](src/main/resources/application.properties), and DataCite
     reads are public/unauthenticated (see [README.md](README.md)).
   - Optional but recommended if `/crossref/products` or `/crossref/grants` will be used: add
     an environment variable `CROSSREF_MAILTO` = your contact email in the TOSCA template's
     environment-variables field, to identify this API to Crossref's "polite pool" for better
     rate limits/uptime (maps onto `crossref.mailto`, see [README.md](README.md#configuration)).

4. **Review and Confirm** (wizard step 3). The tool now appears under **My Tools**.

5. **Deploy** (Tools Hub -> My Tools -> Deploy): pick the project that has Cloud
   Container Platform resources allocated, confirm the CPU/memory/storage shown in the
   dialog are sufficient, click Proceed. You'll get a notification once deployed.

6. **Verify**: in the *Deployments* tab, click **Show Details** on the entry to get the
   route/URL OKD assigned to the container, then:

   ```bash
   curl "https://<assigned-route>/skg-if/api/datacite/products?page_size=5"
   ```

   Confirm the response matches what `mvn quarkus:dev` returns locally.

## Notes on cost

Tools Hub / EOSC EU Node resources are allocated as **credits** against your project (a
finite pool that refreshes periodically, shown in the left sidebar as "Credits
remaining"), not a metered pay-as-you-go bill - so there's no risk of an unexpected
charge, but the deployment can stop working if the project's credit/resource allocation
runs out or isn't renewed.
