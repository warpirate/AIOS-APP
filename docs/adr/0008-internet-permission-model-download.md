# ADR 0008 — INTERNET permission for the one-time model download

**Status:** Accepted
**Date:** 2026-06-04

## Context

CLAUDE.md privacy invariant #5 requires an ADR for any permission-sensitive manifest entry. The app
now needs to fetch the on-device model (~2.6 GB Gemma 4 E2B `.litertlm`) on first run, from inside the
app, so a non-technical user never has to `adb push` a file. This requires the `INTERNET` permission.

This is consistent with the project's stated design, not a deviation:
- PRD: "The only network call the app ever makes is the one-time model download."
- CLAUDE.md: `inference/ModelDownloader.kt` is "the only file allowed to touch the network."

## Decision

- Declare `android.permission.INTERNET` in the manifest.
- All network access lives in `inference/ModelDownloader.kt` (the lint-allowlisted file). No other file
  may import `java.net.*` / `okhttp` / `retrofit`.
- The downloader fetches from a single hardcoded host (`ModelRegistry.MODEL_URL`), writes to app-private
  storage, is resumable, and is never invoked again after the model is present.
- No analytics, telemetry, or any other network use. Ever.

## Consequences

- The convenience build holds `INTERNET`. A future **no-INTERNET build flavor** will omit the permission
  entirely and require the model to be sideloaded — that flavor is the OS-enforced "zero exfiltration"
  proof for the privacy-maximalist audience (see the viability assessment, §5).
- The Gemma 4 E2B HuggingFace repo is **gated**, so the anonymous in-app download needs an **ungated
  mirror**. Gemma 4 weights are Apache-2.0 (redistribution allowed), so the model `.litertlm` will be
  mirrored to the project's own GitHub Release / CDN and `ModelRegistry.MODEL_URL` pointed there.
- Lint must continue to enforce: network imports only in `ModelDownloader.kt`; no telemetry packages.

## Alternatives considered

- **Sideload-only (no INTERNET):** maximal privacy but a terrible first-run UX for non-technical users
  (the project's approachability principle). Kept as a separate build flavor, not the default.
- **Bundle the model in the APK:** ~2.6 GB is impractical and the Gemma Terms make in-APK redistribution
  awkward; assets aren't seekable so it would be copied out anyway.
