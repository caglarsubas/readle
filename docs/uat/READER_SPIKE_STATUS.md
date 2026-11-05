# Build 13 reader handoff

Updated 2026-11-05. The owner reports build 12's other items OK, while EPUB continuous scrolling locks with a selected word and page layout cannot turn while extending a selection. Those reports and accepted earlier checks remain in [UAT_RESULTS.md](UAT_RESULTS.md). Build 13 corrects the native gesture paths; physical Z Fold7 acceptance remains pending.

## Build

- Application ID: `com.readle.uat`; version code 13; version name `0.1.0-spike`.
- Minimum API 24; target 36. Standalone private APK: `output/android/readle-uat-13.apk`.
- APK SHA-256: `5e60b52114e8a3ebc0230928aabfd364351e3380ecc2f67d526eb23a96409f02`.
- Signer SHA-256: `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`, unchanged from builds 10–12.
- SQLite schema remains 2. This increment changes no stored books, sessions or annotations and adds no migration.

## Reader correction

Readium's old column-selection workaround blocked all viewport scrolling while a native selection existed. The local patch enables scrolling along the text axis in continuous layout while retaining the paginated-column guard. A public ActionMode delegate tracks the native handle-drag interval. It keeps Chromium's native selection and handles, extends the viewport at continuous-layout edges, and uses the existing Readium pager to turn at left/right text edges. It retains the opposite source boundary and stops on release. See [ADR 024](../adr/024-epub-selection-gestures.md).

The navigator is built from a checksum-verified immutable Readium 3.1.2 source commit. The complete patch and small local module are reviewable. Generated upstream sources stay in ignored build output; its license is packaged in APK assets. The runtime dependency pins are retained. A clean build downloads the verified source archive; the archive is cached for later builds. No Maven-cache binary patching or reflection into SDK private fields is used.

## Verification boundary

The earlier build-12 multi-screen test programmatically created a DOM range and then exercised save/reopen. That established storage behavior, not touch behavior. Build 13 adds actual long presses, finger swipes and native handle drags. Tests also preserve the selected word during scrolling, check both directions of page extension, stop after release, and compare a reopened rendered range to its saved source quote.

The same continuous-scroll gesture fails against build 12 on a Pixel Fold API 36 AVD and passes against the correction. Local verification passes TypeScript, ESLint, 3 React Native tests and all 17 native tests on Pixel 7 API 36. The native set includes 7 storage/import tests, 2 migration tests and 8 reader interactions. All 4 native selection gesture tests also pass on Pixel Fold API 36. These include finger scrolling, downward handle auto-scroll, both page directions, release-stop behavior and restored rendered source text. Results are retained with the private build receipt and native logs. Physical handle behavior on Z Fold7 / Android 17, named-hardware timings, full accessibility and iOS integration remain separate gates.

Existing independent annotations, blank new sessions, migration retention and the fit-width PDF source/reference view retain their build-12 contracts. EPUB is the primary reader. Selection across separate chapter/spine resources remains open; edge turning is clamped to the current resource. The owner-approved PDF advanced-authoring/reflow deferral remains in effect.

Use [the focused checklist](ANDROID_UAT.md) for B13-01..06. Install over build 12 with Update, retaining data. The APK includes its JavaScript bundle and runs without Metro or an AI key. A rollback must preserve schema 2, the signing identity and increasing version codes. Annotation export is not a complete library backup.
