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

## Build 12 — physical acceptance pending

| Field | Recorded evidence |
|---|---|
| Intended device | Same Z Fold7 / Android 17 |
| Installation | Update over build 11 using the same signer and application ID |
| Expected | Blank new sessions, isolated annotations, preserved old work, EPUB continuous scroll/cross-screen ranges, PDF deselection and one fit-width source page through folding |
| Observed on physical phone | Pending owner report for build 12 |
| Checklist | B12-01..07 in [ANDROID_UAT.md](ANDROID_UAT.md) |
| Engineering evidence | [READER_SPIKE_STATUS.md](READER_SPIKE_STATUS.md); emulator/automated checks remain separate from the physical record |

## Open evidence and rollback

Cross-screen EPUB selection is qualified within one chapter/spine resource; a range across separate chapters remains open. Advanced PDF reflow and cross-page authoring are deferred by the accepted format scope. Physical Z Fold7 fold/unfold, native iOS integration, full TalkBack/VoiceOver journeys, large-book coverage and named-hardware p95 timing remain pending.

Build 12 migrates SQLite schema 1 to 2. Previously shared annotations are preserved as independent copies in already-existing sessions. New sessions start empty. Existing source files and reading/session state are retained. Rollback requires a schema-2-compatible fix-forward APK with the same signer and a higher version code. Do not install a schema-1 build over this database. Uninstalling or clearing storage removes the local library; annotation export is not a full backup.
