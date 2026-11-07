# Build 14 — Android Studio visual review

Performed 2026-11-07 at the owner's request to inspect the actual reader UI and gestures. **Historical first attempt:** the [completed follow-up](BUILD_14_VISUAL_FOLLOWUP.md) supersedes its held-edge, foldable and Back findings. Precise input with Android Studio observation passes held-edge turns and foldable restoration; delivered system Back closes Notes, so VIS14-02 is withdrawn. VIS14-01 (adjustable selection continuity after ordinary turns) remains open. The observations below preserve the original attempt and its input limitations.

## Method and environment

Used the existing Android Studio application on macOS and its visible Running Devices screens. Opened the installed standalone Readle application and operated it through clicks, long presses, drags, on-screen keyboard keys, page controls and emulator toolbar controls. Inspected screenshots after actions. No ADB input or instrumented test drove this visual review. Previous automated results remain separate evidence.

- Installed About screen: `Version 0.1.0-spike · Build 14`, confirmed on the phone and foldable.
- APK SHA-256: `73060fe176c8a6fb882d0e40cad99d1e241d2576bc625e3d8b4fb54a1b5d99f2`.
- Source checkout: `846574e56cb1e5fd8e327ca34dc22190721e1b95`.
- Phone: `Readle_UAT_API36`, Pixel 7 AVD, Android 16 / API 36.
- Foldable: `Readle_Fold_UAT_API36`, Pixel Fold AVD, Android 16 / API 36, inner screen; fold/cover testing stopped at the Android lock screen.
- Book: installed self-authored `readle-sample` EPUB. Created phone Session 2 for this test and saved a synthetic note, `rest`, on `appearance`. Existing sessions were left in place. Started the first EPUB session on the foldable.

These are emulator observations, not Samsung Z Fold7 / Android 17 or iOS acceptance. The IDE's pre-existing unrelated project remained open; its app was not run or modified. Readle was launched from the emulator's launcher.

## Observations against B14-01..07

| Check | Result for this visual run | Observed behavior and limits |
|---|---|---|
| B14-01 | Partial | About visibly reports build 14. Existing library/session data is present. No APK upgrade was performed during this run. |
| B14-02 | Partial | Continuous scroll visibly pans text vertically on the phone and inner screen, including after a native word selection. The phone Selection panel retains `appearance` after scrolling. Pages visibly turns with horizontal swipes in both directions. Chapter-edge transitions were not completed in this run. |
| B14-03 | Blocked for full acceptance | Dragging end/start handles extends the visible range forward/backward within the current page. Attempts at the edges did not demonstrate a page turn. The available drag operation does not expose a held endpoint dwell, so this is insufficient evidence to pass or reject the required hold-at-edge behavior. Both edge directions remain open. |
| B14-04 | Quote retention observed; UX gap open | Next/Previous buttons and forward/backward horizontal swipes retain the selected words. After returning, the source shows a pale working highlight and the status says `Selection kept`; Selection still contains the quote. However, the native handles disappear after a normal page turn, preventing immediate continued adjustment of that active selection. This is distinct from retaining a quote in session storage. |
| B14-05 | Partial / failed Back subcheck | Selection, note entry, saved highlight actions and Notes are inside the reader. A note was entered with the visible keyboard, saved, listed, reopened as an actionable highlight, and Open source returned to the yellow-highlighted word. Android Studio's device Back control was pressed repeatedly with Notes open; the panel remained visible. Its Close button worked. Editing an existing note was not completed. |
| B14-06 | Core search observed; exact checklist incomplete | `note` returned 18 excerpt results. Tapping the first opened chapter 1 and marked `note` in the source. `sum` showed `No matches found.` The prescribed chapter-2 phrase and an airplane-mode phone search were not repeated, so this does not complete the full offline/cross-chapter checklist. The foldable already displayed airplane mode, but Find was not exercised there. |
| B14-07 | Blocked / incomplete | The inner screen rendered and scrolled the EPUB with an active word selection. Folding reached Android's cover lock screen; repeated available swipe gestures did not dismiss it. Unfolding retained the lock screen, so restoration after unlocking, the full cover checks and force-stop/reopen were not verified. No app fold/unfold pass is inferred. |

The phone's emulator keyboard initially used Gboard handwriting input. Its visible “Show on-screen keyboard” action enabled query and note entry. This input setup issue is separate from Readle's note editor. The floating keyboard left the note field and Save control accessible; a regular docked keyboard layout was not qualified.

## Open follow-up

1. **VIS14-01 — selection continuity:** preserve or explicitly restore adjustable boundaries after page navigation, then visually extend the same selection onto the preceding and following page. Acceptance requires both directions, a fixed opposite endpoint, and stopping after release. Quote persistence alone does not close this item.
2. **VIS14-02 — Android Back:** reproduce and fix panel dismissal with the keyboard closed, including any interaction between the reader's native selection mode and the panel. Verify Notes, selection actions and note editing close to the same passage. The callback's presence in source is not a pass.
3. Complete the unobserved chapter-edge, held-edge, offline Find, cover-screen, restart and upgrade subchecks. Resolve the emulator input limitation or use an interactive physical device; do not classify the lock-screen blockage as a Readle defect without evidence.

For subsequent handoffs, run and record a visible Android Studio gesture review in addition to automated checks. Name the exact build, emulator/device, layout, actions, observations and open checks. Keep physical owner UAT separate.

## Saved screenshots

Captured through Android Studio's **Take Screenshot → Save** UI. These are original guest screenshots; the source passage repeats intentionally in the fixture. The screenshots support the observed states, while the action sequence above records the transitions.

- [Find opens and marks the source](evidence/build-14-visual/find-source.png).
- [Quote retained after navigating away and back; handles absent](evidence/build-14-visual/selection-after-page-turn.png).
- [Notes still open after Android Back; saved test note visible](evidence/build-14-visual/notes-after-android-back.png).

The larger emulator lock-screen screenshot is retained privately at `output/android/build-14-visual/fold-lock-screen.png`. No new APK was built or delivered during this review.
