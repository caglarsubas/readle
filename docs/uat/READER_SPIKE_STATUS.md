# Reader spike handoff

Date is 2026-11-04. Phase A is in progress. Android build 9 is an internal UAT build. The full product and the Phase A acceptance gate remain pending.

## Build

- Application ID is `com.readle.uat`.
- Version code is 9, version name is `0.1.0-spike`, minimum Android API is 24, target is 36.
- The APK is supplied privately from the workspace output directory and contains its JavaScript bundle. It starts without Metro, a backend, an account or an AI key.
- SHA-256 is `a1fbc66d2d24ecd60fdfadc83da25603ed9df80e7c1702dcdcc7e773358ba7a6`.
- Signing certificate SHA-256 is `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`.
- The signing key and password remain outside the repository under the builder's private `~/.local/share/readle` directory.

## Observed local engineering evidence

Checks ran on an isolated Pixel 7 Android API 36 arm64 emulator. No physical-device acceptance or iOS compilation is recorded.

| Check | Result |
|---|---|
| TypeScript type check | Pass |
| ESLint on source, excluding generated Android assets | Pass |
| React Native UI regressions | 3 tests pass |
| Native SQLite/import instrumentation | 4 tests pass |
| Signed standalone release build and signature verification | Pass |
| PDF original-page rendering and page navigation | Pass on emulator |
| Static EPUB rendering, contents navigation and font-size change | Pass on emulator |
| Inspect book without creating a session | Pass |
| Two PDF sessions saved independently at pages 2 and 3 | Pass |
| Force-stop, airplane-mode restart, reopen each saved PDF session | Pass |
| A source-linked note entered in one session appears in the other | Pass |
| EPUB chapter 2 restored after in-place update and force-stop | Pass |
| In-place APK updates preserve existing books and sessions | Pass |
| EPUB native selection context UI | Observed on emulator, physical handles/reflow acceptance pending |
| PDF continuous cross-page dragging, pinch zoom and full accessibility | Pending |
| Named hardware performance targets and physical iOS/Android acceptance | Pending |

Source-level storage tests verify independent workspace fields and shared annotations after closing/reopening SQLite, stale revision rejection, book-scoped session isolation, and corrupt-file/duplicate import isolation. UI tests verify inspection creates no session, Continue latest reopens the stored ID, and a new session opens its new ID. The instrumentation database is isolated from manual UAT data.

## Remaining work

The initial Android spike supports original text PDF and static UTF-8 EPUB, with 128 MiB file/expanded-archive limits, 4096 archive entries and an 8 MiB per-markup/CSS resource limit. Scripted, DTD-bearing, encrypted and externally embedded EPUB resources are rejected. The sample corpus has been visually checked and is self-authored CC0.

PDF zoom uses buttons, and two-page selection uses an explicit Extend to next page action. Continuous cross-page dragging and pinch zoom remain pending. EPUB exact search, PDF TOC extraction, manual bookmarks, shelves, comprehensive accessibility text alternatives and large-book performance remain open. The native iOS reader requires full Xcode and physical iOS evidence. Android user UAT is pending the handoff.

AI, BYOK/model switching, conversion, all learning passes, assessments/recovery, research, comparison, backend APIs and sync are planned milestones. Their contracts and requirement mapping are documented. Their implementation remains pending.

The production JavaScript dependency audit reports 12 moderate advisories, with zero high/critical findings. These require dependency upgrade/triage before a production release. The first-build environment used Java 21 and installed NDK 27.1.12297006 and CMake 3.22.1. npm's peer-resolution mode is pinned to keep a single React Native version. Native runtime smoke checks caught and corrected an array conversion error in the bridge, an unsupported Android XML feature setting, PDF view measurement and pre-decor window-insets initialization.

Rollback is an app reinstall for this first schema version. Export annotations before any uninstall because Android removes sandboxed files and sessions. Future schema changes require a migration and upgrade-retention evidence. Preserve the signing identity for updates and keep backups of that identity separately from the APK.
