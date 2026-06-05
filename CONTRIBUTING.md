# Contributing to Mitra

We're building this in the open. Help is welcome.

## Before you contribute

Read [CLAUDE.md](CLAUDE.md). It has the architectural invariants, the "what NOT to do" list, and the privacy rules. Most are non-negotiable; if you're proposing something that touches them, **open an issue to discuss before writing code.**

## How to help

### Easiest first contributions

- **Add eval cases + widen `IntentParser` patterns.** V1's brain (Gemma 4 E2B) calls tools zero-shot — there's no fine-tuning dataset. The impactful work is adding command → expected-tool-call cases to the eval set, and widening the `IntentParser` (`agent/Router.kt`) fallback patterns with more phrasings/synonyms. Variety helps: polite vs. terse, different word orders.
- **Help with languages.** Add `IntentParser` patterns + eval cases in your language so common commands work for bilingual users. Telugu, Tamil, Hindi, Bengali, Marathi especially wanted.
- **File issues** for bugs, with steps to reproduce.

### Medium-effort contributions

- **Implement a new tool.** Pick something from issues tagged `new-tool`. Follow the "How to add a new tool" section in [CLAUDE.md](CLAUDE.md). Every new tool ships with: implementation, side-effect classification, a "Use this when …" `@Tool` description (so the LLM calls it), an `IntentParser` fallback pattern, and a unit test.
- **Improve docs.** If something here is unclear, fix it.

### Larger contributions

- **Tackle a milestone item in [plan.md](plan.md).** Open an issue first; pair on the design before writing code. M-tier work touches the core; we want alignment before code.

## Ground rules

1. **The privacy rule is absolute.** No new network calls. No telemetry. No logging of user content. PRs that violate this get closed, not reviewed.
2. **Every tool needs a test, a "Use this when …" `@Tool` description, and an `IntentParser` pattern.** A tool the model can't reliably call isn't done.
3. **One PR, one concern.** Don't bundle.
4. **Follow the lint config.** It exists to enforce the invariants. If it's wrong, fix the lint config; don't bypass it.
5. **Be kind in review.** This project is built by people doing it for free, in their spare time.

## Code style

Kotlin official style, enforced by `ktlint`. Run `./gradlew formatKotlin` before pushing. CI will reject formatting failures.

## Commit messages

We follow Conventional Commits: `feat(tools): add toggle_dnd`, `fix(safety): gate cancel_alarm`, `docs(arch): clarify routing heuristic`. Scopes generally match top-level package names (`agent`, `tools`, `inference`, `safety`, `accessibility`, etc.).

## PR checklist

The PR template will spell this out, but in short:

- [ ] One concern per PR
- [ ] Tests pass locally (`./gradlew test connectedAndroidTest`)
- [ ] Lint passes (`./gradlew lintKotlin`)
- [ ] Formatter applied (`./gradlew formatKotlin`)
- [ ] If a new tool: `@Tool` "Use this when …" description + `IntentParser` pattern + test added
- [ ] If touching privacy invariants: linked discussion issue
- [ ] CLAUDE.md updated if any convention changed

## Communication

- **GitHub Issues** for bugs and feature discussion
- **GitHub Discussions** for design questions and "is this a good idea" threads
- **(Matrix room TBD)** for synchronous chat

## Licensing of contributions

By contributing, you agree your code is licensed under Apache 2.0, the project's license. Don't submit code you don't own the rights to.

## Code of conduct

Be decent. Disagree about ideas, not people. If something's off, message a maintainer.
