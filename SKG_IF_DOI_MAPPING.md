# DOI Provider → SKG-IF Mapping

This document summarizes how records from **DataCite** and **Crossref** are mapped onto the
SKG-IF `Product` and `Grant` entities by [`DataCiteToSkgIfMapper`](src/main/java/org/skgif/doi/datacite/mapper/DataCiteToSkgIfMapper.java)
and [`CrossrefToSkgIfMapper`](src/main/java/org/skgif/doi/crossref/mapper/CrossrefToSkgIfMapper.java).

Both mappers follow the same conventions:

- **`local_identifier`** is a full, dereferenceable URL whenever a real external identifier
  exists (`https://doi.org/...` for DOIs, `https://orcid.org/...` for ORCIDs,
  `https://ror.org/...` for RORs).
- When no stable identifier exists (free-text subjects, publishers, unidentified affiliations),
  an **"on-the-fly" (otf) identifier** is generated: `otf___<slug(doi)>___<slug(label)>`,
  deterministic per source DOI so repeated calls produce identical output.
- `contributions[].by.entity_type` is left unset for **persons** in both mappers - a generator
  limitation (see mapper javadoc), not a data gap. Organisations, grants, datasources, topics,
  and products all get `entity_type` set correctly.

---

## Product entity

**Coverage legend:** ✅ golden-JSON test (input → expected output, byte-compared) · ✅\* Java-only
assertion, no golden JSON (see footnote) · ❌ not exercised by any test · – not applicable for
this provider

| SKG-IF field | DataCite source | Crossref source | DataCite tested | Crossref tested | Notes |
|---|---|---|---|---|---|
| `local_identifier` | `doi` (as full `https://doi.org/...` URL) | `doi` (as full URL) | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json) · [out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅ [in](src/test/resources/crossref-journal-article.json) · [out](src/test/resources/expected/crossref-product-nature12373.json) | |
| `product_type` | `types.resourceTypeGeneral` | `type` | ✅ research data: [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json)<br>✅\* research software: [in](src/test/resources/datacite-zenodo-software-21826016.json) (`DataCiteToSkgIfMapperTest.mapsResourceTypeGeneralToProductType`)<br>✅\* literature: [in](src/test/resources/datacite-zenodo-text-20750072.json) (same test) | ✅ literature (`journal-article`): [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json)<br>✅ literature (`proceedings-article`): [in](src/test/resources/crossref-proceedings-article.json)·[out](src/test/resources/expected/crossref-product-icmbb18-42.json)<br>✅\* research data: [in](src/test/resources/crossref-dataset.json) (`CrossrefToSkgIfMapperTest.mapsResourceTypeToProductType`) | See [Product type vocabulary mapping](#product-type-vocabulary-mapping) below. `research software` unreachable via Crossref; `other` bucket untested for either provider |
| `identifiers[]` | `{scheme: "doi", value: doi}` | `{scheme: "doi", value: doi}` | ✅ (same golden pair as `local_identifier`) | ✅ (same golden pair as `local_identifier`) | |
| `titles` | `titles[].title` → `{en: [...]}` | `title[]` → `{en: [...]}` | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) | Single-language (`en`) only |
| `abstracts` | `descriptions[]` where `descriptionType == "Abstract"` | `abstract` (JATS-XML string) | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅\* [in](src/test/resources/crossref-journal-article-with-funder.json) (`CrossrefToSkgIfMapperTest.mapsAbstractStrippingJatsXmlTags`)<br>✅ golden: [in](src/test/resources/crossref-journal-article-with-orcid.json)·[out](src/test/resources/expected/crossref-product-s41467-022-33468-6.json) | Crossref: XML tags stripped before use. The original golden-tested fixture (`nature12373`) has no `abstract` at all, but `abstracts` does appear (JATS-stripped) in the `s41467-022-33468-6` golden output |
| `topics[].term` | `subjects[].subject` (otf id; `lang` used if present, else `"none"`) | `subject[]` (Sci-Val vocabulary; otf id, `en`) | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) (both `lang` and no-`lang` cases in the same fixture) | ❌ all Crossref fixtures have empty `subject: []` | Neither provider gives an external ID system for subjects |
| `contributions[]` (authors) | `creators[]` | `author[]` | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) (10 creators) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) (8 authors) | Rank assigned in list order |
| `contributions[]` (editors) | `contributors[]` where `contributorType == "Editor"` | `editor[]` | ❌ no fixture has `contributorType: "Editor"` | ❌ no fixture has an `editor` key | |
| `contributions[]` (publishers) | `contributors[]` where `contributorType == "Publisher"` | *(not modeled)* | ❌ no fixture has `contributorType: "Publisher"` | – not modeled | DataCite-only role |
| `contributions[].by` (person) | `creators[]/contributors[].name/givenName/familyName/nameIdentifiers` (ORCID → full URL, else otf) | `author[]/editor[].given/family/orcid` (ORCID already a full URL; normalized to bare, then re-prefixed) | ✅ ORCID: [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅ ORCID: [in](src/test/resources/crossref-journal-article-with-orcid.json)·[out](src/test/resources/expected/crossref-product-s41467-022-33468-6.json) (`CrossrefToSkgIfMapperTest.mapsAuthorsAsAuthorContributionsWithOrcidWhenPresent`)<br>✅ otf fallback: [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) (`CrossrefToSkgIfMapperTest.mapsAuthorsAsAuthorContributionsWithoutOrcidWhenAbsent`) | Both cases can appear on the same record: the `s41467-022-33468-6` fixture mixes ORCID-bearing and ORCID-absent authors |
| `contributions[].declaredAffiliations` | `creators[]/contributors[].affiliation[]` (ROR if `affiliationIdentifierScheme == "ROR"`, else otf) | `author[]/editor[].affiliation[]` (ROR if asserted directly on the affiliation, else otf) | ❌ with ROR: no fixture has `affiliationIdentifierScheme`<br>✅ otf fallback: [in](src/test/resources/datacite-esrf-es-2210534378.json)·[out](src/test/resources/expected/datacite-product-esrf-es-2210534378.json) | ✅ with ROR: [in](src/test/resources/crossref-journal-article-with-ror-affiliation.json)·[out](src/test/resources/expected/crossref-product-physrevb-110-174515.json) (`CrossrefToSkgIfMapperTest.mapsDeclaredAffiliationsWithRorWhenPresent`)<br>❌ name-only otf fallback: a name-only affiliation exists in `crossref-journal-article-with-funder.json` but isn't asserted by any test | Some publishers (e.g. APS) assert a ROR on author affiliations directly - the mapper's prior assumption that Crossref never carries one here was wrong |
| `manifestations[].type` | `types.resourceTypeGeneral` label | `type` label | ✅ (same golden evidence as `product_type`) | ✅ (same golden evidence as `product_type`) | |
| `manifestations[].dates` | `dates[]` via [date-type table](#date-type-mapping) | `created`, `deposited`, `posted`, `accepted`, `published-print`, `published-online`, `issued` | ✅ (see date-type table below) | ✅ (see date-type table below) | |
| `manifestations[].access_rights.status` | `rightsList[]` - `open` if any `rightsUri` contains `creativecommons.org` | `license[]` - `open` if any `url` contains `creativecommons.org` | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅\* [in](src/test/resources/crossref-journal-article-with-funder.json) (`CrossrefToSkgIfMapperTest.mapsAccessRightsAsOpenFromCreativeCommonsLicence`)<br>✅ golden open: [in](src/test/resources/crossref-journal-article-with-orcid.json)·[out](src/test/resources/expected/crossref-product-s41467-022-33468-6.json) (CC-BY)<br>✅ golden non-open: [in](src/test/resources/crossref-journal-article-with-ror-affiliation.json)·[out](src/test/resources/expected/crossref-product-physrevb-110-174515.json) (APS default licence, `access_rights: {}`) | Both the open and non-open paths are proven at the golden-output level now |
| `manifestations[].licence` | `rightsList[0].rightsUri` | `license[0].url` | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) (non-CC URL - proves the non-open path too) | First entry only |
| `manifestations[].version` | `version` | *(not available)* | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) (`"1"`; the es fixture/golden also proves the `null`→omitted case) | – not available | Crossref doesn't register software versions |
| `manifestations[].biblio.hosting_data_source` | `publisher` (always otf) | `publisher` (always otf) | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) | Neither provider gives an external ID for the publisher itself |
| `manifestations[].biblio.issue/volume/pages` | *(not modeled)* | `issue`, `volume`, `page` (split on `-` into `first`/`last`) | – not modeled | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json)<br>✅ no `page` present: [in](src/test/resources/crossref-journal-article-with-orcid.json)·[out](src/test/resources/expected/crossref-product-s41467-022-33468-6.json) (`CrossrefToSkgIfMapperTest.doesNotFabricatePagesWhenOnlyArticleNumberIsPresent`) | Crossref-only. `article-number` (used by some publishers instead of `page`) has no SKG-IF field to carry it - `pages` stays unset rather than guessed at |
| `manifestations[].biblio.in` (venue) | *(not modeled)* | `container-title[0]` (otf id) + `ISSN[]` (`{scheme: "issn"}`) | – not modeled | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) | Crossref-only - DataCite has no equivalent venue field |
| `funding[]` | `fundingReferences[]` (otf id from `awardNumber`/`awardTitle`) | `funder[].award[]` (one funding entry per award; otf id from award or funder name) | ✅ [in](src/test/resources/datacite-esrf-es-2210534378.json)·[out](src/test/resources/expected/datacite-product-esrf-es-2210534378.json) | ✅\* [in](src/test/resources/crossref-journal-article-with-funder.json) (`CrossrefToSkgIfMapperTest.mapsFunderWithoutAwardNumberOrFunderDoi`)<br>✅ golden: [in](src/test/resources/crossref-journal-article-with-ror-affiliation.json)·[out](src/test/resources/expected/crossref-product-physrevb-110-174515.json) (4 entries, incl. the same funder repeated with 2 different award numbers) | The original golden-tested fixture (`nature12373`) has no `funder` at all, but `funding[]` does appear in the `physrevb-110-174515` golden output |
| `funding[].funding_agency` | `fundingReferences[].funderName/funderIdentifier` (ROR if `funderIdentifierType == "ROR"`, else otf) | `funder[].name` + Funder Registry DOI (`{scheme: "doi"}`, else otf) | ✅ with ROR: [in](src/test/resources/datacite-esrf-es-2210534378.json)·[out](src/test/resources/expected/datacite-product-esrf-es-2210534378.json)<br>❌ otf fallback: not exercised (the only funding fixture has a ROR) | ✅ with DOI: [in](src/test/resources/crossref-journal-article-with-ror-affiliation.json)·[out](src/test/resources/expected/crossref-product-physrevb-110-174515.json) (`CrossrefToSkgIfMapperTest.mapsFundingWithFunderDoiAndMultipleAwardsForSameFunder`)<br>✅\* otf fallback: [in](src/test/resources/crossref-journal-article-with-funder.json) (`CrossrefToSkgIfMapperTest.mapsFunderWithoutAwardNumberOrFunderDoi`) | Crossref funder ID is a **DOI**, not a ROR. A single funder can carry multiple awards, each surfacing as its own `funding[]` entry |
| `related_products.cites` | `relatedIdentifiers[]` where `relationType == "Cites"` (DOI → full URL, else otf) | `reference[]` - `doi` → full URL with `{scheme: "doi"}`, else otf id from `unstructured`/`key` (no `identifiers`) | ❌ no fixture has `relationType: "Cites"` (only `HasVersion`, itself untested) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) (30/30 references present; the one without a DOI gets an otf id)<br>✅ neither DOI nor `unstructured`: [in](src/test/resources/crossref-proceedings-article.json)·[out](src/test/resources/expected/crossref-product-icmbb18-42.json) (`CrossrefToSkgIfMapperTest.mapsReferenceWithNeitherDoiNorUnstructuredToOtfIdFromKey`) | DataCite also computes `IsCitedBy` internally but does not emit it (see code). The otf label falls back to the bare `reference[].key` when a reference has neither a DOI nor `unstructured` text |

\* Java-level assertion only (`assertEquals`/`assertTrue` in the mapper's unit test) - no
golden-JSON comparison test exists for this case, so no "expected output" file can be linked.

### Product type vocabulary mapping

**DataCite `resourceTypeGeneral` → SKG-IF `product_type`:**

| SKG-IF `product_type` | DataCite values |
|---|---|
| `research software` | `Software`, `ComputationalNotebook`, `Workflow` |
| `literature` | `Book`, `BookChapter`, `ConferencePaper`, `ConferenceProceeding`, `DataPaper`, `Dissertation`, `JournalArticle`, `Journal`, `Preprint`, `Report`, `Text`, `PeerReview`, `StudyRegistration`, `OutputManagementPlan` |
| `research data` | `Dataset`, `Collection`, `Image` |
| `other` | `Event`, `Service`, `Project`, `Other`, `Sound`, `PhysicalObject`, `Model`, `Audiovisual`, `InteractiveResource`, `Standard`, and anything unrecognized |
| *(routed to Grants, not a Product)* | `Award` |

**Crossref `type` → SKG-IF `product_type`:**

| SKG-IF `product_type` | Crossref values |
|---|---|
| `research data` | `dataset` |
| `literature` | `journal-article`, `book`, `book-chapter`, `book-section`, `book-part`, `book-series`, `book-set`, `book-track`, `monograph`, `edited-book`, `reference-book`, `reference-entry`, `proceedings`, `proceedings-article`, `proceedings-series`, `report`, `report-series`, `report-component`, `dissertation`, `peer-review`, `posted-content`, `journal`, `journal-issue`, `journal-volume`, `component` |
| `other` | `other`, `database`, `standard`, and anything unrecognized |
| *(routed to Grants, not a Product)* | `grant` |
| `research software` | *(unreachable - Crossref has no software type)* |

### Date-type mapping

| SKG-IF date type | DataCite `dateType` | Crossref field | DataCite tested | Crossref tested |
|---|---|---|---|---|
| `acceptance` | `Accepted` | `accepted` | ❌ not in any fixture | ❌ not in any fixture |
| `access` | `Available` | `posted` | ✅ [in](src/test/resources/datacite-esrf-es-2210534378.json)·[out](src/test/resources/expected/datacite-product-esrf-es-2210534378.json) | ❌ not in any fixture |
| `collected` | `Collected` | *(not available)* | ✅ [in](src/test/resources/datacite-esrf-es-2210534378.json)·[out](src/test/resources/expected/datacite-product-esrf-es-2210534378.json) | – not available |
| `copyright` | `Copyrighted` | *(not available)* | ❌ not in any fixture | – not available |
| `creation` | `Created` | `created` | ❌ not in any fixture (DataCite's `Created` never appears) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) |
| `publication` | `Issued` | `published-print`, `published-online`, `issued` | ✅ [in](src/test/resources/datacite-esrf-dc-2493599001.json)·[out](src/test/resources/expected/datacite-product-esrf-dc-2493599001.json) | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) |
| `deposit` | `Submitted` | `deposited` | ❌ not in any fixture | ✅ [in](src/test/resources/crossref-journal-article.json)·[out](src/test/resources/expected/crossref-product-nature12373.json) |
| `modified` | `Updated` | *(not available)* | ❌ not in any fixture | – not available |
| `validity` | `Valid` | *(not available)* | ❌ not in any fixture | – not available |
| `retraction` | `Withdrawn` | *(not available)* | ❌ not in any fixture | – not available |

---

## Grant entity

DataCite has no dedicated Award schema fields for funder/amount/duration, so its mapping relies
on a heuristic; Crossref's `grant` type carries these explicitly.

**Coverage legend:** ✅ golden-JSON test (input → expected output, byte-compared) · ❌ not
exercised by any test · – not applicable for this provider

| SKG-IF field | DataCite source (`resourceTypeGeneral: "Award"`) | Crossref source (`type: "grant"`) | DataCite tested | Crossref tested | Notes |
|---|---|---|---|---|---|
| `local_identifier` | `doi` (full URL) | `doi` (full URL) | ✅ [in](src/test/resources/datacite-award-r3sy-7371.json)·[out](src/test/resources/expected/datacite-grant-award-r3sy-7371.json) | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) | |
| `identifiers[]` | `{scheme: "doi", value: doi}` | `{scheme: "doi", value: doi}` | ✅ (same golden pair above) | ✅ (same golden pair above) | |
| `titles` / `abstracts` | `titles[]` / `descriptions[type=Abstract]` (same as Product) | `project[].projectTitle[]` / `project[].projectDescription[]`, concatenated across all projects | ✅ (same golden pair above) | ✅ (same golden pair above) | |
| `grant_number` | *(not available)* | `award` | – not available | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) (`"218300"`) | DataCite Award schema has no generic award-number field |
| `funding_agency` | **Heuristic:** first `creators[]` entry carrying a ROR identifier; falls back to `publisher` (otf) if none | `project[].funding[0].funder`, falling back to top-level `funder[0]`; identified by Funder Registry DOI or otf | ✅ ROR-creator heuristic: [in](src/test/resources/datacite-award-r3sy-7371.json)·[out](src/test/resources/expected/datacite-grant-award-r3sy-7371.json)<br>❌ publisher fallback: not exercised | ✅ project funding funder (DOI): [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json)<br>❌ top-level `funder[]` fallback: not exercised | DataCite: no dedicated funder field at all |
| `funding_stream` | *(not available)* | `project[].funding[0].scheme` | – not available | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) | |
| `funded_amount` / `currency` | *(not available)* | `project[].funding[0].award_amount` / its `funding[0].awardAmount`, falling back to `project[].awardAmount` | – not available | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) | Fixture has identical amounts at both `funding[]`- and `project`-level, so it doesn't disambiguate mapper precedence |
| `duration.start/end` | *(not available)* | `project[].awardStart` / `awardEnd` | – not available | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) | |
| `website` | *(not available)* | `resource.primary.url` | – not available | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) | |
| `contributions[]` | Every `creators[]` entry except the identified funding-agency creator, plus all `contributors[]` | `project[].leadInvestigator[]` (role `lead-applicant`) and `project[].investigator[]` (role `co-applicant`), across all projects | ✅ organisational contributor + ✅ person contributor: [in](src/test/resources/datacite-award-r3sy-7371.json)·[out](src/test/resources/expected/datacite-grant-award-r3sy-7371.json) | ✅ lead-applicant + ✅ co-applicant: [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) (1 lead + 8 co) | DataCite contribution roles are unset (no lead/co distinction); Crossref sets `roles[]` explicitly |
| `contributions[].by` | Person (ORCID/otf) or, if `nameType == "Organizational"`, an Organisation (ROR/otf) | Person (ORCID/otf) only - investigators are always individuals | ✅ [in](src/test/resources/datacite-award-r3sy-7371.json)·[out](src/test/resources/expected/datacite-grant-award-r3sy-7371.json) (both Person and Organisation cases) | – not applicable | |
| `beneficiaries[]` | `contributors[]` with `nameType == "Organizational"`, reused as affiliations (ROR/otf) | Investigators' own `affiliation[]`, deduplicated by name (ROR/otf) | ✅ [in](src/test/resources/datacite-award-r3sy-7371.json)·[out](src/test/resources/expected/datacite-grant-award-r3sy-7371.json) | ✅ [in](src/test/resources/crossref-grant.json)·[out](src/test/resources/expected/crossref-grant-218300.json) (3 institutions deduped from 9 investigators) | Same judgment call in both mappers: no clean "beneficiary" field in either source, so declared affiliations of contributors/investigators double as beneficiaries |
| `acronym` | *(not available)* | *(not available)* | – not available | – not available | No equivalent field in either source |

### Product ↔ Grant routing

A DOI record is routed to the **Grants** endpoint instead of **Products** when:

- DataCite: `types.resourceTypeGeneral == "Award"` (`ResourceTypeMapping.isAward`)
- Crossref: `type == "grant"` (`CrossrefTypeMapping.isGrant`)

---

## Known limitations

- **Person `entity_type`** is never emitted on `contributions[].by` (both providers) - an
  openapi-generator limitation on the `by` oneOf, not a data availability gap.
- **DataCite Award records** lack any generic source for `grant_number`, `currency`,
  `funded_amount`, `duration`, `website`, `funding_stream`, or `acronym` - left unset rather than
  guessed at.
- **Crossref** has no software-versioning field, no organisation-level `relevantOrganisations`
  outside per-contributor affiliations, and no `acronym` field for grants.
- **Crossref grant records** with multiple `project[]` entries (e.g. joint awards) contribute
  titles/abstracts/contributions/beneficiaries from *all* projects, but funding
  amount/currency/duration/scheme are taken from the *first* project's *first* funding entry only.
