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
| **SpotBugs** (+ FindSecBugs) | bytecode/dataflow correctness Checkstyle's style rules and PMD's AST-level rules can't reach: null dataflow, resource leaks, concurrency hazards; plus, via the bundled FindSecBugs detector plugin, known security bug classes (injection, XXE, taint-tracked endpoints) | yes - `check` fails the build |
| **Error Prone** | compile-time, type-aware bug patterns caught via `javac`'s own type information - a different reach than PMD's pure-AST rules or SpotBugs's post-compile bytecode analysis | yes - `failOnWarning=true` fails the compile on any diagnostic, `WARNING`-severity default checks included, not just its `ERROR`-severity ones |
| **Error Prone Support** (Picnic) | extra type-aware checks and Refaster template rewrites layered on Error Prone's plugin: JUnit/AssertJ/Mockito idiom, static-import and annotation hygiene. Does **not** own formatting (spotless) or nullness (NullAway) | yes - `-XepAllSuggestionsAsWarnings` promotes its `SUGGESTION` checks into `failOnWarning`'s reach; Guava-introducing and inference-breaking rule families excluded, see its section below |
| **NullAway** | nullness specifically: that a `@Nullable` value never reaches a `@NonNull` field, parameter or return. Rides on Error Prone's `javac` plugin, scoped by one prefix flag over the whole `org.skgif.doi` tree. Owns this concern outright - SpotBugs's weaker bytecode-level `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` is a documented, per-site `@SuppressFBWarnings` duplicate wherever NullAway's own source-level tracking already proved a path safe | yes - `-Xep:NullAway:ERROR` fails the compile, both source roots; only `org.skgif.doi.generated` is out of scope |
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
490 findings; every one was triaged as a rejection, none as a fix - a plausible outcome for a
codebase already gated by strict Checkstyle/PMD/Spotless and written in a record-pattern-heavy
Java 21+ style that predates most SpotBugs detectors.

### Rejections live as `@SuppressFBWarnings` in source, not in `spotbugs-exclude.xml`

Every rejection above was originally recorded as a `<Match>` in `spotbugs-exclude.xml`. That filter
file has since been reduced to a single entry (the generated-code exclusion below) and every other
rejection converted to a `@SuppressFBWarnings` annotation, via the `com.github.spotbugs:
spotbugs-annotations` dependency (`provided` scope, `RetentionPolicy.CLASS` - no runtime footprint),
placed directly on the class/method/field the finding actually belongs to, or on a
`package-info.java` for a whole DTO-only package (`crossref.dto`, `datacite.dto`, `medra.dto` -
confirmed empirically to suppress the pattern for every class in that package). The motivation:
a class-wide XML filter matches by class+pattern only, so it silently also swallows a genuinely new
bug introduced later in the same class/pattern combination; a per-site annotation only ever matches
the one finding it was written for; if that exact bug shape is fixed and a new one appears nearby,
`US_USELESS_SUPPRESSION_ON_METHOD`/`_CLASS` fires and the build fails until the annotation is
revisited. `spotbugs-exclude.xml` itself documents that it now only holds what *cannot* carry an
annotation.

Every pattern name and the shared justification-tail phrase are centralized as `public static final
String` constants in `org.skgif.doi.util.SpotBugsSuppressions` (a constants-only holder, `@SuppressWarnings("PMD.DataClass")`
- deliberately not a Java `interface` of constants, the worse-regarded idiom this pattern replaces)
and referenced via `import static`, rather than repeating the literal at every annotated site - both
because a typo in a repeated literal silently no-ops the suppression, and because PMD's
`AvoidDuplicateLiterals` (`maxDuplicateLiterals=3`) otherwise fires on a class annotating several
methods with the same pattern. Read the class Javadoc there for what each constant covers; read the
`justification` string at each call site for why that specific site is safe - this file intentionally
doesn't duplicate either, to avoid the two drifting apart.

### Lambda bodies can't reliably carry a method-level suppression

An enclosing method's `@SuppressFBWarnings` *sometimes* reaches a finding SpotBugs attributes to a
nested lambda's synthetic method (`lambda$methodName$N`), but this was confirmed unreliable at this
codebase's scale: a full-tree run with ~50 annotations present left 31 findings still failing, every
one where the actual bug site was inside a lambda body, while every finding whose bug site sat in
the enclosing method's own code (or was reached via a plain method reference to a *separately*
annotated method) suppressed correctly. Confirmed via `javap -v -p` bytecode inspection (the
annotation's `RuntimeInvisibleAnnotations` value resolves the shared constant correctly, ruling out
a constant-folding bug) and byte-for-byte identical reruns (ruling out flakiness) - and each failure
carried its own `US_USELESS_SUPPRESSION_ON_METHOD`/`_CLASS` alongside the still-firing original
finding, proving genuine non-suppression rather than the cosmetic self-referential false positive
that detector can also produce.

**The fix, applied throughout:** extract the lambda body into a named private method (static where
possible) invoked via a method reference, and annotate that named method directly -
`CrossrefContributionMapper.firstRor`/`isRorType`, `CrossrefFundingMapper.funderDoi`/`isDoiType`,
`DataCiteContributionMapper.firstBareIdentifier`/`matchesScheme`, and the doc-consistency tests'
`isSourceFixture`/`isMappingDoc` predicates are worked examples. Where the guard and the dereference
it protects were split across a `filter(...).map(...)` stream pair specifically (rather than a
`Nullable`-scheme comparison), a plain indexed loop reads at least as clearly and keeps both in one
method body without needing a second extracted method at all - see the same
`DataCiteContributionMapper`/`DataCiteGrantMapper` methods, and `CrossrefGrantMapper.grantTitles`/
`grantAbstracts`, which instead route the null-dropping through `Objects::nonNull` via
accessor-then-filter method references (`map(CrossrefProject::projectTitle).filter(Objects::nonNull)`)
rather than an inline `p -> p.x() != null` predicate lambda, for the same reason.

Only what genuinely cannot carry an annotation stays in `spotbugs-exclude.xml`:

| Exclusion | Basis |
|---|---|
| whole package `org.skgif.doi.generated.*` | openapi-generator output, regenerated on every build - a hand-added annotation here is silently wiped on the next generator run, the same reason `maven-pmd-plugin`'s `excludeRoots` and `jacoco-maven-plugin`'s `excludes` skip this package. Matched by class, not by bug pattern, so a future generator bump tripping a different detector here doesn't need a new entry |

### FindSecBugs

Added 2026-08-25 alongside the annotation migration, via SpotBugs's own nested
`<configuration><plugins>` detector-plugin mechanism (`com.h3xstream.findsecbugs:findsecbugs-plugin`).
**Not tracked by Dependabot**: its GAV lives inside SpotBugs's `<configuration>` body rather than a
standard `<dependency>`/`<plugin>` declaration site, which Dependabot's Maven updater does not walk
into - `pom.xml` documents this gap with a comment next to the version property, the same convention
`.github/dependabot.yml` uses for its own `Dockerfile.jvm`-ARG and composite-actions-`directories`
blind spots.

FindSecBugs's own findings were triaged the same way as vanilla SpotBugs's - fix the genuine ones,
annotate the rest via `SpotBugsSuppressions`' `JAXRS_ENDPOINT`/`IMPROPER_UNICODE`/`XPATH_INJECTION`/
`PATH_TRAVERSAL_IN` constants where the finding is a documented false positive or an accepted,
justified pattern (each call site's own `justification` string states which and why) - none were
rejected via `spotbugs-exclude.xml`, since none were whole-class/whole-package noise the way the
original DTO-getter flood was.

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

### Error Prone Support (Picnic)

Added 2026-08-25. `tech.picnic.error-prone-support` **0.30.0**, both artifacts
(`error-prone-contrib` + `refaster-runner`), on the existing `annotationProcessorPaths`. 0.30.0 is
built against Error Prone **2.50.0** - the version this pom already pinned - so the two properties
must be bumped together. Requires a JDK 21+ build JVM (this repo builds on 25) and
`-XDaddTypeAnnotationsToSymbol=true`.

**Why `-XepAllSuggestionsAsWarnings` is load-bearing.** Nearly every Picnic check ships at
`SUGGESTION`, which javac reports as a *note* - `failOnWarning` never sees it. Measured: at stock
severities the library reported **12** blocking findings; with suggestions promoted, **521**. Adopting
without the promotion flag would have enforced 12 things and let the other 509 rot silently.

**First-run measured counts (2026-08-25).** 521 findings, excluding 150 `RequireExplicitNullMarking`
which is NullAway's own check surfaced only by the promotion flag, not Picnic's:

| Bucket | Found | Fixed | Left | Disposition |
|---|---|---|---|---|
| `StaticImport` | 154 | 154 | 0 | adopted |
| `LexicographicalAnnotationListing` | 43 | 43 | 0 | adopted |
| `JUnitClassModifiers` | 38 | 38 | 0 | adopted - incl. 7 `@QuarkusTest` classes now `final`, verified safe by the suite |
| `ExplicitArgumentEnumeration` | 20 | 20 | 0 | adopted (20 were *created* by the `JUnitRules.ArgumentsEnumeration` rewrites) |
| `FormatStringConcatenation` | 9 | 9 | 0 | adopted |
| `MockitoMockClassReference` | 5 | 5 | 0 | adopted |
| `CollectorMutability` | 2 | 2 | 0 | adopted - hand-fixed to JDK `toUnmodifiableSet()`; the suggested fix wanted Guava |
| `CanonicalAnnotationSyntax` | 2 | 2 | 0 | adopted despite partial overlap with checkstyle `AnnotationUseStyle` (see below) |
| `OptionalOrElseGet`, `ClassCastLambdaUsage` | 2 | 2 | 0 | adopted |
| Refaster (allowed families) | 145 | 145 | 0 | adopted |
| Refaster (denied families) | 90+21+4+2 | 0 | 0 | **rejected** - excluded via `NamePattern`, see pom |
| `LexicographicalAnnotationAttributeListing` | 4 | 1 | 0 | **rejected** - `-Xep:...:OFF` |

Net: 65 files changed, build green with `failOnWarning=true`, 364 tests passing, **0 residual
findings**. The per-exclusion rationale (Guava dependency, generic-inference breakage, NullAway
provability loss, spotless/checkstyle deadlock) is in the `maven-compiler-plugin` comment in
[pom.xml](pom.xml) rather than duplicated here.

**`CanonicalAnnotationSyntax` and the no-duplication rule.** It overlaps checkstyle's
`AnnotationUseStyle` (already active, `elementStyle=COMPACT_NO_ARRAY`), which would normally make it
a rejected duplicate. Kept because it demonstrably caught 2 sites `AnnotationUseStyle` passed, so it
is a strict superset here, not a duplicate. Revisit if it ever starts disagreeing with checkstyle
rather than extending it.

**Negative-tested in both directions**, per the "a green build never proves a rule is enforced"
gotcha below - and here that matters more than usual, because a `NamePattern` regex matching *nothing*
silently disables Refaster wholesale and is indistinguishable from a clean build. It bit this rollout
once: an earlier anchored/escaped pattern turned every Refaster rule off, and the 262 findings only
reappeared on a control run. The standing check is therefore a pair:

- a scratch `Optional.of("a").filter(s -> !s.isEmpty())` (`StringRules.NotStringIsEmpty`, an
  *allowed* family) must FAIL `test-compile` - proving Refaster is live;
- a scratch `List.of("a")` (`ImmutableListRules`, a *denied* family) must NOT fire - proving the
  deny-list still excludes Guava.

## NullAway

Added 2026-08-25 (NullAway 0.14.0), riding on the Error Prone `javac` plugin configured above - one
extra `<path>` in `annotationProcessorPaths` plus a few flags on the existing `-Xplugin:ErrorProne`
arg. Requires Error Prone >= 2.36.0; this pom pins 2.50.0, and the pairing was verified by compiling,
not by trusting the compatibility note.

It covers the **whole** `org.skgif.doi` tree, both source roots, and reports zero findings.

### Scoping: one flag, not per-package annotations

`-XepOpt:NullAway:AnnotatedPackages=org.skgif.doi` matches by **prefix**, so that single entry
covers every subpackage and anything added later. `src/main/java/org/skgif/doi/` has no classes
directly in the root package, so nothing unexpected is caught by the prefix.

A first iteration instead used `-XepOpt:NullAway:OnlyNullMarked=true` with JSpecify `@NullMarked` on
each `package-info.java`, adopted one package at a time. That was the right *migration* tool but the
wrong destination, and was removed once the tree was fully covered: 19 annotations to keep in sync,
and - the real problem - a newly-added package would be silently **unchecked** until someone
remembered to mark it. The prefix flag fails safe instead. The two options are mutually exclusive;
passing both is an error. **Nothing under `src/` carries `@NullMarked` any more** - if you find one,
it is vestigial.

The trade-off given up: `@NullMarked` is the JSpecify standard and is read by IntelliJ and other
tools, so developers would have seen in-IDE warnings matching the build. A pom flag is invisible to
the IDE. If that becomes the deciding factor, NullAway's `RequireExplicitNullMarking` check (>=
0.12.13, off by default) makes the annotation route fail-safe too, and the two could be combined -
flag for enforcement, annotations for tooling.

`-XepOpt:NullAway:JSpecifyMode=true` is deliberately **not** set. It enables deep generic-type
nullness checking (type arguments, wildcards) and is documented as still under development with
known false positives. Standard mode - top-level nullability only - is what this build relies on. A
consequence worth knowing: annotating an inner type argument, e.g. `List<@Nullable String>`, is
documentation the tool will not act on.

### The generated-code exclusion is required, not cosmetic

`-XepOpt:NullAway:UnannotatedSubPackages=org.skgif.doi.generated` (the `nullaway.unannotated`
property) is load-bearing. `AnnotatedPackages=org.skgif.doi` prefix-matches
`org.skgif.doi.generated.api` and `.model` too, and openapi-generator emits no nullness annotations
at all, which makes every `builder.setX(foo.orElse(null))` call a finding. Measured by neutering the
property (`-Dnullaway.unannotated=org.skgif.doi.NOTHING`): **50 findings appear**, all in generated
code. One entry covers both generated packages because matching is by prefix.

### Framework-initialised fields

`-XepOpt:NullAway:ExcludedFieldAnnotations=...ConfigProperty,...Inject,...RestClient,...InjectMock`
exempts the field-initialization check. The `rest.*` resources declare config as non-final
`@ConfigProperty` fields with no initializer (7 sites) and tests use `@InjectMock` the same way (12);
the container populates both before use. This is an initialisation exemption, **not** a claim that
those fields are nullable. Without it these surface as `initializer method does not guarantee
@NonNull fields ... are initialized along all control-flow paths`.

### JSpecify, and why not JSR-305

There is no standard nullness annotation in Java - the one attempt, JSR-305, was abandoned unratified
in 2012. JSpecify is the multi-vendor successor (Google, JetBrains, Uber, Oracle, Microsoft,
Broadcom) and the only candidate that costs this project nothing: `org.jspecify:jspecify` 1.0.0 was
already on the compile classpath via `junit-jupiter-api`, and main source compiles against it with no
pom change (it is now declared explicitly anyway - main source should not rely on a test-scoped
transitive). `javax.annotation.Nullable` was tried and fails outright here - `package
javax.annotation does not exist` - and would need a new `com.google.code.findbugs:jsr305` dependency.

NullAway itself does not force the choice: it matches `@Nullable` by *simple name*, so any vendor's
would work.

JSpecify's `@Nullable` is `TYPE_USE`, so it sits immediately before the type (`@Nullable String
mailto`, `private final @Nullable String mailto`). NullAway 0.14.0 removed the
`LegacyAnnotationLocations` escape hatch and enforces correct placement on qualified and array types.
One checkstyle interaction: on a declaration with no preceding modifier - an interface method - a
leading `@Nullable` trips `AnnotationLocation` ("should be alone on line"), so it goes on its own
line there. With any modifier present (`private static @Nullable String ...`) inline is fine.

### The two severity levers, and why they are properties

Both `nullaway.severity` (default `ERROR`) and `maven.compiler.failOnWarning` (default `true`) are
`<properties>` the plugin config interpolates, rather than literals in the plugin config. That
indirection exists because **neither can otherwise be overridden from the command line**, and both
failures are silent:

- `-Dmaven.compiler.compilerArgs=...` does not override a `<compilerArgs>` *list* declared in the
  pom. The override is ignored and the pom's value still applies.
- An explicit `<failOnWarning>true</failOnWarning>` takes precedence over the plugin's own
  `${maven.compiler.failOnWarning}` expression, so a bare `-Dmaven.compiler.failOnWarning=false` is
  also ignored - the build still fails with "warnings found and -Werror specified".

### Discovery runs: WARN, and mind the diagnostic cap

```bash
./mvnw -B compile test-compile -Dnullaway.severity=WARN -Dmaven.compiler.failOnWarning=false
```

Never measure at `ERROR`: javac aborts inside `default-compile`, `test-compile` never runs, and every
finding in test sources is invisible while the output still looks like a full survey. The first
attempt at NullAway produced its "100 findings" figure that way - main-source only, with the test
count never measured at all. **Confirm a discovery run actually reached `test-compile`** before
trusting a number from it.

`-Xmaxerrs`/`-Xmaxwarns` are raised from javac's default of 100 for the same reason. That default
silently truncates: a discovery pass here reported exactly 100 test-phase findings twice in a row,
which read as a measurement rather than a cap, and hid a whole file's worth of findings. Any count
taken with the default cap is a lower bound.

### Test sources are in scope

They are under the annotated root prefix like anything else, and they are not a rounding error: of
the 218 findings in the first full-tree measurement, **138 were in test code**. But findings are not
edits - they cluster hard on a few fixture helpers. All 23 findings in `CrossrefGrantMapperTest`
funnelled through 5 private static helpers; annotating those 5 signatures resolved all 23.

### What the rollout actually found

218 findings at the start (80 main, 138 test), zero at the end. The shape of the work, in the order
it was done - bottom-up, so no file needed revisiting:

| Category | What it was | Fix |
|---|---|---|
| Provider DTO components (~95) | Jackson/XML records mirroring provider JSON with `ignoreUnknown`; every absent field deserializes to null. `MedraContributor`'s javadoc already said "all nullable here"; `CrossrefWork`'s said "necessarily has many independent optional fields" | `@Nullable` on every reference-typed record component. A record component's `TYPE_USE` annotation propagates to the field, accessor return **and** canonical-constructor parameter - which is why this alone resolved 58 test findings |
| Null-tolerant helper signatures (the bulk) | Methods whose body already opened with `if (x == null) return ...` or `x != null ? ... : null`, or whose javadoc already hedged ("if configured", "or null if none is known") | `@Nullable` on the parameter, javadoc `@param` updated in the same edit. No behaviour change - the fix records a contract the code already honoured |
| Nullable returns (16) | e.g. `CrossrefContributionMapper#displayName` returns `family` when `given` is null - and `family` may itself be null; `FilterQuerySyntax#schemeOnlyFilter`, whose javadoc and PMD suppression both already said it returns null | `@Nullable` on the return type |
| Guard hidden behind a boolean (4) | `boolean hasRor = x.foo() != null && ...;` then `strip(x.foo())`. Safe, but nullness does not flow through a boolean local | Hoisted the accessor into a local and tested that: `String id = x.foo(); ... id != null && ... ? strip(id) : null`. Same behaviour, and provable |
| Guard in another method (5) | `hasNoContainerTitle(work)` guards, then `work.containerTitle().getFirst()` dereferences; `otherRecordDays`/`relatedByType` relied on a null-check their *caller* had done | Either a self-contained local null-check in the helper, or `Objects.requireNonNull` with a comment naming the guard that makes it safe |
| Filter-then-dereference (1) | `.filter(a -> a.amount() != null).map(a -> a.amount().intValue())` | `.map(CrossrefAmount::amount).map(Double::intValue)` - `Optional.map` already drops a null result, so this is the same behaviour and provable |
| Unguarded response DTO (2) | `mapper.toProduct(data.attributes())` in the DataCite resources - `attributes` is nullable per the DTO and `toProduct` dereferences it. DataCite always sends one, but the type could not promise it | A `null` check returning 404 rather than dereferencing. The only genuine (if unlikely) NPE the rollout surfaced |
| Test fixture helpers | Literal `null`s into private `project(...)`/`withParts(...)`/`withLifecycleDates(...)` builders | `@Nullable` on the helper parameters |
| Test assertion dereferences (~20) | `work.contributors().getFirst()`, `titles.get("en").getFirst()` - a test *wants* to fail loudly here | `Objects.requireNonNull(...)`, which states the assumption instead of relying on an implicit NPE |

**No `@SuppressWarnings("NullAway")` anywhere.** Every finding was resolved by annotating a real
contract or restructuring so the existing guard is visible.

### Consequence: SpotBugs no longer owns nullness

Annotating the DTO accessors handed SpotBugs genuine nullness information, and its
`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` detector went from 6 hits to 49. All 43 new ones are false
positives of a single shape it cannot model - a null-guard on a record accessor followed by another
call to the **same** accessor:

```java
if (work.author() != null) { for (CrossrefContributor a : work.author()) { ... } }
return work.containerTitle() == null || work.containerTitle().isEmpty() || ...;
```

SpotBugs treats each invocation as an independently-nullable value rather than a pure field read, and
likewise cannot follow a guard through a stream filter or across a method boundary. Rather than a
tree-wide XML exclusion, each of these is now a per-site `@SuppressFBWarnings(NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE)`
next to the guarded accessor it fires on, by the same "never duplicate an active rule" principle
this file applies between PMD and checkstyle: NullAway checks the same property at source level,
understands `Optional` chains and `Objects.requireNonNull` assertions, and reports zero findings.
Per-site rather than tree-wide so a genuinely new nullness bug introduced later in an already-annotated
class still gets caught - see the "Rejections live as `@SuppressFBWarnings` in source" subsection of
the SpotBugs decision register above for why that granularity was chosen over a class/package match.

### Negative tests

Per the "a green build never proves a rule is enforced" gotcha above, three checks, all confirmed:

1. **The gate bites, in newly-covered code.** Removing `@Nullable` from
   `MedraOnixXmlParser#journalTitle` - in `medra.xml`, a package the per-package pilot never
   covered - failed `compile` with `[NullAway] returning @Nullable expression from method with
   @NonNull return type`.
2. **The generated exclusion is load-bearing.** Neutering `nullaway.unannotated` produced 50
   findings (above).
3. **Coverage is total.** No `@NullMarked` remains in `src/`, and `nullaway.unannotated` contains
   only `org.skgif.doi.generated` - so no package is silently out of scope.
