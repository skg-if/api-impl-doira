---
name: skg-if-openrewrite-refactor
description: Move a Java class to a new package (bringing its paired test class along), or rename a method/field, and rewrite every reference (imports, call sites) via OpenRewrite instead of hand-editing each site - but only when there are enough referencing files that OpenRewrite's flat cost actually beats hand-editing. Use when asked to move/relocate a class or rename a method/field in this repo.
---

# OpenRewrite refactor toolbox (move class / rename method / rename field)

Hand-editing every reference to a moved/renamed symbol costs one
grep+read+edit cycle *per referencing file* - it scales with usage count.
[OpenRewrite](https://docs.openrewrite.org/) collapses that into one `mvn`
invocation whose cost is roughly flat regardless of how many files it
touches. That's a real win **only** when there are enough referencing files
that the flat cost beats the read+edit cycles it replaces - below that,
OpenRewrite's own setup (recipe YAML + a full Maven+JVM invocation) costs
more than the few edits it would replace. So every invocation of this skill
must decide which path is cheaper - never assume OpenRewrite is automatically
"the fast one."

Scope is deliberately narrow: **move a class (with its paired test), rename
a method, or rename a field.** Nothing else (no PMD/static-analysis
auto-fix, no dead-code cleanup, no namespace sweeps) - those were
considered and explicitly dropped, PMD auto-fix in particular because a
mechanical-looking fix like "remove this unused field" can silently break
behavior if the field is actually read via reflection/Jackson/JPA.

This skill is bound to this repo's own conventions (portable toolchain
paths, checkstyle/spotless rules, `org.skgif.doi.*` package layout, the
`SKG_IF_DOI_MAPPING*.md` docs) - hence the `skg-if-` prefix, matching
`skg-if-format`/`skg-if-build-toolchain`/etc. It is not a portable, drop-into-
any-project skill.

## Shared machinery

**Portable toolchain.** No system Maven/JDK on this machine - dot-source the
`skg-if-build-toolchain` skill's activation script before any build command. It
provisions the JDK if needed and is a no-op afterwards, so it is safe to run every
time; Maven itself comes from the committed `./mvnw` wrapper. Snippets here are
PowerShell; for the two substitutions that turn them into the Bash equivalents, see
[Reading the examples in Bash](../skg-if-build-toolchain/SKILL.md#reading-the-examples-in-bash):

```powershell
. .\.claude\skills\skg-if-build-toolchain\activate.ps1
```

**Ad hoc invocation, never a permanent dependency.** Invoke
`rewrite-maven-plugin` by fully-qualified goal - do **not** add it to
`pom.xml`, since this is an occasional tool, not a routine build step:

```powershell
.\mvnw.cmd -q -B org.openrewrite.maven:rewrite-maven-plugin:6.12.0:run `
  "-Drewrite.configLocation=<scratchpad>\rewrite.yml" `
  "-Drewrite.activeRecipes=com.skgif.Refactor"
```

`6.12.0` was the latest version confirmed resolvable on Maven Central at the
time this skill was authored - OpenRewrite ships frequent releases, so
before relying on this pin, check
<https://search.maven.org/artifact/org.openrewrite.maven/rewrite-maven-plugin>
(or run the command once and see whether Maven complains about a missing
version) and bump the pin here if a newer one resolves. Never use
`RELEASE`/`LATEST` as the version - pin whatever you confirm, the same way
`skg-if-build-toolchain` pins exact JDK/Maven versions.

`ChangeType` and `ChangeMethodName` ship in `rewrite-java`, a transitive
dependency of `rewrite-maven-plugin` itself, so no extra
`-Drewrite.recipeArtifactCoordinates` is needed for either - both were
exercised for real against `rewrite-maven-plugin:6.12.0` while authoring
this skill (a class move and a method rename, including an overloaded
method - `ChangeMethodName`'s `methodPattern` correctly targeted only the
matching signature and left the other overload's call sites untouched) and
worked exactly as documented. The field-rename recipe,
`org.openrewrite.java.ChangeFieldName` (also in `rewrite-java`), was **not**
exercised the same way - its options are believed to be `classType` /
`hasName` / `toName` based on reading its source, not a live run. Confirm
the exact YAML property names once against the pinned version's actual
behavior (a dry run, or `mvn
org.openrewrite.maven:rewrite-maven-plugin:6.12.0:discover` if that goal is
available) before trusting them blindly the first time this skill actually
does a field rename.

**Format immediately after the recipe run - not optional, and before the
compile check, not after it fails.** This repo's `spotless-check` is
actively bound to the `process-sources` phase, so a plain
`.\mvnw.cmd compile` already fails on formatting drift before javac even runs.
OpenRewrite's recipes are confirmed (by real runs while authoring this
skill) to introduce exactly the kind of drift that trips this:
- **Redundant/unused imports.** Moving a class and its paired test into the
  *same* new package in one recipe run left the test file with a leftover
  same-package self-import of the class it no longer needs to import -
  `spotless-check` flags it, `spotless:apply` removes it.
- **Line-length violations.** A rename to a longer identifier (e.g.
  `IdentifierScheme` -> `IdentifierSchemeType`) can push an unrelated call
  site past this repo's 120-character limit even though nothing else about
  that line changed - `spotless:apply` re-wraps it.

Rather than re-deriving the `.\mvnw.cmd spotless:apply` invocation here, **run the
`skg-if-format` skill** right after the recipe (or plain edits) and before
the verify step below - it already documents the correct portable-toolchain
invocation, the `-DspotlessFiles` scoping flag, and the "review the diff
before committing" caveat, so this skill defers to it instead of duplicating
it. Scope it to the touched files when the change is small; run it unscoped
if a batch touched many files across the tree.

**Verify.** `git status --short`, then `.\mvnw.cmd -q -B compile test-compile`
(portable toolchain, per `skg-if-build-toolchain`'s compile-check convention
- cheaper than a full `test` run for "did this break anything"). For a
method/field rename, optionally use `query_graph_tool` (pattern `tests_for`)
to run just the tests covering the renamed symbol. If this still fails after
formatting is clean, it's a real problem - see "Known gotchas" below for the
two confirmed causes (missing `package-info.java`, broken implicit
same-package imports) before assuming the recipe itself is broken.

**Doc cross-reference check, always - generic, not tied to any specific
file, and not limited to Markdown.** OpenRewrite rewrites real code
references (types, method calls, `{@link}` javadoc tags - confirmed by a
real run: renaming `EntityRefs.organisationRef(String,String,String)`
correctly updated a `{@link #organisationRef(String,String,String)}` tag in
a *different* method's javadoc in the same file), but it leaves free-text
prose alone - a `{@code OldName}` mention or a plain-English sentence naming
the old symbol goes stale silently (confirmed: after moving
`CrossrefJournalDoiResolver`, two unrelated files' javadoc comments still
said `{@code CrossrefJournalDoiResolver}` afterward - harmless prose, but a
real example of what this check exists to catch). Grep **both** `**/*.md`
*and* `**/*.java` repo-wide for the exact old identifier (simple class name,
or old method/field name) to catch prose in code comments too, not just
external docs. For each hit, read the surrounding context and judge whether
it's a genuine reference to the renamed symbol (vs. a coincidental text
match) and update it if so. This sweep naturally covers any project-specific
"keep this doc in sync" convention - e.g. this repo's own `CLAUDE.md` rule
about `SKG_IF_DOI_MAPPING*.md` - without hardcoding filenames.

**Known gotchas, confirmed by real verification runs in this repo (not
theoretical):**

- **A class move into a package that doesn't exist yet also needs a new
  `package-info.java`.** This repo's checkstyle config enforces
  `JavadocPackage` - every package has one (confirmed: every existing
  package under `src/main/java` does). `ChangeType` has no notion of this
  project convention - it just moves the file. After a class-move recipe
  run, check whether the destination package is new (no sibling files were
  already there before the move) and if so, write a `package-info.java`
  matching the style of a sibling package's (one-sentence Javadoc + the
  `package` statement) before running the verify step.
- **Moving a class out of its package can silently break *implicit*
  same-package references, in both directions.** Java doesn't require an
  import for same-package access. If the moved class used another
  same-original-package class without an import, or if some *other* file
  still in the original package used the moved class without an import,
  both break the moment the move happens - `ChangeType` only rewrites
  references *to* the type it was told to move, not a file's *other*,
  unrelated same-package dependencies. `.\mvnw.cmd compile test-compile` (the
  verify step) catches this as `cannot find symbol: class X` - treat that
  specific failure as "add `import <original-package>.X;` to the file
  reporting it," not as evidence the move itself failed. Confirmed on both
  the OpenRewrite path (the moved class needed a new import for a sibling it
  had used implicitly) and the plain-edit path (a moved class needed an
  import added inside itself, *and* an unrelated file back in the original
  package needed a new import added for the moved class it had also been
  using implicitly).

## Known limitations of the recipes themselves - not just format/compile fallout

The gotchas above are cases the recipe *causes* but this skill's own
follow-up steps (format, verify) reliably catch and fix. The ones below are
different: things a recipe can get **silently wrong or leave undone**, where
neither OpenRewrite nor a careful plain edit inherently catches the mistake
- they need a deliberate manual check regardless of which path (OpenRewrite
or plain edits) was chosen.

- **Reflection via a string literal is invisible to both paths, not just
  the plain-edit one.** `Class.forName("org.skgif.doi...OldName")`,
  `getMethod("oldMethodName")`, `getDeclaredField("oldFieldName")` are just
  string constants to OpenRewrite's AST-based model - `ChangeType`/
  `ChangeMethodName`/`ChangeFieldName` cannot rewrite a string that happens
  to spell the old name, and neither can a naive plain-text edit tell a real
  reflective reference apart from an unrelated string that coincidentally
  matches. (This corrects an earlier version of this skill, which listed
  reflective access as something OpenRewrite handles by being escalated to
  it - it doesn't. Escalating to OpenRewrite is still right for *overloads*
  and *method references*, which OpenRewrite's AST does understand; it is
  not a fix for reflection strings.) Before finishing a rename, separately
  grep for the old identifier inside string literals
  (`"oldName"`/`'oldName'`) across the codebase - test mocking setups,
  CDI/dependency-injection lookups, and Jackson/serialization frameworks are
  the most likely sources of this in this repo.
- **A field rename can silently change this API's JSON wire format.**
  Jackson serializes a field by its Java name unless an explicit
  `@JsonProperty` overrides it. This repo's DTOs (`org.skgif.doi.*.dto.*`)
  and generated models are exactly the kind of class where that matters -
  this is a real SKG-IF-compliant REST API, not an internal-only service.
  Before renaming any field, check whether it already carries an explicit
  `@JsonProperty` annotation: if yes, the JSON contract is unaffected by a
  Java-side rename; if no, renaming the field changes the serialized/
  deserialized JSON key too, which is an API-compatibility change, not a
  "free" internal refactor - treat it accordingly (explicit sign-off, not
  silent token-saving automation) rather than running this skill on it
  as if it were purely internal.
- **Never target generated code.** Anything under `target/generated-sources`
  or in `org.skgif.doi.generated.*` is produced by
  `openapi-generator-maven-plugin` from the OpenAPI spec on every build - a
  rename applied there is silently reverted the next time the project
  builds. If a rename is really needed for a generated model, the change
  belongs in the OpenAPI spec source, followed by regenerating, not in this
  skill's scope at all.
- **Record components need extra caution, unverified here.** A Java `record`
  component is simultaneously a field and an accessor method of the same
  name (`record Foo(String bar)` generates `bar()`) - this repo uses records
  for several DTOs (e.g. `CrossrefUpdateTo`). `ChangeFieldName`'s behavior on
  a record component was not exercised while authoring this skill (the
  field-rename verification used a plain class field, not a record). Treat
  a record-component rename as unverified: after running it, explicitly
  check that accessor call sites (`foo.oldName()`) were also updated to
  `foo.newName()`, not just the declaration - don't assume parity with a
  regular field just because both are called "field rename."
- **A rename to a longer identifier can push a line past the 120-character
  limit even when nothing else about the line changed.** Confirmed on a
  same-package enum rename (no move, no new package, just a longer name) -
  several call sites needed re-wrapping that only `spotless:apply` (via the
  `skg-if-format` skill, see above) provides, not the recipe itself.

## Move a class / rename a method / rename a field

1. **Resolve identity.** Class move: old FQN (from the file's `package`
   line) + new FQN. Method rename: declaring class FQN + signature (name +
   param types) + new name. Field rename: declaring class FQN + field name +
   new name.

2. **Class moves only - find the paired test class(es).** By this repo's
   convention (`src/test/java/...` mirrors `src/main/java/...`), grep for
   `<SimpleClassName>Test.java`, `<SimpleClassName>Tests.java`,
   `<SimpleClassName>IT.java` under the mirrored test path. Zero matches is
   fine; more than one is normal (e.g. a `Test` and an `IT`).

3. **Decide OpenRewrite vs. plain edits.** Grep `**/*.java` (excluding the
   symbol's own file and, for class moves, its paired test file(s)) with
   `output_mode: "files_with_matches"` so the check itself stays cheap. Some
   patterns are never safe to hand-edit with a text match and **always
   escalate to OpenRewrite** regardless of file count, because a blind edit
   can silently miss or mis-target a usage - correctness risk that outweighs
   any tokens saved. (Reflective string access is a *different* case - see
   "Known limitations" above - escalating to OpenRewrite does not fix it;
   check for it separately regardless of which path is chosen here.)
   - *Class move*: a wildcard import of the old package anywhere, the class
     used via bare fully-qualified name inline (cast/`instanceof`/etc.
     without an import), or static imports of its members.
   - *Method rename*: the method is overloaded (same name, different
     signatures), or used via a method reference (`Foo::methodName`) -
     OpenRewrite's AST understands both correctly (confirmed: an overloaded
     rename left the other overload's call sites untouched).
   - *Field rename*: any other class anywhere declares a same-named field -
     plain grep has no notion of which class owns which field.

   Otherwise, count distinct referencing files:
   - **3 or fewer** - skip OpenRewrite, edit directly (class move: `git mv`
     + edit `package` lines + edit each import line; rename: edit the
     declaration + each call site). Below this size, OpenRewrite's fixed
     setup cost exceeds the handful of Read+Edit calls it would replace.
   - **More than 3** - use OpenRewrite (steps 4-6 below). This is where the
     savings are real: rewriting N call sites collapses into one
     bounded-size `mvn` invocation instead of N read+edit cycles.

   This threshold (3) is a starting heuristic based on rough per-file
   Read+Edit cost vs. OpenRewrite's fixed setup cost, not a measured number -
   adjust it if it's clearly off after using this skill a few times.

   *(If this step chose plain edits, skip to the format/verify steps after
   making them - the doc cross-reference check above still applies either
   way.)*

4. **Batch every independent rename into one recipe run.** If the request
   involves more than one class/method/field (e.g. "rename these 4 enums"),
   run steps 1-3 for *each* symbol first to decide its own path, then group
   the results:
   - Every symbol routed to OpenRewrite goes into **one shared recipe YAML**
     (one `recipeList` entry per symbol) and **one `mvn` invocation** - not
     one invocation per symbol. The fixed setup cost is paid once for the
     whole batch instead of once per symbol - measured on this repo's own
     `org.skgif.doi.spec` enums (4 type renames, 37 reference sites across
     22 distinct files), batching cuts the OpenRewrite-side cost to roughly
     1,000-2,000 tokens total vs. an estimated 35,000-40,000 tokens for
     hand-editing every reference, and vs. running this skill 4 times
     unbatched (4x the fixed per-invocation overhead for no benefit, since
     the recipes don't interact).
   - Every symbol routed to plain edits still gets edited directly,
     independent of the batch - there's no fixed cost to amortize there.
   - This only holds because the recipes are independent - if two symbols in
     the same batch could interact (e.g. renaming a class *and* a method
     that only exists after that class is renamed), don't batch those two;
     run them as separate sequential invocations instead.

5. **Recipe YAML**, one entry per change (per symbol in the batch, if
   batching), written to the session scratchpad, never committed:
   ```yaml
   type: specs.openrewrite.org/v1beta/recipe
   name: com.skgif.Refactor
   recipeList:
     - org.openrewrite.java.ChangeType:
         oldFullyQualifiedTypeName: org.skgif.doi.rest.OldName
         newFullyQualifiedTypeName: org.skgif.doi.newpkg.NewName
     # or, for a method rename:
     - org.openrewrite.java.ChangeMethodName:
         methodPattern: org.skgif.doi.rest.Foo oldMethod(String, int)
         newMethodName: newMethod
     # or, for a field rename (confirm option names per the note above):
     - org.openrewrite.java.ChangeFieldName:
         classType: org.skgif.doi.rest.Foo
         hasName: oldField
         toName: newField
     # - additional entries here for every other symbol in the same batch
   ```

6. Run via the shared invocation, then run the `skg-if-format` skill, then
   the shared verify / doc-check steps above, in that order.
