# DOI Provider → SKG-IF Mapping

This document summarizes how records from **DataCite**, **Crossref**, and **mEDRA** are mapped
onto the SKG-IF `Product` and `Grant` entities by [`DataCiteToSkgIfMapper`](src/main/java/org/skgif/doi/datacite/mapper/DataCiteToSkgIfMapper.java),
[`CrossrefToSkgIfMapper`](src/main/java/org/skgif/doi/crossref/mapper/CrossrefToSkgIfMapper.java),
and [`MedraToSkgIfMapper`](src/main/java/org/skgif/doi/medra/mapper/MedraToSkgIfMapper.java).
mEDRA has no `Grant` mapping at all - see [Product ↔ Grant routing](SKG_IF_DOI_MAPPING_GRANT.md#product--grant-routing).

> **Reference docs:** the ℹ️ links below point to
> [DataCite Metadata Schema 4.7](https://datacite-metadata-schema.readthedocs.io/en/4.7/) (latest
> release at the time of writing) and the
> [SKG-IF Interoperability Framework](https://skg-if.github.io/interoperability-framework/) docs.
> Crossref has no equivalent per-field docs, so its fields aren't individually linked - see instead
> the [journals and articles](https://www.crossref.org/documentation/schema-library/markup-guide-record-types/journals-and-articles/)
> and [grants](https://www.crossref.org/documentation/schema-library/markup-guide-record-types/grants/)
> markup guides, the [REST API reference](https://api.crossref.org/swagger-ui/index.html), and the
> [XML samples](https://www.crossref.org/xml-samples/). mEDRA's format is **ONIX-for-DOI 2.0**,
> documented at [`medra.org/en/onix.htm`](https://www.medra.org/en/onix.htm) and, specifically
> for the "Serial Article" schema this mapper handles, at
> [`ONIX_DOI_Serial_Article_2.0_v.2.pdf`](https://www.medra.org/stdoc/ONIX_DOI_Serial_Article_2.0_v.2.pdf) -
> note that EDItEUR's own overview page ([`editeur.org/97/ONIX-DOI-Registration-Formats`](https://www.editeur.org/97/ONIX-DOI-Registration-Formats/))
> still advertises spec version 1.1, while every live mEDRA record examined declared schema
> version 2.0 (see [Known limitations](SKG_IF_DOI_MAPPING_LIMITATIONS.md)).

All three mappers follow the same conventions:

- **`local_identifier`** is a full, dereferenceable URL whenever a real external identifier
  exists (`https://doi.org/...` for DOIs, `https://orcid.org/...` for ORCIDs,
  `https://ror.org/...` for RORs).
- When no stable identifier exists (free-text subjects, publishers, unidentified affiliations),
  an **"on-the-fly" (otf) identifier** is generated: `otf___<slug(doi)>___<slug(label)>`,
  deterministic per source DOI so repeated calls produce identical output.
- `contributions[].by.entity_type` is set correctly for every branch (persons, organisations,
  agents), same as every other entity in this output (organisations, grants, datasources, topics,
  products).

This document is split by entity/topic to keep each file a manageable read on its own:

- [**Product entity**](SKG_IF_DOI_MAPPING_PRODUCT.md) - field-by-field mapping table and the
  product-type vocabulary
- [**Date-type mapping**](SKG_IF_DOI_MAPPING_DATES.md) - detail for the Product entity's
  `manifestations[].dates` row, broken out separately since it's a table of its own
- [**Grant entity**](SKG_IF_DOI_MAPPING_GRANT.md) - field-by-field mapping table and
  Product/Grant routing rules
- [**Known limitations**](SKG_IF_DOI_MAPPING_LIMITATIONS.md) - real, documented gaps in either
  provider or mapper, organized by provider
