# AGENTS.md — Voice Anywhere V2

> Read this fully before touching any file.

---

## Operating Principles

Be genuinely helpful. No performative warmth, no fake uplift. Answer cleanly and move.

Have opinions. If something is ugly, fragile, slow, or overcomplicated — say so. If it's good, say why.

Be resourceful before asking. Read the file. Check the context. Trace the failure. Ask only if actually stuck.

This is someone's life, workflow, files, and reputation. Treat it like it matters.

### Style

- Short when short is enough
- Deep when the problem is real
- No corporate fluff. No filler. No "maybe" when you know.
- If something is broken, call it broken. If something is messy, say it's messy.
- No emojis in commits, comments, or code
- Answer the question first, then implement

### Execution

1. Read first
2. Verify second
3. Act third
4. Report clearly — don't stall, don't drift, don't invent confidence

When the path is clear, execute.
When the path is unclear, narrow it fast.
When the result is bad, say it bad.

### Quality Bar

- Prefer finished over fancy
- Prefer polished over theoretical
- Prefer installable over impressive
- Prefer stable UX over clever chaos
- **9.7/10 minimum** — if it ships, it must feel intentional. If it doesn't, keep tightening.

---

## Code Quality Rules

- Read files in full before making wide-ranging changes
- No `any` types unless absolutely necessary
- Never hardcode values that should be configurable
- Always ask before removing functionality that appears intentional
- No backward compatibility unless explicitly requested
- After code changes: lint/check, fix all errors before committing
- Never commit unless user asks

---

## Project: Voice Anywhere V2

An Android accessibility overlay app. A floating mic pill appears over every app on the device. User taps → STT fires → transcribed text is inserted at the cursor. Works via `AccessibilityService` + `ACTION_SET_TEXT` (primary) or clipboard fallback.

**Target:** Pixel 8a · Android API 31+ · Kotlin

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | API 31 (Android 12) |
| STT Engine | OpenRouter STT (keyed) → optional FUTO → system `RecognizerIntent` |
| Overlay | `TYPE_APPLICATION_OVERLAY` from keep-alive FGS (`SYSTEM_ALERT_WINDOW`) |
| Text insertion | `ACTION_SET_TEXT` → clipboard fallback |
| Build | Gradle (Kotlin DSL) |

## Key Files

```
app/src/main/java/com/nodaysidle/voiceanywhere/
├── MainActivity.kt — App entry, permission setup, API keys
├── DictationActivity.kt — Bridges to FUTO (optional) or system RecognizerIntent
├── service/
│   └── VoiceAccessibilityService.kt — Core: overlay, text insertion, STT trigger
├── history/ — Local transcript history storage
├── stt/ — OpenRouter STT client, audio recorder, language cycle
├── security/ — Keystore-backed OpenRouter + DeepSeek keys
└── polish/ — Optional DeepSeek polish + offline TextPostProcessor
```

## Critical Decisions — Do Not Reverse

1. **STT routing = OpenRouter when keyed, else optional FUTO, else system recognizer.** Do not make FUTO required. Do not use platform `SpeechRecognizer` as the *only* engine (error=9 on Pixel 8a); `RecognizerIntent` without `setPackage` is the no-key fallback. OpenRouter owns in-pill recording UI when keyed.
2. **Text insertion = ACTION_SET_TEXT first, clipboard fallback second.** Some apps (WebViews, custom editors) reject ACTION_SET_TEXT. Always fall back gracefully.
3. **Overlay = TYPE_APPLICATION_OVERLAY from VoiceKeepAliveService (FGS).** Requires SYSTEM_ALERT_WINDOW. Do not host the pill only on AccessibilityService.onServiceConnected — that dies with Xiaomi a11y binding death. TYPE_ACCESSIBILITY_OVERLAY is legacy fallback only.
4. **Own the recording UI when on OpenRouter.** For FUTO path, auto-select the language picker so tapping the overlay does not require a second user tap.
5. **History = opt-in local transcript text only.** Do not store voice audio by default; transcript history must remain copyable/deletable when enabled, must be off by default, and disabling it must clear saved transcripts.
6. **Paste never waits on polish.** Offline `TextPostProcessor` runs before insert; optional DeepSeek polish is background-only after paste. Timeouts stay on cloud calls.
7. **Languages = EN / IT / SL** via long-press cycle. Pass `EXTRA_LANGUAGE` (`en-US` / `it-IT` / `sl-SI`) and OpenRouter ISO-639-1 (`en` / `it` / `sl`).
8. **Do not claim "no internet required."** OpenRouter STT and DeepSeek polish need network when keyed.

## Current State

- `v0.4.0` — Keep-alive FGS + SYSTEM_ALERT_WINDOW overlay, Voice Anywhere IME insert fallback, honest ACCESS BOUND vs ENABLED/DEAD, battery/Xiaomi persistence prompts, M3 stadium pill (60dp)
- Active work: device smoke on Xiaomi M2007J3SY (a11y death → overlay + IME autopaste)

## Build & Install

```bash
# Build
./gradlew assembleDebug

# Install to Pixel 8a
adb -s 52151JEKB14522 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release Boundary

- Local release signing config exists for validation only.
- Do not publish a release build until Git history is sanitized or explicitly accepted, the rotated signing key is used, and Pixel smoke passes.
- Release signing credentials must come from environment variables or a secret store; never commit keystores or signing passwords.

## Design

- Dark theme always
- Volt `#C8FF00` accent
- Quiet Wispr-adjacent pill — dark fill, subtle stroke, waveform while recording
- Tight animations — nothing sloppy
- No placeholder UI, no broken states in shipping builds

## Out of Scope

- Language switching UI beyond long-press cycle (deferred)
- Public release / distribution until signing-history cleanup and device smoke are complete
- iOS, web, payments, accounts
