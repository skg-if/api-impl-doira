# Puma SKG-IF API

This project is maintained as part of the SKG-IF RDA Working Group.
It is an implementation of the [SKG-IF API](https://skg-if.github.io/api/) for a set of [DOI registration agencies](https://www.doi.org/the-community/existing-registration-agencies/).
PR are welcome for any update :-).

## Features

The implementation exposes a REST API with metadata for **any DataCite or Crossref DOI**, sourced live from
[DataCite](https://datacite.org) or [Crossref](https://www.crossref.org) - no local database,
every request is served by calling the relevant upstream REST API and mapping the result onto a
SKG-IF entity.

Provider selection is by URL path, not auto-detected: `/datacite/products` and `/datacite/grants` are DataCite-
backed, `/crossref/products` and `/crossref/grants` are Crossref-backed. The two providers are
independent, side-by-side implementations (`org.skgif.doi.datacite` / `org.skgif.doi.crossref`) -
there is no runtime DOI-registry lookup deciding which one to call.

Two SKG-IF entities are implemented per provider:

- **Products** (`GET /datacite/products/{local_identifier}`, `GET /datacite/products`, and the Crossref
  equivalents under `/crossref/products`) - every DOI except grants/awards. `product_type`
  (`literature` / `research data` / `research software` / `other`) is derived from DataCite's
  `resourceTypeGeneral` (see `ResourceTypeMapping`) or Crossref's `type` (see
  `CrossrefTypeMapping`) - Crossref has no `software` type, so `research software` is only
  reachable via the DataCite provider.
- **Grants** (`GET /datacite/grants/{local_identifier}`, `GET /datacite/grants`, and the Crossref equivalents
  under `/crossref/grants`) - DataCite DOIs with `resourceTypeGeneral: "Award"`, or Crossref DOIs
  with `type: "grant"` (a grant/funding award registered as its own DOI).

## Requirements

- JDK 21+
- Maven 3.9+
- Network access to `api.datacite.org` and `api.crossref.org` (no API key needed for either -
  both are public, unauthenticated reads)
- The SKG-IF API yaml reference is stored in `src/main/openapi/skg-if-openapi.yaml`.
- The `openapi-generator-maven-plugin` is used to generate java implementation from the openapi yaml

## Running

```bash
mvn quarkus:dev
```

This starts the API on `http://localhost:8080`. All endpoints are served under `/skg-if/api`
(derived from the SKG-IF OpenAPI spec's own path convention).

```bash
# a dataset, by DOI (its https://doi.org/... URL is the local_identifier)
curl http://localhost:8080/skg-if/api/datacite/products/10.15151/esrf-dc-2493599001

# a software DOI - product_type is derived from resourceTypeGeneral, not hardcoded
curl http://localhost:8080/skg-if/api/datacite/products/10.5281/zenodo.21826016

# an Award DOI - served under /datacite/grants, not /datacite/products
curl http://localhost:8080/skg-if/api/datacite/grants/10.71707/yj21-5d60

# a page of products (spans every DataCite prefix unless datacite.prefix is configured)
curl "http://localhost:8080/skg-if/api/datacite/products?page_size=5"

# filtering (see ProductFilters.java / GrantFilters.java for the supported subset)
curl "http://localhost:8080/skg-if/api/datacite/products?filter=cf.search.title:tomography"

# a Crossref-registered DOI, via the separate /crossref path
curl http://localhost:8080/skg-if/api/crossref/products/10.1038/nature12373

# a Crossref grant DOI (type: "grant") - served under /crossref/grants, not /crossref/products
curl http://localhost:8080/skg-if/api/crossref/grants/10.35802/218300
```

Quarkus Dev UI (Swagger UI, config, CDI beans, etc.) is available at
`http://localhost:8080/q/dev-ui` while in dev mode.

## Building

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## Docker image

CI publishes a JVM-mode image to GitHub Container Registry on every push to `main`:

```bash
docker pull ghcr.io/skg-if/api-impl-doira:latest
docker run -p 8080:8080 ghcr.io/skg-if/api-impl-doira:latest
```

Pushing a `vX.Y.Z` git tag also publishes matching `:X.Y.Z` and `:X.Y` image tags, so a specific
release can be pinned instead of tracking `:latest`. Version tags are meant to be created once and
never force-moved to a different commit.

Optionally, set `CROSSREF_MAILTO` to identify this API to Crossref's "polite pool" for better
rate limits/uptime on `/crossref/products` and `/crossref/grants` (see
[Configuration](#configuration) below):

```bash
docker run -p 8080:8080 -e CROSSREF_MAILTO=you@example.org ghcr.io/skg-if/api-impl-doira:latest
```

To deploy this image on the EOSC EU Node Tools Hub instead of running it yourself, see
[DEPLOYMENT_EOSC_NODE.md](DEPLOYMENT_EOSC_NODE.md).

To build and test the image locally instead of relying on the published GHCR image:

```bash
mvn package
docker build -f src/main/docker/Dockerfile.jvm -t puma-skg-if-api:local .
docker run -p 8080:8080 puma-skg-if-api:local
curl "http://localhost:8080/skg-if/api/datacite/products?page_size=1"
```

## Testing

```bash
mvn test
```

Includes golden-file tests that compare the API's full JSON-LD response against committed
reference documents in `src/test/resources/expected/`. After an intentional change to
`DataCiteToSkgIfMapper`/`CrossrefToSkgIfMapper` (or anything else that changes the response
shape), regenerate the relevant provider's fixtures:

```bash
mvn test -Dtest=DataCiteProductsResourceTest,DataCiteGrantsResourceTest -Dgolden.regenerate=true          # DataCite
mvn test -Dtest=CrossrefProductsResourceTest,CrossrefGrantsResourceTest -Dgolden.regenerate=true  # Crossref
git diff src/test/resources/expected/   # review before committing
```

Includes mapper unit tests (against captured real fixtures in `src/test/resources/` - DataCite
fixtures span a Dataset, a Software DOI, a Text DOI and an Award; Crossref fixtures span a
journal article, a dataset, a journal article with an abstract/funder, and a grant) and
`@QuarkusTest` resource tests that mock the respective REST client.

## Configuration

Key properties (`src/main/resources/application.properties`). Any of them can be overridden
without rebuilding the image via a `docker run -e ...` environment variable, per Quarkus/SmallRye
Config's standard mapping (dots to underscores, uppercased - e.g. `crossref.mailto` becomes
`CROSSREF_MAILTO`, see [Docker image](#docker-image) above):

| Property | Purpose |
| --- | --- |
| `datacite.api.base-url` | DataCite REST API base URL |
| `datacite.prefix` | Optional - scopes `/datacite/products` and `/datacite/grants` results to one DataCite DOI prefix (e.g. your own organisation's). Blank (default) means no restriction. |
| `crossref.api.base-url` | Crossref REST API base URL |
| `crossref.prefix` | Optional - scopes `/crossref/products` and `/crossref/grants` results to one DOI prefix, same convention as `datacite.prefix`. |
| `crossref.mailto` | Optional but recommended - identifies this API to Crossref's "polite pool" for better rate limits/uptime. Blank (default) means anonymous/non-polite-pool requests. Set via `-e CROSSREF_MAILTO=you@example.org` on `docker run`. |
| `skgif.local-identifier.base-url` | `https://doi.org/` - prefixed onto every entity's DOI to form its SKG-IF `local_identifier` |
| `skgif.sandbox.base-url` | JSON-LD `@context` `@base` root - namespaced per-response to the DataCite client that registered the served DOI(s) (`relationships.client.data.id`, e.g. `inist.esrf`). Crossref has no equivalent concept mapped yet, so Crossref-backed responses always fall back to `skgif.context.base`. |
| `skgif.context.base` | Fallback `@base`, used when a DataCite DOI carries no client relationship, and always for Crossref-backed responses |
| `skgif.default-page-size` | Default `/datacite/products` and `/datacite/grants` page size (both providers) |

## Project layout

- `src/main/openapi/skg-if-openapi.yaml` - vendored, version-pinned copy of the official SKG-IF
  OpenAPI spec (with one documented local patch, noted in the file's header comment)
- `org.skgif.doi.generated.*` - JAX-RS/model classes generated from that spec via
  `openapi-generator-maven-plugin` (models only are actually used - see `DataCiteProductsResource`'s
  javadoc for why the generated `ProductApi`/`GrantApi` interfaces aren't implemented directly).
  Provider-agnostic - both DataCite and Crossref map onto these same `Product`/`Grant` classes.
- `org.skgif.doi.datacite` - DataCite REST client, DTOs (`datacite.dto`), `ResourceTypeMapping`
  (DataCite `resourceTypeGeneral` &lt;-&gt; SKG-IF `product_type`, and Award detection) and the
  `DataCiteToSkgIfMapper` (`datacite.mapper`) that maps DataCite records to `Product`/`Grant`
- `org.skgif.doi.crossref` - the Crossref sibling of `org.skgif.doi.datacite`: REST client, DTOs
  (`crossref.dto`), `CrossrefTypeMapping` (Crossref `type` &lt;-&gt; SKG-IF `product_type`, and
  grant-type detection) and `CrossrefToSkgIfMapper` (`crossref.mapper`)
- `org.skgif.doi.rest` - the `/datacite/products`/`/datacite/grants` and `/crossref/products`/`/crossref/grants`
  resources, their filter parsing (`ProductFilters`/`GrantFilters` for DataCite,
  `CrossrefFilters` for Crossref), and the shared JSON-LD envelope helper. Provider selection is
  by which resource (and thus URL path) is hit - there is no runtime dispatcher.
- `org.skgif.doi.util.LocalIdentifiers` - DOI &lt;-&gt; `local_identifier` conversion
- `org.skgif.doi.jackson` - a Jackson mixin working around a generator quirk (see its javadoc)
