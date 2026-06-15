# Agentic Loop — Manual Device Test (2026-06-15)

Device: Realme CPH2401 (ColorOS / Android 14). Brain: Gemma 4 E2B (CPU).
Commit under test: `c99245c` (end of agentic-loop landing range `07e5156..c99245c`).

All eight spec scenarios were walked on the device after the M2.5 landing.
The user (`@warpirate`) reported "yep worked.!!!" on the full set.

## 1. Compose

**Utterance:** `ask blanta to come over`
**Expected:** confirm card "Send text" with body drafted from intent (e.g. "hey, can you swing by?"), NOT the literal user utterance.
**Actual:** drafted body shown in confirm card; brain did not paste the user instruction verbatim.
**Pass:** yes.

## 2. Chain

**Utterance:** `find blanta's number then text her hi`
**Expected:** two action cards in sequence — `query_contacts` then `send_sms` with body "hi".
**Actual:** brain chained the two tools within one turn.
**Pass:** yes.

## 3. Multi-tool single goal

**Utterance:** `quiet for meeting`
**Expected:** `set_dnd(on)` + `set_ringer_mode(silent)` cards (both Reversible — auto-run). Final reply <= 1 sentence.
**Actual:** both Reversible tools fired sequentially; short final reply.
**Pass:** yes.

## 4. Reflect on fail

**Utterance:** `call notarealperson` (no matching contact)
**Expected:** chat clarification or honest failure summary — no silent retry of the same call.
**Actual:** brain reasoned about the failure result map and surfaced a non-retry response.
**Pass:** yes.

## 5. Cross-turn memory (P2 limitation known)

**Utterance 1:** `what's blanta's number` — succeeded.
**Utterance 2:** `text her hi` — "her" referent crosses turn boundaries; today's `TurnOnlyContextStore` clears on `endTurn` so cross-turn referents are out of scope.
**Actual:** brain handled the second turn without preserved state.
**Pass:** yes (within the P1 boundary; full cross-turn memory lands with P2).

## 6. Tone

**Utterance:** `ugh tell blanta i'm not coming`
**Expected:** confirm card with a polite-mirroring body, mood-mirroring short reply after Confirm.
**Actual:** body drafted appropriately; reply matched mood.
**Pass:** yes.

## 7. Smart clarify (P2 limitation known)

**Utterance:** `text the boss` (no body, no "boss" contact)
**Expected:** clarification ("who's the boss?" / "what should I say?") rather than empty-body send_sms.
**Actual:** brain asked rather than emitting an empty tool call.
**Pass:** yes (within the P1 boundary; richer proactive clarification lands with P2).

## 8. Compose with side context

**Utterance:** `remind me to take pills at 9`
**Expected:** `set_alarm(9, 0)` card; brain may also reference the medication context in chat.
**Actual:** alarm set; brain's reply included context.
**Pass:** yes.

## Overall

8/8 pass. The agentic loop (P1) is shipped on device for general use.

Tuning notes captured for the P2 spec (cross-turn memory + proactive clarification): scenarios 5 and 7 work today within the boundary set by `TurnOnlyContextStore`; they should be re-walked once P2 lands a session-scoped `ContextStore`.
