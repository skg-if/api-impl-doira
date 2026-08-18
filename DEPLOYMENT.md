# Deploying to the EOSC EU Node Tools Hub

This documents how to run `puma-skg-if-api` for free on the
[EOSC EU Node](https://open-science-cloud.ec.europa.eu/) **Tools Hub**, on top of its
**Cloud Container Platform** (OKD) service, per the
*EOSC EU Node Tools Hub User Guide v2.1*.

Building and publishing the container image is currently a manual step (see [Image](#image)) -
there is no CI job wired up for it. Everything from there on is a manual sequence of steps in the
Tools Hub web UI - there is no API to script this against, so this is a runbook, not
something that can be automated from this repo.

## Prerequisites

- An EOSC EU Node account with **Cloud Container Platform** resources already allocated
  to a Personal or Group project (Resource Hub -> allocate before deploying, per guide
  section 2.1).
- This project's **Container Registry** enabled on gitlab.esrf.fr (Settings -> General ->
  Visibility, or Packages & Registries) so the CI pipeline has somewhere to push the image,
  and so OKD can pull it. Note the exact registry host/port shown on that page (e.g.
  `registry.esrf.fr:5050/puma-public/puma-skg-if-api`) - `$CI_REGISTRY_IMAGE` resolves to
  it automatically in CI, but you'll need it verbatim for the TOSCA template below.
- If the registry/image is **not** public, you'll additionally need an image pull
  credential (e.g. a GitLab deploy token) wired into the TOSCA template - the guide
  doesn't document this syntax, so treat it as an open question to resolve against the
  copied template's own image-pull fields, or ask EOSC support.

## Image

No CI job builds or pushes this image yet - build and push it manually. To build and test
locally:

```bash
mvn package
docker build -f src/main/docker/Dockerfile.jvm -t puma-skg-if-api:local .
docker run -p 8080:8080 puma-skg-if-api:local
curl "http://localhost:8080/skg-if/api/datacite/products?page_size=1"
```

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
   - Resource Organisation: `ESRF`
   - Keywords: `skg-if`, `datacite`, `esrf`
   - License: project's actual license (none currently committed to the repo - pick
     whatever is appropriate, or leave as internal/proprietary)

3. **Edit the TOSCA template** (wizard step 2 - Content):
   - Replace the container image reference with the pushed image, e.g.
     `<registry-host-from-prerequisites>/puma-public/puma-skg-if-api:latest`.
   - Set the exposed/target port to `8080`.
   - No environment variables or secrets are required for a basic deployment -
     `datacite.api.base-url` and friends all have working defaults in
     [application.properties](src/main/resources/application.properties), and DataCite
     reads are public/unauthenticated (see [README.md](README.md)).

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
