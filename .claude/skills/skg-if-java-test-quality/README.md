<!--
This is a copy of the "test-quality" skill from
https://github.com/decebals/claude-code-java/tree/main/.claude/skills/test-quality
-->

# Test Quality (JUnit 5 + AssertJ)

**Load**: `view .claude/skills/skg-if-java-test-quality/SKILL.md`

---

## Description

Helps Claude suggest meaningful JUnit tests and improve test coverage for Java projects.

---

## Use Cases

- "Add tests for PluginManager.loadAll()"
- "Review existing tests in PluginLoaderTest"
- "Improve test coverage for lifecycle module"

---

## Examples

```
> view .claude/skills/skg-if-java-test-quality/SKILL.md
> "Add unit tests for ExtensionFactory with edge cases"
→ Generates JUnit 5 tests with AssertJ assertions
```

---

## Notes / Tips

- Works best when class/method signatures are available
- Can suggest missing edge cases or null checks
