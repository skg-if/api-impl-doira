# `getProductById` request flow

One request, traced through the real code path for each registration agency. All three providers
share the same shape - resolve identifier, fetch upstream, gate by record type, map to SKG-IF,
wrap in a JSON-LD envelope - but differ in exactly where. Traced from
`src/main/java/org/skgif/doi/rest/{datacite,crossref,medra}`.

## DataCite

**What's different here:** a single upstream call, then a type gate - DataCite DOIs with
`resourceTypeGeneral: Award` are grants, not products, and get redirected to
`/datacite/grants` instead of mapped.

```mermaid
sequenceDiagram
    actor Client
    participant Res as DataCiteProductsResource
    participant Loc as LocalIdentifiers
    participant Fetch as DataCiteDoiFetcher
    participant Api as DataCiteClient
    participant DC as DataCite REST API
    participant RTM as ResourceTypeMapping
    participant Mapper as DataCiteToSkgIfMapper
    participant Env as JsonLdEnvelopes

    Client->>Res: GET /datacite/products/{local_identifier}
    Res->>Loc: toDoi(localIdentifierParam)
    Loc-->>Res: doi

    Res->>Fetch: fetchByDoi(client, doi)
    Fetch->>Api: getDoi(doi)
    Api->>DC: HTTP GET /dois/{doi}
    DC-->>Api: DOI JSON
    Api-->>Fetch: DataCiteDoiResponse
    Fetch-->>Res: Optional~DataCiteDoiData~

    alt DOI not found
        Res-->>Client: 404 (JsonLdErrors.notFound)
    else found, but resourceTypeGeneral = Award
        Res->>RTM: isAward(attributes)
        RTM-->>Res: true
        Res-->>Client: 404 "see /datacite/grants/{id} instead"
    else found, is a product
        Res->>RTM: isAward(attributes)
        RTM-->>Res: false
        Res->>Mapper: toProduct(attributes)
        Note over Mapper: fans out to Title/Biblio/Contribution/<br/>Manifestation/RelatedProduct mappers<br/>+ shared util (Licence, EntityRefs, ...)
        Mapper-->>Res: Product
        Res->>Env: singleEntityResponse(contextBase, meta, product)
        Env-->>Res: 200 response body
        Res-->>Client: 200 OK + JSON-LD product envelope
    end
```

**What's called:**

| Component | Role |
| --- | --- |
| `DataCiteProductsResource` | REST entry point for this request - orchestrates every step below. |
| `LocalIdentifiers` | Normalizes the path param (with or without the SKG base domain) down to a bare DOI. |
| `DataCiteDoiFetcher` / `DataCiteClient` | Live REST call to DataCite's own API for the DOI record - no local storage. |
| `ResourceTypeMapping` | Gate: Award-type DOIs are grants, not products - routed away before mapping. |
| `DataCiteToSkgIfMapper` | Maps the DataCite record to an SKG-IF `Product`; fans out to field-level mappers. |
| `JsonLdLinks` / `JsonLdContextBase` / `JsonLdEnvelopes` | Assemble the `@context`/meta/`@graph` JSON-LD response by hand. |

## Crossref

**What's different here:** an optional *second* upstream call - some work types get their venue
metadata enriched from a separate XML endpoint before mapping.

```mermaid
sequenceDiagram
    actor Client
    participant Res as CrossrefProductsResource
    participant Loc as LocalIdentifiers
    participant Fetch as CrossrefWorkFetcher
    participant Api as CrossrefClient
    participant CR as Crossref REST API
    participant CTM as CrossrefTypeMapping
    participant Ven as CrossrefVenueEnricher
    participant Mapper as CrossrefToSkgIfMapper
    participant Env as JsonLdEnvelopes

    Client->>Res: GET /crossref/products/{local_identifier}
    Res->>Loc: toDoi(localIdentifierParam)
    Loc-->>Res: doi

    Res->>Fetch: fetchByDoi(client, doi)
    Fetch->>Api: works(doi)
    Api->>CR: HTTP GET /works/{doi}
    CR-->>Api: work JSON
    Api-->>Fetch: CrossrefWorkResponse
    Fetch-->>Res: Optional~CrossrefWork~

    alt work not found
        Res-->>Client: 404 (JsonLdErrors.notFound)
    else found, but is a grant
        Res->>CTM: isGrant(work)
        CTM-->>Res: true
        Res-->>Client: 404 "see /crossref/grants/{id} instead"
    else found, is a product
        Res->>CTM: isGrant(work)
        CTM-->>Res: false
        Res->>CTM: isXmlVenueEnrichable(work)
        CTM-->>Res: true or false
        opt work type is venue-enrichable
            Res->>Ven: fetchVenueMetadata(doi)
            Ven->>CR: HTTP GET (venue XML)
            CR-->>Ven: XML metadata
            Ven-->>Res: Optional~venueXml~
        end
        Res->>Mapper: toProduct(work, venueXmlOrNull)
        Note over Mapper: fans out to Title/Biblio/Contribution/<br/>Manifestation/RelatedProduct mappers<br/>+ shared util (Licence, EntityRefs, ...)
        Mapper-->>Res: Product
        Res->>Env: singleEntityResponse(contextBase, meta, product)
        Env-->>Res: 200 response body
        Res-->>Client: 200 OK + JSON-LD product envelope
    end
```

**What's called:**

| Component | Role |
| --- | --- |
| `CrossrefProductsResource` | REST entry point - orchestrates every step below. |
| `LocalIdentifiers` | Normalizes the path param down to a bare DOI. |
| `CrossrefWorkFetcher` / `CrossrefClient` | Live REST call to Crossref's own API for the work record - no local storage. |
| `CrossrefTypeMapping` | Two gates: grant-vs-product, and whether this work type needs venue XML enrichment. |
| `CrossrefVenueEnricher` | Conditional second upstream call - fetches venue metadata as XML, only for enrichable types. |
| `CrossrefToSkgIfMapper` | Maps the work (+ optional venue XML) to an SKG-IF `Product`; fans out to field-level mappers. |
| `JsonLdLinks` / `JsonLdContextBase` / `JsonLdEnvelopes` | Assemble the `@context`/meta/`@graph` JSON-LD response by hand. |

## mEDRA

**What's different here:** no typed fetch layer - the client hands back raw ONIX XML directly,
which gets parsed in-resource. mEDRA also has no list endpoint, only this single-item lookup.

```mermaid
sequenceDiagram
    actor Client
    participant Res as MedraProductsResource
    participant Loc as LocalIdentifiers
    participant Api as MedraClient
    participant MD as mEDRA ONIX-for-DOI API
    participant Parser as MedraOnixXmlParser
    participant Mapper as MedraToSkgIfMapper
    participant Env as JsonLdEnvelopes

    Client->>Res: GET /medra/products/{local_identifier}
    Res->>Loc: toDoi(localIdentifierParam)
    Loc-->>Res: doi

    Res->>Api: getMetadata(doi)
    Api->>MD: HTTP GET /metadata/{doi}
    MD-->>Api: ONIX XML (or non-200)
    Api-->>Res: raw XML Response

    alt non-200 status, or client throws
        Res-->>Client: 404 (JsonLdErrors.notFound)
    else 200, but XML doesn't parse
        Res->>Parser: parse(xml)
        Parser-->>Res: Optional.empty()
        Res-->>Client: 404 (JsonLdErrors.notFound)
    else 200, parses to a MedraWork
        Res->>Parser: parse(xml)
        Parser-->>Res: MedraWork
        Res->>Mapper: toProduct(work)
        Note over Mapper: fans out to Title/Biblio/<br/>Contribution/Manifestation mappers<br/>+ shared util (Licence, MapperTextUtils, ...)
        Mapper-->>Res: Product
        Res->>Res: build MetaSingleEntity inline
        Res->>Env: singleEntityResponse(contextBase, meta, product)
        Env-->>Res: 200 response body
        Res-->>Client: 200 OK + JSON-LD product envelope
    end
```

**What's called:**

| Component | Role |
| --- | --- |
| `MedraProductsResource` | REST entry point - single-item lookup only, no list endpoint exists for mEDRA. |
| `LocalIdentifiers` | Normalizes the path param down to a bare DOI. |
| `MedraClient` | Live call to mEDRA's ONIX-for-DOI metadata API - returns raw XML, not a typed DTO. |
| `MedraOnixXmlParser` | Parses the ONIX XML into a `MedraWork`; unparseable XML is also a 404. |
| `MedraToSkgIfMapper` | Maps the work to an SKG-IF `Product`; fans out to field-level mappers. No grant concept - ONIX-for-DOI has none. |
| `JsonLdLinks` / `JsonLdContextBase` / `JsonLdEnvelopes` | Assemble the response envelope; the single-entity meta is built inline here, unlike the other two providers. |
