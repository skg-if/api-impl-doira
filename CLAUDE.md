# Notes for agents working in this repo

## No system Maven/JDK 21 on your machine

There is no system-wide JDK/Maven on this machine. For building, testing, or running
`mvn`/`java` in any form, use the `build-toolchain` skill.

## Keep SKG_IF_DOI_MAPPING.md in sync

[`SKG_IF_DOI_MAPPING.md`](SKG_IF_DOI_MAPPING.md) documents, field by field, how
`DataCiteToSkgIfMapper`/`CrossrefToSkgIfMapper` map onto SKG-IF, and which test fixture proves
each case. Whenever you touch either mapper, add/rename/remove a fixture under
`src/test/resources`, or add/change a mapper or REST test, update this doc in the same change:

- Add a link for any newly-tested case (`✅`/`✅\*`) or newly-untested gap (`❌`).
- Re-check existing prose in the row you're touching - a claim like "never appears in a golden
  output" can go stale even when no file is renamed, which `MappingDocConsistencyTest` (below)
  cannot catch.

`src/test/java/org/skgif/doi/docs/MappingDocConsistencyTest.java` fails the build if a fixture
under `src/test/resources` isn't mentioned anywhere in the doc, or if the doc links to a fixture
that doesn't exist - treat a failure there as a reminder to update the doc, not just satisfy the
test by adding a bare filename mention.
