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

An Android accessibility overlay app. A floating mic button appears over every app on the device. User taps → STT fires → transcribed text is inserted at the cursor. Works via `AccessibilityService` + `ACTION_SET_TEXT` (primary) or clipboard fallback.

**Target:** Pixel 8a · Android API 31+ · Kotlin

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | API 31 (Android 12) |
| STT Engine | FUTO Keyboard `RecognizeActivity` |
| Overlay | `TYPE_ACCESSIBILITY_OVERLAY` window |
| Text insertion | `ACTION_SET_TEXT` → clipboard fallback |
| Build | Gradle (Kotlin DSL) |

## Key Files

```
app/src/main/java/com/nodaysidle/voiceanywhere/
├── MainActivity.kt                    — App entry, permission setup
├── DictationActivity.kt               — Bridges to FUTO RecognizeActivity
├── service/
│   └── VoiceAccessibilityService.kt  — Core: overlay, text insertion, STT trigger
├── history/                           — Local transcript history storage
├── stt/                               — STT engine wrappers
└── polish/                            — UI polish components
```

## Critical Decisions — Do Not Reverse

1. **STT = FUTO only.** Platform `SpeechRecognizer` fails with error=9 on Pixel 8a even with RECORD_AUDIO granted. FUTO handles its own permissions. Do not revert.
2. **Text insertion = ACTION_SET_TEXT first, clipboard fallback second.** Some apps (WebViews, custom editors) reject ACTION_SET_TEXT. Always fall back gracefully.
3. **Overlay = TYPE_ACCESSIBILITY_OVERLAY.** Required to draw over all apps without SYSTEM_ALERT_WINDOW permission.
4. **FUTO language picker = auto-select.** Tapping the overlay must not require a second user tap when FUTO shows its language picker.
5. **History = opt-in local transcript text only.** Do not store voice audio by default; transcript history must remain copyable/deletable when enabled, must be off by default, and disabling it must clear saved transcripts.

## Current State

- `v0.2.0` — FUTO STT working, overlay functional, text insertion implemented
- Active work: compatibility map of ACTION_SET_TEXT vs clipboard fallback across target apps

## Build & Install

```bash
# Build
cd /Volumes/omarchyuser/projekti/nodaysidle-voice-anywhere-v2
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
- Tight animations — nothing sloppy
- No placeholder UI, no broken states in shipping builds

## Out of Scope

- Language switching UI (deferred)
- Public release / distribution until signing-history cleanup and device smoke are complete
