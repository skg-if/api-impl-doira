# Onboarding: working on puma-skg-if-api with Claude Code

Quick orientation for opening this repo in Claude Code. For the full project description,
running/testing commands, and config reference, see [README.md](README.md) - this file only
covers the things specific to working here with an agent.

## What this project is

A [SKG-IF](https://skg-if.github.io/api/) REST API that serves DataCite and Crossref DOI metadata
live, with no local database. Two provider implementations
(`org.skgif.doi.datacite` / `org.skgif.doi.crossref`) map their respective upstream records onto
the same generated `Product`/`Grant` SKG-IF entities. Provider selection is by URL path
(`/datacite/...` vs `/crossref/...`), never auto-detected.

## No system Maven/JDK 21

This machine has no system-wide JDK/Maven, and none should be installed via `winget`/`choco`.
Use the **`skg-if-build-toolchain`** skill for any `mvn`/`java` invocation - it sets up a portable
toolchain cached under `.tools/` (gitignored, survives across conversations, downloaded once per
checkout).

## The mapping doc is a contract, not documentation-after-the-fact

[`SKG_IF_DOI_MAPPING.md`](SKG_IF_DOI_MAPPING.md) indexes four files (Product entity, Date-type
mapping, Grant entity, Known limitations) that together are the field-by-field record of how
`DataCiteToSkgIfMapper`/`CrossrefToSkgIfMapper` map onto SKG-IF, with a link to the test fixture
proving each case. It's enforced by
`src/test/java/org/skgif/doi/docs/MappingDocConsistencyTest.java`, which fails the build if a
fixture under `src/test/resources` isn't mentioned in any of them, or if one of them links to a
fixture that doesn't exist.

**Whenever you (or an agent) touch either mapper, add/rename/remove a fixture, or change a
mapper/REST test, update this doc in the same change:**

- Add a link for any newly-tested case (`✅`/`✅\*`) or newly-untested gap (`❌`).
- Re-check existing prose in the row you're touching - a claim like "never appears in a golden
  output" can go stale even when no file is renamed, which the consistency test cannot catch.

Treat a `MappingDocConsistencyTest` failure as a reminder to update the doc, not just a bare
filename mention to satisfy the test.

## Golden-file tests

`DataCiteProductsResourceTest`/`DataCiteGrantsResourceTest` (DataCite) and `CrossrefProductsResourceTest`/
`CrossrefGrantsResourceTest` (Crossref) byte-compare full JSON-LD responses against committed
fixtures in `src/test/resources/expected/`. After a mapper change that alters response shape,
regenerate the affected provider's fixtures with `-Dgolden.regenerate=true` (see README's Testing
section for the exact commands), then read the diff before committing - don't just accept
whatever the regeneration produces.

## Working conventions

- Never commit or push unless explicitly asked to in that message.
- Don't install tools globally (`winget`/`choco`) - everything portable goes under `.tools/`.
