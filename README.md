# Readle

An offline reader with independent, resumable workspaces for each book. The product blueprint is `AI_Reading_Learning_Platform_Codex_Spec_v1.3.md` plus the accepted saved-session and Android UAT additions.

The current deliverable is an Android reader spike. It opens original PDF pages and EPUBs, imports multiple files independently, persists sessions in SQLite, and stores source-linked highlights and notes. The React Native iOS shell is present. The native iOS reader, AI, guided learning, backend, and sync milestones are pending. Phase A requires physical iOS and Android evidence before acceptance.

## Run and build

Use Node 22 or newer, JDK 17 or 21, Android SDK 36, Build Tools 36.0.0 and NDK 27.1.12297006. `ANDROID_HOME` must point to your SDK. A complete Xcode installation is needed for subsequent iOS work.

```sh
cd apps/mobile
npm ci
npm run typecheck
npm test -- --runInBand
cd ../..
./scripts/build-android.sh 11
```

The build script creates a private UAT signing identity under `~/.local/share/readle`, reuses it on later builds, and writes a standalone APK under `output/android`. Preserve that signing directory securely for future updates. Increase the build number for each handoff. Install using Android's file installer or `adb install -r output/android/readle-uat-10.apk`.

## Install the current Android UAT build

Build 10 is the current private handoff. With one authorized Android phone connected by USB, run `./scripts/install-android-uat.sh 10` from the repository root. The installer verifies the APK checksum, retains existing app data, copies the import-test fixtures and opens Readle. See [the phone setup and UAT checklist](docs/uat/ANDROID_UAT.md). Use the same signing identity and a build number higher than 10 for the next update.

## Try it

Open **Add sample books**, select either publication, and start reading. Navigate to a later page or chapter. Return to Library and start a second session. Force-stop and reopen, select that book, and choose either session. In PDF, long press text and drag a selection endpoint, then use **Selection** to highlight, note, copy, look up, or explicitly search Google. Use **Extend to next page** for a two-page selection. EPUB uses native Readium selection handles and its contextual Highlight/Note actions. **Notes** opens shared annotations and JSON export.

See `docs/uat/ANDROID_UAT.md` for acceptance instructions and `docs/IMPLEMENTATION_PLAN.md` for the remaining milestones. The sample books are self-authored CC0 fixtures. Regenerate them with Python and ReportLab using `python scripts/generate_fixtures.py`.
