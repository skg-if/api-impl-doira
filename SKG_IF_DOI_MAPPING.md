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

| SKG-IF field | DataCite source | Crossref source | Notes |
|---|---|---|---|
| `local_identifier` | `doi` (as full `https://doi.org/...` URL) | `doi` (as full URL) | |
| `product_type` | `types.resourceTypeGeneral` | `type` | See [Product type vocabulary mapping](#product-type-vocabulary-mapping) below |
| `identifiers[]` | `{scheme: "doi", value: doi}` | `{scheme: "doi", value: doi}` | |
| `titles` | `titles[].title` → `{en: [...]}` | `title[]` → `{en: [...]}` | Single-language (`en`) only |
| `abstracts` | `descriptions[]` where `descriptionType == "Abstract"` | `abstract` (JATS-XML string) | Crossref: XML tags stripped before use |
| `topics[].term` | `subjects[].subject` (otf id; `lang` used if present, else `"none"`) | `subject[]` (Sci-Val vocabulary; otf id, `en`) | Neither provider gives an external ID system for subjects |
| `contributions[]` (authors) | `creators[]` | `author[]` | Rank assigned in list order |
| `contributions[]` (editors) | `contributors[]` where `contributorType == "Editor"` | `editor[]` | |
| `contributions[]` (publishers) | `contributors[]` where `contributorType == "Publisher"` | *(not modeled)* | DataCite-only role |
| `contributions[].by` (person) | `creators[]/contributors[].name/givenName/familyName/nameIdentifiers` (ORCID → full URL, else otf) | `author[]/editor[].given/family/orcid` (ORCID already a full URL; normalized to bare, then re-prefixed) | |
| `contributions[].declaredAffiliations` | `creators[]/contributors[].affiliation[]` (ROR if `affiliationIdentifierScheme == "ROR"`, else otf) | `author[]/editor[].affiliation[]` (name-only; always otf - Crossref carries no ROR here) | |
| `manifestations[].type` | `types.resourceTypeGeneral` label | `type` label | |
| `manifestations[].dates` | `dates[]` via [date-type table](#date-type-mapping) | `created`, `deposited`, `posted`, `accepted`, `published-print`, `published-online`, `issued` | |
| `manifestations[].access_rights.status` | `rightsList[]` - `open` if any `rightsUri` contains `creativecommons.org` | `license[]` - `open` if any `url` contains `creativecommons.org` | |
| `manifestations[].licence` | `rightsList[0].rightsUri` | `license[0].url` | First entry only |
| `manifestations[].version` | `version` | *(not available)* | Crossref doesn't register software versions |
| `manifestations[].biblio.hosting_data_source` | `publisher` (always otf) | `publisher` (always otf) | Neither provider gives an external ID for the publisher itself |
| `manifestations[].biblio.issue/volume/pages` | *(not modeled)* | `issue`, `volume`, `page` (split on `-` into `first`/`last`) | Crossref-only |
| `manifestations[].biblio.in` (venue) | *(not modeled)* | `container-title[0]` (otf id) + `ISSN[]` (`{scheme: "issn"}`) | Crossref-only - DataCite has no equivalent venue field |
| `funding[]` | `fundingReferences[]` (otf id from `awardNumber`/`awardTitle`) | `funder[].award[]` (one funding entry per award; otf id from award or funder name) | |
| `funding[].funding_agency` | `fundingReferences[].funderName/funderIdentifier` (ROR if `funderIdentifierType == "ROR"`, else otf) | `funder[].name` + Funder Registry DOI (`{scheme: "doi"}`, else otf) | Crossref funder ID is a **DOI**, not a ROR |
| `related_products.cites` | `relatedIdentifiers[]` where `relationType == "Cites"` (DOI → full URL, else otf) | `reference[]` where a `doi` is present (full URL only; free-text-only refs skipped) | DataCite also computes `IsCitedBy` internally but does not emit it (see code) |

### Product type vocabulary mapping

**DataCite `resourceTypeGeneral` → SKG-IF `product_type`:**

| SKG-IF `product_type` | DataCite values |
|---|---|
| `research software` | `Software`, `ComputationalNotebook`, `Workflow` |
| `literature` | `Book`, `BookChapter`, `ConferencePaper`, `ConferenceProceeding`, `DataPaper`, `Dissertation`, `JournalArticle`, `Journal`, `Preprint`, `Report`, `Text`, `PeerReview`, `StudyRegistration`, `OutputManagementPlan` |
| `research data` | `Dataset`, `Collection`, `Image`, `Sound`, `PhysicalObject`, `Model`, `Audiovisual`, `InteractiveResource`, `Standard` |
| `other` | `Event`, `Service`, `Project`, `Other`, and anything unrecognized |
| *(routed to Grants, not a Product)* | `Award` |

**Crossref `type` → SKG-IF `product_type`:**

| SKG-IF `product_type` | Crossref values |
|---|---|
| `research data` | `dataset`, `database`, `standard` |
| `literature` | `journal-article`, `book`, `book-chapter`, `book-section`, `book-part`, `book-series`, `book-set`, `book-track`, `monograph`, `edited-book`, `reference-book`, `reference-entry`, `proceedings`, `proceedings-article`, `proceedings-series`, `report`, `report-series`, `report-component`, `dissertation`, `peer-review`, `posted-content`, `journal`, `journal-issue`, `journal-volume`, `component` |
| `other` | `other`, and anything unrecognized |
| *(routed to Grants, not a Product)* | `grant` |
| `research software` | *(unreachable - Crossref has no software type)* |

### Date-type mapping

| SKG-IF date type | DataCite `dateType` | Crossref field |
|---|---|---|
| `acceptance` | `Accepted` | `accepted` |
| `access` | `Available` | `posted` |
| `collected` | `Collected` | *(not available)* |
| `copyright` | `Copyrighted` | *(not available)* |
| `creation` | `Created` | `created` |
| `publication` | `Issued` | `published-print`, `published-online`, `issued` |
| `deposit` | `Submitted` | `deposited` |
| `modified` | `Updated` | *(not available)* |
| `validity` | `Valid` | *(not available)* |
| `retraction` | `Withdrawn` | *(not available)* |

---

## Grant entity

DataCite has no dedicated Award schema fields for funder/amount/duration, so its mapping relies
on a heuristic; Crossref's `grant` type carries these explicitly.

| SKG-IF field | DataCite source (`resourceTypeGeneral: "Award"`) | Crossref source (`type: "grant"`) | Notes |
|---|---|---|---|
| `local_identifier` | `doi` (full URL) | `doi` (full URL) | |
| `identifiers[]` | `{scheme: "doi", value: doi}` | `{scheme: "doi", value: doi}` | |
| `titles` / `abstracts` | `titles[]` / `descriptions[type=Abstract]` (same as Product) | `project[].projectTitle[]` / `project[].projectDescription[]`, concatenated across all projects | |
| `grant_number` | *(not available)* | `award` | DataCite Award schema has no generic award-number field |
| `funding_agency` | **Heuristic:** first `creators[]` entry carrying a ROR identifier; falls back to `publisher` (otf) if none | `project[].funding[0].funder`, falling back to top-level `funder[0]`; identified by Funder Registry DOI or otf | DataCite: no dedicated funder field at all |
| `funding_stream` | *(not available)* | `project[].funding[0].scheme` | |
| `funded_amount` / `currency` | *(not available)* | `project[].funding[0].award_amount` / its `funding[0].awardAmount`, falling back to `project[].awardAmount` | |
| `duration.start/end` | *(not available)* | `project[].awardStart` / `awardEnd` | |
| `website` | *(not available)* | `resource.primary.url` | |
| `contributions[]` | Every `creators[]` entry except the identified funding-agency creator, plus all `contributors[]` | `project[].leadInvestigator[]` (role `lead-applicant`) and `project[].investigator[]` (role `co-applicant`), across all projects | DataCite contribution roles are unset (no lead/co distinction); Crossref sets `roles[]` explicitly |
| `contributions[].by` | Person (ORCID/otf) or, if `nameType == "Organizational"`, an Organisation (ROR/otf) | Person (ORCID/otf) only - investigators are always individuals | |
| `beneficiaries[]` | `contributors[]` with `nameType == "Organizational"`, reused as affiliations (ROR/otf) | Investigators' own `affiliation[]`, deduplicated by name (ROR/otf) | Same judgment call in both mappers: no clean "beneficiary" field in either source, so declared affiliations of contributors/investigators double as beneficiaries |
| `acronym` | *(not available)* | *(not available)* | No equivalent field in either source |

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
