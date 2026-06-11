# query_contacts — Tool Design

**Status:** Approved 2026-06-11
**Owner:** warpirate
**Milestone:** M1 (V1 tool surface)
**Plan task:** plan.md right-now task #8 — unblocks future `make_call` / `send_sms` arg resolution

## Problem

Mitra has no way to answer "what's Mom's number?" or "find Priya's contact." Today the user has to leave the app, open Contacts, search manually, then come back. That breaks the on-device assistant promise for one of the most common voice-agent intents.

The deeper reason this matters: `make_call` and `send_sms` (later in M1) need a way to resolve a human name to a phone number. Without `query_contacts`, those tools would have to bake contact resolution into themselves, coupling telephony to ContactsContract. A standalone lookup tool keeps that boundary clean and gives users a useful capability in its own right.

## Scope

In:
- One read-only tool, `query_contacts`, that takes a name and returns matched contacts with their phone numbers
- New runtime permission, `READ_CONTACTS`, wired through the existing `Permission` enum + Permissions / Settings screens
- IntentParser fallback regex so the deterministic parser path catches the common phrasings
- Eval set additions to gate regressions

Out (deliberately deferred):
- Chaining query_contacts → make_call / send_sms inside a single turn (would require AgentRuntime multi-step tool loop + ContextStore writes; tracked separately under M5/M6)
- Number normalisation to E.164 (Indian numbers in contacts are inconsistent; respecting stored format is safer for downstream dial intents)
- Searching nicknames, notes, or email fields (display_name only for V1)
- Per-tool Robolectric unit test (project convention is eval-set + IntentParser tests; no per-tool unit tests exist today)

## Architecture

Standalone Tool. `SideEffect.None` so it bypasses `ConfirmationGate`. Routes through the existing `ManagerApiBackend` like every other V1 tool.

```
LLM emits query_contacts(name="mom")
        ↓
ToolRegistry → ManagerApiBackend → QueryContacts.execute(args)
        ↓
READ_CONTACTS check → on miss, bounce to settings + Failure
        ↓
ContactsContract.Contacts query (tier 1 exact, else tier 2 prefix, else tier 3 contains)
        ↓
ContactsContract.CommonDataKinds.Phone query (IN-list across matched contact IDs)
        ↓
Format result (per match: "DisplayName — type1 number1, type2 number2")
        ↓
ToolResult.Success("Found Mom — mobile +91 98XXX, work +91 80XXX")
        ↓
ChatScreen renders as action card detail
AuditLog records: name="query_contacts", sideEffect=None, ok=true
```

The single point of contact with `ContactsContract` is `QueryContacts.kt`. Other tools never reach into the contacts provider — when `make_call` lands later, it will call into the same `ContactsContract` paths via a small `ContactResolver` helper extracted at that point (YAGNI for now).

## Tool contract

```kotlin
class QueryContacts(private val context: Context) : Tool {
    override val name = "query_contacts"
    override val sideEffect = SideEffect.None
    override fun execute(args: Map<String, Any?>): ToolResult
}
```

**Arg:** `name: String` — required, non-blank. Coerced via the existing `argString` helper. Blank → `Failure("I need a name to search for")`.

**Returns:** `ToolResult.Success(message)` on at least one match, `ToolResult.Failure(message)` otherwise. Messages are user-facing chat strings, never IDs.

## Match strategy

Three-tier fuzzy, case-insensitive against `ContactsContract.Contacts.DISPLAY_NAME`:

1. **Exact** — `LOWER(display_name) = LOWER(:q)`. If any matches → use these.
2. **Starts-with** — `LOWER(display_name) LIKE LOWER(:q) || '%'`. If any → use these.
3. **Contains** — `LOWER(display_name) LIKE '%' || LOWER(:q) || '%'`. If any → use these.
4. None → `Failure("No contact named 'X'")`.

Cap at **5 results total** (across whichever tier matched — not 5 per tier), ordered by `display_name ASC` for determinism. If the cap clips: `"Found 5 of 17 contacts matching 'j': …"`. This keeps the surface calm (approachability principle) and prevents accidental address-book dumps.

Implementation note: tier escalation happens in code, not in one combined SQL `OR`. Per tier, one ContentResolver query against `ContactsContract.Contacts` with a parameterised `selection` (SQL-injection-safe via `selectionArgs`). We do **not** read all contacts and filter in Kotlin — too slow on large address books. Once contacts are picked, a second ContentResolver query against `ContactsContract.CommonDataKinds.Phone` resolves each matched contact's phone numbers (`selection = "contact_id IN (?, ?, ...)"`, one parameterised IN-list call, not N queries).

## Result shape

All phones per matched contact, type-labeled, comma-joined.

Single match:
```
Found Mom — mobile +91 98XXXXXXXX, work +91 80XXXXXXXX
```

Multiple matches (bulleted):
```
Found 2 contacts for 'sharma':
• Raj Sharma — mobile +91 98…
• Priya Sharma — mobile +91 98…, home +91 80…
```

Phone format: raw as stored in `ContactsContract.CommonDataKinds.Phone.NUMBER`. No normalisation. Indian contacts often mix `+91`, leading `0`, and bare 10-digit forms; converting forces a guess that downstream dial intents (`ACTION_DIAL`, `Intent.ACTION_SENDTO`) handle natively.

Type labels (mapped from `Phone.TYPE`):
- `TYPE_MOBILE` → `mobile`
- `TYPE_HOME` → `home`
- `TYPE_WORK` → `work`
- `TYPE_MAIN` → `main`
- `TYPE_OTHER` → `other`
- `TYPE_CUSTOM` → the user's custom label from `Phone.LABEL` if non-blank, else `other`
- Anything else → `other`

If a matched contact has zero phone numbers, skip it. If all matched contacts have zero phones → `Failure("Found 'X' but no phone numbers stored")`.

## Permission flow

New permission: `android.permission.READ_CONTACTS` (runtime, dangerous, single permission — no group concerns).

Wiring (copies the BLUETOOTH_CONNECT shape that already works in this codebase):

1. **AndroidManifest.xml** — add `<uses-permission android:name="android.permission.READ_CONTACTS" />`.
2. **permissions/PermissionState.kt** —
   - Add `READ_CONTACTS("read_contacts")` to the `Permission` enum.
   - Add `isReadContactsGranted(context)` (delegates to `ContextCompat.checkSelfPermission`).
   - Extend `snapshot()` to include the new status.
   - Extend `runtimePermission(p)` so READ_CONTACTS returns `Manifest.permission.READ_CONTACTS` (standard runtime path, same as BLUETOOTH_CONNECT).
   - `launchGrant` for READ_CONTACTS falls through the runtime branch; no special case.
3. **ui/PermissionsScreen.kt** + **ui/SettingsScreen.kt** — add icon + title + why-copy:
   - Icon: `Icons.Filled.Contacts`
   - PermissionsScreen title: "Find contacts"
   - PermissionsScreen why: "Mitra looks up phone numbers when you ask. Without this, name lookups won't work."
   - SettingsScreen title: "Contacts"
   - SettingsScreen why: "Look up phone numbers by name"
4. **QueryContacts.execute** — checks `Permissions.isReadContactsGranted(context)` first. If missing:
   - Bounces to the app-permissions page via `ACTION_APPLICATION_DETAILS_SETTINGS` (same pattern as `SetBluetooth.bounceToAppPermissions`)
   - Returns `Failure("Grant Mitra Contacts permission on the page I just opened, then ask again")`

## Error handling

| Case | Result |
|---|---|
| Missing or blank `name` arg | `Failure("I need a name to search for")` |
| READ_CONTACTS denied at runtime | Bounce to app permissions + `Failure("Grant Mitra Contacts permission on the page I just opened, then ask again")` |
| `SecurityException` from ContentResolver despite permission | `Failure("Contacts permission missing — grant it and ask again")` |
| Provider returns null cursor | `Failure("Couldn't read contacts")` |
| Other exception | `Failure("Couldn't read contacts")` |
| No matches | `Failure("No contact named 'X'")` |
| Matches found, but every match has zero phones | `Failure("Found 'X' but no phone numbers stored")` |

`X` in the messages is the trimmed search term, echoed back verbatim — this is the user's own input, so echoing it isn't a privacy violation.

## Privacy invariants

Per CLAUDE.md privacy rules, enforced by hand-review (lint rules are still M0 TODO):

- **AuditLog** records `query_contacts` + `SideEffect.None` + `ok=true|false` only. **Never the searched name, never any matched name, never any number.** The existing `AuditLog.Entry` field whitelist test enforces this structurally.
- **No `Log.*` calls** anywhere in `QueryContacts.kt` carry user content. The file should not log at all in production paths.
- **ContentResolver projection** is explicit and minimal — no `null` projection that returns every column:
  - Contacts query: `Contacts._ID`, `Contacts.DISPLAY_NAME`
  - Phone query (per matched contact): `Phone.NUMBER`, `Phone.TYPE`, `Phone.LABEL`
- **No network.** Pure ContentResolver. Lint rule (when it lands) will catch any accidental `java.net.*` import.
- **No persistence.** Result message lives only in chat session memory. `UserPrefs.setLastUtterance` already caps at 240 chars; if a contact result is rebroadcast through the RepeatLastPill it stays bounded.

## IntentParser fallback

In `agent/Router.kt`, add **above** the panel resolver so contact phrasings don't get swallowed by `open_settings`:

```kotlin
// Contact lookup: "what's mom's number", "find priya's contact", "raj's phone"
Regex("""(?:what'?s|find|show|get|where'?s)\s+(.+?)'?s\s+(?:number|phone|contact|details)""")
    .find(t)?.let {
        return ToolCall("query_contacts", mapOf("name" to it.groupValues[1].trim()))
    }
```

Plus a bare-noun form: `"contact X"`, `"call mom's number"` (no `make_call` yet, so route to lookup):

```kotlin
Regex("""(?:contact|number for)\s+(.+)""").find(t)?.let {
    return ToolCall("query_contacts", mapOf("name" to it.groupValues[1].trim()))
}
```

Add 3 cases to `IntentParserTest.kt` covering these patterns.

## LLM `@Tool` description (LiteRtBrain.kt PhoneTools)

```kotlin
@Tool(description = "Use this ONLY when the user wants to find a person's phone number, ask whose number something is, or look up a contact by name (e.g. 'what's mom's number', 'find priya', 'raj's phone'). Do NOT use this for opening the Contacts app, dialling, sending a message, or general chat — it only reads the address book.")
fun query_contacts(
    @ToolParam(description = "the contact's name or partial name to search for") name: String,
): Map<String, Any> = mapOf("ok" to true)
```

The `Use this ONLY when` / `Do NOT use this for` phrasing matches the tightened descriptions landed in commit `efd7f68` and the `tool-calling-tutor` skill guidance.

## Eval set additions (commands.yaml)

Append 4 commands covering match strategies and a chit-chat negative:

```yaml
- id: "0051"
  utterance: "what's mom's number"
  gold:
    - tool: query_contacts
      args: { name: "mom" }
  language: en

- id: "0052"
  utterance: "find priya"
  gold:
    - tool: query_contacts
      args: { name: "priya" }
  language: en

- id: "0053"
  utterance: "show raj's contact"
  gold:
    - tool: query_contacts
      args: { name: "raj" }
  language: en

- id: "0054"
  utterance: "where's dad's phone"
  gold:
    - tool: query_contacts
      args: { name: "dad" }
  language: en
```

Update `commands.yaml` header comment from "13 V1 tools" to "14 V1 tools".

## Tests

Following project convention (no per-tool unit tests in the suite):

- **IntentParserTest.kt** — 3 new cases: `"what's mom's number"`, `"find priya"`, `"contact raj"`. Asserts parsed `ToolCall("query_contacts", {name: …})`.
- **commands.yaml** — 4 new eval entries (above).
- **AuditLogTest.kt** — no changes; existing field-whitelist test covers the privacy invariant.

No Robolectric / ContentResolver fake — the project doesn't ship Robolectric and adding it for one tool is over-engineering. The ContentResolver path is exercised in practice on device (M0 manual-test cadence) and via the eval gate on the IntentParser side.

## Files touched

| New | Modified |
|---|---|
| `app/src/main/kotlin/com/mitra/tools/QueryContacts.kt` | `app/src/main/AndroidManifest.xml` |
| | `app/src/main/kotlin/com/mitra/permissions/PermissionState.kt` |
| | `app/src/main/kotlin/com/mitra/ui/PermissionsScreen.kt` |
| | `app/src/main/kotlin/com/mitra/ui/SettingsScreen.kt` |
| | `app/src/main/kotlin/com/mitra/tools/ToolRegistry.kt` |
| | `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt` |
| | `app/src/main/kotlin/com/mitra/agent/Router.kt` |
| | `app/src/test/kotlin/com/mitra/agent/IntentParserTest.kt` |
| | `app/src/test/resources/eval/commands.yaml` |
| | `plan.md` (mark task #8 done, mark `query_contacts` checkbox in M1) |

## Commit plan

Four commits, leaves first → consumers last:

1. **`feat(permissions): READ_CONTACTS runtime permission + UI rows`** — manifest, PermissionState, PermissionsScreen, SettingsScreen. Plugin tier — no tool depends on it yet, but the wiring is reusable.
2. **`feat(tools): query_contacts — ContactsContract lookup with all-phones format`** — QueryContacts.kt, ToolRegistry registration, LiteRtBrain @Tool declaration.
3. **`feat(agent): IntentParser fallback for contact lookup`** — Router.kt regex + IntentParserTest cases.
4. **`test(eval): 4 contact-query commands + plan.md tick`** — commands.yaml additions + plan.md M1 checkbox + right-now task #8 cleared.

## Success criteria

- On a real device with READ_CONTACTS granted, `"what's mom's number"` returns a chat reply naming the contact and listing their phones with type labels.
- Eval gate passes the 4 new commands at the existing ≥80% threshold.
- IntentParserTest passes with the 3 new cases.
- AuditLog still passes the field-whitelist test (no schema drift).
- No new `Log.*`, `java.net.*`, or analytics imports introduced (visual review until lint rules land).

## Out of scope (call-outs for future PRs)

- `make_call` and `send_sms` will need their own resolver path; this tool deliberately does not chain to them. When those tools land, a small `ContactResolver` may be extracted from QueryContacts.kt and reused — that's a YAGNI deferral, not a missing piece.
- Wake-word / always-listening voice integration is M4.
- AccessibilityService-driven contact actions are V2 / M6.
