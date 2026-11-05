# Android UAT results

User reports and local engineering checks are recorded separately. A checklist pass does not substitute for named-hardware timings or iOS verification.

## Build 10 — owner acceptance — 2026-11-05

| Field | Recorded evidence |
|---|---|
| Tester | Product owner, in this development chat |
| Device | Samsung Z Fold7, user reported |
| Android | 17, user reported |
| Installation | Downloaded APK and opened it on the phone, user confirmed |
| Expected | Original PDF/EPUB reading, highlights/notes, independent sessions, offline/restart restoration, isolated imports/retry, upgrade retention when a subsequent APK is supplied |
| Observed | User reported: “all test items passes.” The available build 10 checklist is accepted on that basis. No separate per-item logs or performance measurements were supplied. |
| Follow-up boundary | This report preceded delivery of build 11. A physical v10→v11 update is recorded with build 11's results when reported. |
| Feedback | EPUB UI/UX is better than PDF, including hover and fonts. A highlighted block in both formats should be selectable as an object with actions. Continued development authorized. |
| Decision | Record build 10 Android UAT as passed. Address feedback in B01a / build 11. Keep iOS and the remaining Phase A gate open. |

Build 10 APK SHA-256: `4e1b450bdcf999e7c60b4b628b1cd72f32eb2a540c112f687bb628bfd35bf465`.

## Build 11 — owner feedback — 2026-11-05

Device remains Samsung Z Fold7 / Android 17. This is a user report, distinct from the earlier green local/CI checks.

| Topic | Observed / decision |
|---|---|
| New sessions | Old notes/highlights appear. Owner now requires a blank workspace; this supersedes the earlier book-shared annotation contract. |
| PDF deselection | A short tap outside a selected word cannot clear it. Defect. |
| PDF zoom | Raw coordinate magnification makes the page unfit to the screen. Reader UX rejected. |
| Cross-page highlighting | Owner confirms the problem occurs in EPUB as well as PDF. |
| PDF folding | Owner reports duplicated pages after unfolding the Z Fold7. Physical fold gate failed. |
| Format direction | Owner selected **EPUB-first; PDF for source/reference**. |
| Decision | Do not mark build 11 reader acceptance complete. Apply B01b / ADR 022 and 023 in build 12. |

## Build 12 — owner feedback — 2026-11-05

| Field | Recorded evidence |
|---|---|
| Intended device | Same Z Fold7 / Android 17 |
| Installation | Update over build 11 using the same signer and application ID |
| Expected | Blank new sessions, isolated annotations, preserved old work, EPUB continuous scroll/cross-screen ranges, PDF deselection and one fit-width source page through folding |
| Observed on physical phone | Owner reports all other items passed. B12-01/02/03/05/06/07 accepted on that basis. B12-04 fails when trying to move the viewport with a native selection active. |
| Checklist | B12-01..07 in [ANDROID_UAT.md](ANDROID_UAT.md) |
| Engineering evidence | [READER_SPIKE_STATUS.md](READER_SPIKE_STATUS.md); emulator/automated checks remain separate from the physical record |

## Build 13 — native EPUB selection gestures — physical acceptance pending

The owner reports two outstanding interactions on Z Fold7 / Android 17: continuous layout must scroll while a word remains selected, and dragging a selection boundary to the left/right edge in paginated layout must turn the page while extending the same range. Other build-12 items are reported OK. Build-13 physical acceptance is pending.

Build-12's previous automated multi-screen test created a DOM range, then exercised saving and reopening. It did not verify native handle dragging or scrolling while selected. Build 13 now passes those actual touch paths on Pixel 7 and Pixel Fold API 36 AVDs, including finger scrolling, downward handle auto-scroll, forward/backward page extension, rendered source restoration and stopping on release. All 20 local tests pass. Physical Z Fold7 acceptance is pending.

## Open evidence and rollback

Cross-screen EPUB selection is qualified within one chapter/spine resource; a range across separate chapters remains open. Advanced PDF reflow and cross-page authoring are deferred by the accepted format scope. Build-12 physical Z Fold7 fold/unfold was accepted in the owner report. Native iOS integration, full TalkBack/VoiceOver journeys, large-book coverage and named-hardware p95 timing remain pending. EPUB selection gesture acceptance remains open.

Build 12 migrates SQLite schema 1 to 2. Previously shared annotations are preserved as independent copies in already-existing sessions. New sessions start empty. Existing source files and reading/session state are retained. Rollback requires a schema-2-compatible fix-forward APK with the same signer and a higher version code. Do not install a schema-1 build over this database. Uninstalling or clearing storage removes the local library; annotation export is not a full backup.
