# Build 16 — Android reader follow-up

Updated 2026-11-12. Build 16 restores draggable EPUB selection grips when WebView drops its range after a turn. Selected vertical edge swipes use the chapter pager; the sample import action reports each format's outcome and links to the existing books. [Build 16 evidence](BUILD_16_VISUAL_REVIEW.md).

The signed standalone APK is `output/android/readle-uat-16.apk`; SHA-256 `92a99dd630165aee66b660e35b72b502deecb80c0b78fe364c7f7a13d9492f30`. Application ID `com.readle.uat`, signer and SQLite schema 2 are unchanged. Install using **Update**, then run [B16-01..04](ANDROID_UAT.md) on both physical screens. The physical Continuous scroll failure was not reproduced on the API 36 baseline and requires a retest on the Z Fold7. A selection stays within a single EPUB chapter.

The foldable cover suite passed 28 native tests; four focused checks passed unfolded. Three focused checks passed again on the final APK after encoding the marker IDs. Android Studio showed the restored grips and the sample import results. Build 16 owner acceptance is pending.

## Build 15 handoff history

Updated 2026-11-07. Build 15 addresses VIS14-01: ordinary turns preserve the live range and its adjustment handles, while the complete source locator stays independent of the reading page. [Implementation and evidence](BUILD_15_VISUAL_REVIEW.md).

The private standalone APK is `output/android/readle-uat-15.apk`; SHA-256 `06e2f7bfd090d8f0367dbb560945ecc7b919b5a215cb97bd03aab7743e7dc794`. Application ID `com.readle.uat`, signing identity and SQLite schema 2 are unchanged. Install with **Update**, keeping application data. Fixtures and the archived [B15-01..07](BUILD_15_CHECKLIST.md) accompany it.

Engineering qualification passes: 23 native tests on Pixel 7 API 36, three consecutive synchronized swipe-to-handle replays without visual pauses, two native-handle tests per foldable screen with Android Studio observation, the complete-locator regression, nine storage/migration checks, four React Native tests, TypeScript, ESLint, native compilation and signing. Final-APK offline restart visibly retained the phone's two sessions and note; cover → inner → cover retained the foldable note and Pages preference. Earlier failed attempts and two host-emulator interruptions are recorded separately in the report.

The owner later failed B15-02 and B15-04 on Samsung Z Fold7 / Android 17; the remaining behavior was reported working. See [UAT results](UAT_RESULTS.md). Native iOS, accessibility, large-book coverage and named-hardware timings remain open. This handoff does not complete Phase A. Native selection across separate chapters remains open.

## Build 14 handoff history

Updated 2026-11-07. The Z Fold7 owner reported that build 13 extends selections forward but not backward, its two layouts feel alike, ordinary turns lose selections, annotation dialogs feel separate, Find is unavailable in EPUB, and the installed version is not visible. See [the owner record](UAT_RESULTS.md).

- Standalone APK: `output/android/readle-uat-14.apk` (private). Application ID `com.readle.uat`, version `0.1.0-spike`, version code 14, min API 24, target 36.
- APK SHA-256: `73060fe176c8a6fb882d0e40cad99d1e241d2576bc625e3d8b4fb54a1b5d99f2`.
- Signer SHA-256: `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`, unchanged from build 13.
- SQLite schema 2; no migration. Install with **Update** over build 13. Library → About shows the installed metadata.

## Reader changes

Continuous scroll pans vertically within reflowable EPUB chapters. A swipe started at the top or bottom moves to the adjacent chapter. Pages turns horizontally, including deliberate swipes that slow before release. The native selection-handle monitor compares the resting range and accepts neighboring text boundaries when extending backward or forward. An ordinary turn saves the selected passage in the session, where Selection can open, annotate or clear it. The note editor, notes list and selection actions are panels in the reader. New selections store their source DOM endpoints and verify the quote when reopening; this fixes a long range that previously resolved to empty on the folded emulator.

Find uses the local Readium search iterator and shows excerpts. Tapping a result opens and marks the source passage without creating a note. The EPUB source, format scope and session isolation remain the same. The [navigator source patch](../adr/025-reader-panels-and-page-navigation.md) is checksum pinned.

## Verification scope

The [2026-11-07 Android Studio follow-up](BUILD_14_VISUAL_FOLLOWUP.md), using owner-authorized precise input, completes both held-edge directions and foldable emulator restoration. Cover and inner screen checks exercise offline scrolling, chapter transitions, quote retention, note editing, system Back and Find. The saved note and Pages preference survive folding/unfolding and force-stop/reopen through Library. Delivered system Back closes Notes, withdrawing the earlier VIS14-02 suspicion. Adjustable selection handles still disappear after normal page turns (VIS14-01), so the full interaction gate remains open. The [initial visual attempt](BUILD_14_VISUAL_REVIEW.md) is retained as historical evidence of input limitations.

The app test exercises About using package metadata. Native touch tests distinguish the two layouts, navigate vertical chapter edges, drag both native handles, stop after release, retain a new selection through button/swipe turns, and save/reopen the rendered passage. Tests also exercise the in-reader note editor and search for a passage in a different EPUB chapter, including a no-results query. The private receipt retains exact APK, emulator and CI evidence.

Physical Z Fold7 / Android 17 testing is open. Historical checklist: [B14-01..07](BUILD_14_CHECKLIST.md) on both screens, including your own EPUB. Continuous scroll has discrete transitions at chapter boundaries; native range extension across separate resources is still open. Fixed-layout EPUBs stay paginated. Native iOS and named-hardware accessibility and timing gates are separate.
