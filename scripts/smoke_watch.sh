#!/bin/bash
# VoiceAnywhere — Cross-App Smoke Matrix Live Monitor
# Run this on your Mac while doing the smoke tests.
# It shows the insertion result for every dictation in real time.

DEVICE="52151JEKB14522"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║    VoiceAnywhere — Smoke Matrix Live Monitor         ║"
echo "║    setText=true  → ✓ SET  (auto-insert worked)      ║"
echo "║    paste=true    → ✓ PST  (paste fallback worked)   ║"
echo "║    both=false    → ↗ CPY  (clipboard notification)  ║"
echo "║    no field      → NO FIELD (recording blocked)     ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

adb -s "$DEVICE" logcat -v time -c 2>/dev/null
adb -s "$DEVICE" logcat -v time 2>/dev/null \
  | grep --line-buffered -E \
    "Insert attempt|Dictation blocked|VoiceAccessibility|TextPostProcessor|DeepSeekPolisher" \
  | while IFS= read -r line; do
      if echo "$line" | grep -q "setText=true"; then
        echo "✓ SET          │ $line"
      elif echo "$line" | grep -q "paste=true"; then
        echo "✓ PST          │ $line"
      elif echo "$line" | grep -q "setText=false.*paste=false"; then
        echo "↗ CPY          │ $line"
      elif echo "$line" | grep -q "Dictation blocked"; then
        echo "NO FIELD       │ $line"
      else
        echo "   INFO        │ $line"
      fi
  done
