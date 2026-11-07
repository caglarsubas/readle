# Build 14 — completed gesture and foldable follow-up

Performed 2026-11-07 in the installed Android Studio application, after the owner explicitly authorized ADB and native touch instrumentation with visual verification. This completes the previously blocked held-edge and foldable emulator checks. The broader reader acceptance gate remains open for VIS14-01 and physical-device UAT.

## Build and method

- Installed application: `com.readle.uat`, version `0.1.0-spike`, version code **14**. The production APK was unchanged throughout this follow-up.
- APK SHA-256: `73060fe176c8a6fb882d0e40cad99d1e241d2576bc625e3d8b4fb54a1b5d99f2`; application source commit `846574e56cb1e5fd8e327ca34dc22190721e1b95`. The installed `base.apk` was pulled and its hash matches the delivered APK.
- Visible device: Android Studio Running Devices, `Readle_Fold_UAT_API36`, Pixel Fold AVD, Android 16 / API 36, `emulator-5554`. Inner screen **2208 × 1840**; cover screen **1080 × 2092**.
- Fixture: isolated imports of the self-authored sample EPUB, titled **Highlight UI fixture**. Its repeated paragraphs are intentional. Synthetic notes contain no personal book content.
- Offline: airplane mode `1`, Wi-Fi disabled, active default network `none`, verified using Android system diagnostics.

The native tests deliver real MotionEvent long presses and handle drags, including the edge dwell and release. Android Studio screenshots were inspected at opt-in checkpoints before and after gestures. Tests also check viewport movement, selected text, opposite endpoints, SQLite state and rendered decorations. Find and note text are entered using accessibility actions; these follow-up tests do not qualify keyboard ergonomics.

Studio's Fold/Unfold menu controls posture. ADB wakes the emulator and dismisses its default swipe keyguard after posture changes. The system Back action is delivered through Android UI Automation. Android Studio toolbar/key-event attempts are retained as earlier input failures, rather than treated as app results.

## Results

| Behavior | Cover | Inner | Evidence / boundary |
|---|---|---|---|
| Continuous scrolling with a native word selection | Pass | Pass | Finger scroll moves the viewport while retaining `appearance`; it can then be saved as a highlight. |
| Distinct layouts and chapter edges | Pass | Pass | Scroll moves vertically, crosses to chapter 2 at its bottom, and returns at the top. Pages moves horizontally. Chapter transitions remain discrete. |
| End handle held at the right edge | Pass | Pass | The viewport turns forward and the range grows from `appearance`. Saving and recreating the reader restores the same rendered range. |
| Start handle held at the left edge | Pass | Pass | The viewport turns backward and the extended quote still ends at the original `appearance` endpoint. |
| Release after either held-edge gesture | Pass | Pass | Page offset and selected quote stay fixed for the 1.4-second post-release assertion. |
| Ordinary button/swipe navigation | Quote retained; UX gap | Quote retained; UX gap | The selected passage survives navigation and can be annotated or reopened. Native adjustment handles disappear after an ordinary turn: **VIS14-01 remains open**. |
| In-reader note save, edit, Open source and Back | Pass | Pass | `Fold visual UAT note` is edited to `Fold visual UAT edited`; Open source returns to highlighted `appearance`. Delivered system Back closes Notes without leaving the passage. |
| Offline Find | Pass | Pass | A missing query shows `No matches found.`; `hypothetical linear response` opens chapter 2 with the exact phrase marked. |
| Fold/unfold and offline restart | Pass | Pass | Through Library → book → Continue latest, the saved `appearance` note and Pages preference survive force-stop/reopen, the cover-to-inner transition, and folding back to the cover. |

**VIS14-02 is withdrawn as an unconfirmed app defect.** The earlier toolbar Back attempts left Notes visible, but a delivered Android system Back action closes it on both screens with the same installed APK. No app fix was needed or made for this finding.

## Reproduction and evidence

The test harness supports `readleVisualPauseMs` (0 by default, at most 30 seconds per checkpoint) and `readleKeepVisualFixture=true` for preserving the synthetic note workspace for a real force-stop/reopen through Library. Ordinary test runs clean up their fixtures. Tests live in `apps/mobile/android/app/src/androidTest/java/com/readle/reader/HighlightInteractionTest.kt`.

Example, after installing the release Android test APK onto the visible emulator:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e readleVisualPauseMs 15000 \
  -e class 'com.readle.reader.HighlightInteractionTest#nativeEndHandleTurnsNextPageAndSavesItsExtendedRange,com.readle.reader.HighlightInteractionTest#nativeStartHandleTurnsPreviousPageAndKeepsTheEndAnchor' \
  com.readle.uat.test/androidx.test.runner.AndroidJUnitRunner
```

JUnit output must say `OK`; the shell exit code alone is insufficient. The final runs pass **7 focused native tests per screen, 14 executions total**. This count covers the specific assertions, including quote retention; it does not close VIS14-01. The release Android test APK compiled successfully using Java 21. Original attempts and complete local captures are retained under `output/android/build-14-visual-followup/`. Two early Notes attempts failed because of input delivery and a harness assumption that Save left highlight actions open. The harness now delivers system Back and reopens Notes before Open source; the final Notes checks pass. Production reader code and APK were unchanged.

Saved guest screenshots and native logs support the live Android Studio observations:

- Inner: [forward before](evidence/build-14-visual-followup/inner-end-before.png), [forward extended and stopped](evidence/build-14-visual-followup/inner-end-stopped.png), [backward before](evidence/build-14-visual-followup/inner-start-before.png), [backward extended and stopped](evidence/build-14-visual-followup/inner-start-stopped.png).
- Cover: [forward before](evidence/build-14-visual-followup/cover-end-before.png), [forward extended and stopped](evidence/build-14-visual-followup/cover-end-stopped.png), [backward before](evidence/build-14-visual-followup/cover-start-before.png), [backward extended and stopped](evidence/build-14-visual-followup/cover-start-stopped.png).
- Saved workspace: [cover after restart](evidence/build-14-visual-followup/cover-restarted-notes.png), [inner after restart](evidence/build-14-visual-followup/inner-restarted-notes.png), [cover after folding back](evidence/build-14-visual-followup/cover-refolded-notes.png).
- Runs: [cover held edges, 2 passed](evidence/build-14-visual-followup/cover-held-edges.txt), [cover reader checks, 5 passed](evidence/build-14-visual-followup/cover-reader-final.txt), [inner selection checks, 4 passed](evidence/build-14-visual-followup/inner-selection-final.txt), [inner Find, panels and layout, 3 passed](evidence/build-14-visual-followup/inner-reader-checks.txt).
- Offline Find: [cover](evidence/build-14-visual-followup/cover-find-chapter-two.png), [inner](evidence/build-14-visual-followup/inner-find-chapter-two.png). Notes closed after delivered system Back: [cover](evidence/build-14-visual-followup/cover-notes-back-closed.png), [inner](evidence/build-14-visual-followup/inner-notes-back-closed.png).
- VIS14-01 retained quote without handles: [cover](evidence/build-14-visual-followup/cover-selection-after-button-turns.png), [inner](evidence/build-14-visual-followup/inner-selection-after-button-turns.png).
- [Hashed receipt, environment, session IDs and result boundaries](evidence/build-14-visual-followup/receipt.json). Earlier failed harness attempts: [Back input](evidence/build-14-visual-followup/cover-reader-checks.txt), [Open source sequencing](evidence/build-14-visual-followup/cover-panels-find.txt).

## Remaining acceptance and rollback

- **VIS14-01:** restore adjustable boundaries after ordinary page navigation. Saving a quote does not satisfy that interaction.
- Native selection across separate EPUB chapters/resources remains open.
- Physical Samsung Z Fold7 / Android 17, own-book testing, iOS, accessibility and named-hardware performance remain independent gates. No new APK upgrade was performed in this follow-up; the earlier build-14 upgrade evidence remains separate.
- No new APK or schema change is delivered here. Existing schema-2 rollback guidance still applies: use a compatible fix-forward APK with the same signer and a higher version code; preserve app data.
