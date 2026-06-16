# NLnet NGI Zero — Mitra Application (Draft v0.1)

**Status:** Draft. Submit before the next even-month NLnet deadline (Aug 1 or Oct 1, 2026).
**Owner:** @warpirate
**Target fund:** [NGI Zero Commons Fund](https://nlnet.nl/commonsfund/) (preferred) — internet-commons, privacy, decentralisation. Backup: [Mobifree](https://nlnet.nl/mobifree/eligibility/) if Mobifree's next round is open and the mobile-OS-independence framing fits better.
**Budget ask:** €30,000 over 12 months (mid-range of the 5K–50K band). Breakdown in §10.

---

## 1 — Project name

**Mitra** (working codename, Sanskrit/Telugu for "friend"). A public name is locked before the first non-anonymous public release.

## 2 — Abstract (one paragraph)

Mitra is a fully on-device, open-source AI agent for Android. It replicates the agentic phone-control capabilities of the Google Gemini mobile app — setting alarms, sending messages, placing calls, toggling hardware, opening apps, automating multi-step tasks — without sending any user data to a remote server. The model runs locally; it reads local data locally; it acts on the device locally. The single hard invariant: if a feature would require user data to leave the device, it does not ship.

## 3 — Problem being addressed

Every mobile assistant on the market — Google Gemini, Apple Siri, Samsung Bixby, Amazon Alexa — sends user commands, contact metadata, message bodies, location, and (in some cases) screen contents to a remote cloud where they become rows in a commercial data warehouse. There is no on-device equivalent. Users who choose privacy (the GrapheneOS / CalyxOS / LineageOS / F-Droid audience, plus the broader "I sideload" community in privacy-conscious markets) are forced to give up the convenience of an assistant entirely.

Mitra is the on-device equivalent. It uses the same underlying open-weights model class (Google's Gemma 4 E2B, Apache 2.0 licensed) running through Google's actively-maintained LiteRT-LM on-device runtime, with native tool-calling so the model both decides what to do and emits structured tool calls that dispatch to Android-native actions (Manager APIs, Content Providers, Intents, and — for V2 in-app automation — the AccessibilityService and notification RemoteInput pathways).

The privacy stance is not marketing. It is a layered technical posture: a no-INTERNET build flavor where the app holds no `INTERNET` permission and the model must be sideloaded (OS-enforced unexfiltratability); reproducible builds; published tracker scans (Exodus / SUSS); published mitmproxy network captures; and a per-action audit log inside the app so the user can verify exactly what Mitra did and when, with no content ever logged.

## 4 — Who developed the technology and what is special

Mitra is currently a solo project (`@warpirate`). At the time of this application it is in late M1 (V1 tool surface complete: 16 tools shipped across hardware control, telephony, contacts, audio, display, alarms/timers, apps, and intents) and mid-M2 (ConfirmationGate + content-free AuditLog shipped; integration test guarding R-006 in place). The multi-step "agentic loop" landed in M2.5 (8/8 manual scenarios pass on a Realme CPH2401). All work-product is Apache 2.0 on GitHub.

What is special:

- **It is the only on-device agentic assistant in this category that does not depend on a cloud backend.** Comparable apps (Layla, MLC Chat) are chat-only — they lack the device-control surface. Comparable on-device assistants in the Indian-context space (Krutrim/Kruti, Sarvam Indus) are cloud-bound. No comparable product combines all three properties: on-device, device-control-capable, F-Droid-distributable.
- **The fork-able-by-design property.** 100% on-device + Apache 2.0 = no proprietary backend any company can switch off. This is the Mycroft-died / OpenVoiceOS-survived lesson applied as a foundational design constraint. Even if the original maintainer leaves, the project remains usable and forkable indefinitely.
- **Architected for Indian and low-connectivity contexts as a first-class concern**, not as an afterthought. The model is small enough for mid-range hardware (Snapdragon 7 Gen 2 / 6 GB RAM minimum); the privacy claim is verifiable rather than rhetorical; Hindi is a V1 string target with Telugu / Tamil / regional languages as planner-routed beta.

## 5 — Team

Currently one person (`@warpirate`). The grant explicitly funds the bootstrap of the contributor community + maintenance of stranger-runnable infrastructure (§7) — i.e., the grant is partly **to de-risk the one-person-team property**, which is the project's #1 existential risk per its own risk register ([risks.md R-008](../risks.md)).

If awarded, the milestones in §6 are deliverable solo. The community-infrastructure milestone (§6.3) creates the conditions for a second contributor to merge unaided, and is the criterion that closes the bus-factor risk.

## 6 — Have you been involved in NLnet-funded projects before?

No. This is `@warpirate`'s first NLnet application.

## 7 — Comparable existing technology + lessons from prior projects

| Product | On-device? | Open source? | Device control? | Mobile? | Status |
|---|---|---|---|---|---|
| Google Gemini (Android) | No (cloud) | No | Yes | Yes | Active |
| Apple Siri | No (cloud) | No | Yes | Yes (iOS only) | Active |
| Samsung Bixby | No | No | Yes | Yes (Samsung only) | Active |
| Layla | Yes | No (proprietary) | No (chat only) | Yes | Active |
| MLC Chat | Yes | Yes | No (chat only) | Yes | Active |
| Mycroft | Partly (server-led) | Yes | Partial (smart-home) | No (mainly desktop / pi) | **Died 2023** — corporate backer collapsed |
| OpenVoiceOS | Yes (no central server) | Yes | Yes (smart-home focus) | Limited mobile | **Survived** — federated, no single point of failure |
| Krutrim Kruti (India) | No (cloud) | No | Yes (Ola ecosystem) | Yes | **Withdrawn from app stores April 2026** |
| Sarvam Indus (India) | No (cloud) | No | No (chat / search) | Yes | Active |

Lessons taken from this list:

- **Mycroft died because its server was the project's load-bearing point.** Mitra's architectural answer: no server exists. The model runs on the user's phone. There is nothing to switch off.
- **OpenVoiceOS survived for the same reason inverted: no single point of failure.** Mitra's analogue is fork-ability — Apache 2.0 code + Apache 2.0 model + reproducible build + an in-repo eval set + a stranger-runnable CONTRIBUTING flow.
- **Krutrim's app-store withdrawal demonstrates how fragile cloud-dependent Indian-context assistants are**: a corporate restructuring removed the product overnight. An app distributed on F-Droid + IzzyOnDroid + GitHub Releases cannot be unilaterally removed in the same way.

## 8 — Are you aware of similar past projects, including failed ones?

Yes — see the table above. The two that most informed Mitra's design are Mycroft (cautionary tale of corporate-server dependence) and OpenVoiceOS (proof that the federated / no-central-server model can persist). Mitra's "100% on-device, no server at all" position is the strongest version of OpenVoiceOS's lesson applied to mobile.

There is a long history of on-device LLM chat apps (MLC Chat, Layla, gpt4all-mobile, llama.cpp wrappers). None of them attempt the **agentic phone-control** surface that makes an assistant useful, which is the gap Mitra fills.

## 9 — Skills and capacity to deliver

- The owner has shipped 16 tools through the V1 agent surface, an agentic multi-step loop (`AgentRuntime` + `LiteRtBrain.sendToolResult`), the safety + audit infrastructure, on-device model download with SHA-256 verification, and the onboarding / chat / permissions UI. Repo state is verifiable at `github.com/warpirate/AIOS-APP`.
- CI is green: build + Android lint + unit tests + assembleDebug on every push/PR via GitHub Actions.
- The project has structural test guards: `GateCoverageTest` (proves the ConfirmationGate fires for every `SideEffect.Irreversible` tool, with a source-grep drift-catcher that fails the build if a new Irreversible tool is added without test coverage); `AuditLogTest` (proves the audit log's field schema cannot drift to leak content); `ModelDownloaderTest` (proves SHA-256 integrity verification deletes corrupt files).
- The roadmap is **realistic, dated, milestone-gated, and adversarially fact-checked** ([viability assessment 2026-06-04](../research/2026-06-04-mitra-viability-assessment.md), 12 dimensions, 32 agents). The owner has revised the plan publicly when the assessment found bad assumptions (e.g. downgrading AccessibilityService restriction from "project-killing" to "caps V2"; re-pinning to LiteRT-LM after discovering MediaPipe LLM Inference is deprecated; defaulting to E2B over E4B for 6 GB RAM realism).

Capacity is the honest open question — see §11.

## 10 — Budget breakdown (€30,000 / 12 months)

| Line item | Amount | What it buys |
|---|---|---|
| Maintainer compensation | €18,000 | Buys ~3 days/week of focused engineering time for 12 months at well below market rate. Without this the project is a side project competing with paid work. |
| Hardware: 3 dev devices | €1,500 | One Snapdragon 7 Gen 2 / 6 GB Android (the realistic mid-range floor — currently the bottleneck on R-001 "hardware truth test"), one Pixel running GrapheneOS (R-002 / R-003 / AAPM testing), one OnePlus / Realme already owned (existing dev device, kept). Spend includes shipping + import duty to India where applicable. |
| Eval-set construction | €3,000 | Pays for hand-labelled command → tool-call gold datasets across English (target 200 commands), Hindi V1 (target 100), Telugu / Tamil beta (target 50 each). Eval is the only way to gate accuracy regressions as the tool set grows; without a real dataset the project ships blind. |
| Security audit (subcontract) | €4,500 | One independent third-party security review against the privacy invariants before V1 lock. Outputs: a published audit report, plus any remediation work. The audit's existence is part of the trust framing. |
| F-Droid main packaging work | €1,500 | Engineering time on the llama.cpp / GGUF backend that makes F-Droid main publishable (current default LiteRT-LM ships prebuilt native libs that don't meet F-Droid's "buildable from FLOSS source" rule). Stretch goal; descope if other work overruns. |
| Beta-tester logistics | €500 | Pays for 10–20 testers from the privacy community a small honorarium for completing structured feedback; covers community-call infrastructure. |
| Documentation / translation | €1,000 | Hindi / Telugu / Tamil translation of user-facing copy + onboarding screens. Done by paid native speakers, not auto-translated. |
| **Total** | **€30,000** | |

The budget is structured so most milestones still ship even if any single line item is descoped. Maintainer compensation is the single largest line because R-008 is the only genuinely existential risk; structural funding of maintainer time is the only known mitigation.

## 11 — Other funding sources

None at time of application. The owner has not received NLnet, Sovereign Tech Fund, NSF POSE, or Mozilla MOSS funding for this project. The repo has no commercial backers and no paid contributors. No corporate sponsorship is being pursued for V1 — the entire point of the project's posture is that no commercial party can withdraw it.

## 12 — Milestones (the grant pays against these)

### M-A (months 1–3) — Bootstrap stranger-runnable infrastructure
- [ ] Stranger-runnable CONTRIBUTING walkthrough + `docs/runbooks/add-a-tool.md` — **shipped 2026-06-16, before submission**, so reviewers can verify the property directly.
- [ ] Reproducible-builds Gradle + NDK pipeline; documented build-from-source procedure for the inference backend.
- [ ] Exodus / SUSS tracker scan publication (post-V1-release) + mitmproxy capture published in `docs/audits/`.
- [ ] 25 GitHub issues tagged `good-first-issue`, each scoped to ~2 hours work with a self-contained acceptance criterion.
- [ ] At least one external contributor (someone who has not previously committed) merges a tool unaided.

### M-B (months 3–6) — Eval set + accuracy gate
- [ ] V1 eval set: 200 English commands with hand-labelled gold tool calls + state-after assertions.
- [ ] Hindi eval slice: 100 commands.
- [ ] Telugu + Tamil beta slices: 50 commands each (honest per-language targets — no parity promise).
- [ ] Eval harness runs in CI on every PR; regression in tool-name accuracy or arg-shape accuracy fails the PR.

### M-C (months 6–9) — V1 release (0.1.0) on F-Droid alternatives
- [ ] First-run model download SHA verification (already shipped pre-application).
- [ ] Per-permission "why" copy in onboarding (per [`docs/design/permissions.md`](../design/permissions.md)).
- [ ] No-INTERNET build flavor (the OS-enforced unexfiltratability proof artifact).
- [ ] Signed APK on GitHub Releases + IzzyOnDroid listing.
- [ ] F-Droid main repo metadata package (gated on the llama.cpp backend landing — stretch).
- [ ] Third-party security audit complete + report published.
- [ ] Beta with 10–20 testers from the privacy community.

### M-D (months 9–12) — V2 foundation
- [ ] `AutomationBackend` tier interface formalised (M5.5 in [plan.md](../../plan.md)).
- [ ] `NotificationReplyBackend` (RemoteInput) — the seamless reply-to-incoming-message path.
- [ ] AccessibilityService MVP — first target app (WhatsApp send via tier ladder per [`ARCHITECTURE.md`](../../ARCHITECTURE.md)).
- [ ] Per-app reliability dashboard for the top 5 target apps.

Public retrospective at each milestone exit + monthly progress posts to the GitHub Discussions board.

## 13 — Why NGI Zero / NLnet specifically

Mitra is precisely the profile NGI Zero exists to fund: a privacy-preserving, internet-commons-aligned, fully-open-source piece of mobile infrastructure that the commercial market will not build because its existence undermines the commercial market's data-extraction model. The €5K–€50K range and even-month deadlines fit the project's stage. The fork-able-by-design property aligns with NLnet's commons posture: even if the original maintainer detaches, the public good Mitra produces persists.

## 14 — What we will publicly commit to if funded

- A public, NLnet-acknowledged status post in the repo README and in [`docs/grants/`](.) on award.
- A public mid-grant report at month 6 covering what shipped, what didn't, what changed in the plan and why.
- A public end-of-grant report at month 12 covering deliverables, learnings, and the next-step funding posture.
- The eval set + the synthetic-data generator + every dataset built with grant funds, all released under permissive licenses to the same standard as the app code.
- If milestones slip, an honest accounting in the next report. If the project terminates, the code stays Apache 2.0 forever and the eval set stays public — nothing is taken back.

## 15 — What this draft still needs before submission

- [ ] Confirm next NLnet deadline (Aug 1 vs Oct 1 2026) + format requirements (web form vs PDF) on `nlnet.nl/commonsfund`.
- [ ] Pick the public project name (Mitra is a working codename) — required for the application's "project name" field.
- [ ] Add the production GitHub repository URL once the project moves out of the personal namespace (if it does).
- [ ] Two-letter ISO country code for "country of the project lead" field.
- [ ] Personal CV / brief bio (NLnet asks).
- [ ] Letters of support, if any community / privacy organisations want to write one (not required — strengthens the application).
- [ ] Final budget sanity-check in EUR vs INR vs USD per the lead's actual living costs.
- [ ] Cross-check the [Mobifree eligibility criteria](https://nlnet.nl/mobifree/eligibility/) against the project to decide whether Commons Fund or Mobifree is the better fit; if Mobifree, rewrite §13 to lean into mobile-OS-independence language.

The draft is checked in so future-self / future-contributor can pick it up and finish the submission without re-deriving the framing.
