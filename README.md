<img src=".github/social-preview.png" alt="Voice Anywhere V2 — NODAYSIDLE" width="100%">

# Voice Anywhere V2

> Universal voice dictation overlay for Android — speak into any app, anywhere.

![Platform](https://img.shields.io/badge/platform-Android%2012%2B-green?style=flat-square)
![Version](https://img.shields.io/badge/version-0.3.0-C8FF00?style=flat-square)
![License](https://img.shields.io/badge/license-proprietary-black?style=flat-square)

## Overview

Voice Anywhere is a floating mic pill that lives on top of every app on your phone. Focus a text field, tap it, speak, and your transcribed text is injected directly into that field. If no editable field is focused, the app blocks recording instead of producing a confusing clipboard-only result.

It uses Android Accessibility Services to detect the focused input field and intelligently selects the best insertion method per app.

## Features

- **Floating pill overlay** — dark, quiet, Wispr-adjacent; draggable; waveform while recording
- **Cursor-aware insertion** — appends at cursor, not end of field
- **Smart hint detection** — never confuses placeholder text with real content
- **No-field guard** — shows `NO FIELD` instead of launching dictation with nowhere to insert
- **STT routing** — OpenRouter cloud STT when keyed; optional FUTO if installed; system recognizer otherwise
- **Languages** — long-press cycles EN / IT / SL
- **Opt-in local transcript history** — last dictations are copyable, retryable, and deletable only after the user enables local history
- **Three insertion modes** with live visual feedback:
  - `✓ SET` — direct `ACTION_SET_TEXT` (fastest, most apps)
  - `✓ PST` — `ACTION_PASTE` fallback (Jetpack Compose apps)
  - `↗ CPY` — clipboard copy (sandboxed fields — Gmail, browsers)
- **AI text polish** — optional DeepSeek API key for grammar cleanup (Keystore-encrypted); paste never waits on polish
- **OpenRouter STT** — optional OpenRouter API key (Keystore-encrypted); blank key uses system/FUTO path

## Compatibility

Tested on Pixel 8a, Android 16 / API 36.

| App | Insertion Mode | Notes |
|---|---|---|
| WhatsApp | ✓ SET | Hint strip via `selStart==-1` guard |
| Telegram | ✓ SET | Clean |
| Google Messages | ✓ SET | Clean |
| Google Keep | ✓ SET | Clean |
| YouTube Search | ✓ SET | Clean |
| ChatGPT | ✓ SET | Clean |
| Perplexity | ✓ SET | Clean |
| Gemini | ↗ CPY | Jetpack Compose — `ACTION_PASTE` sandboxed |
| Gmail body | ✓ SET | Body composer confirmed |
| Comet browser | ✓ SET | Assistant/navigation field confirmed |

## Requirements

- Android 12 or later (API 31)
- Accessibility Service enabled
- `RECORD_AUDIO` permission
- `POST_NOTIFICATIONS` permission for clipboard fallback alerts on Android 13+
- (Optional) OpenRouter API key for cloud STT
- (Optional) FUTO Voice Input — used automatically when installed and no OpenRouter key is set
- (Optional) DeepSeek API key for background polish

## Installation

1. Install APK via adb or sideload
2. Open app → grant **Microphone**
3. Open app → grant **Notifications** for clipboard fallback alerts
4. Settings → Accessibility → **Voice Anywhere** → Enable
5. (Optional) Enter OpenRouter API key for cloud STT. Leave blank for system/FUTO.
6. (Optional) Enter DeepSeek API key for background polish. Paste never waits on it.
7. (Optional) Enable local transcript history. Off by default; disabling clears saved transcripts.

The floating dark pill will appear on screen.

## Usage

1. Open any app and tap an input field
2. Tap the floating pill
3. Speak — waveform shows while recording (OpenRouter path); system/FUTO UI otherwise
4. Tap again to stop (OpenRouter path) — text inserts at the cursor
5. Long-press the pill to cycle EN / IT / SL
6. If local history is enabled, open the app to copy, retry, delete, or clear recent transcripts

## Architecture

```
VoiceAccessibilityService   — core service, node tracking, injection logic
FloatingMicOverlay          — TYPE_ACCESSIBILITY_OVERLAY pill + waveform
InsertionFeedback           — mode → state/label/color mapping
TextInsertionMerger         — pure cursor-aware merge logic with unit tests
PillDrawable                — rounded pill background drawable
DictationActivity           — FUTO (optional) or system RecognizerIntent bridge
DictationLanguage           — EN / IT / SL cycle + locale tags
OpenRouterSttClient         — cloud STT via OpenRouter audio transcriptions
DictationAudioRecorder      — in-pill AAC recorder for OpenRouter path
TextPostProcessor           — offline text cleanup (spacing, caps) before insert
DeepSeekTextPolisher        — optional AI grammar polish (background, non-blocking)
OpenRouterKeyStore          — Android Keystore-backed OpenRouter API key
DeepSeekKeyStore            — Android Keystore-backed DeepSeek API key
TranscriptHistoryStore      — opt-in local-only capped transcript history
ClipboardNotification       — private nudge notification for clipboard-only apps
```

## Development

```bash
# Debug
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Release (requires keystore)
./gradlew assembleRelease
```

### Signing

Release builds use `app/keystore/release.jks`. The keystore is **not committed to the repo**. Set credentials with `VOICE_ANYWHERE_STORE_PASSWORD`, `VOICE_ANYWHERE_KEY_ALIAS`, and `VOICE_ANYWHERE_KEY_PASSWORD` environment variables.

Release boundary: do not publish or migrate this repository history publicly until the old committed signing material has been sanitized or explicitly accepted as burned history. Use only the rotated local release key for future release validation.

## Known Limitations

- Some sandboxed web or custom editor fields may still fall back to clipboard-only
- System `SpeechRecognizer` alone can fail with error=9 on Pixel 8a; this app uses `RecognizerIntent` or OpenRouter instead of depending on that API as the only engine
- FUTO (when used) steals focus during STT; node snapshot is taken before mic tap to compensate
- Accessibility service restarts on app update — must re-enable manually
- Clipboard fallback notifications do not show transcript previews
- OpenRouter STT and DeepSeek polish require network when their keys are set

## Version History

| Version | Notes |
|---|---|
| 0.3.0 | FUTO optional, OpenRouter STT, SL language, Wispr-dark overlay, non-blocking polish |
| 0.2.0 | Mode indicator (SET/PST/CPY), hint text fix, cursor-aware append, release signing |
| 0.1.0 | Initial build — basic overlay + injection |

## Status

Active — v0.3.0. Proprietary project maintained by NODAYSIDLE.

## License

Proprietary — NODAYSIDLE. All rights reserved.
