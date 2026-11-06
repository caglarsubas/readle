# Build 14 reader handoff

Updated 2026-11-06. The Z Fold7 owner reported that build 13 extends selections forward but not backward, its two layouts feel alike, ordinary turns lose selections, annotation dialogs feel separate, Find is unavailable in EPUB, and the installed version is not visible. See [the owner record](UAT_RESULTS.md).

- Standalone APK: \`output/android/readle-uat-14.apk\` (private). Application ID \`com.readle.uat\`, version \`0.1.0-spike\`, version code 14, min API 24, target 36.
- APK SHA-256: \`16669e47244713a0cc30c78b64e4a6b57f935d25b26607c71b1c5284d80f548e\`.
- Signer SHA-256: \`0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5\`, unchanged from build 13.
- SQLite schema 2; no migration. Install with **Update** over build 13. Library → About shows the installed metadata.

## Reader changes

Continuous scroll pans vertically within reflowable EPUB chapters. A swipe started at the top or bottom moves to the adjacent chapter. Pages turns horizontally, including deliberate swipes that slow before release. The native selection-handle monitor compares the resting range and accepts neighboring text boundaries when extending backward or forward. An ordinary turn saves the selected passage in the session, where Selection can open, annotate or clear it. The note editor, notes list and selection actions are panels in the reader.

Find uses the local Readium search iterator and shows excerpts. Tapping a result opens and marks the source passage without creating a note. The EPUB source, format scope and session isolation remain the same. The [navigator source patch](../adr/025-reader-panels-and-page-navigation.md) is checksum pinned.

## Verification scope

The app test exercises About using package metadata. Native touch tests distinguish the two layouts, navigate vertical chapter edges, drag both native handles, stop after release, retain a new selection through button/swipe turns, and save/reopen the rendered passage. Tests also exercise the in-reader note editor and search for a passage in a different EPUB chapter, including a no-results query. The private receipt retains exact APK, emulator and CI evidence.

Physical Z Fold7 / Android 17 testing is open. Run [B14-01..07](ANDROID_UAT.md) on both screens, including your own EPUB. Continuous scroll has discrete transitions at chapter boundaries; native range extension across separate resources is still open. Fixed-layout EPUBs stay paginated. Native iOS and named-hardware accessibility and timing gates are separate.
