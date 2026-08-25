# Static analysis policy

> **Note on the numbers below:** every violation count in this file (in the exclusions above and in
> the register) was measured as of 2026-08-24, against the codebase and ruleset versions at that
> time. They are not re-verified automatically on each change to `checkstyle.xml`,
> `pmd-ruleset.xml`, `pmd-ruleset-tests.xml`, or the code they scan - a count can drift as the
> codebase grows or a rule's config changes. Treat a stale count as a reason to re-measure, not as
> proof the rule is still (in)active at that level.

Why this file exists: the ruleset files (`checkstyle.xml`, `pmd-ruleset.xml`,
`pmd-ruleset-tests.xml`, and spotless' config in `pom.xml`) each explain *individual* decisions in
comments, but nothing recorded the rules those decisions follow, or which of the hundreds of
available checks had already been evaluated and turned down. That made every ruleset change start
from scratch. This file is the durable half: the policy, plus a register of every check considered
and why.

Read this before adding, removing or retuning a rule in any of those files.

## Division of labour

Six tools, and mixing up their jobs is the single most expensive mistake available here.

| Tool | Owns | Enforced? |
|---|---|---|
| **Spotless** (Eclipse formatter + `intellij-style.xml`) | formatting: indentation, wrapping, whitespace, blank lines | yes - `check` fails the build |
| **Checkstyle** | semantics and style a formatter cannot express: naming, Javadoc, imports, language-idiom rules | yes - both source roots |
| **PMD** | correctness, design, complexity, error-prone patterns | yes - both source roots |
| **SpotBugs** | bytecode/dataflow correctness Checkstyle's style rules and PMD's AST-level rules can't reach: null dataflow, resource leaks, concurrency hazards | yes - `check` fails the build |
| **Error Prone** | compile-time, type-aware bug patterns caught via `javac`'s own type information - a different reach than PMD's pure-AST rules or SpotBugs's post-compile bytecode analysis | yes - `failOnWarning=true` fails the compile on any diagnostic, `WARNING`-severity default checks included, not just its `ERROR`-severity ones |
| **NullAway** | nullness specifically: that a `@Nullable` value never reaches a `@NonNull` field, parameter or return. Rides on Error Prone's `javac` plugin but is opt-in per package, so unlike the five above it does **not** cover the whole tree | yes, but only inside `@NullMarked` packages - `-Xep:NullAway:ERROR` fails the compile there, and everything else is unchecked by design |
| **Sonar** | nothing - no Sonar server runs here | **no** |

Sonar is used only as a *naming scheme*: `SXXXX` ids give each concern a stable, documented
external name, so one register row can carry the Checkstyle and PMD implementations of the same
idea. Several existing rules cite a Sonar id as their justification.

## Guiding principles

Each is the distilled form of something this repo already learned the hard way.

### 1. Formatting belongs to Spotless, never Checkstyle

Both are blocking gates, and they will disagree. Concretely: Checkstyle's `Indentation` treats a
wrapped `switch` arrow body as a *lambda child* (case label + `lineWrappingIndentation`, i.e. +8)
while the Eclipse formatter treats it as a *case-body statement* (+4). No formatter property
reconciles this - `intellij-style.xml` has ~24 switch/lambda keys and none controls arrow-body wrap
indentation. The fix was restructuring the code so nothing wrapped.

So: do not add whitespace/wrapping checks to Checkstyle, even when they measure 0 today. A
0-violation formatting check only means it agrees with the formatter's *current* output; the next
Spotless or profile bump can flip it into a build-blocking conflict with no config-level escape.

### 2. Never duplicate an active PMD rule

Already the repo's decision: `pmd-ruleset.xml`'s description excludes PMD's `codestyle.xml` and
`documentation.xml` because they "overlap with what checkstyle already enforces". PMD runs ~231
active rules on main, so the overlap surface is large - check every candidate against it. Where a
rule is rejected as a duplicate, the register names the covering PMD rule.

The inverse also applies: when a built-in rule grows to cover a hand-written one, delete the
hand-written one. `IgnoredMethodResult` (a custom XPath rule) was removed once
`errorprone/UselessPureMethodCall` was shown to catch all 12 of its allow-listed methods - and,
being type-resolved, to catch them without the name-collision false positives that forced the
custom rule to stay narrow.

### 3. Respect the documented exclusions

`checkstyle.xml` disables four Sun-baseline checks with a measured count and a rationale:
`DesignForExtension`, `VisibilityModifier` (201 violations), `FinalParameters` (330), and
`IllegalCatch` (5, all deliberate degrade-gracefully sites). These are decisions, not oversights.
Reversing one is legitimate but must be deliberate and explained - not a side effect of a sweep.

A corollary: keep related decisions consistent. `FinalLocalVariable` is rejected in the register
purely because accepting it while `FinalParameters` stays rejected would be incoherent - it is the
same argument about the same kind of churn.

### 4. Record the measured count for every decision

Both adopt and reject. A rule turned down with "produced 330 violations" can be re-evaluated later;
one turned down with "too noisy" cannot. This mirrors the convention already used in
`checkstyle.xml`, where disabled checks are commented out with their real numbers rather than
deleted.

Corollary for adoption: a check measuring 0 is *not* self-evidently worth adding - it must still
pass principles 1-3. What a 0-violation check buys is a regression guard, which is worth having
when the rule guards a style the codebase deliberately follows (`SealedShouldHavePermitsList` has
nothing to check today, but will the moment a sealed type appears).

## Decision register

Every check evaluated in the Checkstyle 14 pass, with the count it reported at the time. **55
adopted, 52 rejected, none without a recorded reason.** Counts are from a single probe run against
both source roots; `adopted - N fixed` means the code was changed to satisfy the rule.

A future engine bump only needs to evaluate checks *absent from this table* - get that list with
the `comm` diff under "Reproducing the measurement" below.

| Checkstyle check | Status | Basis | main | test |
|---|---|---|---:|---:|
| `AbbreviationAsWordInName` | **adopted** | adopted - was 0 | 0 | 0 |
| `AbstractClassName` | rejected | style: test base classes are named *TestBase by convention, not Abstract* | 0 | 2 |
| `AnnotatedDeclarationVisibility` | rejected | other-guide: encodes a convention this ruleset does not follow | 0 | 0 |
| `AnnotationOnSameLine` | rejected | formatting: Spotless owns formatting (principle 1) | 163 | 330 |
| `AnnotationUseStyle` | **adopted** | adopted - was 0 | 0 | 0 |
| `AnonInnerLength` | **adopted** | adopted - was 0 | 0 | 0 |
| `ArrayBracketNoWhitespace` | rejected | formatting: Spotless owns formatting (principle 1) | 2 | 0 |
| `ArrayTrailingComma` | rejected | conflict: direct inverse of the adopted NoArrayTrailingComma | 0 | 0 |
| `AtclauseOrder` | **adopted** | adopted - was 0 | 0 | 0 |
| `AvoidDoubleBraceInitialization` | rejected | duplicate: PMD bestpractices/DoubleBraceInitialization | 0 | 0 |
| `AvoidEscapedUnicodeCharacters` | **adopted** | adopted - was 0 | 0 | 0 |
| `AvoidInlineConditionals` | rejected | style: ternaries are idiomatic in the mappers; PMD NoNestedTernaryOperators covers the bad case | 79 | 1 |
| `AvoidNoArgumentSuperConstructorCall` | **adopted** | adopted - was 0 | 0 | 0 |
| `AvoidStaticImport` | rejected | test-hostile: 0 in main but 87 in tests where AssertJ static imports are correct | 0 | 87 |
| `BooleanExpressionComplexity` | rejected | duplicate: folded into PMD CyclomaticComplexity/CognitiveComplexity | 2 | 0 |
| `CatchParameterName` | **adopted** | adopted - was 0 | 0 | 0 |
| `ClassDataAbstractionCoupling` | rejected | duplicate: PMD design/CouplingBetweenObjects (partial) | 1 | 1 |
| `ClassFanOutComplexity` | rejected | duplicate: PMD design/CouplingBetweenObjects | 4 | 0 |
| `ClassMemberImpliedModifier` | rejected | conflict: demands the redundant modifiers RedundantModifier removes | 19 | 0 |
| `CommentsIndentation` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `ConstructorsDeclarationGrouping` | **adopted** | adopted - was 0 | 0 | 0 |
| `CyclomaticComplexity` | rejected | duplicate: PMD design/CyclomaticComplexity | 0 | 0 |
| `DeclarationOrder` | rejected | style: would reorder fields by visibility, breaking logical grouping | 26 | 0 |
| `DefaultComesLast` | rejected | duplicate: PMD bestpractices/DefaultLabelNotLastInSwitch | 0 | 0 |
| `EmptyCatchBlock` | rejected | duplicate: PMD errorprone/EmptyCatchBlock | 0 | 0 |
| `EmptyForInitializerPad` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `EmptyLineSeparator` | rejected | formatting: Spotless owns formatting (principle 1) | 52 | 25 |
| `ExecutableStatementCount` | rejected | duplicate: PMD design/NcssCount | 0 | 0 |
| `ExplicitInitialization` | rejected | duplicate: PMD performance/RedundantFieldInitializer | 0 | 0 |
| `FinalLocalVariable` | rejected | churn: same argument as the already-rejected FinalParameters; both-or-neither | 273 | 333 |
| `HexLiteralCase` | **adopted** | adopted - was 0 | 0 | 0 |
| `IllegalBlockTag` | **adopted** | adopted - was 0 | 0 | 0 |
| `IllegalIdentifierName` | **adopted** | adopted - was 0 | 0 | 0 |
| `IllegalThrows` | rejected | duplicate: PMD design/AvoidUncheckedExceptionsInSignatures | 0 | 0 |
| `IllegalType` | rejected | duplicate: PMD bestpractices/LooseCoupling | 0 | 0 |
| `ImportOrder` | **adopted** | adopted - 105 fixed | 22 | 83 |
| `InnerTypeLast` | rejected | style: nested DTO records read better declared next to their parent than after methods | 12 | 0 |
| `InterfaceMemberImpliedModifier` | rejected | conflict: demands the redundant modifiers RedundantModifier removes | 20 | 0 |
| `JavaNCSS` | rejected | duplicate: PMD design/NcssCount | 0 | 0 |
| `JavadocLeadingAsteriskAlign` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `JavadocMissingWhitespaceAfterAsterisk` | **adopted** | adopted - was 0 | 0 | 0 |
| `JavadocParagraph` | **adopted** | adopted - was 0 | 0 | 0 |
| `JavadocTagContinuationIndentation` | **adopted** | adopted - was 0 | 0 | 0 |
| `LambdaBodyLength` | rejected | duplicate: close enough to PMD NcssCount; 1 site, not worth a second rule | 1 | 0 |
| `LambdaParameterName` | **adopted** | adopted - was 0 | 0 | 0 |
| `MethodCount` | rejected | duplicate: PMD design/TooManyMethods | 0 | 0 |
| `MissingCtor` | rejected | style: utility classes already covered by HideUtilityClassConstructor | 5 | 38 |
| `MissingDeprecated` | **adopted** | adopted - was 0 | 0 | 0 |
| `MissingJavadocPackage` | **adopted** | adopted - was 0 | 0 | 0 |
| `MissingJavadocType` | **adopted** | adopted - 33 fixed | 33 | 0 |
| `MissingNullCaseInSwitch` | **adopted** | adopted - was 0 | 0 | 0 |
| `MissingOverride` | rejected | duplicate: PMD bestpractices/MissingOverride | 0 | 0 |
| `MissingOverrideOnRecordAccessor` | **adopted** | adopted - was 0 | 0 | 0 |
| `ModifiedControlVariable` | rejected | duplicate: PMD bestpractices/AvoidReassigningLoopVariables | 0 | 0 |
| `MultilineCommentLeadingAsteriskPresence` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `MultipleStringLiterals` | rejected | duplicate: PMD errorprone/AvoidDuplicateLiterals (maxDuplicateLiterals=3) | 29 | 175 |
| `MutableException` | **adopted** | adopted - was 0 | 0 | 0 |
| `NPathComplexity` | rejected | duplicate: PMD design/NPathComplexity | 0 | 0 |
| `NestedForDepth` | **adopted** | adopted - was 0 | 0 | 0 |
| `NestedIfDepth` | rejected | duplicate: PMD design/AvoidDeeplyNestedIfStmts | 0 | 0 |
| `NestedTryDepth` | **adopted** | adopted - was 0 | 0 | 0 |
| `NoArrayTrailingComma` | **adopted** | adopted - was 0 | 0 | 0 |
| `NoClone` | **adopted** | adopted - was 0 | 0 | 0 |
| `NoCodeInFile` | **adopted** | adopted - was 0 | 0 | 0 |
| `NoEnumTrailingComma` | **adopted** | adopted - was 0 | 0 | 0 |
| `NoFinalizer` | **adopted** | adopted - was 0 | 0 | 0 |
| `NoLineWrap` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `NoWhitespaceBeforeCaseDefaultColon` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `OneTopLevelClass` | **adopted** | adopted - was 0 | 0 | 0 |
| `OuterTypeFilename` | **adopted** | adopted - was 0 | 0 | 0 |
| `OuterTypeNumber` | **adopted** | adopted - was 0 | 0 | 0 |
| `OverloadMethodsDeclarationOrder` | **adopted** | adopted - was 0 | 0 | 0 |
| `PackageAnnotation` | **adopted** | adopted - was 0 | 0 | 0 |
| `PackageDeclaration` | **adopted** | adopted - was 0 | 0 | 0 |
| `ParameterAssignment` | rejected | duplicate: PMD bestpractices/AvoidReassigningParameters | 0 | 0 |
| `PatternVariableAssignment` | **adopted** | adopted - was 0 | 0 | 0 |
| `PatternVariableName` | **adopted** | adopted - was 0 | 0 | 0 |
| `PreferLiteralJavadocInlineTag` | **adopted** | adopted - 6 fixed | 6 | 0 |
| `RecordComponentName` | **adopted** | adopted - was 0 | 0 | 0 |
| `RecordComponentNumber` | rejected | n/a: DTO width is imposed by the provider JSON, not a design choice | 3 | 0 |
| `RecordTypeParameterName` | **adopted** | adopted - was 0 | 0 | 0 |
| `RequireEmptyLineBeforeBlockTagGroup` | **adopted** | adopted - was 0 | 0 | 0 |
| `RequireThis` | rejected | churn: `this.` on every field access, same low value as FinalParameters | 0 | 0 |
| `ReturnCount` | rejected | style: guard-clause early returns are deliberate throughout | 27 | 0 |
| `SealedShouldHavePermitsList` | **adopted** | adopted - was 0 | 0 | 0 |
| `SeparatorWrap` | rejected | formatting: Spotless owns formatting (principle 1) | 344 | 420 |
| `SingleSpaceSeparator` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |
| `SuperClone` | rejected | duplicate: PMD errorprone/ProperCloneImplementation | 0 | 0 |
| `SuperFinalize` | rejected | duplicate: PMD errorprone/FinalizeDoesNotCallSuperFinalize | 0 | 0 |
| `TextBlockGoogleStyleFormatting` | rejected | other-guide: Google style; also formatting (principle 1) | 0 | 0 |
| `ThrowsCount` | **adopted** | adopted - was 0 | 0 | 0 |
| `TrailingComment` | rejected | formatting: Spotless owns formatting (principle 1) | 6 | 28 |
| `TypeBodyPadding` | rejected | formatting: Spotless owns formatting (principle 1) | 75 | 42 |
| `UncommentedMain` | rejected | n/a: no main method exists | 0 | 0 |
| `UnnecessaryNullCheckWithInstanceOf` | **adopted** | adopted - was 0 | 0 | 0 |
| `UnnecessarySemicolonAfterOuterTypeDeclaration` | **adopted** | adopted - was 0 | 0 | 0 |
| `UnnecessarySemicolonAfterTypeMemberDeclaration` | **adopted** | adopted - was 0 | 0 | 0 |
| `UnnecessarySemicolonInEnumeration` | **adopted** | adopted - was 0 | 0 | 0 |
| `UnnecessarySemicolonInTryWithResources` | **adopted** | adopted - was 0 | 0 | 0 |
| `UnnecessaryTypeArgumentsWithRecordPattern` | **adopted** | adopted - was 0 | 0 | 0 |
| `UnusedCatchParameterShouldBeUnnamed` | **adopted** | adopted - 7 fixed | 7 | 0 |
| `UnusedLambdaParameterShouldBeUnnamed` | **adopted** | adopted - 17 fixed | 15 | 2 |
| `UnusedTryResourceShouldBeUnnamed` | **adopted** | adopted - was 0 | 0 | 0 |
| `UseEnhancedSwitch` | **adopted** | adopted - was 0 | 0 | 0 |
| `VariableDeclarationUsageDistance` | **adopted** | adopted - 2 fixed | 2 | 0 |
| `WhenShouldBeUsed` | **adopted** | adopted - was 0 | 0 | 0 |
| `WhitespaceBeforeEmptyBody` | rejected | formatting: Spotless owns formatting (principle 1) | 0 | 0 |

## Sonar cross-reference

The catalogue is `org.sonarsource.java:sonar-java-plugin` (**not** `java-checks`, which ships only
the check classes and no metadata). Rule metadata lives at
`org/sonar/l10n/java/rules/java/S*.json` inside that jar - 731 rules, 719 non-deprecated, in
8.40.0.46617.

Ids already cited as the justification for a hand-written rule here, all verified against that
catalogue:

| Id | Title | Cited by |
|---|---|---|
| `S119` | Type parameter names should comply with a naming convention | `checkstyle.xml` ClassTypeParameterName et al |
| `S135` | Loops should not contain more than a single "break" or "continue" | PMD `SeveralBreakOrContinuePerLoop` |
| `S1488` | Local variables should not be declared and then immediately returned | PMD `VariableCanBeInlined` |
| `S1656` | Variables should not be self-assigned | PMD `SelfAssignment` |
| `S2178` | Short-circuit logic should be used in boolean contexts | PMD `NonShortCircuitBooleanOperator` |
| `S2201` | Return values from functions without side effects should not be ignored | PMD `UselessPureMethodCall` (was custom `IgnoredMethodResult`) |
| `S3358` | Ternary operators should not be nested | PMD `NoNestedTernaryOperators` |
| `S3400` | Methods should not return constants | PMD `MethodReturnsConstant` |
| `S3626` | Jump statements should not be redundant | PMD `RedundantTrailingJump` |
| `S3984` | Exceptions should not be created without being thrown | PMD `ExceptionCreatedNotThrown` |
| `S6902` | **not found in the 8.40.0.46617 catalogue** | PMD `PreferSequencedCollectionAccessors` |

That last row is a real discrepancy, not a formatting slip: `S6902` is cited in `pmd-ruleset.xml`
but no such rule file exists in the catalogue checked. The rule it justifies is sound either way
(prefer `getFirst()`/`getLast()`); only the citation needs confirming.

Useful ids for concerns this repo enforces without citing them: `S1176` (public API should be
documented with Javadoc - the `SummaryJavadoc`/`MissingJavadocType`/`MissingJavadocMethod`
cluster), `S1228` (packages should have `package-info.java` - `JavadocPackage`), `S1128`
(unnecessary imports - `UnusedImports`), `S131` (`switch` should have `default` -
`MissingSwitchDefault`).

**Mapping a rule to a Sonar id is semantic, not mechanical.** Enumerating the catalogue is
automatic; deciding that check X *is* `SXXXX` is not. Use title keyword-matching to propose
candidates, then confirm against the rule's own description. Mark an unconfirmed guess `S1234?` and
an uninvestigated concern `—`. Never invent an id - a plausible-looking wrong `SXXXX` is worse than
a blank.

### Backlog

The catalogue is ~719 curated Java rules. Inverting the register - listing Sonar concerns that
*neither* Checkstyle nor PMD covers here - yields a candidate list sourced from an outside opinion
rather than from whatever the two jars happen to ship. Not yet done; it is the obvious next pass.

## Reproducing the measurement

All of it runs from the portable toolchain (`skg-if-build-toolchain` skill); there is no system
JDK/Maven.

**List the checks an engine ships**, and drop the abstract base classes that are not usable
modules (7 of them at the time of writing):

```bash
unzip -l checkstyle-<ver>.jar | grep -oE 'com/puppycrawl/tools/checkstyle/checks/[A-Za-z0-9/]*Check\.class'
javap -cp checkstyle-<ver>.jar <fqcn>   # "public abstract class" => not a module
```

`javap` also settles whether a check belongs under `TreeWalker` or `Checker`: walk the superclass
chain to `api.AbstractCheck` (TreeWalker) or `api.AbstractFileSetCheck` (Checker). Putting one in
the wrong parent is a hard config error, not a violation.

**Diff against a previous engine** to find what a bump actually added - this is what makes the
register pay off, since only rules absent from it need evaluating:

```bash
comm -13 checks-<old>.txt checks-<new>.txt
```

**Measure candidates in bulk.** Write a probe config containing only the candidates and point the
build at it. One run yields every candidate's count, because each violation names its rule in
`target/checkstyle-result.xml` (`source` attribute).

Two traps here, both cost a wasted run:

- `-Dcheckstyle.config.location=...` is **ignored**. The POM sets `configLocation` explicitly, and
  an explicit POM parameter beats a `-D` user property. The same is true of
  `-Dcheckstyle.failOnViolation=false`. Swap `checkstyle.xml` for the probe temporarily instead
  (and restore it in the same command).
- The default execution covers `src/main/java` only. Test sources need
  `checkstyle:check@test-checkstyle-check`, and they *do* differ - `AvoidStaticImport` is 0 on main
  and 87 in tests.

## Gotchas worth not rediscovering

- **`maven-checkstyle-plugin` pins its own engine.** 3.6.0 is current but hardcodes
  `checkstyleVersion` 9.3 (Jan 2022). Without the `checkstyle.version` property in `pom.xml` and
  the matching `<dependencies>` block on the plugin, the build parses Java with a grammar that
  predates records patterns, `case … when` guards and unnamed variables - and those come out as
  *parse errors*, not violations.
- **A green build never proves a rule is enforced.** Two checks here silently do not cover what
  their name suggests: `MissingSwitchDefault` ignores switch *expressions* entirely (verified on
  both 9.3 and 14, so it is not a regression), and `UnnecessaryNullCheckWithInstanceOf` fires on a
  simple variable receiver but not a method-call one. Negative-test any rule you are relying on:
  reintroduce a violation and confirm the build fails with that rule's message.
- **`spotless:apply` can introduce a Checkstyle violation.** It once produced a 121-character line
  against a 120 limit. Always run `spotless:apply` *before* `checkstyle:check`; checking either
  alone proves nothing.
- **Adding Javadoc to a record activates a second check.** `JavadocType` requires an `@param` per
  record component, but only once the type carries any Javadoc at all. Adding 33 one-line type
  Javadocs therefore surfaced 99 missing `@param` tags in the same run.
- **Watch for mutually exclusive rules.** `ArrayTrailingComma` and `NoArrayTrailingComma` are
  direct inverses; `InterfaceMemberImpliedModifier`/`ClassMemberImpliedModifier` demand exactly the
  modifiers the already-enabled `RedundantModifier` removes. Both pairs read as harmless while the
  construct they govern happens to be absent from the codebase.
- **PMD rules referenced by name keep running when deprecated**, emitting only a warning.
  `UnnecessaryLocalBeforeReturn` was deprecated in PMD 7.17.0 and had to be spotted by reading the
  category XML inside `pmd-java-<ver>.jar`; nothing failed.
- **Two `maven-compiler-plugin` settings cannot be overridden from the command line**, and both fail
  *silently* rather than erroring: `-Dmaven.compiler.compilerArgs=...` does not override a
  `<compilerArgs>` list declared in the pom, and an explicit `<failOnWarning>true</failOnWarning>`
  beats the plugin's own `${maven.compiler.failOnWarning}` expression. Anything that needs to be
  dialled down for a one-off probe has to be routed through a `<properties>` entry first - see the
  NullAway section's severity levers.
- **A compile-phase gate at `ERROR` severity hides every later source root.** javac aborts inside
  `default-compile`, so `test-compile` never runs and its findings are invisible while the run still
  looks like a full survey. Any discovery/measurement pass over a compile-phase tool must run at
  `WARN` with warnings non-fatal, and should confirm it actually reached `test-compile`.

## SpotBugs decision register

Added because Checkstyle (style-only, no dataflow) and PMD's rulesets here (source-level, no
interprocedural null-dataflow tracking of JDK APIs) both miss a class of bug neither is designed
to catch - see the investigation that started this: a `getResourceAsStream()` call in
`MedraToSkgIfMapperTest#parseFixture` used without a null guard. **That specific bug is still not
caught** even by SpotBugs at `effort=Max, threshold=Low` (its per-file `bugCount` for that class is
0) - `ClassLoader.getResourceAsStream`'s nullability isn't in SpotBugs's nullness database, so this
gap remains a known, accepted risk shared by ~15 identical fixture-loading test helpers across the
suite (`ProductsGoldenTest`, `GrantsGoldenTest`, `CrossrefToSkgIfMapperTestBase`,
`DataCiteToSkgIfMapperTestBase`, `XmlFixtureResponses`, etc.) rather than something this tool
addresses.

What SpotBugs *is* wired up for: a genuine regression guard against the bug classes its detectors
do reach reliably (see principle 4 above - a 0-violation check is still worth having as a guard).
The first `effort=Max, threshold=Low`, no-filter run against this codebase (2026-08-25) reported
490 findings; every one was triaged into `spotbugs-exclude.xml` as a rejection, none as a fix - a
plausible outcome for a codebase already gated by strict Checkstyle/PMD/Spotless and written in a
record-pattern-heavy Java 21+ style that predates most SpotBugs detectors.

| SpotBugs pattern | Status | Basis | Count |
|---|---|---:|---:|
| `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` | rejected | noise: fires on nearly every List/Map-typed getter or constructor across the OpenAPI-generated models and JSON-deserialized provider DTOs (`CrossrefWork`, `DataCiteAttributes`, ...) - plain data carriers with no independent mutation path once deserialized; defensive-copying ~30 DTO classes is pure churn for no real safety gain | 469 |
| `BC_VACUOUS_INSTANCEOF` | rejected | false positive: fires on every Java 21+ record deconstruction pattern (e.g. `work.resource() instanceof CrossrefResource(Primary(String url))`), where the language requires naming the type at each nesting level even though it is statically redundant with the accessor's return type - SpotBugs's bytecode check doesn't recognize the JEP 440/441 pattern desugaring | 8 |
| `SIO_SUPERFLUOUS_INSTANCEOF` | rejected | false positive: same record-pattern root cause as `BC_VACUOUS_INSTANCEOF` above | 1 |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | rejected, scoped to `org.skgif.doi.docs.*` | false positive: `Path.getFileName()` calls in the doc-consistency tests, all on paths sourced from `Files.list`/`Files.walk` over a known directory or `resolve()`'d against one - never a root path, so `getFileName()` cannot actually return null here even though the JDK contract allows it in general | 6 |
| `REC_CATCH_EXCEPTION` | rejected, scoped to `CrossrefVenueMetadataXmlParser`/`MedraOnixXmlParser` | duplicate: the same two deliberate `catch (Exception _)` degrade-gracefully blocks `checkstyle.xml`'s `IllegalCatch` exclusion already documents (5 violations there) - malformed upstream XML degrades to a REST-JSON-only result rather than failing the whole response | 4 |
| *(any pattern)* | rejected, whole package `org.skgif.doi.generated.model.*` | generated code: same "generated code doesn't get the same scrutiny" precedent as `maven-pmd-plugin`'s `excludeRoots` and `jacoco-maven-plugin`'s `excludes` for this package - matched by class, not by bug pattern, so a future openapi-generator bump tripping a different detector here doesn't need a new row before the build goes green again. The only pattern this package actually tripped was `SE_NO_SERIALVERSIONID` (on `JsonLdCtxBaseOrMore`) | 1 |
| `DLS_DEAD_LOCAL_STORE` | rejected, scoped to `ResourceTypeMapping#isAward` | false positive: the "dead store" is the unnamed pattern variable `_` in a record deconstruction (`DataCiteAttributes.Types(String resourceTypeGeneral, _)`) - the language's own marker for an intentionally discarded component, not an accidentally unused value | 1 |

A future re-run only needs to evaluate findings from packages/classes not already covered by a
`<Match>` in `spotbugs-exclude.xml` - anything new surfacing in an already-matched class/pattern
combination is filtered by construction and should be looked at manually rather than assumed
still valid, since a filter matches by class+pattern, not by the original measured instance.

## Error Prone

Added 2026-08-25, wired via `maven-compiler-plugin` (previously absent from this pom entirely -
the build relied on whichever version shipped with the pinned Maven wrapper) rather than as a
separate `check`-goal execution like the other four tools, because Error Prone is a `javac`
compiler plugin: it has no standalone Maven goal to bind to a later phase, it runs as part of
`compile` itself.

Deliberately scoped to **default checks only** - no `-Xep:Check:SEVERITY` overrides to enable
extra (non-default) checks. What *is* overridden is the severity floor, not which checks run:
`maven-compiler-plugin`'s `failOnWarning=true` fails the compile on any compiler warning,
Error-Prone-sourced or not, closing the `WARNING`/`ERROR` asymmetry so this tool is no weaker a
gate than Checkstyle/PMD/SpotBugs/Spotless. javac's own `-Werror` was considered instead and
rejected: Error Prone's issue tracker documents it as not reliably catching its own diagnostics
(google/error-prone#614, a still-open feature request for exactly that gap) - `failOnWarning` is
enforced at the Maven plugin layer against every diagnostic the shared `javax.tools` compiler API
reports instead, and negative-testing (see below) confirmed it does catch Error Prone's
`WARNING`-severity findings specifically.

`target/generated-sources/openapi/**` (the openapi-generator output) is exempted via
`-XepExcludedPaths:.*/generated-sources/.*` in `pom.xml`, the same "generated code doesn't get
the same scrutiny" precedent already applied via `maven-pmd-plugin`'s `excludeRoots`,
`jacoco-maven-plugin`'s `excludes`, and the SpotBugs whole-package match above.
`-XepDisableWarningsInGeneratedCode` is also set, recognizing the
`@jakarta.annotation.Generated` annotation openapi-generator already emits on every generated
class - kept as a second, narrower layer since it only suppresses `WARNING`-severity findings,
not `ERROR`-severity ones, which the path exclusion covers instead.

Not gated by `${skipTests}` the way Spotless/Checkstyle/PMD/SpotBugs are: those four are bound to
executions that specifically check `${skipTests}` so `-DskipTests` (and, by extension, a
`notest`/`skiptest` commit message per CLAUDE.md) skips them. Error Prone runs during `compile`,
a phase `-DskipTests` never skips (that flag only skips test *execution*), so there is no
equivalent lever - this is expected, not a gap to close.

**First-run measured count (2026-08-25):** a clean `mvn compile`/`test-compile` against the
pre-existing codebase (default checks, no severity overrides yet) reported 7 findings across 5
files, all `WARNING`-severity, none in generated code:

| File | Check | Fix |
|---|---|---|
| `CrossrefTitleMapper.java` | `EscapedEntity` | double-escaped HTML entities inside a `{@code}` Javadoc tag - use literal `<`/`>` instead |
| `DataCiteGrantMapper.java` (x2) | `ReferenceEquality`, `InvalidParam` | `ReferenceEquality` fixed at the root instead of suppressed: the caller (`DataCiteToSkgIfMapper.toGrant`) now finds the funding-agency creator's *index* and removes it positionally (`List.remove(int)`) before ever calling `grantContributions`, so the method no longer needs to skip one specific instance by identity while it maps every creator it's given; added `@SuppressWarnings("InvalidParam")` where Javadoc prose names the SKG-IF `contributions` field, not a typo of the `contributors` parameter |
| `DataCiteManifestationDates.java` | `ReferenceEquality` | same root fix, applied to `otherRecordDays`/`applyDatesArray`: both now iterate `attributes.dates()` by index and exclude the current entry by position (`i != excludingIndex`) instead of comparing `DataCiteDate` references |
| `JsonLdMeta.java` | `InvalidBlockTag` | a wrapped `@param` description's continuation line started with `@graph[i]`, which Javadoc's parser mistook for a block tag - reflowed and wrapped in `{@code @graph[i]}` |
| `CrossrefFilters.java` (x2, plus one that only surfaced after the first fix) | `ExposedPrivateType` | `ValueClauseBuilder` widened from `private` to package-private to match the package-private fields typed with it; that widening then exposed its `clause` method's reference to `ParsedFilter.Builder` (itself `private`), which needed widening to package-private too - a reminder that narrowing/widening one type in an exposure chain can just relocate the same finding rather than resolve it |

All 7 are fixed in this pass; `failOnWarning=true` now makes any regression of these (or any other
default `WARNING`-severity check) fail the build. Negative-tested per the "a green build never
proves a rule is enforced" gotcha below: a scratch `record`-typed `==` comparison (pure
`ReferenceEquality`, no other check involved) failed `test-compile` with "warnings found and
-Werror specified" - confirming `failOnWarning` genuinely reaches Error Prone's diagnostics, not
just javac's own `-Xlint` categories.

## NullAway

Added 2026-08-25 (NullAway 0.14.0), riding on the Error Prone `javac` plugin configured above -
one extra `<path>` in `annotationProcessorPaths` plus two flags on the existing `-Xplugin:ErrorProne`
arg. Requires Error Prone >= 2.36.0; this pom pins 2.50.0, and the pairing was verified by
compiling, not by trusting the compatibility note.

### Why it is opt-in per package

This is the second attempt. The first wired it as `-XepOpt:NullAway:AnnotatedPackages=org.skgif.doi`,
which opts the entire main tree in at once, measured **100 findings**, and was reverted - there was
no way to land partial progress, so the only choices were one enormous change or nothing.

The current wiring is `-XepOpt:NullAway:OnlyNullMarked=true` (NullAway >= 0.12.3, and mutually
exclusive with `AnnotatedPackages` - passing both is an error). Only code annotated
`@org.jspecify.annotations.NullMarked` is in scope, so a package opts in by adding that annotation
to the `package-info.java` it already has - checkstyle's `JavadocPackage`/`MissingJavadocPackage`
guarantee all 19 main packages have one, so this is a two-line edit and never a new file.
Everything unmarked is not "excluded", it is simply not analysed.

Two consequences of `OnlyNullMarked` that look like missing configuration but are not:

- **No generated-code exclusion.** The openapi-generator model in `target/generated-sources`
  carries no `@NullMarked`, so it is out of scope for free. Do **not** add
  `-XepOpt:NullAway:UnannotatedSubPackages=org.skgif.doi.generated`; it is dead config here. This
  matters because generated builder setters carry no nullness annotations at all, which made the
  `builder.setX(foo.orElse(null))` idiom the single largest noise category in the first attempt.
- **No `ExcludedFieldAnnotations`.** No field-initialization finding has appeared yet, because the
  marked package uses constructor injection only. Add the flag when a `@Inject`-field package is
  marked, not pre-emptively.

`-XepOpt:NullAway:JSpecifyMode=true` is deliberately **not** set. That flag turns on deep
generic-type nullness checking, is documented as still under development with known false
positives, and is orthogonal to using `@NullMarked` for scoping, which works in standard mode.

### Why JSpecify annotations

There is no standard nullness annotation in Java - the one attempt, JSR-305, was abandoned
unratified in 2012. JSpecify is the multi-vendor successor (Google, JetBrains, Uber, Oracle,
Microsoft, Broadcom) and the only candidate that costs this project nothing: `org.jspecify:jspecify`
1.0.0 was already on the compile classpath via `junit-jupiter-api`, and main source compiles against
it with no pom change. `javax.annotation.Nullable` was tried and fails outright here - `package
javax.annotation does not exist` - and would need a new `com.google.code.findbugs:jsr305`
dependency. NullAway itself does not force the choice: it matches `@Nullable` by *simple name*, so
any vendor's would work. `@NullMarked` is the differentiator, since it is what makes per-package
adoption possible at all.

Note that JSpecify's `@Nullable` is `TYPE_USE`, so it sits immediately before the type
(`@Nullable String mailto`). NullAway 0.14.0 removed the `LegacyAnnotationLocations` escape hatch
and enforces correct placement on qualified and array types.

### The two severity levers, and why they are properties

Both `nullaway.severity` (default `ERROR`) and `maven.compiler.failOnWarning` (default `true`) are
`<properties>` that the plugin config interpolates, rather than literals in the plugin config. That
indirection exists because **neither can otherwise be overridden from the command line**, both
confirmed empirically:

- `-Dmaven.compiler.compilerArgs=...` does not override a `<compilerArgs>` *list* declared in the
  pom. The override is silently ignored and the pom's value still applies.
- An explicit `<failOnWarning>true</failOnWarning>` takes precedence over the plugin's own
  `${maven.compiler.failOnWarning}` expression, so a bare `-Dmaven.compiler.failOnWarning=false`
  is also silently ignored - the build still fails with "warnings found and -Werror specified".

This matters because of a trap that cost real time in the first attempt: **run discovery at `WARN`,
never at `ERROR`.**

```bash
./mvnw -B compile test-compile -Dnullaway.severity=WARN -Dmaven.compiler.failOnWarning=false
```

At `ERROR`, javac aborts inside `default-compile`, so `test-compile` never runs and every finding in
test sources is silently invisible. The first attempt's "100 findings" figure was produced that way
and is **main-source only** - the test-source count was never measured at all, while the run looked
like a complete survey. Always confirm a discovery run actually reached `test-compile`.

### Test sources are in scope - the non-obvious part

A package's `@NullMarked` applies to **both source roots**. `src/test/java` has no
`package-info.java` files of its own, which makes it tempting to conclude test code is structurally
out of scope; it is not. The test compilation sees the *main* `package-info.class` on its classpath
and inherits the annotation, so marking `org.skgif.doi.crossref` puts
`src/test/java/org/skgif/doi/crossref/**` in scope too.

Measured, and it dominates: marking `org.skgif.doi.crossref.mapper` as a scope experiment produced
**36 findings, 26 of them in test files** (`CrossrefGrantMapperTest` alone accounts for 23). Budget
for test-side churn when planning a package, and do not size the work from a main-source-only
number.

### NullAway adoption register

| Package | Findings | Outcome |
|---|---|---|
| `org.skgif.doi.crossref` | 5 (4 main, 1 test) | all fixed, none suppressed - see below |
| everything else (18 main packages) | not marked | unchecked by design; adopt one package at a time |

The pilot's 5 findings were all *documentation* gaps rather than latent NPEs - in each case the code
already handled null correctly and the signature simply failed to say so, which is the useful thing
NullAway does here:

| Finding | Fix |
|---|---|
| `CrossrefJournalDoiResolver:58` assigning `@Nullable` to `@NonNull` field | field `mailto` is `@Nullable` - its own Javadoc already said "if configured", and the constructor assigns `crossrefMailto.filter(...).orElse(null)` |
| `CrossrefJournalDoiResolver:125` x3, passing `null` where `@NonNull` required | `CrossrefClient.listWorks`'s `queryTitle`/`queryBibliographic`/`offset`/`mailto` are `@Nullable`. Each is a `@QueryParam`, where null means "omit this parameter" per the MicroProfile REST Client contract, so `@Nullable` is simply the honest signature. `filter`/`rows` deliberately left alone - no checked call site passes them null |
| `CrossrefJournalDoiResolverTest:48` passing `null` where `@NonNull` required | `resolveJournalDoi`'s `issns` parameter is `@Nullable`; the method opens with `if (issns == null) return Optional.empty();` and the test asserting this is literally named `resolvesToNullIssnList` |

Negative-tested twice, per the "a green build never proves a rule is enforced" gotcha:

1. **The gate bites.** Removing `@Nullable` from the `mailto` field failed `compile` with
   `[NullAway] assigning @Nullable expression to @NonNull field`.
2. **Unmarked really is unchecked** - load-bearing for the whole incremental strategy, so it was
   verified rather than assumed. Temporarily marking `org.skgif.doi.crossref.mapper` surfaced 36
   findings that the green build had not been reporting; removing the annotation returned it to
   green with those same violations still in the source.

The second test also shows why the first attempt's per-package numbers are not a usable backlog:
that package measured 21 under `AnnotatedPackages=org.skgif.doi` but 36 under `OnlyNullMarked`.
The figures move in both directions - up because test sources are now actually reached, down because
calls into *other* unmarked packages stop being checked - so they are neither upper nor lower bounds.
Re-measure per package at adoption time.
