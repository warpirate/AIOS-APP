# query_contacts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a single read-only Tool that looks up phone numbers by contact name via ContactsContract, wired through the existing permission + Tool + IntentParser + eval-gate plumbing.

**Architecture:** Standalone `Tool` with `SideEffect.None`. `QueryContacts.kt` performs a three-tier fuzzy match (exact -> starts-with -> contains, capped at 5 total) against `ContactsContract.Contacts`, then resolves all phones for matched contacts via `ContactsContract.CommonDataKinds.Phone`, returns a chat-formatted string. New runtime permission `READ_CONTACTS` added to the existing `Permission` enum + Settings -> Access row (not PermissionsScreen — that surface requires per-permission video assets and Contacts is non-critical to first-run; the tool itself bounces to app-permissions on missing-perm). IntentParser gets a regex fallback. Eval gate covers regression.

**Tech Stack:** Kotlin, Android `ContactsContract`, JUnit 4, existing Mitra `Tool` / `Router` / `Permissions` / `AgentRuntime` plumbing. No new dependencies.

**Reference spec:** [docs/superpowers/specs/2026-06-11-query-contacts-design.md](../specs/2026-06-11-query-contacts-design.md)

---

## Task 1: AndroidManifest declares READ_CONTACTS

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the permission declaration**

Open `app/src/main/AndroidManifest.xml`. Locate the existing block of `<uses-permission ... />` lines (look for `BLUETOOTH_CONNECT` to find them). Add this line in alphabetical order with the surrounding permissions:

```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

- [ ] **Step 2: Verify the project still builds**

```
./gradlew :app:processDebugMainManifest --no-daemon
```

Expected: BUILD SUCCESSFUL. No new warnings about the permission.

- [ ] **Step 3: Commit**

```
git add app/src/main/AndroidManifest.xml
git commit -m "feat(manifest): declare READ_CONTACTS for query_contacts tool"
```

---

## Task 2: Permission enum + state-checking + runtime mapping

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/permissions/PermissionState.kt`

- [ ] **Step 1: Add READ_CONTACTS to the Permission enum**

In `PermissionState.kt`, the `enum class Permission(val key: String)` block currently ends with `BLUETOOTH_CONNECT("bluetooth_connect")`. Add a new variant after it:

```kotlin
enum class Permission(
    val key: String,
) {
    WRITE_SETTINGS("write_settings"),
    NOTIFICATION_POLICY("notification_policy"),
    BLUETOOTH_CONNECT("bluetooth_connect"),
    READ_CONTACTS("read_contacts"),
}
```

- [ ] **Step 2: Add isReadContactsGranted() helper**

At the bottom of the `Permissions` object (after `isBluetoothConnectGranted`), add:

```kotlin
private fun isReadContactsGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
```

Plus expose a public version for callers (`QueryContacts.kt` will use this):

```kotlin
fun hasReadContacts(context: Context): Boolean = isReadContactsGranted(context)
```

Place `hasReadContacts` just before the `private fun launchSpecial` line so the public API stays grouped at the top of `Permissions`.

- [ ] **Step 3: Extend snapshot() to include the new status**

Add a new `PermissionStatus(Permission.READ_CONTACTS, isReadContactsGranted(context))` line inside the `listOf(...)` in `snapshot()`. The result should look like:

```kotlin
fun snapshot(context: Context): PermissionSnapshot =
    PermissionSnapshot(
        statuses =
            listOf(
                PermissionStatus(Permission.WRITE_SETTINGS, isWriteSettingsGranted(context)),
                PermissionStatus(Permission.NOTIFICATION_POLICY, isNotificationPolicyGranted(context)),
                PermissionStatus(Permission.BLUETOOTH_CONNECT, isBluetoothConnectGranted(context)),
                PermissionStatus(Permission.READ_CONTACTS, isReadContactsGranted(context)),
            ),
    )
```

- [ ] **Step 4: Extend runtimePermission() to route READ_CONTACTS**

Update the `when` block in `runtimePermission`:

```kotlin
fun runtimePermission(permission: Permission): String? =
    when (permission) {
        Permission.BLUETOOTH_CONNECT -> Manifest.permission.BLUETOOTH_CONNECT
        Permission.READ_CONTACTS -> Manifest.permission.READ_CONTACTS
        else -> null
    }
```

- [ ] **Step 5: Extend launchGrant() with a no-op branch**

`READ_CONTACTS` is a runtime permission requested via ActivityResult in the calling Composable. Mirror the existing `BLUETOOTH_CONNECT` branch (which is `Unit`):

```kotlin
fun launchGrant(context: Context, permission: Permission) {
    when (permission) {
        Permission.WRITE_SETTINGS ->
            launchSpecial(
                context,
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}")),
            )
        Permission.NOTIFICATION_POLICY ->
            launchSpecial(
                context,
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            )
        Permission.BLUETOOTH_CONNECT -> Unit
        Permission.READ_CONTACTS -> Unit
    }
}
```

- [ ] **Step 6: Verify compile**

```
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```
git add app/src/main/kotlin/com/mitra/permissions/PermissionState.kt
git commit -m "feat(permissions): READ_CONTACTS runtime permission + state + runtime mapping"
```

---

## Task 3: Settings -> Access row for Contacts + PermissionsScreen exhaustive coverage

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/ui/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/mitra/ui/PermissionsScreen.kt`

PermissionsScreen onboarding flow stays effectively unchanged — its per-permission video preview requires raw resources we don't have for Contacts, and Contacts is not a first-run requirement. Users grant proactively via Settings or reactively via the bounce-to-app-permissions on first `query_contacts` call. We still need to extend the exhaustive `when` branches in PermissionsScreen.kt so the file compiles after the enum grows.

- [ ] **Step 1: Add the Contacts icon import to SettingsScreen.kt**

In `SettingsScreen.kt`, find the icon imports near the top (look for `import androidx.compose.material.icons.filled.Bluetooth`). Add:

```kotlin
import androidx.compose.material.icons.filled.Contacts
```

Keep alphabetical order with adjacent imports.

- [ ] **Step 2: Wire the icon for READ_CONTACTS in SettingsScreen.kt**

Find `iconFor(p: Permission): ImageVector` near the bottom of the file. Add the new branch:

```kotlin
private fun iconFor(p: Permission): ImageVector =
    when (p) {
        Permission.WRITE_SETTINGS -> Icons.Filled.BrightnessMedium
        Permission.NOTIFICATION_POLICY -> Icons.Filled.NotificationsOff
        Permission.BLUETOOTH_CONNECT -> Icons.Filled.Bluetooth
        Permission.READ_CONTACTS -> Icons.Filled.Contacts
    }
```

- [ ] **Step 3: Wire the title in SettingsScreen.kt**

Find `titleFor(p: Permission): String`:

```kotlin
private fun titleFor(p: Permission): String =
    when (p) {
        Permission.WRITE_SETTINGS -> "System settings"
        Permission.NOTIFICATION_POLICY -> "Do Not Disturb"
        Permission.BLUETOOTH_CONNECT -> "Bluetooth"
        Permission.READ_CONTACTS -> "Contacts"
    }
```

- [ ] **Step 4: Wire the why-copy in SettingsScreen.kt**

Find `whyFor(p: Permission): String`:

```kotlin
private fun whyFor(p: Permission): String =
    when (p) {
        Permission.WRITE_SETTINGS -> "Brightness, auto-rotate, screen timeout"
        Permission.NOTIFICATION_POLICY -> "Turn Do Not Disturb on or off, silent mode"
        Permission.BLUETOOTH_CONNECT -> "Switch Bluetooth on and off"
        Permission.READ_CONTACTS -> "Look up phone numbers by name"
    }
```

- [ ] **Step 5: Add the Contacts icon import to PermissionsScreen.kt**

Same import line, keep alphabetical:

```kotlin
import androidx.compose.material.icons.filled.Contacts
```

- [ ] **Step 6: Extend PermissionsScreen.kt iconFor / titleFor / whyFor**

Locate `iconFor` in PermissionsScreen.kt (around line 230):

```kotlin
private fun iconFor(p: Permission): ImageVector =
    when (p) {
        Permission.WRITE_SETTINGS -> Icons.Filled.BrightnessMedium
        Permission.NOTIFICATION_POLICY -> Icons.Filled.NotificationsOff
        Permission.BLUETOOTH_CONNECT -> Icons.Filled.Bluetooth
        Permission.READ_CONTACTS -> Icons.Filled.Contacts
    }
```

Locate `titleFor`:

```kotlin
private fun titleFor(p: Permission): String =
    when (p) {
        Permission.WRITE_SETTINGS -> "Change system settings"
        Permission.NOTIFICATION_POLICY -> "Control Do Not Disturb"
        Permission.BLUETOOTH_CONNECT -> "Switch Bluetooth on and off"
        Permission.READ_CONTACTS -> "Find contacts"
    }
```

Locate `whyFor`:

```kotlin
private fun whyFor(p: Permission): String =
    when (p) {
        Permission.WRITE_SETTINGS ->
            "Mitra adjusts brightness, auto-rotate, and screen timeout when you ask."
        Permission.NOTIFICATION_POLICY ->
            "Mitra turns Do Not Disturb on or off, and switches the ringer to silent."
        Permission.BLUETOOTH_CONNECT ->
            "Mitra switches Bluetooth on and off directly. Without this, Mitra opens the Bluetooth page instead."
        Permission.READ_CONTACTS ->
            "Mitra looks up phone numbers when you ask. Without this, name lookups won't work."
    }
```

- [ ] **Step 7: Extend PermissionPreview() with a no-preview branch**

Locate `PermissionPreview(perm: Permission)` further down (around line 255). It has another `when` on `Permission` that maps to a raw video resource. Add an early-return branch for READ_CONTACTS so the onboarding flow renders nothing for it:

```kotlin
@Composable
private fun PermissionPreview(perm: Permission) {
    val resId =
        when (perm) {
            Permission.WRITE_SETTINGS -> com.mitra.R.raw.perm_settings
            Permission.NOTIFICATION_POLICY -> com.mitra.R.raw.perm_dnd
            Permission.BLUETOOTH_CONNECT -> com.mitra.R.raw.perm_bluetooth
            Permission.READ_CONTACTS -> return  // No preview video; tool grants reactively when first used.
        }
    // ... existing Surface { Box { LoopingVideoView ... } } block stays unchanged ...
}
```

- [ ] **Step 8: Verify compile**

```
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```
git add app/src/main/kotlin/com/mitra/ui/SettingsScreen.kt app/src/main/kotlin/com/mitra/ui/PermissionsScreen.kt
git commit -m "feat(ui): Contacts row in Settings + exhaustive Permission when in PermissionsScreen"
```

---

## Task 4: QueryContacts tool + LiteRtBrain declaration + ToolRegistry

**Files:**
- Create: `app/src/main/kotlin/com/mitra/tools/QueryContacts.kt`
- Modify: `app/src/main/kotlin/com/mitra/tools/ToolRegistry.kt`
- Modify: `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt`

- [ ] **Step 1: Create QueryContacts.kt with the full implementation**

Write `app/src/main/kotlin/com/mitra/tools/QueryContacts.kt`:

```kotlin
package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.mitra.permissions.Permissions

/**
 * Looks up phone numbers by contact display-name.
 *
 * SideEffect.None - pure read. Three-tier fuzzy match against
 * [ContactsContract.Contacts.DISPLAY_NAME] (exact -> starts-with -> contains), capped at 5 results
 * total. For each matched contact, all phone numbers are read from
 * [ContactsContract.CommonDataKinds.Phone] with type labels.
 *
 * Privacy: result string contains contact names + numbers, returned only to the chat UI (volatile
 * session memory). [com.mitra.safety.AuditLog] receives tool-name + outcome only - never the
 * search term, never any matched name or number. ContentResolver projections are explicit, never
 * null.
 */
class QueryContacts(
    private val context: Context,
) : Tool {
    override val name = "query_contacts"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val name = argString(args["name"]) ?: return ToolResult.Failure("I need a name to search for")

        if (!Permissions.hasReadContacts(context)) {
            bounceToAppPermissions()
            return ToolResult.Failure(
                "Grant Mitra Contacts permission on the page I just opened, then ask again",
            )
        }

        return try {
            val matches = findContacts(name)
            if (matches.isEmpty()) {
                ToolResult.Failure("No contact named '$name'")
            } else {
                val withPhones = matches.map { it to phonesFor(it.id) }.filter { it.second.isNotEmpty() }
                if (withPhones.isEmpty()) {
                    ToolResult.Failure("Found '$name' but no phone numbers stored")
                } else {
                    ToolResult.Success(formatResult(name, matches.size, withPhones))
                }
            }
        } catch (_: SecurityException) {
            ToolResult.Failure("Contacts permission missing - grant it and ask again")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't read contacts")
        }
    }

    private data class ContactRow(val id: Long, val displayName: String)

    private data class PhoneRow(val number: String, val typeLabel: String)

    private fun findContacts(query: String): List<ContactRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        // Tier 1: exact (case-insensitive).
        var rows = queryContacts(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) = LOWER(?)",
            arrayOf(q),
        )
        if (rows.isNotEmpty()) return rows.take(MAX_RESULTS)

        // Tier 2: starts-with.
        rows = queryContacts(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE LOWER(?) || '%'",
            arrayOf(q),
        )
        if (rows.isNotEmpty()) return rows.take(MAX_RESULTS)

        // Tier 3: contains.
        rows = queryContacts(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE '%' || LOWER(?) || '%'",
            arrayOf(q),
        )
        return rows.take(MAX_RESULTS)
    }

    private fun queryContacts(selection: String, selectionArgs: Array<String>): List<ContactRow> {
        val projection =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            )
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        val rows = mutableListOf<ContactRow>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "$selection AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            selectionArgs,
            sortOrder,
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (c.moveToNext() && rows.size < MAX_RESULTS) {
                val displayName = c.getString(nameIdx) ?: continue
                rows += ContactRow(c.getLong(idIdx), displayName)
            }
        }
        return rows
    }

    private fun phonesFor(contactId: Long): List<PhoneRow> {
        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            )
        val phones = mutableListOf<PhoneRow>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            while (c.moveToNext()) {
                val number = c.getString(numIdx)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val type = c.getInt(typeIdx)
                val customLabel = c.getString(labelIdx)?.trim().orEmpty()
                phones += PhoneRow(number, labelFor(type, customLabel))
            }
        }
        return phones
    }

    private fun labelFor(type: Int, customLabel: String): String =
        when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel.lowercase().ifBlank { "other" }
            else -> "other"
        }

    private fun formatResult(
        query: String,
        totalMatched: Int,
        contacts: List<Pair<ContactRow, List<PhoneRow>>>,
    ): String {
        if (contacts.size == 1) {
            val (contact, phones) = contacts[0]
            return "Found ${contact.displayName} - " + phones.joinToString(", ") { "${it.typeLabel} ${it.number}" }
        }
        val header =
            if (totalMatched > MAX_RESULTS) {
                "Found $MAX_RESULTS of $totalMatched contacts for '$query':"
            } else {
                "Found ${contacts.size} contacts for '$query':"
            }
        val body =
            contacts.joinToString("\n") { (c, phones) ->
                "- ${c.displayName} - " + phones.joinToString(", ") { "${it.typeLabel} ${it.number}" }
            }
        return "$header\n$body"
    }

    private fun bounceToAppPermissions() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        private const val MAX_RESULTS = 5
    }
}
```

Notes for the engineer: `MAX_RESULTS = 5` matches the spec. `findContacts` filters server-side on `HAS_PHONE_NUMBER = 1` to skip phoneless contacts at the source — saves a follow-up filter pass and reduces cursor churn.

- [ ] **Step 2: Register in ToolRegistry**

Open `app/src/main/kotlin/com/mitra/tools/ToolRegistry.kt`. Add `QueryContacts(context),` to the `listOf` call — place it after `OpenSettings(context)` so the "find / open" cluster stays grouped:

```kotlin
object ToolRegistry {
    fun all(context: Context): List<Tool> =
        listOf(
            ToggleFlashlight(context),
            SetAlarm(context),
            StartTimer(context),
            OpenUrl(context),
            OpenApp(context),
            OpenSettings(context),
            QueryContacts(context),
            SetMediaVolume(context),
            SetBrightness(context),
            SetDnd(context),
            SetRingerMode(context),
            SetAutoRotate(context),
            SetScreenTimeout(context),
            SetBluetooth(context),
        )
}
```

- [ ] **Step 3: Declare the @Tool annotation in LiteRtBrain.kt**

Open `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt`. Find the `PhoneTools` class (the section with `open_app`, `open_url`, etc.). Add this method next to `open_app` to keep "find / open" tools grouped:

```kotlin
@Tool(
    description = "Use this ONLY when the user wants to find a person's phone number, ask whose number something is, or look up a contact by name (e.g. 'what's mom's number', 'find priya', 'raj's phone'). Do NOT use this for opening the Contacts app, dialling, sending a message, or general chat - it only reads the address book.",
)
fun query_contacts(
    @ToolParam(description = "the contact's name or partial name to search for") name: String,
): Map<String, Any> = mapOf("ok" to true)
```

- [ ] **Step 4: Verify compile**

```
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/mitra/tools/QueryContacts.kt app/src/main/kotlin/com/mitra/tools/ToolRegistry.kt app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt
git commit -m "feat(tools): query_contacts - ContactsContract lookup with all-phones format"
```

---

## Task 5: IntentParser fallback (TDD)

**Files:**
- Modify: `app/src/test/kotlin/com/mitra/agent/IntentParserTest.kt`
- Modify: `app/src/main/kotlin/com/mitra/agent/Router.kt`

- [ ] **Step 1: Write the three failing tests first**

Open `app/src/test/kotlin/com/mitra/agent/IntentParserTest.kt`. Add these three tests at the end of the class, before the closing brace:

```kotlin
@Test
fun `whats moms number routes to query_contacts`() {
    val c = parser.route("what's mom's number")
    assertEquals("query_contacts", c?.name)
    assertEquals("mom", c?.args?.get("name"))
}

@Test
fun `find priya routes to query_contacts`() {
    val c = parser.route("find priya")
    assertEquals("query_contacts", c?.name)
    assertEquals("priya", c?.args?.get("name"))
}

@Test
fun `contact raj routes to query_contacts`() {
    val c = parser.route("contact raj")
    assertEquals("query_contacts", c?.name)
    assertEquals("raj", c?.args?.get("name"))
}
```

- [ ] **Step 2: Verify the new tests fail**

```
./gradlew :app:testDebugUnitTest --tests "com.mitra.agent.IntentParserTest" --no-daemon
```

Expected: 3 test failures - `expected:<query_contacts> but was:<null>` (or routes wrong because no fallback yet).

- [ ] **Step 3: Add the regex fallbacks to Router.kt**

Open `app/src/main/kotlin/com/mitra/agent/Router.kt`. The `IntentParser.route(input)` body has a sequence of regex blocks. The two new blocks must run BEFORE the panel resolver (otherwise `find priya` could be swallowed as a settings query). Place them right after the existing `Regex("""\b(?:open|go to|launch|visit|browse)\s+(\S+\.\S+)""")` block:

```kotlin
// Contact lookup - possessive form: "what's mom's number", "find priya's phone", "raj's contact"
Regex("""(?:what'?s|find|show|get|where'?s)\s+(.+?)'?s\s+(?:number|phone|contact|details)""")
    .find(t)?.let {
        return ToolCall("query_contacts", mapOf("name" to it.groupValues[1].trim()))
    }

// Contact lookup - bare-noun form: "find priya", "contact raj", "number for amma"
Regex("""(?:find|contact|number for)\s+(.+)""").find(t)?.let {
    return ToolCall("query_contacts", mapOf("name" to it.groupValues[1].trim()))
}
```

Important: place the **possessive form first** so `"find priya's contact"` resolves with `name = "priya"`, not `name = "priya's contact"`.

- [ ] **Step 4: Verify the new tests pass**

```
./gradlew :app:testDebugUnitTest --tests "com.mitra.agent.IntentParserTest" --no-daemon
```

Expected: ALL passing. Watch for regressions in the existing tests too - the new bare-noun `find` regex could over-trigger on, say, `"find the brightness slider"`. If any old test fails, narrow the bare-noun regex (e.g. constrain the captured group to `\w+` only).

- [ ] **Step 5: Run the full eval suite to catch hidden regressions**

```
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: ALL passing. The eval gate in `EvalSmokeTest` should keep its existing pass threshold.

- [ ] **Step 6: Commit**

```
git add app/src/test/kotlin/com/mitra/agent/IntentParserTest.kt app/src/main/kotlin/com/mitra/agent/Router.kt
git commit -m "feat(agent): IntentParser fallback for contact lookup"
```

---

## Task 6: Eval set + plan.md tick

**Files:**
- Modify: `app/src/test/resources/eval/commands.yaml`
- Modify: `plan.md`

- [ ] **Step 1: Append 4 contact-query commands to the eval YAML**

Open `app/src/test/resources/eval/commands.yaml`. Append to the end (be mindful of the existing newline trailing format):

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

- [ ] **Step 2: Update the eval YAML header comment**

The file starts with comments like `# 50 commands: 6 chit-chat negatives + 44 tool cases covering all 13 V1 tools.` Change the counts:

```yaml
# Mitra eval starter set - Phase 0
# 54 commands: 6 chit-chat negatives + 48 tool cases covering all 14 V1 tools.
# Format: id, utterance, gold (ordered list of tool calls), language, optional multi_step flag.
```

- [ ] **Step 3: Run the eval gate**

```
./gradlew :app:testDebugUnitTest --tests "*EvalSmokeTest*" --no-daemon
```

Expected: PASS. If the new commands fail, revisit the IntentParser regex from Task 5 - the gold expects `args.name` to equal `"mom"`, `"priya"`, `"raj"`, `"dad"` exactly.

- [ ] **Step 4: Tick plan.md**

Open `plan.md`. Two changes:

1. In the M1 section, find the `query_contacts` line under **Contacts:** and mark it done:

```markdown
- [x] `query_contacts(name)` - resolves names and relations to phone numbers
```

2. In the **Right-now tasks** section, strike-through task #8 since it's complete:

```markdown
8. ~~**Add `query_contacts`** (ContentResolver, read-only) - unblocks `make_call` / `send_sms` arg resolution.~~ Shipped 2026-06-11.
```

- [ ] **Step 5: Commit**

```
git add app/src/test/resources/eval/commands.yaml plan.md
git commit -m "test(eval): 4 query_contacts commands + plan.md tick"
```

---

## Task 7: Push

- [ ] **Step 1: Verify the working tree is clean**

```
git status -s
```

Expected: empty output.

- [ ] **Step 2: Inspect the commit list**

```
git log origin/main..HEAD --oneline
```

Expected: 6 commits (Tasks 1-6).

- [ ] **Step 3: Push to origin/main**

```
git push origin main
```

Expected: clean push, no rejections.

---

## Out of scope reminders (do NOT add to this plan)

- `make_call` and `send_sms` (separate M1 tasks, separate spec).
- ContactResolver helper extraction (YAGNI - wait for the second consumer).
- Robolectric or per-tool unit test for QueryContacts (project convention is eval + IntentParser tests).
- Number normalisation (raw stored format is correct for V1 - see spec).
- Multi-turn tool loop / ContextStore chaining (deferred to M5/M6).
- READ_CONTACTS video preview for PermissionsScreen (Contacts is non-critical to first-run; reactive grant is the chosen path).

## Manual on-device verification checklist (post-push, before declaring shipped)

After CI passes, on a real device with at least one contact saved:

1. Open Mitra -> Settings -> Access -> tap "Contacts" -> grant permission via system dialog.
2. Return to chat -> "what's <name>'s number" where `<name>` matches a contact -> expect `Found <name> - mobile +91 XXXXX` style reply.
3. Revoke READ_CONTACTS in system settings -> return to Mitra -> repeat the query -> expect bounce to app-permissions page + the grant-then-retry failure message.
4. Try a no-match name -> expect `No contact named 'X'`.
5. Spot-check the AuditLog (debug log inspection or future debug-screen) - entries should record only the tool name and outcome, NEVER the searched name or any matched contact data.
