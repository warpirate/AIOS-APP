# Runbook — Add a Tool to Mitra

Step-by-step guide for adding a new tool that the Gemma 4 E2B brain can call. Assumes you've completed §1 (Quick start) of [CONTRIBUTING.md](../../CONTRIBUTING.md) and have the app building + installing on your phone.

We use `toggle_airplane_mode` as the running example. It is **not** actually shipped — pick a different tool name for your real PR, and check [`plan.md`](../../plan.md) for the M1 / M2 / Mx checklist where your tool likely already has an unchecked box.

Time budget: ~30 minutes for a simple tool with no new permission. ~60 minutes if it needs a runtime permission and a settings-bounce flow.

---

## Step 0 — Pick a tool from the plan

Open [`plan.md`](../../plan.md) and find an unchecked `- [ ]` item in the M1 (V1 tool surface) section that no one has started. If you want a tool that isn't in the plan, open an issue first so we can decide whether it belongs in V1.

Decide three things up front:

1. **Tool name.** Lowercase snake_case (`toggle_airplane_mode`, `get_battery`). This is what the model emits and what the dispatcher matches on. Bad names: `airplane`, `airplaneToggle`, `airplane-mode`.
2. **Side effect class.** `None` (read-only or self-evident — `get_battery`, `toggle_flashlight`), `Reversible` (state change with undo affordance — `set_alarm`, `set_brightness`), `Irreversible` (cannot be undone, costs money, or hits another human — `make_call`, `send_sms`, `delete_alarm`). **When in doubt, choose Irreversible.** Better an extra confirmation card than a silent destructive action.
3. **Permission needed?** Some tools need a runtime permission (`SEND_SMS`, `CALL_PHONE`, `READ_CONTACTS`) or a special permission (`WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`). Some need neither. If yours needs a new permission entry, plan for the extra UI wiring at step 5 below.

---

## Step 1 — Create the tool file

One file per tool, in `app/src/main/kotlin/com/mitra/tools/`. The file name matches the class name in PascalCase; the class's `name` field matches the lowercase snake_case tool name.

```kotlin
// app/src/main/kotlin/com/mitra/tools/ToggleAirplaneMode.kt
package com.mitra.tools

import android.content.Context

/**
 * Toggles airplane mode. Note: on Android 4.2+ third-party apps cannot toggle airplane mode
 * directly; this tool opens the system airplane-mode settings panel so the user can flip it.
 * Classification: SideEffect.Reversible because the user remains in control of the toggle.
 */
class ToggleAirplaneMode(private val context: Context) : Tool {
    override val name = "toggle_airplane_mode"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        // Tools never reach across modules. Use the context handed in; do not call into
        // other tools, do not read SharedPreferences here. Any shared logic belongs in
        // automation/, intents/, or providers/ — never inlined into a tool file.
        return runCatching {
            val intent = android.content.Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("Opened airplane mode settings")
        }.getOrElse { ToolResult.Failure("Couldn't open airplane mode settings") }
    }
}
```

**Rules of thumb:**
- Coerce args via the existing `argString` / `argInt` / `argBool` helpers (see other tools). Don't trust `Map<String, Any?>` shapes.
- Never log args, recipient names, message bodies, URLs, or anything the model produced. Privacy invariant.
- Return a `ToolResult.Success(userFacingMessage)` or `ToolResult.Failure(userFacingMessage)`. The string is shown in the chat action card.
- Echo the user's own input in error messages if useful ("No contact named 'X'") — that's not a privacy violation, the user typed it.

---

## Step 2 — Register it

Add the tool to `ToolRegistry.kt`:

```kotlin
// app/src/main/kotlin/com/mitra/tools/ToolRegistry.kt
object ToolRegistry {
    fun all(context: Context): List<Tool> =
        listOf(
            ToggleFlashlight(context),
            // …
            ToggleAirplaneMode(context),  // ← add here
        )
}
```

---

## Step 3 — Declare it to the model

The brain doesn't see `Tool.kt` files. It sees the `@Tool`-annotated method on `PhoneTools` inside [`inference/LiteRtBrain.kt`](../../app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt). LiteRT-LM auto-generates the JSON schema from these annotations.

```kotlin
// Inside class PhoneTools
@Tool(
    description =
        "Use this ONLY when the user wants to turn airplane mode on or off, or open " +
        "airplane-mode settings (e.g. 'turn on airplane mode', 'flight mode off', 'open " +
        "airplane settings'). Do NOT use this for other connectivity (wifi, bluetooth, " +
        "mobile data) — each has its own tool.",
)
fun toggle_airplane_mode(): Map<String, Any> = mapOf("ok" to true)
```

**Rules of thumb for the description:**
- Start with `Use this ONLY when…` and follow with `Do NOT use this for…`. Tightening the boundaries is the single biggest lever on tool-calling accuracy (the brain is zero-shot).
- Give 3–5 example phrasings the user might say. The model latches onto these.
- Keep arg parameters minimal. Each arg should have its own short `@ToolParam(description = …)` explaining what to put in it. Field-level descriptions matter; the LLM reads them too.
- Avoid arguments with overlapping meanings. If two args could plausibly carry the same value, the model will guess wrong.

---

## Step 4 — Add an IntentParser fallback

The `IntentParser` in [`agent/Router.kt`](../../app/src/main/kotlin/com/mitra/agent/Router.kt) is the deterministic shortcut for the 25-ish most common commands. If your tool has obvious keyword phrasings, add them. The IntentParser bypasses the brain entirely for matched utterances → instant response, no model latency, also works when the model file is missing.

```kotlin
// in agent/Router.kt IntentParser.route()
Regex("""(?:turn|switch)\s+(?:on|off)\s+(?:airplane|flight)\s+mode""")
    .find(t)?.let { return ToolCall("toggle_airplane_mode", emptyMap()) }

Regex("""(?:airplane|flight)\s+mode\s+(?:on|off)""")
    .find(t)?.let { return ToolCall("toggle_airplane_mode", emptyMap()) }
```

**Ordering matters.** If your regex would match phrases another tool already handles, place yours either above or below the conflicting entry intentionally and add a comment explaining why. See how `query_contacts` sits above `open_settings` in the file, with a "so contact phrasings don't get swallowed" comment.

---

## Step 5 — (If needed) Wire the permission

Most existing tools don't need this step. If yours needs `SEND_SMS`, `CALL_PHONE`, `READ_CONTACTS`, etc.:

1. **AndroidManifest:** add `<uses-permission android:name="android.permission.YOUR_PERMISSION" />`.
2. **permissions/PermissionState.kt:** add a new entry to the `Permission` enum, a `is<Yours>Granted(context)` accessor, extend `snapshot()`, and map `runtimePermission(p)` for runtime permissions. Use the `BLUETOOTH_CONNECT` row as the cleanest reference shape.
3. **ui/PermissionsScreen.kt + ui/SettingsScreen.kt:** add an icon + title + why-needed copy. Follow [`docs/design/voice.md`](../design/voice.md): no begging, no exclamations, name what fails without it.
4. **Your tool's `execute()`:** check the permission first. If missing, bounce to `ACTION_APPLICATION_DETAILS_SETTINGS` and return `ToolResult.Failure("Grant Mitra <Permission> on the page I just opened, then ask again")`. See `SetBluetooth.bounceToAppPermissions` for the reference shape.

---

## Step 6 — Write the tests

The project's convention is **no per-tool unit tests**. Instead, the IntentParser is tested and the action dispatch is exercised by `AgentRuntimeTest`. So your contribution is:

**`app/src/test/kotlin/com/mitra/agent/IntentParserTest.kt`** — add three cases minimum:

```kotlin
@Test fun `parses turn on airplane mode`() {
    assertEquals(
        ToolCall("toggle_airplane_mode", emptyMap()),
        IntentParser().route("turn on airplane mode"),
    )
}

@Test fun `parses flight mode off (terse variant)`() {
    assertEquals(
        ToolCall("toggle_airplane_mode", emptyMap()),
        IntentParser().route("flight mode off"),
    )
}

@Test fun `does not match unrelated open settings prompt`() {
    assertNull(IntentParser().route("open the airplane settings page now please"))
    // Or, if the phrasing SHOULD match, assert the expected ToolCall and explain why.
}
```

If your tool's `SideEffect` is `Irreversible`, **you MUST also add its name to `GateCoverageTest.irreversibleToolNames`** in `app/src/test/kotlin/com/mitra/safety/GateCoverageTest.kt`. The drift-catcher in that test will otherwise fail the build with a precise diff telling you exactly what to add. The test then exercises (a) the gate fires before any dispatch and (b) Cancel aborts dispatch — both for free, no extra code from you.

---

## Step 7 — Test on the phone

```bash
./gradlew :app:installDebug
```

Open the app. Type your trigger phrase ("turn on airplane mode"). You should see:
1. An action card render in chat with your tool's name.
2. If `SideEffect.Irreversible`, a confirmation modal.
3. The tool fires and the result appears in chat.
4. The Activity screen (Settings → "View recent actions") shows a new audit-log entry with your tool's name, side-effect class, and outcome. No content — that's the privacy invariant working.

If the brain ignored your tool and called nothing, the description in step 3 is too vague. Tighten the `Use this ONLY when…` boundaries.

If the brain called your tool with the wrong args, the `@ToolParam` descriptions are too vague. Spell out exactly what string belongs in each arg.

---

## Step 8 — Update the docs

Per the keep-docs-honest rule in [CLAUDE.md](../../CLAUDE.md):

- **plan.md** — tick the `- [ ]` box for your tool. Add a short trailing note (`shipped YYYY-MM-DD`).
- **PRD.md capability tiers** — if your tool changes what the user-visible capability tier says, update the row.
- **ARCHITECTURE.md** — if you added a new pattern (e.g. a new content-provider wrapper, a new automation backend), update the matching section.
- **docs/design/voice.md** — if your tool surfaces user-facing copy, register the success / failure / permission-needed strings in the right section.

---

## Step 9 — Open the PR

Branch name: `feat/toggle-airplane-mode`. PR title: `feat(tools): add toggle_airplane_mode`. Apply the `new-tool` label.

PR description (template):

```markdown
## What
Adds the `toggle_airplane_mode` tool. Reversible — opens system airplane-mode settings panel.

## Why
plan.md M1 hardware row had an unchecked entry. Bilingual users (Telugu/Tamil) asked for it
in issue #NNN.

## Privacy invariants touched
- None. No new network paths. No new logging. No `Log.*` calls on user content.

## Tests
- 4 new IntentParserTest cases.
- GateCoverageTest unaffected (this tool is Reversible, not Irreversible).
- ./gradlew :app:test passes locally.

## On-device verification
Tested on <device model>, Android <version>. Phrases tested:
- "turn on airplane mode" → opens settings panel
- "flight mode off" → opens settings panel
- "open the airplane settings" → opens settings panel
```

Wait for review. Address feedback. Merge.

---

## Common gotchas

| Symptom | Cause | Fix |
|---|---|---|
| Model never calls your tool | Tool description is too vague or overlaps with another | Tighten with `Use this ONLY when…` + 3–5 examples + `Do NOT use for…` |
| Model calls your tool with wrong args | `@ToolParam` descriptions are vague | Spell out exact arg semantics |
| IntentParser test passes, model still misses it | The brain has its own internal phrasing biases — only the description in `@Tool` matters to it | Refine the description, not the regex |
| GateCoverageTest fails after your PR | You added an Irreversible tool without updating `irreversibleToolNames` | Add your tool's name to the set in `GateCoverageTest.kt` |
| `AuditLogTest` "Entry data class only has whitelisted fields" fails | You added a field to `AuditLog.Entry` that could carry user content | Don't. The whitelist exists to prevent exactly that. Find another way. |
| `./gradlew :app:test` works locally but CI fails | Toolchain drift | Make sure your local JDK + Android SDK match `.tool-versions` and `app/build.gradle.kts` |

---

## Reach for help

If you get stuck for more than 30 minutes, open a GitHub issue with the failing command and its output. We'd rather unblock you than have you struggle in silence. This file exists so contributors can ship unaided — when it doesn't work, the file (not you) is broken.
