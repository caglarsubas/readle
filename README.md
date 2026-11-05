# Readle

An offline reader with independent, resumable workspaces for each book. The product blueprint is `AI_Reading_Learning_Platform_Codex_Spec_v1.3.md` plus the accepted saved-session and Android UAT additions.

The current deliverable is an EPUB-first Android reader, with PDF retained as an original-source reference view. It opens original PDF pages and EPUBs, imports multiple files independently, persists sessions in SQLite, and stores source-linked highlights and notes independently in each session. The React Native iOS shell is present. The native iOS reader, AI, guided learning, backend, and sync milestones are pending. Phase A requires physical iOS and Android evidence before acceptance.

## Run and build

Use Node 22 or newer, JDK 17 or 21, Android SDK 36, Build Tools 36.0.0 and NDK 27.1.12297006. `ANDROID_HOME` must point to your SDK. A complete Xcode installation is needed for subsequent iOS work.

```sh
cd apps/mobile
npm ci
npm run typecheck
npm test -- --runInBand
cd ../..
./scripts/build-android.sh 13
```

The build script creates a private UAT signing identity under `~/.local/share/readle`, reuses it on later builds, and writes a standalone APK under `output/android`. Preserve that signing directory securely for future updates. Increase the build number for each handoff. Install using Android's file installer or `adb install -r output/android/readle-uat-12.apk`.

## Install the current Android UAT build

Build 12 is the current private handoff. It applies build 11 feedback: blank new sessions, isolated annotations, EPUB continuous scroll and a fit-width PDF reference view. Build 10 passed the product owner’s Android UAT on a Samsung Z Fold7 running Android 17. With one authorized Android phone connected by USB, run `./scripts/install-android-uat.sh 12` from the repository root. The installer verifies the APK checksum, retains existing app data, copies the import-test fixtures and opens Readle. See [the phone setup and UAT checklist](docs/uat/ANDROID_UAT.md). Use the same signing identity and a build number higher than 12 for the next update.

## Try it

Open **Add sample books**, select an EPUB, and start reading. Each new session starts at the beginning with no copied notes, highlights, selection or assistant state. Add a note, return to Library and create another session for the same book. It starts blank. Resume the first session to return to its own work.

New EPUB sessions use continuous scrolling. **Layout** switches between continuous scroll and pages; each session remembers its choice. In scroll mode, extend a native selection beyond the screen boundary within the chapter, then choose Highlight or Note. Tap a saved highlight for object actions. **Notes** lists and exports only this session’s annotations.

PDF is labelled **Source/reference** and always fits the current window width. It retains page/vertical position and its session’s existing annotations. A short tap outside a temporary selection clears it. **Open original** opens a local PDF viewer of your choice for advanced PDF controls. PDF reflow and cross-page authoring are deferred. Existing multi-page annotations remain readable.

On upgrading from build 10/11, formerly shared notes are preserved as independent copies in already-existing sessions because the old database did not record their originating session. New sessions created after the upgrade remain empty. See [ADR 022](docs/adr/022-session-annotations.md) and [ADR 023](docs/adr/023-epub-first.md).

See [the Android checklist](docs/uat/ANDROID_UAT.md) and [implementation plan](docs/IMPLEMENTATION_PLAN.md). The sample books are self-authored CC0 fixtures. Regenerate them with Python and ReportLab using `python scripts/generate_fixtures.py`.
