# Notes for agents working in this repo

## No system Maven/JDK 21 on your machine

There is no system-wide JDK/Maven on this machine. For building, testing, or running
`mvn`/`java` in any form, use the `skg-if-build-toolchain` skill.

## "notest"/"skiptest" commit messages skip CI tests/validation

If a commit message contains `notest`, `notests`, `skiptest`, or `skiptests` (case-insensitive,
anywhere in the message), the GitHub Actions workflow defined in
[.github/workflows/maven-build.yml](.github/workflows/maven-build.yml) skips verification for
that commit: the `build` job runs `./mvnw -B package -DskipTests` instead of `./mvnw -B package`,
and the `validate-live-api` job (the live DataCite/Crossref contract-test script,
`scripts/ci/validate-live-endpoints.sh`) doesn't run at all. This is intentional - don't treat a
skipped/green-without-tests pipeline as broken when the triggering commit message uses one of
these keywords.

## Grep before reading large files

The `SKG_IF_DOI_MAPPING*.md` docs (up to ~31KB each - see the next section) and the OpenAPI specs
(`src/main/openapi/skg-if-openapi.yaml`, `target/generated-sources/.../openapi.yaml`) are large
enough that reading a whole file costs real tokens regardless of how much of it is actually
relevant. Grep for the specific field/row first to get a line number, then Read a narrow window
around it - reserve a full read for tasks that genuinely need whole-document context (e.g.
auditing every row of a table).

For test failures, read `target/surefire-reports/*.txt` (plain-text summary) instead of the
matching `TEST-*.xml` report - see the `skg-if-build-toolchain` skill for the measured size gap
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

## Java code style

Follow these when writing or editing `.java` files - `spotless-maven-plugin` (pom.xml) enforces
formatting on every `mvn test`/`package` (skipped by `-DskipTests`, same as PMD/checkstyle) and
fails the build on drift rather than auto-fixing it, so getting it right the first time avoids a
failed build. Run `mvn spotless:apply` to fix violations it reports:

- 4-space indent, never tabs.
- Max line length: 120 characters.
- Opening brace on the same line (`if (x) {`); `else`/`catch`/`finally` on the same line as the
  preceding closing brace (`} else {`).
- No space just inside parens: `foo(bar)`, `if (x)` - not `foo( bar )`.
- Space around binary/assignment operators: `a + b`, `x = y`.
- When a boolean/logical expression must wrap, put the operator at the END of the line being
  wrapped, not the start of the continuation line:
  ```java
  if (someCondition &&
          anotherCondition) {
  ```
- No unused or wildcard imports.
- No trailing whitespace; file ends with a newline.
- Public classes/methods need Javadoc.

Before reporting a task done, if it edited any `.java` files, run `spotless:apply` scoped to just
those files - this catches/fixes drift proactively instead of letting `mvn test` fail on it later,
without touching unrelated files elsewhere in the tree:

```
mvn spotless:apply -DspotlessFiles=<absolute-path-regex-1>,<absolute-path-regex-2>,...
```

`-DspotlessFiles` takes a comma-separated list of regexes, each matched with `Pattern.matches()`
against a candidate file's absolute path (so a bare filename needs a leading `.*`, e.g.
`.*MapperTextUtils\.java`) - confirmed by decompiling `AbstractSpotlessMojo.class` in the
`spotless-maven-plugin` 2.46.1 jar, since this isn't documented in the plugin's compiled metadata.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

If these tools aren't showing up as available (e.g. right after a fresh clone), the
local venv these tools run from hasn't been built yet — see the
`code-review-graph-setup` skill, then fall back to Grep/Glob/Read until it's done.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes_tool` or `query_graph_tool` instead of Grep
- **Understanding impact**: `get_impact_radius_tool` instead of manually tracing imports
- **Code review**: `detect_changes_tool` + `get_review_context_tool` instead of reading entire files
- **Finding relationships**: `query_graph_tool` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview_tool` + `list_communities_tool`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes_tool` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context_tool` | Need source snippets for review — token-efficient |
| `get_impact_radius_tool` | Understanding blast radius of a change |
| `get_affected_flows_tool` | Finding which execution paths are impacted |
| `query_graph_tool` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes_tool` | Finding functions/classes by name or keyword |
| `get_architecture_overview_tool` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes_tool` for code review.
3. Use `get_affected_flows_tool` to understand impact.
4. Use `query_graph_tool` pattern="tests_for" to check coverage.
