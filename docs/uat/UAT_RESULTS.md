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

## Build 11 — feedback increment — physical acceptance pending

| Field | Recorded evidence |
|---|---|
| Intended device | Same Z Fold7 / Android 17 |
| Installation | Update over build 10 using the same UAT signing identity and application ID |
| Expected | Saved highlight actions in PDF and EPUB, source-preserving note edits, confirmed deletion, PDF pinch/double-tap and both scroll axes restored |
| Observed on physical phone | Pending owner report for build 11 |
| Checklist | UAT-12 upgrade retention and FB-01..05 in [ANDROID_UAT.md](ANDROID_UAT.md) |
| Engineering evidence | See [READER_SPIKE_STATUS.md](READER_SPIKE_STATUS.md). Emulator and automated results remain separate from this physical record. |

## Open evidence and rollback

Native iOS integration and physical iOS/iPadOS checks remain pending. Full TalkBack/VoiceOver journeys, continuous PDF cross-page dragging, large-book coverage and named-hardware p95 timing remain open. Cover/inner display transitions in build 11 require the Z Fold7 feedback check even when emulator resizing passes.

Build 11 retains SQLite schema 1 and the existing book/session/annotation IDs. Keep the installed app and its storage during updates. To roll back code while retaining data, rebuild the previous source with the same signer and a new, higher version code. Uninstalling or clearing app storage removes the local library and sessions. The current annotation export is not a full library/session backup.
