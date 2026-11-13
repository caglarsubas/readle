# Readle

Readle helps learners follow a recommended walkthrough through a book's ideas, with independent, resumable sessions and direct access to the original source. The product blueprint is `AI_Reading_Learning_Platform_Codex_Spec_v1.3.md` plus the accepted saved-session and Android UAT additions.

Android Build 17 includes a curated walkthrough for *Identity Management for Agentic AI* (arXiv:2510.25819v1). The learner chooses a goal and experience level, works through a recommended lesson sequence, and can inspect the cited PDF pages. The EPUB reader, PDF reference view and local sessions are also available. The local development harness can generate three-pass walkthroughs from text PDFs. Mobile generation, the native iOS reader, AI chat and sync are pending. [Whole-document walkthrough test](docs/uat/AUTOMATIC_WALKTHROUGH.md).

## Run and build

Use Node 22 or newer, JDK 17 or 21, Android SDK 36, Build Tools 36.0.0 and NDK 27.1.12297006. `ANDROID_HOME` must point to your SDK. A complete Xcode installation is needed for subsequent iOS work.

```sh
cd apps/mobile
npm ci
npm run typecheck
npm test -- --runInBand
cd ../..
./scripts/build-android.sh 17
```

The build script creates a private UAT signing identity under `~/.local/share/readle`, reuses it on later builds, and writes a standalone APK under `output/android`. Preserve that signing directory securely for future updates. Increase the build number for each handoff. Install using Android's file installer or `adb install -r output/android/readle-uat-17.apk`.

## Install the current Android UAT build

Install Build 17 using **Update**, or run `./scripts/install-android-uat.sh 17` with one authorized phone connected by USB. Confirm the build in **About**. Import your copy of `2510.25819v1.pdf` and select **Plan a walkthrough**. [Build 17 checklist](docs/uat/BUILD_17_WALKTHROUGH.md) · [Build 16 reader checklist](docs/uat/ANDROID_UAT.md).

## Try it

Open **Add sample books**, select an EPUB, and start reading. Each new session starts at the beginning with no copied notes, highlights, selection or assistant state. Add a note, return to Library and create another session for the same book. It starts blank. Resume the first session to return to its own work.

New reflowable EPUB sessions use vertical scrolling. In **Continuous scroll**, swipe up or down, then swipe again at a chapter edge to continue. **Pages** uses horizontal turns. The layout is saved per session. Drag an end/start handle to the right/left text edge to extend the selection into the next/previous page of the current chapter. An ordinary page turn keeps the passage available from **Selection**. Open or edit notes and highlights in the reader panel. **Find** searches EPUB text offline and marks the chosen result in the book.

PDF is labelled **Source/reference** and always fits the current window width. It retains page/vertical position and its session’s existing annotations. A short tap outside a temporary selection clears it. **Open original** opens a local PDF viewer of your choice for advanced PDF controls. PDF reflow and cross-page authoring are deferred. Existing multi-page annotations remain readable.

On upgrading from build 10/11, formerly shared notes are preserved as independent copies in already-existing sessions because the old database did not record their originating session. New sessions created after the upgrade remain empty. See [ADR 022](docs/adr/022-session-annotations.md) and [ADR 023](docs/adr/023-epub-first.md).

See [the Android checklist](docs/uat/ANDROID_UAT.md) and [implementation plan](docs/IMPLEMENTATION_PLAN.md). The sample books are self-authored CC0 fixtures. Regenerate them with Python and ReportLab using `python scripts/generate_fixtures.py`.

The Android build materializes a checksum-pinned Readium navigator source archive and applies the small patch described in [ADR 024](docs/adr/024-epub-selection-gestures.md). The initial clean build requires network access; the source archive is cached for subsequent builds.
