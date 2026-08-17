# Known limitations

## DataCite

- **DataCite Award records** lack any generic source for `grant_number`, `currency`,
  `funded_amount`, `duration`, `website`, `funding_stream`, or `acronym` - left unset rather than
  guessed at.
- **DataCite's `Other` and `Coverage` `dateType` values** have no SKG-IF equivalent - both are
  absent from `DataCiteManifestationMapper#DATACITE_DATE_TYPE_TO_SKGIF` and silently dropped, same as
  any other unrecognized `dateType` (see `DataCiteToSkgIfMapperTest.dropsUnrecognizedDateTypesLikeCoverage`).
  `Coverage` describes the temporal span covered by the resource's *content* (e.g. a historical
  dataset spanning 1900-1950), not an event in the resource's own lifecycle - conceptually closest
  to `collected`, but SKG-IF has no field for it either.
- **DataCite's top-level `created`/`registered`/`updated`/`published` attributes** (system
  timestamps, distinct from the researcher-asserted `dates[]` array - see `DataCiteAttributes`)
  are read by the mapper only as *fallbacks* for `creation`/`deposit`/`modified`/`publication`
  respectively: an explicit `dates[]` entry always wins when both exist (see
  `DataCiteToSkgIfMapperTest.explicitDatesEntryWinsOverTopLevelAttributeFallback`). In practice
  they're the primary source, not a rare fallback - no fixture's `dates[]` has ever carried a
  `Created`/`Submitted`/`Updated` entry.
- **DataCite's `fundingReferences[].funderIdentifierType`** has no literal `"DOI"` value in its
  controlled vocabulary, so `funding_agency` detects a Funder Registry DOI by checking whether
  `funderIdentifier` itself is DOI-shaped, regardless of what the type label says (covers
  `"Crossref Funder ID"` and any other/unlabeled DOI in practice - see
  [`datacite-thesis-crossref-funder-id-4342.json`](src/test/resources/datacite-thesis-crossref-funder-id-4342.json)).
  Genuinely non-DOI schemes (ISNI, GRID, Wikidata) still have no home here and fall back to an
  otf id, same as an entirely absent `funderIdentifier` (see
  [`datacite-dataset-funder-no-identifier-e449e75a.json`](src/test/resources/datacite-dataset-funder-no-identifier-e449e75a.json),
  where the same funder name recurring across 2 grants resolves to the same otf id both times).

## Crossref

- **Crossref's per-license `start` date (+ `delay-in-days`)** - present in several committed
  fixtures already (e.g. [`crossref-journal-article.json`](src/test/resources/crossref-journal-article.json))
  - would be the natural source for SKG-IF's `embargo` date type, but `CrossrefLicense`
  (`src/main/java/org/skgif/doi/crossref/dto/CrossrefLicense.java`) only deserializes `URL` today;
  `start`/`delay-in-days` reach neither the DTO nor the mapper. Flagged here as a real,
  unimplemented gap rather than a documentation oversight. DataCite's side of `embargo` (via
  `Available`, with the same-day dedup rule described in the
  [date-type table](SKG_IF_DOI_MAPPING_DATES.md#date-type-mapping)) is handled -
  this limitation is Crossref-only.
- **Crossref's `posted` field** [ℹ️](https://github.com/CrossRef/rest-api-doc/blob/master/api_format.md#work) [ℹ️](https://data.crossref.org/reports/help/schema_doc/5.5.0/schema_5_5_0.html#posted_date)
  ("date on which posted content was made available online") is not deserialized by `CrossrefWork` and never
  reaches the mapper. Unlike the generic `published` field (which duplicates `issued` and is
  deliberately left unread - see below), `posted` is the only publication-style date `posted-content`
  works (preprints) reliably carry, since those rarely have a `published-print`/`published-online`
  date - so this is a real, unimplemented gap for that type rather than a deliberate redundancy
  skip.
- **Crossref's `update-to[].type`** [ℹ️](https://data.crossref.org/reports/help/schema_doc/5.5.0/schema_5_5_0.html#update) is an
  exhaustive, 12-value enum (`addendum`, `clarification`, `correction`, `corrigendum`, `erratum`,
  `expression_of_concern`, `new_edition`, `new_version`, `partial_retraction`, `removal`,
  `retraction`, `withdrawal`) - only `"correction"` and `"retraction"` map to an SKG-IF date type
  today, so `CrossrefManifestationMapper#dates` recognizes just those two; the other 10 are ignored
  rather than guessed at (see
  [`crossref-journal-article-with-update-to.json`](src/test/resources/crossref-journal-article-with-update-to.json),
  a hand-built fixture since no live-captured record with `update-to[]` was available).
- **Crossref's `indexed` field** (present on every work, reflecting when Crossref last
  re-indexed the record) is deliberately *not* read as a `modified` source despite the superficial
  resemblance - Crossref's own documentation notes re-indexing doesn't imply a metadata change.
  `deposited` is Crossref's genuine most-recent-metadata-update signal - and, per Crossref's own
  description of it ("date on which the work metadata was most recently updated"), it feeds
  SKG-IF's `modified` directly, in addition to the `deposit` date type it already fed (see
  `CrossrefToSkgIfMapperTest.mapsDepositedIntoBothDepositAndModified`) - the same source value
  appears twice in the output, once under each key.
- **Crossref's generic `published` field** duplicates `issued` in every fixture observed (Crossref
  computes it as the earliest of `published-print`/`published-online`, same as `issued`) - reading
  it would add no new information over the existing `issued`/`published-print`/`published-online`
  sources for `publication`, so it's deliberately left unread.
- **The ℹ️ links on `created`/`deposited`/`indexed`/`issued`/`published-print`/`published-online`/
  generic `published`/`posted`** in the
  [date-type table](SKG_IF_DOI_MAPPING_DATES.md#date-type-mapping) point at
  [`CrossRef/rest-api-doc`](https://github.com/CrossRef/rest-api-doc)'s `api_format.md`, which
  Crossref's own README marks **deprecated** in favour of `https://api.crossref.org/` - but the
  current Swagger UI ([`api.crossref.org/swagger-ui`](https://api.crossref.org/swagger-ui/index.html))
  documents endpoints/parameters, not per-field response-schema descriptions, so the deprecated
  doc remains the only place `created`/`deposited`/`indexed`/`issued`/generic `published` are
  actually defined. `accepted` and `update-to[]` instead link to the still-current
  [schema documentation](https://data.crossref.org/reports/help/schema_doc/5.5.0/index.html) for
  Crossref's deposit/submission XSD, which does define them (as `acceptance_date` and the `update`
  element respectively) and isn't affected by this deprecation. `published-print` and
  `published-online` carry a second ℹ️ pointing at that same current schema doc's
  `publication_date` element - the submission-side source for both (distinguished there by a
  `media_type` attribute) - alongside their deprecated-doc link, since the REST API's split into
  two separately-named fields has no single matching schema element of its own. `posted` likewise
  carries a second ℹ️ alongside its deprecated-doc link, pointing at that schema doc's own
  dedicated `posted_date` element.
- **Crossref** has no software-versioning field, no organisation-level `relevantOrganisations`
  outside per-contributor affiliations, and no `acronym` field for grants.
- **Crossref grant records** with multiple `project[]` entries (e.g. joint awards) contribute
  titles/abstracts/contributions/beneficiaries from *all* projects, but funding
  amount/currency/duration/scheme are taken from the *first* project's *first* funding entry only.
- **Crossref's REST JSON `ISBN[]`** (present on book/book-chapter and proceedings-article records,
  e.g. [`crossref-book-chapter.json`](src/test/resources/crossref-book-chapter.json)) is still not
  deserialized by `CrossrefWork` and never reaches the mapper directly. For chapter-in-a-book and
  paper-in-proceedings types it's no longer a real gap in practice - `venue.identifiers[]` now
  gets an `isbn` entry via the XML transform enrichment described in the venue row of
  [Product entity mapping (Crossref)](SKG_IF_DOI_MAPPING_PRODUCT.md#crossref) - but
  every other Crossref type, and any enrichable record whose XML fetch fails, still has no ISBN
  on its venue at all.
- **Crossref's XML transform enrichment** (chapter-in-a-book and paper-in-proceedings venues, see
  the venue row of [Product entity mapping (Crossref)](SKG_IF_DOI_MAPPING_PRODUCT.md#crossref)) is
  fetched only by the single-item `GET /crossref/products/{doi}` endpoint,
  never the list/search endpoint - list results still use the `container-title[0]` heuristic. The
  parser also discards the XML's `subtitle` and publisher name/place (`publisher_name`/
  `publisher_place`) - SKG-IF's Venue has no field for either (`ProductManifestationBiblioIn` only
  has `local_identifier`/`identifiers`/`entity_type`/`name`/`acronym`), and `work.publisher`
  already drives `hosting_data_source` and the top-level `publisher` contribution. It also does
  not use the XML's `event_metadata` (conference name/location/date) for proceedings-article
  records - no equivalent field exists in the SKG-IF Product schema to carry conference-specific
  metadata separately from the venue itself.
- **A container's own DOI (`doi_data/doi`) is inconsistently recorded by Crossref** - present for
  every book fixture tested so far, but absent from both real proceedings-article fixtures tested
  ([`crossref-proceedings-article-with-series.xml`](src/test/resources/crossref-proceedings-article-with-series.xml),
  [`crossref-proceedings-article-standalone.xml`](src/test/resources/crossref-proceedings-article-standalone.xml)).
  `CrossrefVenueMetadataXmlParser`/`CrossrefBiblioMapper#venueFromXmlMetadata` treat this as
  optional and fall back to an otf id rather than guessing one.
- **`CrossrefJournalDoiResolver`'s journal-DOI lookup** (`GET works?filter=type:journal,issn:<issn>`,
  see the venue row of [Product entity mapping (Crossref)](SKG_IF_DOI_MAPPING_PRODUCT.md#crossref)) is,
  unlike the XML transform enrichment, deliberately issued on *every*
  request that reaches `CrossrefBiblioMapper#venue` with an ISSN present - both the single-item
  and list/search endpoints - accepting the extra per-article Crossref call as a tradeoff for a
  real, resolvable venue identifier. The resolver is `@RequestScoped`: a *found* DOI is cached per
  ISSN only for the current request (covering the main practical case - many articles from the
  same journal on one list/search page sharing a single lookup), not indefinitely across
  requests; a miss (no journal-level DOI registered, or a non-2xx response/network error) is
  never cached at all, so it's retried on the very next call rather than a transient failure
  being indistinguishable from "this journal has no DOI".

## mEDRA

- **No funding/grant data of any kind** - ONIX-for-DOI has no `Grant`, `Funding`, or `Project`
  element (verified against 7 live examples). `/medra/grants` doesn't exist, and
  `MedraToSkgIfMapper` has no `toGrant` method - see
  [Product ↔ Grant routing](SKG_IF_DOI_MAPPING_GRANT.md#product--grant-routing).
- **No list/search endpoint** - `api.medra.org/metadata/{doi}` is a DOI-keyed metadata lookup,
  not a search/facet/browse API (no mEDRA equivalent of Crossref's `filter=`/DataCite's list
  query was found). `MedraProductsResource` therefore exposes only `GET
  /medra/products/{local_identifier}`, never a bare `GET /medra/products`.
- **No ORCID (or any other person identifier) was observed on any contributor** in the 6+1
  ONIX-for-DOI records examined - `MedraContributionMapper#personRef` always mints an otf id for
  contributors, unlike DataCite/Crossref's ORCID-when-present path.
- **A bare `PersonName` with no `PersonNameInverted` sibling** (e.g. `"Cotte M."`, seen on
  [`medra-version-message-book-series.xml`](src/test/resources/medra-version-message-book-series.xml))
  isn't safely splittable into given/family name - mEDRA gives no delimiter convention for it
  (unlike Crossref's already-split `given`/`family` fields), so both stay unset rather than
  guessed at.
- **Only the ONIX-for-DOI "Serial Article" schema is handled** - the only schema family observed
  live, across both its `ONIXDOISerialArticleWorkRegistrationMessage` and
  `ONIXDOISerialArticleVersionRegistrationMessage` root-element forms (confirmed to differ only
  in element naming, not in the nesting `MedraOnixXmlParser` relies on). A record in any other
  schema family (none of the 6 requested example DOIs turned out to actually be one, including a
  book-series proceedings chapter that mEDRA itself modeled as a Serial Article) finds no
  `ContentItem` and degrades to `Optional.empty()`, which the REST layer turns into a 404 rather
  than a guessed-at partial mapping.
  **TODO: implement Monograph / Monograph Chapter / Serial Title / Serial Issue support** - mEDRA
  documents four more ONIX-DOI 2.0 schema families beyond Serial Article at
  [`medra.org/en/metadata_td.htm`](https://www.medra.org/en/metadata_td.htm) (Monographs,
  Monograph Chapters, Serial Titles, Serial Issues, each with its own spec PDF linked from that
  page). A DOI registered under any of those is currently unreachable via `/medra/products`.
- **EDItEUR's own ONIX-DOI landing page** ([`editeur.org/97/ONIX-DOI-Registration-Formats`](https://www.editeur.org/97/ONIX-DOI-Registration-Formats/))
  currently advertises spec version 1.1, but every live record fetched during this provider's
  implementation declared schema/namespace version `2.0` (`xsi:schemaLocation` pointing at
  `ONIX_DOIMetadata_2.0.xsd`) - the `medra.org`-hosted
  [`ONIX_DOI_Serial_Article_2.0_v.2.pdf`](https://www.medra.org/stdoc/ONIX_DOI_Serial_Article_2.0_v.2.pdf)
  is the version that actually matches production data. This is a documentation-source
  discrepancy on mEDRA/EDItEUR's side, not a mapper bug.
- **No journal-DOI resolution equivalent to `CrossrefJournalDoiResolver`** - the venue's
  `local_identifier` is always an otf id, backed only by the journal/series' own ISSN(s) (when
  present - `SerialVersion/ProductIdentifier` is occasionally coded with a non-ISSN
  `ProductIDType`, e.g. `06` (DOI) rather than `07` (ISSN), on
  [`medra-personname-inverted-only.xml`](src/test/resources/medra-personname-inverted-only.xml),
  or carries a proprietary `01` id as a *second* `ProductIdentifier` sibling alongside the real
  `07` ISSN within the same `SerialVersion`, on
  [`medra-multiple-product-identifiers.xml`](src/test/resources/medra-multiple-product-identifiers.xml) -
  the parser correctly excludes both non-`07` cases rather than misreading them as an ISSN).
- **`JournalIssue/JournalIssueDate`, `manifestations[].biblio.issue/volume/pages`, and
  `topics[].term`** all have real ONIX source fields (`JournalVolumeNumber`,
  `JournalIssueNumber`, `TextItem/PageRun`) that aren't mapped - see the corresponding rows in
  [Product entity mapping (mEDRA)](SKG_IF_DOI_MAPPING_PRODUCT.md#medra) and
  [Date-type mapping](SKG_IF_DOI_MAPPING_DATES.md#medra) for why each is left out rather than
  guessed at.
- **mEDRA's HTML-named-entity quirk**: title/abstract/copyright text occasionally embeds HTML
  named character entities (e.g. `&ldquo;`, `&rdquo;`, `&mldr;`, `&copy;`, `&agrave;`) that
  aren't valid XML entities on their own - ONIX escapes the leading `&` again (`&amp;ldquo;`) so
  the document parses, which means the parsed text contains the *literal* entity reference
  string (e.g. `&ldquo;Enrico Fermi&rdquo;`) rather than the actual Unicode character it names.
  `MedraOnixXmlParser` does not attempt to resolve these - passed straight through as received
  (see the `&amp;lt;sup&amp;gt;`-wrapped superscript digits in
  [`medra-no-contributors.xml`](src/test/resources/medra-no-contributors.xml)'s title for the
  same phenomenon applied to markup rather than named entities).
