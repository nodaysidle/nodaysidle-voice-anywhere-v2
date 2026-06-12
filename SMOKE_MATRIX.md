# VoiceAnywhere Cross-App Smoke Matrix

**Purpose:** Determine which apps get `✓ SET` (ACTION_SET_TEXT), `✓ PST` (ACTION_PASTE fallback), or `↗ CPY` (clipboard-only fallback) so we know where users need to manually paste.

**How insertion works:**
1. Always sets clipboard first (safety net)
2. Tries `ACTION_SET_TEXT` on the focused `EditText` node
3. Falls back to `ACTION_PASTE` if SET_TEXT fails (e.g. custom views)
4. If both fail, keeps the text on the clipboard and posts a persistent private notification with no transcript preview
5. Overlay shows `✓ SET`, `✓ PST`, or `↗ CPY`
6. If no editable field is focused before tapping mic, overlay shows `NO FIELD` and does not launch FUTO
7. If FUTO shows its language picker, Voice Anywhere auto-selects the overlay language without requiring a user tap

---

## Test Protocol (per app)

For each app, run **3 passes**:

| Pass | Scenario | What to check |
|------|----------|---------------|
| A | Empty field, tap once to focus → dictate | Cursor at start, text appears clean |
| B | Existing text, tap to mid-word position → dictate | Text spliced at cursor, no duplication |
| C | Dictate filler sentence: *"ej allora basically so we need to ship this"* | Fillers stripped, brand terms capitalized |

**Record for each:**
- Feedback badge shown: `✓ SET`, `✓ PST`, `↗ CPY`, or `NO FIELD`
- logcat line: `Insert attempt setText=true/false paste=true/false`
- Any visual glitch (cursor jump, text replaced, extra space)

---

## App Matrix

### 1. Google Keep (Notes)
- **Node type expected:** `android.widget.EditText`
- **Prediction:** ✓ SET (SET_TEXT confirmed in previous session)
- **Known:** cursor-aware tested, works at position 110 and 180

| Pass | Result | Badge | setText | paste | Notes |
|------|--------|-------|---------|-------|-------|
| A    |        |       |         |       |       |
| B    |        |       |         |       |       |
| C    |        |       |         |       |       |

---

### 2. Telegram (message input)
- **Node type expected:** `androidx.compose.ui.platform.ComposeView` or `android.widget.EditText`
- **Prediction:** ⚠️ Uncertain — Telegram uses a custom composer; may need paste fallback
- **Risk:** Compose text fields may block SET_TEXT

| Pass | Result | Badge | setText | paste | Notes |
|------|--------|-------|---------|-------|-------|
| A    |        |       |         |       |       |
| B    |        |       |         |       |       |
| C    |        |       |         |       |       |

---

### 3. Chrome (browser — address bar)
- **Node type expected:** `android.widget.EditText` (URL bar) or WebView content
- **Prediction:** ↗ CPY for WebView body, ✓ SET for address bar
- **Risk:** Chrome's address bar may intercept focus before accessibility gets it

| Location | Pass | Result | Badge | setText | paste | Notes |
|----------|------|--------|-------|---------|-------|-------|
| Address bar | A |   |       |         |       |       |
| Address bar | B |   |       |         |       |       |
| Google Docs (web) | A |  |  |         |       |       |
| Google Docs (web) | B |  |  |         |       |       |
| Any text input field | A | | |        |       |       |

---

### 4. Google Messages (SMS)
- **Node type expected:** `android.widget.EditText`
- **Prediction:** ✓ SET

| Pass | Result | Badge | setText | paste | Notes |
|------|--------|-------|---------|-------|-------|
| A    |        |       |         |       |       |
| B    |        |       |         |       |       |

---

### 5. Gmail (compose)
- **Node type expected:** `android.widget.EditText` body, custom subject line
- **Prediction:** ✓ SET for body, ⚠️ uncertain for subject

| Location | Pass | Result | Badge | setText | paste |
|----------|------|--------|-------|---------|-------|
| Subject  | A    |        |       |         |       |
| Body     | A    |        |       |         |       |
| Body     | B    |        |       |         |       |

---

### 6. WhatsApp (message input)
- **Node type expected:** Custom rich text editor (not standard EditText)
- **Prediction:** ↗ CPY — WhatsApp historically blocks SET_TEXT

| Pass | Result | Badge | setText | paste | Notes |
|------|--------|-------|---------|-------|-------|
| A    |        |       |         |       |       |
| B    |        |       |         |       |       |

---

### 7. Search bars (Google, Play Store)
- **Node type expected:** `android.widget.EditText`
- **Prediction:** ✓ SET but may lose focus on mic tap

| App     | Pass | Result | Badge | setText | paste |
|---------|------|--------|-------|---------|-------|
| Google  | A    |        |       |         |       |
| Play Store | A |       |       |         |       |

---

## Logcat Monitoring Command

Run this on your Mac while testing — it streams the insertion result for every dictation:

```bash
adb -s 52151JEKB14522 logcat -v time VoiceAnywhereService:D '*:S' | grep -E "Insert attempt|Dictation blocked|setText|paste"
```

Expected output format:
```
D VoiceAnywhereService: Insert attempt setText=true paste=false cursor=0 existingLength=0 textLength=42
D VoiceAnywhereService: Insert attempt setText=false paste=true cursor=0 existingLength=0 textLength=42
```

`setText=true` → auto-insert worked → badge shows `✓ SET`
`setText=false paste=true` → paste fallback worked → badge shows `✓ PST`
`setText=false paste=false` → clipboard-only fallback → badge shows `↗ CPY` and posts a private notification without transcript preview
`Dictation blocked: no focused editable field` → badge shows `NO FIELD`

---

## Results Summary (fill in after testing)

| App | Location | Auto-Insert | Fallback | Status |
|-----|----------|-------------|----------|--------|
| Google Keep | Notes body | ✅ SET_TEXT | — | ✓ SET |
| Google Docs (app) | Doc body | ❌ | ✅ ACTION_PASTE | ✓ PST |
| Telegram | Message input | ✅ SET_TEXT | — | ✓ SET |
| WhatsApp | Message input | ✅ SET_TEXT | — | ✓ SET |
| Gmail | Compose body | ✅ SET_TEXT | — | ✓ SET |
| Comet Browser | Assistant/navigation field | ✅ SET_TEXT | — | ✓ SET |
| Chrome | — | — | — | SKIPPED (not used) |
| Google Messages | Message input | ? | ? | TODO |
| Play Store | Search bar | ? | ? | TODO |

---

## Decision Rules (post-testing)

Based on results, we'll implement:

- **Tier 1 (auto-insert works):** No changes needed. Badge shows `✓ SET`.
- **Tier 2 (paste fallback works):** Badge shows `✓ PST`. Clipboard is also set as a safety net.
- **Tier 3 (both fail):** Badge shows `↗ CPY` and the persistent notification says the text is copied without exposing the transcript preview.
- **No target:** Badge shows `NO FIELD`, and no recording starts.

Apps confirmed Tier 3 should be listed as manual-paste apps in README.

---

*Last updated: 2026-05-16 | VoiceAnywhere V2*
