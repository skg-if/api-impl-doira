# Notes for agents working in this repo

## No system Maven/JDK 21 on your machine

There is no system-wide JDK/Maven on this machine. For building, testing, or running
`mvn`/`java` in any form, use the `build-toolchain` skill.

## "notest"/"skiptest" commit messages skip CI tests/validation

If a commit message contains `notest`, `notests`, `skiptest`, or `skiptests` (case-insensitive,
anywhere in the message), the GitLab pipeline defined in [.gitlab-ci.yml](.gitlab-ci.yml) skips
verification for that commit: the `build` job runs `mvn -B package -DskipTests` instead of
`mvn -B package`, and the `validate-live-api` job (the live DataCite/Crossref contract-test
script, `scripts/ci/validate-live-endpoints.sh`) doesn't run at all. This is intentional - don't
treat a skipped/green-without-tests pipeline as broken when the triggering commit message uses
one of these keywords.

## Grep before reading large files

The `SKG_IF_DOI_MAPPING*.md` docs (up to ~31KB each - see the next section) and the OpenAPI specs
(`src/main/openapi/skg-if-openapi.yaml`, `target/generated-sources/.../openapi.yaml`) are large
enough that reading a whole file costs real tokens regardless of how much of it is actually
relevant. Grep for the specific field/row first to get a line number, then Read a narrow window
around it - reserve a full read for tasks that genuinely need whole-document context (e.g.
auditing every row of a table).

For test failures, read `target/surefire-reports/*.txt` (plain-text summary) instead of the
matching `TEST-*.xml` report - see the `build-toolchain` skill for the measured size gap
(~180x smaller for the same pass/fail information), and for the `-q`/`-B`/`-Dtest=` flags that
keep `mvn` output itself from being noisy.

## Keep SKG_IF_DOI_MAPPING*.md in sync

[`SKG_IF_DOI_MAPPING.md`](SKG_IF_DOI_MAPPING.md) is a short index into four files that together
document, field by field, how `DataCiteToSkgIfMapper`/`CrossrefToSkgIfMapper` map onto SKG-IF,
and which test fixture proves each case:
[`SKG_IF_DOI_MAPPING_PRODUCT.md`](SKG_IF_DOI_MAPPING_PRODUCT.md),
[`SKG_IF_DOI_MAPPING_DATES.md`](SKG_IF_DOI_MAPPING_DATES.md),
[`SKG_IF_DOI_MAPPING_GRANT.md`](SKG_IF_DOI_MAPPING_GRANT.md), and
[`SKG_IF_DOI_MAPPING_LIMITATIONS.md`](SKG_IF_DOI_MAPPING_LIMITATIONS.md) - split apart so no
single file grows unreadably large (see the "Grep before reading large files" section above).
Whenever you touch either mapper, add/rename/remove a fixture under `src/test/resources`, or
add/change a mapper or REST test, update whichever of these files covers that field in the same
change:

- Add a link for any newly-tested case (`✅`/`✅\*`) or newly-untested gap (`❌`).
- Re-check existing prose in the row you're touching - a claim like "never appears in a golden
  output" can go stale even when no file is renamed, which `MappingDocConsistencyTest` (below)
  cannot catch.
- If a change moves content between one of these files and another, update the cross-references
  between them too (a `[date-type table](SKG_IF_DOI_MAPPING_DATES.md#date-type-mapping)`-style
  link, not a same-file `#anchor`).

`src/test/java/org/skgif/doi/docs/MappingDocConsistencyTest.java` fails the build if a fixture
under `src/test/resources` isn't mentioned anywhere across the `SKG_IF_DOI_MAPPING*.md` files, or
if any of them links to a fixture that doesn't exist - treat a failure there as a reminder to
update the relevant doc, not just satisfy the test by adding a bare filename mention.
