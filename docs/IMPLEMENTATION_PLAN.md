# Readle implementation plan

Prepared 2026-11-04, updated 2026-11-12. The v1.3 specification and the user's full-workspace session and direct Android APK choices govern this plan. Milestones advance on evidence. **Build 11 UAT overrides:** new sessions are blank, with session-owned annotations ([ADR 022](adr/022-session-annotations.md)); EPUB is the primary MVP reader/learning format and PDF is retained for source/reference ([ADR 023](adr/023-epub-first.md)). These decisions supersede conflicting text in the original blueprint and earlier build records. The first engineering timebox is a two-day reader spike. Engineering checks, emulator results, physical-device results, live-provider checks, and user acceptance have independent recorded status.

## Phase A — Reader spike — P0 — in progress

1. A01 Establish the React Native TypeScript/native Android application, dependency lockfile, SQLite schema, and CI. Depends on architecture ADR. Accept when type checks, JS tests, native compilation and durable-storage instrumentation pass.
2. A02 Open a self-authored EPUB through Readium and the original PDF through a native renderer. Depends on A01. Accept on physical iOS and Android when EPUB range selection, highlights, notes and bookmarks survive restart, font reflow, rotation and selection across screen boundaries within a chapter. PDF reference pages must fit the reading window and retain page/vertical position through folding; advanced PDF reflow/authoring is optional P1.
3. A03 Import multiple files with isolated status, exact-byte identity and item retry. Depends on A01. Accept when a corrupt file and duplicate preserve all other imported publications.
4. A04 Deliver a standalone signed Android APK with sample books and UAT checklist. Depends on A02/A03. Record build ID, signer fingerprint, checksum, installation, restart and upgrade evidence.

Build 10 passed the product owner’s Android checklist on Samsung Z Fold7 / Android 17, reported 2026-11-05. The Android spike exercises early Phase B session behavior. Phase A remains open until both native platforms pass the acceptance gate. Broad AI-course work depends on that gate.

## Phase B — Offline library and sessions — P0

B01 Deliver Library, shelves, book details, exact search, dictionary actions, bookmarks, notes and annotation export. Depends on A acceptance. Verify book and annotation persistence without an account or AI key.

B02 Add an explicit Sessions list per book, Continue latest, Start new session and the global Continue list. Viewing a book creates no session. Store an immutable source-version reference, independent Read/Learn cursors, selection, visual state, quiz draft, detours and assistant state per session. Keep notes/highlights owned by their session; new sessions start empty and count committed assessments once. Restore only against the same edition or show a recovery action. Depends on A01/A02. Verify two independent sessions at different passages, a second book with isolated sessions, force-stop, offline restore and APK upgrade.

B03 Complete the eight-file batch fixture matrix with revised, translated, corrupt, duplicate and OCR-poor files. Isolate cancellation/retry, show true progress and privacy/rights settings. Reading progress, learning coverage and mastery remain distinct. Depends on A03. User Android UAT records the session acceptance journey before B acceptance.

B01a Address the first Android UAT feedback. Depends on Android A02 and existing B02 persistence. Make saved PDF/EPUB highlights actionable objects with Add/Edit note, Copy, Share, Open source and confirmed Delete. Keep the same annotation identity and anchor when editing, enforce book/edition scope and reject stale note writes. Clear the owning session’s deleted selection without changing another session. The build 11 PDF zoom approach failed owner UAT and is superseded by B01b’s fit-width reference view. Accept with real PDF/EPUB touch tests, reflow/reopen, scoped edit/delete storage tests, v10→v11 upgrade retention and Z Fold7 feedback checks FB-01..05.

B01b Apply build 11 UAT. Depends on B02 and schema 2 migration. Isolate annotations by session; preserve old work during upgrade; add EPUB continuous scroll and session-owned layout choice; save a selection spanning more than one screen; clear PDF selection by outside tap; retain exactly one fit-width PDF page surface through folding; provide Open original for a local PDF viewer. Verify schema-1 upgrade/orphan recovery, scoped edit/delete, EPUB range/reopen/blank-session isolation, PDF long-press/deselect/resize and real fold/unfold emulator behavior. Physical Z Fold7 acceptance remains a separate gate.

B01c Complete native EPUB selection gestures from build-12 UAT. Depends on B01b and the pinned navigator patch in ADR 024. Verify a real long-press selection survives finger scrolling in Continuous scroll, dragging an end handle turns forward, dragging a start handle turns backward, the opposite anchor remains fixed, page turning stops after release, and the saved range survives reopen. DOM-range-only tests are insufficient for this gate. Build 12's other UAT items are accepted by owner report. Build 13 forward extension passed on the Z Fold7, with the remaining reader defects tracked by B01d.

B01d Address build-13 UAT. Depends on B01b/B01c. Show the installed Android version and build in About. Verify vertical scrolling and chapter transitions, horizontal page turns, both native handle directions and retaining an adjustable selection during page turns. Keep note and selection actions inside the reading view. Find EPUB text on device and open the matched source. Verify offline on emulators and repeat on Z Fold7 cover and inner screens. See ADR 025–027 and B16-01..04.

### Current delivery status

| Work item | Verification status | Remaining evidence |
|---|---|---|
| A01 | Android build, CI baseline, JS checks and SQLite tests verified | iOS reader build remains separate |
| A02 | Build 10 Android UAT reported pass | Native iOS integration/physical verification, EPUB cross-screen selection on physical devices, full accessibility and measured performance |
| A03 | Four-file import, duplicate, corrupt retry and missing-source recovery verified | B03 expanded corpus |
| A04 | Signed standalone APKs delivered; build 10 installed and accepted by owner | Build 16 physical upgrade and current reader acceptance |
| B01 | Partial Android reader, dictionary lookup, notes and export | Shelves, manual bookmarks, EPUB search qualification and remaining B acceptance |
| B01a | Build 11 locally verified; owner reported PDF/selection defects and changed session/format scope | Superseded by B01b |
| B01b | Build 12 owner reports other items passed; native selection scrolling/page extension failed | Selection gestures carried by B01c |
| B01c | Build 13 emulator tests passed; owner confirmed forward handle extension | Scroll, reverse extension and selection retention carried by B01d |
| B01d | Build 15 owner UAT failed EPUB selection adjustment and selected chapter-edge swipes. Build 16 restores dropped ranges, routes vertical chapter turns directly and reports sample imports inline. | Physical Z Fold7 / Android 17 B16-01..04. [Build 16 evidence](uat/BUILD_16_VISUAL_REVIEW.md). |
| B02 | Android local independent reading workspaces verified; build 10 UAT reported pass | Full learning/chat restoration as those features arrive, server ownership/revisions and later sync |
| B03 | Initial fixtures verified | Revised edition, translation, OCR-poor file and eight-file batch evidence |

The next bounded work is physical B01d acceptance on Build 16, alongside the iOS reader prerequisite. The Build 15 physical report supersedes emulator-only acceptance for its selection cases. Repeat visible Android Studio testing before each handoff. Remaining offline-library behavior and the expanded import corpus follow the reader gate. C and later milestones retain their stated dependencies. See [the UAT results](uat/UAT_RESULTS.md) and [current build evidence](uat/READER_SPIKE_STATUS.md).

## Phase C — Source-grounded assistant and switching — P0

C01 Add session-linked chat, selection and no-selection context, phone sheet/tablet panel, source navigation and exact return. Depends on A/B. Prove one live selection-to-answer-to-source flow.

C02 Add four BYOK adapters and the actual authenticated inference-engine catalog. Expose model readiness, capability and verified effort metadata. Add revisioned selections, immutable attempts, future-turn switching, explicit stop/retry, per-answer attribution and cost records. Keep long jobs pinned. Reauthorize egress and budget for every attempt, including engine cloud routes. Device credentials stay in secure storage unless explicitly delegated. Pass all SWITCH-001..012 tests and live BYOK-to-engine switching with a privacy-denied cloud attempt. Verify two sessions retain independent transcripts, drafts and models. Provider-specific live verification gates the capabilities advertised by that adapter.

## Phase D — Source AST and Pass 1 — P0

D01 Build canonical sections, figures, equations, immutable anchors and edition identity for EPUB first. Depends on A/B source correctness. Verify one technical EPUB. PDF source-to-learning ingestion is deferred under ADR 023, with source-preserving qualification required before adding it.

D02 Add conversion scope/model/effort/cost/egress review, consented execution modes, idempotent per-section units, retries, partial results and versioned lessons. Depends on C/D01. Publish only source-validated sections, preserve original files and keep separate books' learning editions independent. Add ungraded checkpoints in every section and pass-aware session cursors. Test interruption and independently resumed sessions.

## Phase E — Pass 2, assessment and recovery — P0 MVP

E01 Add technical lessons, concept-aligned free pass switching and three typed visuals (algorithm stepper, attention matrix, parameter sweep). Preserve serializable visual state. Depends on D.

E02 Add independent Pass 3 MCQ workflows, drafts, deterministic keys/grades, invalid-item handling and mastery evidence. Checkpoints remain ungraded. Initial mastery target is 90% over adequate independent and transfer questions with no unresolved severe misconception. Depends on D and a human-reviewed question fixture set.

E03 Add bounded misconception recovery with a fresh reassessment and exact nested-detour return. Depends on E01/E02. Verify force-stop and session switching mid-quiz without resubmission. Accept MVP only after import/read/select/ask/learn/checkpoint/quiz/recover/saved-session-resume works on both platforms.

## Phase E2 — Research and citation previews — P1

Add capability-discovered research routes with explicit scope, tool, egress and cost preflight, truthful asynchronous state, verified reports, phone/touch and tablet focus/hover previews, cross-book Open/Back and lawful external metadata. Depends on C/D and authorized background mode. Contract-test four adapters and demonstrate two live research modes. Peeking preserves session/progress. Opening resolves the exact owned edition and preserves the originating workspace.

## Phase E3 — Multi-book evidence — P1

Add comparison sets and frozen CorpusSnapshot scope, work/edition review, passage overlap, upstream lineage, qualified claims, abstention and side-by-side review. Add opt-in comparative learning and disputed-question safeguards. Depends on D/E2. Compare an explicitly selected subset of five or more books, retain original anchors after dedup, distinguish disputes from changed assumptions and shared upstream evidence, and invalidate only affected outputs after revision/deletion. Test scope/tenant isolation and prompt injection.

## Phase F — Sync and release — P1 V1

Add opt-in multi-device session sync, revisions/tombstones, non-destructive note conflict handling, backup/restore, reviewed OCR, additional formats, accessibility and reproducible cloud/on-prem deployment. Depends on P0 and P1 feature gates. Verify offline edits, stale writes, device revocation, upgrades, source deletion and restore. Target ordinary cached book open p95 under 2 seconds on named mid-range hardware, navigation under 150 ms and selection UI under 200 ms. Report technical/large-file performance separately. Complete security, rights and store-distribution reviews.

## Phase G — Study ecosystem — P2

Evaluate spaced review, cross-book concept graphs, authorized audio, private collaboration and a companion app using learning outcomes, rights and reader-performance evidence. Depends on V1.

## Evidence and delivery

Every work item closes with tests/screens, persisted-state evidence, failures/accessibility checks, schema/API agreement, known risks, migration/rollback notes and the next prioritized work. The public repository contains code and rights-cleared fixtures. User books and provider keys remain private. UAT is recorded by build, device, Android version, expected result and observed result. The repository backlog is the tracking surface chosen by the user.
