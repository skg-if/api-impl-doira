# Puma SKG-IF API

A [SKG-IF](https://skg-if.github.io/api/) (Scientific Knowledge Graph Interoperability
Framework) REST API exposing metadata for **any DataCite DOI**, sourced live from
[DataCite](https://datacite.org) - no local database, every request is served by calling the
DataCite REST API and mapping the result onto a SKG-IF entity.

Two SKG-IF entities are implemented, routed by DataCite's own `resourceTypeGeneral`:

- **Products** (`GET /products/{local_identifier}`, `GET /products`) - every DataCite DOI
  except Awards. `product_type` (`literature` / `research data` / `research software` /
  `other`) is derived from `resourceTypeGeneral` (see `ResourceTypeMapping`).
- **Grants** (`GET /grants/{local_identifier}`, `GET /grants`) - DataCite DOIs with
  `resourceTypeGeneral: "Award"` (a grant/funding award registered as its own DOI).

A Crossref DOI provider is a planned follow-up, not implemented yet - the `datacite`
sub-package (client, DTOs, mapper) is kept separate from the provider-agnostic `rest`/`util`/
`generated` code specifically to leave room for a sibling provider package later.

## Requirements

- JDK 21+
- Maven 3.9+
- Network access to `api.datacite.org` (no API key needed - public reads)

## Running

```bash
mvn quarkus:dev
```

This starts the API on `http://localhost:8080`. All endpoints are served under `/skg-if/api`
(derived from the SKG-IF OpenAPI spec's own path convention).

```bash
# a dataset, by DOI (its https://doi.org/... URL is the local_identifier)
curl http://localhost:8080/skg-if/api/products/10.15151/esrf-dc-2493599001

# a software DOI - product_type is derived from resourceTypeGeneral, not hardcoded
curl http://localhost:8080/skg-if/api/products/10.5281/zenodo.21826016

# an Award DOI - served under /grants, not /products
curl http://localhost:8080/skg-if/api/grants/10.3565/83eg-9981

# a page of products (spans every DataCite prefix unless datacite.prefix is configured)
curl "http://localhost:8080/skg-if/api/products?page_size=5"

# filtering (see ProductFilters.java / GrantFilters.java for the supported subset)
curl "http://localhost:8080/skg-if/api/products?filter=cf.search.title:tomography"
```

Quarkus Dev UI (Swagger UI, config, CDI beans, etc.) is available at
`http://localhost:8080/q/dev-ui` while in dev mode.

## Building

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## Testing

```bash
mvn test
```

Includes golden-file tests that compare the API's full JSON-LD response against committed
reference documents in `src/test/resources/expected/`. After an intentional change to
`DataCiteToSkgIfMapper` (or anything else that changes the response shape), regenerate them:

```bash
mvn test -Dtest=ProductsResourceTest -Dgolden.regenerate=true
git diff src/test/resources/expected/   # review before committing
```

Includes mapper unit tests (against captured real DataCite fixtures in
`src/test/resources/`, spanning a Dataset, a Software DOI, a Text DOI and an Award) and
`@QuarkusTest` resource tests that mock the DataCite client.

## Configuration

Key properties (`src/main/resources/application.properties`):

| Property | Purpose |
| --- | --- |
| `datacite.api.base-url` | DataCite REST API base URL |
| `datacite.prefix` | Optional - scopes `/products` and `/grants` results to one DataCite DOI prefix (e.g. your own organisation's). Blank (default) means no restriction. |
| `skgif.local-identifier.base-url` | `https://doi.org/` - prefixed onto every entity's DOI to form its SKG-IF `local_identifier` |
| `skgif.sandbox.base-url` | JSON-LD `@context` `@base` root - namespaced per-response to the DataCite client that registered the served DOI(s) (`relationships.client.data.id`, e.g. `inist.esrf`) |
| `skgif.context.base` | Fallback `@base`, used only when a DOI's DataCite data carries no client relationship |
| `skgif.default-page-size` | Default `/products` and `/grants` page size |

## Project layout

- `src/main/openapi/skg-if-openapi.yaml` - vendored, version-pinned copy of the official SKG-IF
  OpenAPI spec (with one documented local patch, noted in the file's header comment)
- `org.skgif.doi.generated.*` - JAX-RS/model classes generated from that spec via
  `openapi-generator-maven-plugin` (models only are actually used - see `ProductsResource`'s
  javadoc for why the generated `ProductApi`/`GrantApi` interfaces aren't implemented directly)
- `org.skgif.doi.datacite` - DataCite REST client, DTOs (`datacite.dto`), `ResourceTypeMapping`
  (DataCite `resourceTypeGeneral` &lt;-&gt; SKG-IF `product_type`, and Award detection) and the
  `DataCiteToSkgIfMapper` (`datacite.mapper`) that maps DataCite records to `Product`/`Grant`
- `org.skgif.doi.rest` - the `/products` and `/grants` resources, their filter parsing
  (`ProductFilters`/`GrantFilters`), and the shared JSON-LD envelope helper
- `org.skgif.doi.util.LocalIdentifiers` - DOI &lt;-&gt; `local_identifier` conversion
- `org.skgif.doi.jackson` - a Jackson mixin working around a generator quirk (see its javadoc)
