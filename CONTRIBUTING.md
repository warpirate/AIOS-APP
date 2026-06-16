# Contributing to Mitra

We're building this in the open. Help is welcome — and we want a stranger to be able to clone, build, install, and ship their first PR without anyone holding their hand.

If the steps below don't work for you on your machine, **that's a bug**: open an issue with the failure log. The whole point of this file is that it stays runnable by someone who has never spoken to us.

---

## 0 — Before you touch the code

Read **[CLAUDE.md](CLAUDE.md)** end-to-end. It has the privacy invariants, the "what NOT to do" list, and the architectural conventions. Most are non-negotiable; if your idea touches any of them, **open a GitHub issue to discuss before writing code**.

---

## 1 — Quick start (clone → build → first command, ~10 min)

### What you need

- A **physical Android phone** (API 26+ / Android 8.0+). The emulator does not handle AccessibilityService reliably and the LiteRT XNNPACK delegate behaves differently on emulated hardware. **Mitra cannot be developed against the emulator.**
- ~10 GB free disk space (gradle caches + the 2.59 GB model file once downloaded on the device).
- A USB cable + USB debugging enabled on the phone (Settings → About → tap Build Number 7 times → Settings → Developer Options → USB Debugging).

### Toolchain — pinned, do not improvise

Pinned in [`.tool-versions`](.tool-versions):

| Tool | Version | Why |
|---|---|---|
| Java (JDK) | 17.0.13 | AGP 8.5 requires JDK 17. |
| Gradle | 8.9 | Bundled via `gradlew` wrapper — you do not need to install it. |
| Kotlin | 2.2.21 | Pinned in the root `build.gradle.kts`. |
| Android Gradle Plugin | 8.5.2 | Same. |
| Android compileSdk / targetSdk | 35 | Pinned in `app/build.gradle.kts`. |
| Android minSdk | 26 | Same. |
| Android NDK | 26.1.10909125 | LiteRT-LM ships native libs; pinning prevents AGP from grabbing whatever NDK is on your machine. |

If you use [asdf](https://asdf-vm.com/) or [mise](https://mise.jdx.dev/), `.tool-versions` is auto-consumed. Otherwise: install JDK 17.0.13 yourself (Temurin works on every platform we test on) and Android SDK Platform 35 + NDK 26.1.10909125 via `sdkmanager`.

### Build, install, run

```bash
git clone https://github.com/warpirate/AIOS-APP mitra
cd mitra/mitra

# Build the debug APK
./gradlew :app:assembleDebug

# Plug in your phone (USB debugging on), then install
./gradlew :app:installDebug
```

On first launch the app downloads the **Gemma 4 E2B** model (~2.59 GB, Apache 2.0, from the public `litert-community/gemma-4-E2B-it-litert-lm` HuggingFace mirror — no token required). Use Wi-Fi. The download is resumable. The downloaded file is integrity-checked (SHA-256 + size) against the pins in [`ModelRegistry.kt`](app/src/main/kotlin/com/mitra/inference/ModelRegistry.kt); if your bytes don't match, the file is deleted and you can retry cleanly.

After the model loads, type `turn on the flashlight` in the chat. The torch toggles. That's the loop: text in → on-device brain → tool call → device action.

### Run the test suite

```bash
./gradlew :app:test
```

This includes:

- `safety/GateCoverageTest` — proves every `SideEffect.Irreversible` tool routes through the ConfirmationGate. If you add a new Irreversible tool, this test will fail loudly until you add its name to the test's coverage set.
- `safety/AuditLogTest` — locks the audit log's field whitelist. Any new property on `AuditLog.Entry` that could carry user content fails this test.
- `inference/ModelDownloaderTest` — exercises SHA-256 + size verification against a tiny embedded HTTP server.
- `agent/AgentRuntimeTest` — covers the agentic loop (chain, cancel, replan, step-cap, JNI-error).

### CI

[GitHub Actions](.github/workflows/ci.yml) runs unit tests + Android lint + `assembleDebug` on every push to `main` and every PR. Same toolchain pins as above. If your local build is green and CI is red, that's a bug — open an issue.

---

## 2 — Add your first tool (~30 min)

The quickest path from "I cloned this repo" to "my code shipped" is adding a new tool. See the step-by-step walkthrough in **[docs/runbooks/add-a-tool.md](docs/runbooks/add-a-tool.md)**.

Every new tool ships with the same five things:

1. **The implementation** — a single `.kt` file in `app/src/main/kotlin/com/mitra/tools/` named after the tool's class (e.g. `ToggleFlashlight.kt`).
2. **A `SideEffect` classification** — `None` (auto-runs), `Reversible` (auto-runs with undo), or `Irreversible` (gates behind a Confirm modal). When in doubt, default to `Irreversible`. If you choose `Irreversible`, you MUST add the tool's name to `GateCoverageTest.irreversibleToolNames` or the test will fail the build.
3. **A `@Tool` description on the matching method in `inference/LiteRtBrain.kt` `PhoneTools`** — phrased `"Use this when the user wants …"` with distinct boundaries from neighbouring tools. The model picks tools by their descriptions; a vague description means the model won't call your tool. See the existing tools for the shape.
4. **An `IntentParser` regex in `agent/Router.kt`** — the deterministic fallback for common phrasings. Add at least 2 regex patterns covering polite + terse variants.
5. **A unit test in `agent/IntentParserTest.kt`** — at least 3 cases covering your regex.

PR title format: `feat(tools): add <tool_name>`. Apply the `new-tool` label.

---

## 3 — Other ways to help

### Easy first contributions (no Android setup needed)

- **Add `IntentParser` patterns + eval cases** in your language. Telugu, Tamil, Hindi, Bengali, Marathi especially wanted — V1's brain (Gemma 4 E2B) calls tools zero-shot, so the deterministic parser is what catches common phrasings reliably. Variety matters: polite vs terse, different word orders.
- **File issues** for bugs — clear repro steps, expected vs actual, device + Android version.
- **Improve docs** — if something here is wrong or unclear, fix it.

### Medium-effort

- **Tackle a `good-first-issue`-tagged ticket** on the [issue tracker](https://github.com/warpirate/AIOS-APP/issues).
- **Implement a new tool** (see §2).
- **Add custom Detekt / lint rules** that enforce the privacy invariants in [CLAUDE.md](CLAUDE.md) — `NoUserContentInLogs`, `NetworkImportAllowlist`, etc.

### Larger (talk to us first)

- **Tackle a milestone item in [plan.md](plan.md).** Open a GitHub Discussion before writing code so we can pair on design — M-tier work touches the core and we want alignment before code.
- **Run the hardware truth test** on a real Snapdragon 7 Gen 2 / 6 GB device and park results in `docs/research/`. We are bottlenecked on having one in hand.
- **Start the AccessibilityService backend** (M6 — read the autonomous-interaction spec in `docs/superpowers/specs/` first).

---

## 4 — Ground rules

These get PRs closed unread; please respect them:

1. **The privacy rule is absolute.** No new network calls. No telemetry. No `Log.*` call carrying user content (message bodies, contact names, query text, screen contents). The lint allowlist will eventually enforce this; until then reviewers do.
2. **Every tool needs a `@Tool` description, an `IntentParser` pattern, a `SideEffect` classification, and a test.** A tool the model can't reliably call is not done.
3. **One concern per PR.** Don't bundle.
4. **Follow the lint config.** If lint is wrong, fix the lint config; don't bypass it.
5. **Don't introduce a cloud fallback.** Not even "opt-in." Every line of code that adds a network path is a future leak. If a feature genuinely can't be done locally, it does not ship.
6. **No `Runtime.exec()` or `ProcessBuilder`.** AccessibilityService is our action surface, not ADB.

---

## 5 — Code style

Kotlin official style. Indentation, max line length, and trailing-whitespace rules are pinned in [`.editorconfig`](.editorconfig). Run your formatter against that file.

We tried `ktlint-gradle` and dropped it (the 1.5.0 worker fails on otherwise-valid Kotlin 2.2 source — see the comment at the top of the root `build.gradle.kts`). We will revisit when ktlint releases a clean Kotlin 2.2 parser or move to Spotless. Until then: match the surrounding style in the file you're editing.

---

## 6 — Commit messages

Conventional Commits. Examples from this repo:

```
feat(tools): add toggle_dnd
fix(safety): gate cancel_alarm
refactor(inference): extract Brain interface from LiteRtBrain
test(agent): cover cancel-mid-chain / replan / step-cap / JNI-error paths
docs(arch): clarify dispatcher routing heuristic
```

Scopes generally match top-level package names (`agent`, `tools`, `inference`, `safety`, `automation`, `ui`, `permissions`).

---

## 7 — PR checklist

The PR template will spell this out, but at minimum:

- [ ] One concern per PR
- [ ] `./gradlew :app:test` passes locally
- [ ] `./gradlew :app:assembleDebug` succeeds locally
- [ ] If you added a new tool: implementation + `@Tool` description + `IntentParser` pattern + test added
- [ ] If you added a tool with `SideEffect.Irreversible`: name added to `GateCoverageTest.irreversibleToolNames`
- [ ] If you touched a privacy invariant: linked discussion issue
- [ ] CLAUDE.md updated if a convention changed
- [ ] plan.md updated per the keep-docs-honest checklist in CLAUDE.md

---

## 8 — Where to talk

- **GitHub Issues** — bugs, feature ideas, "I can't get the toolchain to work."
- **GitHub Discussions** — design questions, "is this a good idea," language-pattern proposals.
- **Matrix room** — coming once we have a community larger than the core maintainer set. Until then GitHub is enough and a public history is good for the project.

We do not run a Discord. We have no Slack. There is no mailing list. The project is small and everything happens in public on GitHub.

---

## 9 — Licensing of contributions

By contributing, you agree your code is licensed under **Apache 2.0**, the project's license. Don't submit code you don't have the right to license.

---

## 10 — Code of conduct

Be decent. Disagree about ideas, not people. If something feels off in an interaction, message a maintainer privately.

---

## 11 — Why this file is this long

Because the project's #1 existential risk is solo-maintainer attrition ([risks.md R-008](docs/risks.md)). ~70% of OSS projects lose all core developers in their first three years and most never recover one. Mitra's design (Apache 2.0, 100% on-device, no proprietary backend) makes it fork-able, but only if a stranger can run, hack, and ship it without us. That's what this file is for. If any part of it is wrong, **please** open an issue or send a PR — keeping this file runnable is the most valuable contribution you can make.
