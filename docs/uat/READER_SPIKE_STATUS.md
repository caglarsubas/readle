# Build 12 reader handoff

Updated 2026-11-05. Build 11 owner UAT rejected its PDF zoom/fold behavior and found missing deselection and cross-page selection. The owner changed the product to **blank independent sessions** and **EPUB-first reading/learning, with PDF source/reference**. [User results](UAT_RESULTS.md), [ADR 022](../adr/022-session-annotations.md), [ADR 023](../adr/023-epub-first.md).

## Build

- Application ID: `com.readle.uat`.
- Version code: 12. Version name: `0.1.0-spike`. Minimum API: 24. Target: 36.
- Private standalone APK: `output/android/readle-uat-12.apk`. JavaScript is bundled; Metro, a backend and an AI key are unnecessary.
- APK SHA-256: `0ef5dcac5b79494c1d69e01f1e0b853c01ae3395f3c8a1b2b5c1b51cb9ef18c1`.
- Signing certificate SHA-256: `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`, unchanged from builds 10/11.
- SQLite schema: 2, with transactional schema-1 migration. Existing sessions/reading state are retained; legacy book-shared annotations become independent copies in each existing session, with origin lineage. Orphaned legacy annotations get a recovery session. Future sessions start empty.

## Current behavior

EPUB is listed first and labelled Reader. New sessions use continuous scroll, permitting selections beyond the initial screen within a chapter. Layout can switch to Pages and is saved independently, alongside font size and source locators. Rendered annotations, note lists, exports and object actions all require the owning session.

PDF is labelled Source/reference. The page always fits the window width, with one persistent native page surface per activity. Resize work is coalesced and stale restore callbacks are ignored. Folding does not stack replacement views. Bitmap opacity resets for every repaint. A short tap outside a temporary selected word clears the selection and saved selection state without deleting gold highlights. Open original grants temporary read access to a local Android PDF viewer chosen by the reader. In-app coordinate magnification and cross-page PDF authoring are removed from this MVP scope. Existing multi-page annotations remain viewable.

## Local engineering evidence

| Check | Result |
|---|---|
| TypeScript / ESLint | Pass |
| React Native session UI tests | 3 pass |
| SQLite/import persistence and scope tests | 7 pass |
| Real schema-1 migration / orphan recovery tests | 2 pass |
| Native reader interaction tests on Pixel Fold API 36 arm64 | 4 pass |
| Signed standalone release / same-signer verification | Pass |
| Existing build-11 Pixel 7 AVD upgraded in place | Existing page-2/page-3 sessions and old notes retained; a new session opens at page 1 with an empty notes list |
| EPUB tap/edit menu, font reflow, reopen | Pass |
| Multi-screen EPUB range and blank-session isolation | Pass: DOM range longer than viewport saved via the real Selection → Highlight menu, original source anchor restored; a second session has no rendered annotations |
| PDF real long-press then outside short tap | Pass: selection persisted, then cleared without replacement |
| PDF content resize / one surface / accessibility action | Pass |
| Repeated fold/unfold on dedicated Pixel Fold AVD | Pass: three fold/unfold cycles, seven observations, always one page-2 surface; widths 1080/2208 px. Keyguard dismissed after posture changes. Physical Z Fold7 gate remains pending |
| Build 12 physical upgrade / cross-screen handle dragging / Z Fold7 folding | Pending owner UAT |
| Native iOS / physical iOS and iPadOS | Pending |

The multi-screen automated EPUB test constructs a standard WebView selection and uses the reader's actual action and persistence paths. It does not substitute for physical handle dragging. Cross-chapter selection across separate EPUB spine resources remains open. CI builds the release APK and compiles native tests; instrumentation executes on the local AVD.

## Scope and remaining evidence

Phase A remains open for native iOS EPUB integration and physical evidence. Shelves, bookmarks, EPUB exact search, full accessibility, large-book performance and the expanded eight-file corpus remain open. PDF advanced authoring/reflow is optional P1; future PDF-to-learning conversion must preserve source mapping, equations, figures and reading order before use. AI, model switching, conversion, learning, assessment, research, backend and sync retain their revised milestone gates.

Supported intake remains unencrypted text PDF and static UTF-8 EPUB, with 128 MiB file/expanded limits, 4096 archive entries and 8 MiB per markup/CSS resource. Source hashes and files are preserved. The self-authored sample corpus is CC0. Dependencies were unchanged; the previous audit baseline had 12 moderate advisories requiring production triage, with no high/critical findings at that earlier audit.

Rollback uses a schema-2-compatible fix-forward build and a higher version code. Preserve the private signing identity outside Git. Annotation export does not back up complete books/sessions. Physical results and unresolved limitations remain separate from green local tests and CI.
