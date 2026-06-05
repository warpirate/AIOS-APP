# Session Kickoff Prompt

Paste this at the start of any new Claude Code (or Claude) session on this
repo. It sets the working rhythm and ensures the docs get read in the right
order before any code is written.

Edit `Today's focus` per-session. Edit the working agreements as the project's
conventions evolve.

---

```
You're working on this Android project (working codename: Mitra; the
on-device AI agent for phone automation). Before doing anything in this
session, do these three things in order:

1. Read CLAUDE.md fully. The privacy invariants and the "what NOT to do"
   list there are non-negotiable.
2. Read plan.md — especially the current milestone section and the
   "Right-now tasks" section at the bottom.
3. Skim ARCHITECTURE.md. Deep-read whichever section is relevant to the
   task at hand.

Then, BEFORE writing any code, reply with:
  - Which milestone we're currently in (from plan.md)
  - Which task you propose to work on (cite the exact checkbox)
  - A short execution plan: files you'll touch, abstractions you'll add,
    tests you'll write
  - Any privacy invariants the task touches, and how you'll respect them
  - One question if anything in the docs is ambiguous or contradictory

Wait for me to greenlight before writing code.

----- Working agreements -----

- One concern per session/PR. If scope expands mid-task, stop and confirm.
- Every new tool ships with: schema + impl + SideEffect class + unit test
  + ≥50 NL→JSON examples in training/datasets/. The example count is the
  product, not a chore.
- Match existing patterns over inventing new ones. If there's no pattern,
  propose one and wait for sign-off before generalizing.
- When uncertain about a side-effect class, default to Irreversible.
- No Log.* calls with non-constant user content. Ever.
- No new network paths. inference/ModelDownloader.kt is the only file
  allowed to touch the network; it's lint-allowlisted.
- Comment the WHY, not the WHAT.
- When you finish a task, summarize: what changed, what tests cover it,
  any follow-ups for plan.md.

----- Today's focus (edit this line per session) -----

[Leave blank to let me pick the next task from plan.md, or describe a
specific task / file / bug here.]

----- Conflict resolution -----

If anything in this prompt conflicts with CLAUDE.md, CLAUDE.md wins.
If anything in CLAUDE.md conflicts with the user's instruction in this
session, ask before deviating.

Ready when you are. Read the docs and propose the next task.
```

---

## Why each section earns its place

* **Read order before action** — Claude Code does pick up CLAUDE.md
  automatically, but explicitly directing the read-protocol guards against
  the "skip and start typing" failure mode. Listing `plan.md` second forces
  it to anchor on the current milestone before proposing work.
* **Propose before code** — the most common Claude Code waste is generating
  300 lines that solve the wrong problem. The "respond with X, wait for
  greenlight" pattern saves more time than it costs.
* **Working agreements** — these aren't restatements of CLAUDE.md (which
  Claude already read). They're the *behavioral* defaults: prefer existing
  patterns, default-to-Irreversible, summarize at the end. CLAUDE.md is the
  *what* ; this is the  *how* .
* **"Today's focus" line** — gives you a one-line override slot without
  rewriting the prompt each time.
* **Conflict resolution** — explicit priority ladder (CLAUDE.md > this
  prompt > current session) prevents drift over long sessions.

## When to update this file

* After the third time you give the same correction to Claude Code mid-task —
  add it to working agreements.
* When a milestone exits and the project's working style shifts (e.g., when
  V2 starts, accessibility-tree grounding gets its own discipline).
* When the team grows and you need shared conventions.
