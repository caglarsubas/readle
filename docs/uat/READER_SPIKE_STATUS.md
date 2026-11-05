# Reader spike and highlight-action handoff

Updated 2026-11-05. Build 10 passed the product owner's Android UAT on Samsung Z Fold7 / Android 17. Build 11 addresses that feedback with saved highlight objects and PDF interaction improvements. Phase A remains open for native iOS and the remaining reader evidence. [User results and feedback](UAT_RESULTS.md).

## Build 11

- Application ID: `com.readle.uat`.
- Version code: 11. Version name: `0.1.0-spike`. Minimum Android API: 24. Target: 36.
- Private APK: `output/android/readle-uat-11.apk`, with embedded JavaScript bundle. It starts without Metro, a backend, an account or an AI key.
- APK SHA-256: `04789bac508fbc76236eda245b0f37e03a825dc766a4cf424c00fe967b69b326`.
- Signing certificate SHA-256: `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`, unchanged from build 10.
- SQLite remains at schema 1. Existing book/session/annotation IDs and anchors are retained. The new PDF horizontal offset defaults to zero for a build 10 session.
- Keys and passwords remain outside the repository in the builder's private `~/.local/share/readle` directory.

## Feedback implementation

A saved gold highlight can be tapped in PDF and EPUB. The same object menu is available through Notes. It shows the quote/note and offers Add/Edit note, Copy quote, Share quote, Open source and confirmed Delete. Editing updates the existing object without changing its anchor. Writes are scoped to book and edition, with stale-note detection that keeps the current editor draft. Deletion clears matching saved selections across the book's sessions while retaining their reading cursors and background last-activity order. PDF overlapping objects get a chooser and either half of a two-page annotation resolves the same ID.

PDF now supports pinch preview/commit and double-tap zoom, clearer Zoom controls, fit to the actual reading-window width, and persisted horizontal/vertical offsets. Toolbar buttons use equal widths and 48 dp minimum height. The source PDF's typography and layout remain original. EPUB retains native Readium selection and reflow.

## Local engineering evidence

Checks run on an isolated Pixel 7 Android API 36 arm64 emulator. Native tests use separate storage fixtures or uniquely identified temporary publications, with cleanup scoped to those fixtures. This is independent of the owner's physical-device UAT.

| Check | Result |
|---|---|
| TypeScript and ESLint | Pass |
| React Native session UI regressions | 3 tests pass |
| SQLite/import persistence | 7 tests pass |
| Real native reader interaction tests | 3 tests pass |
| Standalone release build, APK version and signer verification | Pass |
| PDF double-tap, both scroll axes restored after reader recreation, tap/edit/delete | Pass in native interaction test |
| PDF pinch commit and per-highlight accessibility action | Pass in native interaction test |
| EPUB saved highlight tap, font reflow, copy action and reopen | Pass in native interaction test |
| Original source anchor and single annotation identity after edit | Pass |
| Wrong-book/stale edits rejected, deleted selection cleared across sessions | Pass |
| Existing v10 session entries (pages 2/3), EPUB chapter 2 and old note survive v11 install | Pass on emulator |
| PDF controls and page fit after widening window to 1768 px | Pass on emulator; physical fold transitions pending |
| Build 11 physical Z Fold7 feedback and v10→v11 update | Pending |
| Native iOS reader and physical iOS/iPadOS verification | Pending |
| Full assistive-technology journeys and named-hardware performance | Pending |

The new EPUB touch test caught registration before fragment attachment and a JavaBridge-thread UI call. The listener now registers after attachment and dispatches object UI to the main thread. CI builds the release app and compiles the native test APK; instrumentation executes locally on the emulator.

Build 10's engineering baseline also verified independent PDF sessions at pages 2 and 3, force-stop/airplane-mode restoration, shared book notes, EPUB chapter restoration through an in-place APK update, isolated import failure/retry and missing-source repair through exact reimport. Build 10 was installed by the owner by downloading/opening its APK. The owner then reported all test items passed. See the separate user report for its scope and the new build's pending acceptance.

## Remaining work and rollback

The initial Android reader supports original text PDF and static UTF-8 EPUB, with 128 MiB file/expanded-archive limits, 4096 archive entries and an 8 MiB per-markup/CSS resource limit. Scripted, DTD-bearing, encrypted and externally embedded EPUB resources are rejected. The self-authored CC0 corpus remains the initial fixture set.

Two-page PDF selection uses the explicit Extend to next page action. Continuous cross-page dragging, EPUB exact search, PDF contents extraction, manual bookmarks, shelves, comprehensive accessibility alternatives and large-book performance remain open. Full Xcode is required for native iOS integration. Android build 11 cover/inner-display behavior and performance require physical evidence.

AI, BYOK/model switching, conversion, learning passes, assessments/recovery, research, comparison, backend APIs and sync retain their planned milestone gates. B01a is a bounded Android feedback increment, not full Phase B acceptance.

The production JavaScript dependency audit baseline reports 12 moderate advisories and zero high/critical findings. Dependencies were unchanged in this increment; triage is required before production release. Build tooling uses Java 21, SDK 36, NDK 27.1.12297006, CMake 3.22.1 and the pinned npm peer-resolution mode.

To roll back code without discarding local data, rebuild the previous source with the same signer and a higher version code. Keep the installed app and its storage. An uninstall removes sandboxed books and sessions; annotation JSON export does not back up the full library. Future schema changes require migrations and explicit upgrade-retention evidence. Preserve signing-key backups separately from the APK.
