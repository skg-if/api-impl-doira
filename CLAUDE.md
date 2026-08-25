# Notes for agents working in this repo

## No system Maven/JDK on your machine

There is no system-wide JDK/Maven on this machine. Before running `java`, or any build
command, dot-source the activation script from the `skg-if-build-toolchain` skill - it
provisions the portable JDK into `.tools/` if it's missing and is a no-op afterwards, so
there's no separate setup step:

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1   # or, in Bash:
#   source .claude/skills/skg-if-build-toolchain/activate.sh
```

Then build through the committed wrapper - `.\mvnw.cmd` (PowerShell) or `./mvnw` (Bash),
never a bare `mvn`. Neither the skill nor any command hardcodes a version: the JDK's comes
from `pom.xml`'s `<maven.compiler.release>` and Maven's from
`.mvn/wrapper/maven-wrapper.properties`. `ToolchainVersionConsistencyTest` fails the build if
a version literal creeps back into a skill.

## No system Python or jq either

There is no `python`/`python3`/`jq` on PATH on this machine - a bare `python3 -c ...` or
`which jq` will fail (exit 126 / "no jq") rather than falling back to something usable. For a
one-off script or interpreter, use the `portable-python` skill. For filtering/reshaping JSON
(e.g. inspecting a field across many fixtures under `src/test/resources`), use the `jq-json`
skill instead of reaching for `python3`/`jq` directly - it sets up a portable `jq` the same way
`skg-if-build-toolchain` sets up Maven/JDK.

## "notest"/"skiptest" commit messages skip CI tests/validation

If a commit message contains `notest`, `notests`, `skiptest`, or `skiptests` (case-insensitive,
anywhere in the message), the GitHub Actions workflow defined in
[.github/workflows/maven-build.yml](.github/workflows/maven-build.yml) skips verification for
that commit: the `build` job runs `./mvnw -B package -DskipTests` instead of `./mvnw -B package`,
and the `validate-live-api` job (the live DataCite/Crossref contract-test script,
`scripts/ci/validate-live-endpoints.sh`) doesn't run at all. This is intentional - don't treat a
skipped/green-without-tests pipeline as broken when the triggering commit message uses one of
these keywords.

Two caveats on this mechanism:

- It only works on `push`/tag events. The `build` job reads the message with
  `git log -1 --pretty=%B`, and on a `pull_request` event `actions/checkout` checks out the
  *merge* commit, so that command returns `"Merge <sha> into <sha>"` - never the author's
  message. A `notest` keyword in a PR's commits therefore has no effect.
- It is no longer the only reason `validate-live-api` gets skipped - see the section below.

## Dependency updates arrive as monthly bot PRs

[.github/dependabot.yml](.github/dependabot.yml) opens grouped update PRs on the first Monday of
each month for three ecosystems: Maven, GitHub Actions and the Docker base image. Things worth
knowing before "fixing" something that looks wrong:

- **Version-less `io.quarkus:*` dependencies never get their own PR, and that's correct.** They
  are managed by the imported `quarkus-bom`, so there is no version in this repo to bump - they
  move when `${quarkus.platform.version}` moves. The corollary is that a CVE in a BOM-managed
  transitive cannot be auto-fixed by Dependabot; the monthly `quarkus-platform` PR is what ships
  that fix, which is why the cadence is monthly rather than quarterly.
- **`validate-live-api` is deliberately skipped on `dependabot/github_actions/*` and
  `dependabot/docker/*` branches**, because those cannot change mapper output and the job makes
  real outbound calls to DataCite and Crossref. It still runs on `dependabot/maven/*`, where a
  Quarkus bump genuinely could alter the JSON-LD shape. A skipped job on a bot PR is not breakage.
- **`Dockerfile.jvm` spells its JDK major as a literal (`openjdk-25`) on purpose.** Dependabot
  does no `ARG` interpolation, so a `${...}` anywhere in that `FROM` line makes it skip the image
  silently - leaving the published container's CVE patch level unmonitored while the config still
  looks fine. `ToolchainVersionConsistencyTest` is what keeps the literal in step with
  `pom.xml`'s `<maven.compiler.release>`.
- **A new composite action under `.github/actions/` must be added to the `directories` list** in
  the `github-actions` block of `dependabot.yml`. Dependabot only scans `.github/workflows` plus
  an `action.yml` in the repo *root*; it does not walk nested composite actions. Miss this and
  that action's `uses:` refs go unmonitored forever, with no error and no test to catch it.
- The Maven wrapper has no Dependabot ecosystem, so
  [.github/workflows/maven-wrapper-update.yml](.github/workflows/maven-wrapper-update.yml) covers
  it separately on a monthly schedule.

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

## Log tool-run issues that reveal skill gaps

Whenever a tool run in this repo fails, behaves confusingly, or wastes turns in a way that traces
back to a `.claude/skills/*/SKILL.md` being incomplete, wrong, or ambiguous, append an entry to
[`.claude/skills/ISSUES.md`](.claude/skills/ISSUES.md) in the same session - automatically,
without waiting to be asked - following the template and process documented at the top of that
file. Do this even when you also patch the `SKILL.md` directly in the same turn: log it as
`Fixed` rather than skipping the log entry just because it's already resolved.

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

## Static-analysis rulesets have a written policy

Before adding, removing or retuning a rule in [checkstyle.xml](checkstyle.xml),
[pmd-ruleset.xml](pmd-ruleset.xml), [pmd-ruleset-tests.xml](pmd-ruleset-tests.xml) or spotless'
config in [pom.xml](pom.xml), read
[STATIC_ANALYSIS_POLICY.md](STATIC_ANALYSIS_POLICY.md). It records which tool owns what (formatting
is spotless', never checkstyle's - they *will* disagree and no config reconciles it), the
no-duplicating-an-active-PMD-rule rule, and a register of all 107 checkstyle checks already
evaluated with their measured violation counts - so an already-rejected rule isn't re-litigated.
Add a register row for anything you evaluate, adopted or not.

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
- **Every Javadoc block must open with a prose summary sentence ending in a period**, before any
  `@param`/`@return`/`@throws` tag - checkstyle's `SummaryJavadoc` fails the build on a tag-only
  block. Describe what the member does or the non-obvious behaviour it carries (a fallback, a
  degradation, a routing decision); don't restate the signature. Openings of the form
  `@return the ...`, `This method returns ...` and `A {@code Foo} is a ...` are rejected outright
  by the check's own default `forbiddenSummaryFragments`. `ManifestationDateSetters#addDateItem`
  and `XmlParsingUtils#parseDocument` show the intended flavour.

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

## Nullness: `@Nullable` and `Objects.requireNonNull`

NullAway runs at `ERROR` over the **whole** `org.skgif.doi` tree, both source roots, and currently
reports zero findings - so any nullness slip in new code **fails `mvn compile`**, not just a later
check goal. Scoping is one pom flag (`AnnotatedPackages=org.skgif.doi`), not `@NullMarked`: there is
no `@NullMarked` anywhere in `src/` and none should be added. Only `org.skgif.doi.generated` is out
of scope. Full rationale and the measured rollout are in
[STATIC_ANALYSIS_POLICY.md](STATIC_ANALYSIS_POLICY.md)'s NullAway section.

**The annotation.** `org.jspecify.annotations.Nullable`, never `javax.annotation.Nullable` (not on
the classpath at all here) or a vendor one. It's `TYPE_USE`, so it goes immediately before the type:

```java
private final @Nullable String mailto;
Optional<String> currency(@Nullable CrossrefProject project, @Nullable CrossrefFunding funding)
static @Nullable String displayName(@Nullable String given, @Nullable String family)
```

Two placement traps: on a declaration with **no preceding modifier** - an interface method - a
leading `@Nullable` trips checkstyle's `AnnotationLocation` and must go on its own line; with any
modifier present (`private static @Nullable String ...`) inline is correct. And don't annotate inner
type arguments (`List<@Nullable String>`) - `JSpecifyMode` is deliberately off, so the tool ignores
it and it reads as a promise the build doesn't keep.

**When a finding appears, pick by cause - don't reach for the annotation reflexively:**

- **Genuinely null-tolerant** (the body opens with `if (x == null) return ...`, or the javadoc
  already hedges "or null if none is known") → annotate `@Nullable`, and update the `@param`/`@return`
  text in the same edit. This is the common case and changes no behaviour.
- **Safe but unprovable** → restructure so the guard is visible rather than annotating around it.
  Nullness doesn't flow through a boolean local, so hoist the accessor:
  `String id = x.foo(); ... id != null && ... ? strip(id) : null`. And prefer
  `.map(Foo::amount).map(Double::intValue)` over `.filter(a -> a.amount() != null).map(a -> a.amount().intValue())`
  - `Optional.map` already drops a null result, so it's the same behaviour and provable.
- **Invariant established in another method** → `Objects.requireNonNull(...)` with a comment naming
  the guard that makes it safe (see `CrossrefBiblioMapper#venue`, guarded by `hasNoContainerTitle`).
  Prefer a self-contained local null-check in the helper where that's reasonable - it removes the
  unstated dependency on the caller instead of documenting it.
- **Genuinely missing guard** → add the guard. Rare, but the reason the gate is worth having: the
  DataCite resources' `data.attributes()` was one, and now returns 404 rather than dereferencing.

**Never `@SuppressWarnings("NullAway")`.** There are zero in the tree; a suppression records "we
looked" and nothing about the contract.

**`Objects.requireNonNull` conventions.** Always with a message that names what's missing
(`"Fixture not found on classpath: " + resourceName`), so a failure identifies itself. In tests it's
also the right way to state an assumption an assertion depends on -
`Objects.requireNonNull(work.contributors()).getFirst()` - rather than relying on an implicit NPE.

**To measure rather than guess** (e.g. after a wide refactor), run discovery at `WARN` - never at
`ERROR`, where javac aborts in `default-compile` and every test-source finding silently disappears:

```bash
./mvnw -B compile test-compile -Dnullaway.severity=WARN -Dmaven.compiler.failOnWarning=false
```

Confirm the run actually reached `test-compile` before trusting its count, and note that
`-Dmaven.compiler.compilerArgs=`/`-Dmaven.compiler.failOnWarning=` overrides of pom-declared plugin
config are silently ignored - the `nullaway.severity`, `maven.compiler.failOnWarning` and
`nullaway.unannotated` properties in `pom.xml` exist precisely so these overrides work.

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
