# Mitra

> A fully on-device, open-source AI agent for Android. Phone-control capabilities like the Gemini mobile app, but nothing ever leaves your device.

**Status:** Pre-alpha. Active development. Not yet usable.

---

## Why

Every mobile assistant on the market — Gemini, Siri, Bixby, Alexa — sends your commands, contacts, location, and screen content to the cloud. Mitra doesn't. The model runs on your phone, reads your data on your phone, acts on your phone. The only network call the app ever makes is the one-time model download.

If a feature would require user data to leave the device, it does not ship in Mitra. That's the rule.

## What it does

**V1 (in progress):**

- Toggle hardware: flashlight, wifi, bluetooth, DND, mobile data, airplane mode
- Adjust display & audio: brightness, volume per stream, ringer mode
- Set alarms, timers, calendar events
- Make calls and send SMS to your local contacts
- Open apps and deep-link into settings screens
- Polish rambling messages into clean drafts

**V2 (planned):** In-app UI automation via Android's AccessibilityService — replying to WhatsApp, ordering through Swiggy, etc.

**V3 (planned):** Community-extensible tool platform with multimodal screenshot understanding.

See [PRD.md](PRD.md) for the full capability roadmap.

## How it works

A single local model (**Gemma 4 E2B**, downloaded once on first run) turns your natural language into a structured tool call — the model decides what to do and calls the matching tool. A deterministic parser stands behind it as a fallback for the known commands, so common actions always work. The calls dispatch to Android-native handlers — hardware managers, the intent system, content providers, and (later) the AccessibilityService. Side-effectful actions surface a Confirm card before they fire.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full system breakdown.

## Quick start

> Pre-skeleton at the moment. The instructions below are what they'll look like once M0 lands.

```bash
git clone https://github.com/yourorg/mitra
cd mitra
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

After install, grant Mitra accessibility access via **Settings → Accessibility → Mitra**. On Android 13+ you'll go through "Restricted Settings" first — that's normal and intentional.

## Requirements

- Android 8.0 (API 26) or newer
- 6 GB RAM minimum recommended
- Snapdragon 7 Gen 2 or comparable SoC
- ~2 GB free storage for the model bundle

## Project docs

- [CLAUDE.md](CLAUDE.md) — context for AI coding assistants; read this first if you're hacking on the code
- [PRD.md](PRD.md) — what we're building and why
- [ARCHITECTURE.md](ARCHITECTURE.md) — how it's built
- [plan.md](plan.md) — milestone breakdown and current sprint
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to help

## Distribution

At launch: **signed APKs on GitHub Releases + IzzyOnDroid** (both session-based installs, which avoid Play Protect and the Android 13+ restricted-settings gate). **F-Droid main repo is a stretch goal** — it needs a fully FLOSS-buildable inference backend (the llama.cpp/GGUF path), since the default runtime ships prebuilt native libraries. Play Store is not a target — Play's accessibility-policy review is a structural risk to a project like this, and it doesn't bind us off-Play.

## License

App code: **Apache 2.0**. See [LICENSE](LICENSE).

Model — **Gemma 4 E2B**: Apache 2.0 (the first OSI-licensed Gemma generation). Downloaded once on first run, never bundled in the APK.

## Acknowledgements

Built on Google's open-weights work (Gemma, FunctionGemma, LiteRT, AI Edge Gallery). Inspired by the entire de-Google community keeping Android usable without surveillance.
