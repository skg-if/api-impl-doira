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

Four tools, and mixing up their jobs is the single most expensive mistake available here.

| Tool | Owns | Enforced? |
|---|---|---|
| **Spotless** (Eclipse formatter + `intellij-style.xml`) | formatting: indentation, wrapping, whitespace, blank lines | yes - `check` fails the build |
| **Checkstyle** | semantics and style a formatter cannot express: naming, Javadoc, imports, language-idiom rules | yes - both source roots |
| **PMD** | correctness, design, complexity, error-prone patterns | yes - both source roots |
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
