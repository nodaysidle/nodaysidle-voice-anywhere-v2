# Hybrid release flow

APK is built locally (Mac / Android Studio or an existing local build). CI only creates the GitHub Release with generated notes when a version tag is pushed.

1. **Build the APK locally** (debug APK is fine if that is what you ship today).
2. **Tag and push** a version tag (`v*`), e.g. `git tag v0.4.1 && git push origin v0.4.1`.
3. **CI creates the release** (`.github/workflows/release-on-tag.yml`) with generated notes — no APK build in CI.
4. **Attach the APK** (and optional `mapping.txt` if present) with `Scripts/attach-release-asset.sh`:

   ```bash
   Scripts/attach-release-asset.sh v0.4.1 ./path/to/app.apk
   Scripts/attach-release-asset.sh v0.4.1 ./path/to/app.apk ./path/to/mapping.txt
   ```

Existing Latest release is **v0.4.0**. This automation does not republish or replace it; a new `v*` tag is required for a new release.

No Play Store / AAB publishing and no CI Android build — APK stays local until attached.
