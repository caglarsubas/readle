# Readle implementation plan

Prepared 2026-11-04. The v1.3 specification and the user's full-workspace session and direct Android APK choices govern this plan. Milestones advance on evidence. The first engineering timebox is a two-day reader spike. Engineering checks, emulator results, physical-device results, live-provider checks, and user acceptance have independent recorded status.

## Phase A — Reader spike — P0 — in progress

1. A01 Establish the React Native TypeScript/native Android application, dependency lockfile, SQLite schema, and CI. Depends on architecture ADR. Accept when type checks, JS tests, native compilation and durable-storage instrumentation pass.
2. A02 Open a self-authored EPUB through Readium and the original PDF through a native renderer. Depends on A01. Accept on physical iOS and Android when range selection, highlights, notes and bookmarks survive restart, font reflow, zoom, rotation, and cross-page selection.
3. A03 Import multiple files with isolated status, exact-byte identity and item retry. Depends on A01. Accept when a corrupt file and duplicate preserve all other imported publications.
4. A04 Deliver a standalone signed Android APK with sample books and UAT checklist. Depends on A02/A03. Record build ID, signer fingerprint, checksum, installation, restart and upgrade evidence.

The Android spike may exercise early Phase B session behavior. Phase A remains open until both native platforms pass the acceptance gate. Broad AI-course work depends on that gate.

## Phase B — Offline library and sessions — P0

B01 Deliver Library, shelves, book details, exact search, dictionary actions, bookmarks, notes and annotation export. Depends on A acceptance. Verify book and annotation persistence without an account or AI key.

B02 Add an explicit Sessions list per book, Continue latest, Start new session and the global Continue list. Viewing a book creates no session. Store an immutable source-version reference, independent Read/Learn cursors, selection, visual state, quiz draft, detours and assistant state per session. Share annotations at book level and count committed assessments once. Restore only against the same edition or show a recovery action. Depends on A01/A02. Verify two independent sessions at different passages, a second book with isolated sessions, force-stop, offline restore and APK upgrade.

B03 Complete the eight-file batch fixture matrix with revised, translated, corrupt, duplicate and OCR-poor files. Isolate cancellation/retry, show true progress and privacy/rights settings. Reading progress, learning coverage and mastery remain distinct. Depends on A03. User Android UAT records the session acceptance journey before B acceptance.

## Phase C — Source-grounded assistant and switching — P0

C01 Add session-linked chat, selection and no-selection context, phone sheet/tablet panel, source navigation and exact return. Depends on A/B. Prove one live selection-to-answer-to-source flow.

C02 Add four BYOK adapters and the actual authenticated inference-engine catalog. Expose model readiness, capability and verified effort metadata. Add revisioned selections, immutable attempts, future-turn switching, explicit stop/retry, per-answer attribution and cost records. Keep long jobs pinned. Reauthorize egress and budget for every attempt, including engine cloud routes. Device credentials stay in secure storage unless explicitly delegated. Pass all SWITCH-001..012 tests and live BYOK-to-engine switching with a privacy-denied cloud attempt. Verify two sessions retain independent transcripts, drafts and models. Provider-specific live verification gates the capabilities advertised by that adapter.

## Phase D — Source AST and Pass 1 — P0

D01 Build canonical sections, figures, equations, immutable anchors and edition identity. Depends on A/B source correctness. Verify one technical PDF and one EPUB.

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
