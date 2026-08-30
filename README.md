<img src=".github/social-preview.png" alt="Voice Anywhere V2 — NODAYSIDLE" width="100%">

<h1 align="center">Voice Anywhere V2</h1>

<p align="center">
  Overlay mic. Speak into any app. Words land at the cursor.
</p>

<p align="center">
  <a href="https://github.com/nodaysidle/nodaysidle-voice-anywhere-v2/releases/download/v0.3.0/voice-anywhere-v2-v0.3.0.apk"><strong>Download v0.3.0 debug APK</strong></a>
  ·
  <a href="https://github.com/nodaysidle/nodaysidle-voice-anywhere-v2/releases/tag/v0.3.0">v0.3.0 release</a>
  ·
  <a href="https://github.com/nodaysidle/nodaysidle-voice-anywhere-v2/releases">All releases</a>
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-12%2B-C8FF00?style=flat-square&logo=android&logoColor=0A0A0F">
  <img alt="Version" src="https://img.shields.io/badge/v0.3.0-debug-6B6B80?style=flat-square">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-native-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="OpenRouter" src="https://img.shields.io/badge/STT-OpenRouter-6467F2?style=flat-square">
  <img alt="License" src="https://img.shields.io/badge/License-Proprietary-black?style=flat-square">
</p>

## The loop

Voice Anywhere exists for one path: speech becomes text at the cursor in whatever app you already have open.

```text
Tap overlay mic (field focused)
  → STT (OpenRouter if keyed → FUTO if installed → system ACTION_RECOGNIZE_SPEECH)
  → local clean (TextPostProcessor)
  → insert at cursor   ← paste path ends here
       SET → PST → clipboard + notification

Optional DeepSeek polish (if keyed)
  → runs in the background AFTER insert
  → result is dropped — never re-inserted
```

| Stage | What happens |
|---|---|
| **Focus** | Tap an editable field in any app. No field → pill shows `NO FIELD` and does not record. |
| **Speak** | Tap the floating pill. Waveform while speaking; idle shows mic + language tag (`EN` / `IT` / `SL`). Long-press cycles language. |
| **Transcribe** | OpenRouter `deepgram/nova-3` when an OpenRouter key is set; else FUTO if installed; else system `ACTION_RECOGNIZE_SPEECH` with **no** `setPackage`. FUTO is optional — never required, never blocks ready. |
| **Clean** | Offline `TextPostProcessor` runs before insert. |
| **Insert** | Cursor insert: `ACTION_SET_TEXT` → `ACTION_PASTE` → clipboard + private notification. Pill flashes `✓ SET` / `✓ PST` / `↗ CPY`. |
| **Polish (optional)** | DeepSeek, if keyed, runs after paste in the background. Output is **not** written back into the field. |

Android only (`com.nodaysidle.voiceanywhere`; published debug APK is `com.nodaysidle.voiceanywhere.debug`). Accessibility Service + microphone required. OpenRouter and DeepSeek keys live in Android Keystore when set. Opt-in local transcript history if you turn it on (off by default; disabling clears saved transcripts).

## What’s on screen (v0.3.0)

| Surface | Role in the loop |
|---|---|
| **Floating pill** | Wispr-dark overlay over every app. Idle: mic + language tag. Recording: waveform. Feedback: SET / PST / CPY / `NO FIELD`. |
| **Setup screen** | Permissions, Accessibility enable, OpenRouter key, DeepSeek key, opt-in history. |
| **History (opt-in)** | Copy / retry / delete recent transcripts when enabled. |

**Not on the pill:** language picker UI beyond the long-press `EN` → `IT` → `SL` cycle. No second tap for FUTO language when FUTO is used — auto-select handles it.

## STT (OpenRouter)

| Priority | Engine | When |
|---|---|---|
| 1 | OpenRouter `deepgram/nova-3` | OpenRouter API key set (Keystore). In-pill AAC record → cloud transcription. |
| 2 | FUTO Voice Input | Key blank **and** FUTO installed. Optional — missing FUTO does not block ready. |
| 3 | System `ACTION_RECOGNIZE_SPEECH` | No key, no FUTO. Intent launched **without** `setPackage`. |

Languages passed as `en-US` / `it-IT` / `sl-SI` (OpenRouter ISO-639-1: `en` / `it` / `sl`). Network is required only when OpenRouter STT or DeepSeek polish keys are set — do not claim “no internet required.”

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | API 31 (Android 12+) |
| Overlay | `TYPE_ACCESSIBILITY_OVERLAY` |
| STT | OpenRouter `deepgram/nova-3` → optional FUTO → system `ACTION_RECOGNIZE_SPEECH` |
| Text insertion | `ACTION_SET_TEXT` → `ACTION_PASTE` → clipboard + notification |
| Keys | Android Keystore (OpenRouter, DeepSeek) |
| Build | Gradle (Kotlin DSL) |

## Install the APK

The published artifact is a **debug** build — version **0.3.0**, package `com.nodaysidle.voiceanywhere.debug`.

1. Download [`voice-anywhere-v2-v0.3.0.apk`](https://github.com/nodaysidle/nodaysidle-voice-anywhere-v2/releases/download/v0.3.0/voice-anywhere-v2-v0.3.0.apk) from the [v0.3.0 release](https://github.com/nodaysidle/nodaysidle-voice-anywhere-v2/releases/tag/v0.3.0).
2. Sideload on Android 12+ (API 31). Allow install from unknown sources for your file manager/browser.
3. Open the app → grant **Microphone** (and **Notifications** on Android 13+ for clipboard fallback alerts).
4. Settings → Accessibility → **Voice Anywhere** → Enable.
5. (Optional) OpenRouter key for cloud STT. (Optional) DeepSeek key for background polish. (Optional) Local history.

Sideloaded and used on a **Xiaomi M2007J3SY**, Android 12 (API 31). Source `applicationId` is `com.nodaysidle.voiceanywhere`; this APK carries the debug suffix.

## Build from source

```bash
./gradlew assembleDebug
```

Artifact:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` is **not** a bare one-liner. It needs `app/keystore/release.jks` (gitignored) plus:

```bash
export VOICE_ANYWHERE_STORE_PASSWORD=...
export VOICE_ANYWHERE_KEY_ALIAS=...
export VOICE_ANYWHERE_KEY_PASSWORD=...
./gradlew assembleRelease
```

## Not here

- iOS
- Web / Capacitor
- Play Store distribution
- “No internet required” (OpenRouter STT and DeepSeek polish need network when keyed)
- FUTO as a hard requirement (optional only)
- Platform `SpeechRecognizer` as the documented fallback API (fallback is `ACTION_RECOGNIZE_SPEECH` without `setPackage`)

## License

Proprietary — NODAYSIDLE. All rights reserved.
