# Voice Anywhere V2

> Universal voice dictation overlay for Android — speak into any app, anywhere.

![Platform](https://img.shields.io/badge/platform-Android%2012%2B-green)
![Version](https://img.shields.io/badge/version-0.2.0-C8FF00)
![License](https://img.shields.io/badge/license-proprietary-black)

---

## What it does

Voice Anywhere is a floating mic overlay that lives on top of every app on your phone. Focus a text field, tap it, speak, and your transcribed text is injected directly into that field. If no editable field is focused, the app blocks recording instead of producing a confusing clipboard-only result.

It uses Android Accessibility Services to detect the focused input field and intelligently selects the best insertion method per app.

---

## Features

- **Floating pill overlay** — draggable, snaps to screen edge, persists position
- **Cursor-aware insertion** — appends at cursor, not end of field
- **Smart hint detection** — never confuses placeholder text with real content
- **No-field guard** — shows `NO FIELD` instead of launching dictation with nowhere to insert
- **FUTO picker auto-select** — skips the extra FUTO language tap when multiple FUTO languages are enabled
- **Opt-in local transcript history** — last dictations are copyable, retryable, and deletable only after the user enables local history
- **Three insertion modes** with live visual feedback:
  - `✓ SET` 🟢 — direct `ACTION_SET_TEXT` (fastest, most apps)
  - `✓ PST` 🟣 — `ACTION_PASTE` fallback (Jetpack Compose apps)
  - `↗ CPY` 🔵 — clipboard copy (sandboxed fields — Gmail, browsers)
- **AI text polish** — optional DeepSeek API key for grammar/punctuation cleanup, stored with Android Keystore encryption; blank key keeps dictation local-only
- **No internet required** — works fully offline without API key

---

## Compatibility Map

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

---

## Requirements

- Android 12+ (API 31)
- Accessibility Service enabled
- `RECORD_AUDIO` permission
- `POST_NOTIFICATIONS` permission for clipboard fallback alerts on Android 13+
- FUTO Voice Input installed (`org.futo.voiceinput`)

---

## Build

```bash
# Debug
./gradlew assembleDebug

# Release (requires keystore — see Signing)
./gradlew assembleRelease
```

### Signing

Release builds use `app/keystore/release.jks`. The keystore is **not committed to the repo**. Set credentials with `VOICE_ANYWHERE_STORE_PASSWORD`, `VOICE_ANYWHERE_KEY_ALIAS`, and `VOICE_ANYWHERE_KEY_PASSWORD` environment variables.

Release boundary: do not publish or migrate this repository history publicly until the old committed signing material has been sanitized or explicitly accepted as burned history. Use only the rotated local release key for future release validation.

---

## Setup on Device

1. Install APK via adb or sideload
2. Open app → grant **Microphone**
3. Open app → grant **Notifications** for clipboard fallback alerts
4. Settings → Accessibility → **Voice Anywhere** → Enable
5. (Optional) Enable local transcript history if you want recent dictated text saved on this phone for copy/retry. It is off by default, and disabling it clears saved transcripts.
6. (Optional) Enter DeepSeek API key in app settings for AI polish. When enabled, dictated text is sent to DeepSeek before insertion. Leave blank for local-only dictation.

The floating `● MIC` pill will appear on screen.

---

## Usage

1. Open any app and tap an input field
2. Tap the `● MIC` pill
3. Speak through FUTO — pill turns `■ REC` while recording
4. FUTO returns text — pill shows `… AI` while processing
5. Text is inserted at cursor — pill flashes the insertion mode and returns to `● MIC`
6. If local history is enabled, open the app to copy, retry, delete, or clear recent transcript history

---

## Architecture

```
VoiceAccessibilityService   — core service, node tracking, injection logic
FloatingMicOverlay          — TYPE_ACCESSIBILITY_OVERLAY pill widget
InsertionFeedback           — mode → state/label/color mapping
TextInsertionMerger         — pure cursor-aware merge logic with unit tests
PillDrawable                — rounded pill background drawable
DictationActivity           — FUTO speech recognition bridge
TextPostProcessor           — offline text cleanup (spacing, caps)
DeepSeekTextPolisher        — optional AI grammar polish via API
DeepSeekKeyStore            — Android Keystore-backed API key storage
TranscriptHistoryStore      — opt-in local-only capped transcript history
ClipboardNotification       — private nudge notification for clipboard-only apps
```

---

## Known Limitations

- Some sandboxed web or custom editor fields may still fall back to clipboard-only
- FUTO Keyboard steals focus during STT; node snapshot is taken before mic tap to compensate
- Accessibility service restarts on app update — must re-enable manually
- Clipboard fallback notifications do not show transcript previews; the dictated text stays on the clipboard only
- DeepSeek polish is optional cloud processing; keep the API key blank for fully offline dictation

---

## Version History

| Version | Notes |
|---|---|
| 0.2.0 | Mode indicator (SET/PST/CPY), hint text fix, cursor-aware append, release signing |
| 0.1.0 | Initial build — basic overlay + injection |

---

*Built by [NODAYSIDLE](https://gitlab.com/NODAYSIDLE) — No Days Idle.*
