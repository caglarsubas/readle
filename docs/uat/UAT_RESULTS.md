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

## Build 13 — owner feedback — 2026-11-06

Device: Samsung Z Fold7 / Android 17, as previously reported. These are physical observations, separate from the build-13 Pixel 7 and Pixel Fold API 36 emulator passes.

| Topic | Owner observation / follow-up |
|---|---|
| About | No way to see which version is installed. Show the installed version and build number. |
| EPUB layout | Continuous scroll and Pages both feel paginated. Make the gestures and chapter transitions distinct. |
| Handle drag | Forward page extension works. Previous-page extension fails. Retest backward on both screens. |
| Page turns with a selection | The selected passage is lost. Preserve it in the session when navigating away and back. |
| Notes and selection | The dialogs feel separate from the app. Keep their controls and editing inside the reader. |
| Find | EPUB shows a pending-spike message. Search the book on device and open matching passages. |

Forward extension was reported working. The report does not establish a pass for the other build-13 checks. Build 14 addresses these observations under B01d; physical acceptance remains pending.

## Build 14 — Android Studio visual review — 2026-11-07

At the owner's request, operated the installed build through the existing Android Studio application's visible Pixel 7 and Pixel Fold API 36 emulators. [Full observations and screenshots](BUILD_14_VISUAL_REVIEW.md) distinguish the exercised subchecks from blocked or untested ones.

The first attempt observed About, scrolling, retained quotes, note save/reopen and Find, but its available input could not qualify held-edge turns, cover restoration or Android Back. These initial limitations are superseded by the follow-up below. Active selection handles disappear after ordinary page turns (VIS14-01); retaining the quote alone does not complete the requested adjustable selection interaction.

Future reader handoffs require a recorded visual Android Studio gesture review alongside automated checks. Build 14's full interaction acceptance remains open.

## Build 14 — precise-input visual follow-up — 2026-11-07

The owner explicitly authorized ADB and native touch instrumentation with visual observation in Android Studio. On the Pixel Fold API 36 emulator, both cover and inner screens pass native held-edge extension forward and backward, fixed opposite endpoints, and stopping after release. Both screens also exercise continuous scrolling, chapter-edge transitions, retained quotes, note save/edit/Open source, delivered system Back and offline Find. The saved note and Pages preference survive the fold/unfold transition and actual force-stop/reopen through Library. See the [follow-up report and evidence](BUILD_14_VISUAL_FOLLOWUP.md).

VIS14-02 is withdrawn: system Back closes Notes on both screens with the unchanged build-14 APK. Earlier toolbar and synthetic gesture attempts did not demonstrate that action reliably. Two early native Notes attempts exposed input/harness problems; corrected tests pass without changing production code. VIS14-01 remains an app UX defect. These results complete the formerly blocked emulator checks; physical Z Fold7 / Android 17 acceptance and APK-upgrade evidence remain separate.

## Build 15 — engineering qualification and owner handoff — 2026-11-07

Continued VIS14-01 development with owner-authorized precise input and Android Studio visual observation. Build 15 preserves the live native range through ordinary turns, uses the native pager while selecting, retains unchanged source locators independently of reading progression, and protects saved JSON from Readium's consuming parser. See [ADR 026](../adr/026-live-selection-page-turns.md) and the [qualification evidence](BUILD_15_VISUAL_REVIEW.md).

**Engineering checks passed:** 23/23 native tests on Pixel 7 API 36, three consecutive swipe-to-handle replays without multi-second visual pauses, two handle regressions on each foldable screen with visual checkpoints, complete-locator regression, nine storage/migration tests, four React Native tests, TypeScript, lint, native compilation and signature verification. The original build-14 APK fails both new handle regressions. An early 22/23 run exposed the need to await rendered native handles before coordinate input; the final test keeps all original range, real-handle and fixed-opposite-endpoint assertions.

The Build 14 → candidate 15 → final 15 chain retained old work without clearing data. On the final APK, offline force-stop/reopen visibly restored two distinct phone sessions and the saved `appearance` / `rest` note in Pages. Cover → inner → cover retained the foldable's edited note, source highlights, Notes panel and layout. Host-emulator exits, startup System UI ANR and manual Mac unlock are documented as infrastructure history. The second host exit interrupted an additional phone note screenshot and final About revisit after the passing suite and live restoration; it does not erase those completed observations.

The signed APK, fixtures, upgrade guidance and B15-01..07 checklist are ready. The install helper defaults to build 15. **Physical Samsung Z Fold7 / Android 17 owner acceptance has not been reported.** Emulator results do not supply that acceptance or the native iOS gate.

## Open evidence and rollback

Cross-screen EPUB selection is qualified within one chapter/spine resource; a range across separate chapters remains open. Advanced PDF reflow and cross-page authoring are deferred by the accepted format scope. Build-12 physical Z Fold7 fold/unfold was accepted in the owner report. Native iOS integration, full TalkBack/VoiceOver journeys, large-book coverage and named-hardware p95 timing remain pending. EPUB selection gesture acceptance remains open.

Build 12 migrates SQLite schema 1 to 2. Previously shared annotations are preserved as independent copies in already-existing sessions. New sessions start empty. Existing source files and reading/session state are retained. Rollback requires a schema-2-compatible fix-forward APK with the same signer and a higher version code. Do not install a schema-1 build over this database. Uninstalling or clearing storage removes the local library; annotation export is not a full backup.
