# Requirements traceability

All 77 original IDs and the two session additions are tracked below. Partial means an Android spike path exists and the full requirement gate remains open. Planned tests and server routes are not reported as implemented. See the implementation plan for acceptance behavior and the UAT record for observed evidence.

| Requirement | Behavior | Phase | Implementation / test | Status |
|---|---|---|---|---|
| BATCH-001 | multi-file upload session | B | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| BATCH-002 | per-file progress/retry | B | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| BATCH-003 | duplicate and edition detection | B | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| BATCH-004 | partial success | B | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| BATCH-005 | scope snapshot | B | Planned in milestone | Planned |
| BATCH-006 | per-book privacy | B | Planned in milestone | Planned |
| BATCH-007 | import-session QA | B | Planned in milestone | Planned |
| PROV-001 | immutable source anchors | D/E3 | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| PROV-002 | passage fingerprints | D/E3 | Planned in milestone | Planned |
| PROV-003 | edition identity | D/E3 | Planned in milestone | Planned |
| PROV-004 | citation lineage | D/E3 | Planned in milestone | Planned |
| PROV-005 | evidence independence | D/E3 | Planned in milestone | Planned |
| PROV-006 | citation source attribution | D/E3 | Planned in milestone | Planned |
| PROV-007 | overlap dedup without citation loss | D/E3 | Planned in milestone | Planned |
| PROV-008 | snapshot invalidation | D/E3 | Planned in milestone | Planned |
| CONFLICT-001 | qualified claims | E3 | Planned in milestone | Planned |
| CONFLICT-002 | cross-book alignment | E3 | Planned in milestone | Planned |
| CONFLICT-003 | context/units/time | E3 | Planned in milestone | Planned |
| CONFLICT-004 | contradiction candidates | E3 | Planned in milestone | Planned |
| CONFLICT-005 | review/abstention | E3 | Planned in milestone | Planned |
| CONFLICT-006 | comparison UI | E3 | Planned in milestone | Planned |
| CONFLICT-007 | evidence-aware synthesis | E3 | Planned in milestone | Planned |
| CONFLICT-008 | assessment safety | E3 | Planned in milestone | Planned |
| CONFLICT-009 | privacy/rights | E3 | Planned in milestone | Planned |
| CONFLICT-010 | regression evaluation | E3 | Planned in milestone | Planned |
| LIB-001 | multi-book library/shelves | B | Planned in milestone | Planned |
| RESEARCH-001 | selected-scope launch | E2 | Planned in milestone | Planned |
| RESEARCH-002 | provider-native capability routing | E2 | Planned in milestone | Planned |
| RESEARCH-003 | consent and egress manifest | E2 | Planned in milestone | Planned |
| RESEARCH-004 | plan and cost | E2 | Planned in milestone | Planned |
| RESEARCH-005 | durable research job | E2 | Planned in milestone | Planned |
| RESEARCH-006 | report and citation provenance | E2 | Planned in milestone | Planned |
| RESEARCH-007 | saved research/notes | E2 | Planned in milestone | Planned |
| RESEARCH-008 | research QA | E2 | Planned in milestone | Planned |
| CITE-001 | citation resolver | C/E2 | Planned in milestone | Planned |
| CITE-002 | PDF page peek | C/E2 | Planned in milestone | Planned |
| CITE-003 | EPUB snippet peek | C/E2 | Planned in milestone | Planned |
| CITE-004 | touch/hover/focus UI | C/E2 | Planned in milestone | Planned |
| CITE-005 | cross-book Open and return | C/E2 | Planned in milestone | Planned |
| CITE-006 | bibliographic discovery | C/E2 | Planned in milestone | Planned |
| CITE-007 | preview permission/caching | C/E2 | Planned in milestone | Planned |
| CITE-008 | edition mismatch handling | C/E2 | Planned in milestone | Planned |
| READ-001 | EPUB primary reader; PDF source/reference | A/B/P1 | ReaderActivity single PDF surface; EPUB reflow/layout tests; ADR 023 | Partial, Android; full PDF authoring deferred |
| SEL-001 | Native EPUB scrolling while selected and handle-driven page extension | A/B | ADR 024–027; real handle drags after chapter/column return, selected vertical edge swipes, exact opposite endpoints and release stability | Build 15 physical failures recorded; Build 16 physical UAT and cross-chapter ranges open |
| ANN-001 | Session-owned highlights/notes and object actions | A/B | Schema 2; session scope/CAS tests; AnnotationMigrationTest; EPUB/PDF interaction tests and in-reader panels (ADR 025/026) | Partial, Android; Build 15 owner reports other behavior working; Build 16 regression follow-up |
| BOOKMARK-001 | flags/resume | A/B | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| AI-001 | contextual chat | C | Planned in milestone | Planned |
| AI-002 | BYOK provider registry | C | Planned in milestone | Planned |
| AI-003 | effort/settings | C | Planned in milestone | Planned |
| AI-004 | on-prem endpoint | C | Planned in milestone | Planned |
| SWITCH-001 | in-reader model picker | C | Planned in milestone | Planned |
| SWITCH-002 | BYOK catalog discovery | C | Planned in milestone | Planned |
| SWITCH-003 | inference-engine model discovery | C | Planned in milestone | Planned |
| SWITCH-004 | model readiness/eligibility | C | Planned in milestone | Planned |
| SWITCH-005 | per-thread/turn selection | C | Planned in milestone | Planned |
| SWITCH-006 | model-specific effort | C | Planned in milestone | Planned |
| SWITCH-007 | in-flight cancellation/retry | C | Planned in milestone | Planned |
| SWITCH-008 | cross-provider conversation continuity | C | Planned in milestone | Planned |
| SWITCH-009 | response provenance/cost | C | Planned in milestone | Planned |
| SWITCH-010 | pinned durable jobs | C | Planned in milestone | Planned |
| SWITCH-011 | privacy and egress enforcement | C | Planned in milestone | Planned |
| SWITCH-012 | model-switch contract/E2E tests | C | Planned in milestone | Planned |
| INGEST-001 | source AST/figures | D | Planned in milestone | Planned |
| CONVERT-001 | Guided Learning job | D | Planned in milestone | Planned |
| PASS1-001 | Conceptual lessons | D | Planned in milestone | Planned |
| PASS2-001 | Technical lessons | E | Planned in milestone | Planned |
| PASS3-001 | Independent deterministic assessment | E | Planned in milestone | Planned |
| CHECKPOINT-001 | ungraded section end | D/E | Planned in milestone | Planned |
| RECOVERY-001 | See specification | E | Planned in milestone | Planned |
| ANCHOR-001 | source locator | A/D | Exact EPUB DOM endpoints and quote verification; unchanged complete locator after page turns; defensive parser copies; LibraryPersistenceTest | Partial, Android; exact-source restoration after dropped ranges in Build 16 |
| DET-001 | detour/resume | C/E | Planned in milestone | Planned |
| VIS-001 | safe interactive visual | E | Planned in milestone | Planned |
| PRIV-001 | privacy/egress | B/C | Planned in milestone | Planned |
| SEC-001 | secrets/auth | A/C/F | Android reader/storage/importer; LibraryPersistenceTest | Partial |
| SYNC-001 | Offline conflict-safe multi-device sync | F | Planned in milestone | Planned |
| A11Y-001 | Accessible interactions | A/B/F | Native object menus/notes list; PDF per-highlight accessibility actions tested | Partial; comprehensive assistive-technology acceptance open |
| EVAL-001 | source validation and educational quality | D/E/F | Planned in milestone | Planned |
| SESSION-001 | Discover/resume existing sessions; create a blank session | A/B | Android SQLite/LibraryModule/App; new session has zero annotations | Partial, Android |
| SESSION-002 | Independent workspace and annotation restoration | A/B/C/E/F | Schema 1→2 migration, scoped notes/selection and native EPUB range restoration; Build 15 offline restart and update-chain evidence | Partial, Android reader; chat/learning restoration awaits those features |

Backend `/v1/books/{id}/sessions` and session update endpoints are planned. The working Android path is a local native bridge and requires no backend.

Build 10: the product owner reported all Android checklist items passed on Samsung Z Fold7 / Android 17 on 2026-11-05. Build 11 addresses saved-highlight actions and PDF ergonomics. See [UAT results](uat/UAT_RESULTS.md) for the distinction between user reports, local tests and pending physical/iOS evidence.

Build 11 owner UAT changes the original scope: annotations belong to sessions, new sessions are blank, EPUB is the primary reading/learning format, and PDF is an original-source reference view. [ADR 022](adr/022-session-annotations.md) and [ADR 023](adr/023-epub-first.md) supersede conflicting earlier requirements; the original 77 IDs plus SESSION-001/002 remain tracked.

Build 12 owner UAT accepts the remaining checklist items and identifies two EPUB gesture failures. Build 13 adds actual native gesture coverage and the pinned SDK correction in [ADR 024](adr/024-epub-selection-gestures.md). Programmatically constructing a DOM range remains storage evidence only, not gesture acceptance.

Build 15 resolves VIS14-01 in engineering checks: 23 native tests, three consecutive synchronized swipe-to-handle replays and Android Studio visual observation on both foldable screens. The original complete-locator assertion remains unchanged. See [ADR 026](adr/026-live-selection-page-turns.md), [qualification evidence](uat/BUILD_15_VISUAL_REVIEW.md) and [B15-01..07](uat/BUILD_15_CHECKLIST.md). The owner subsequently reported B15-02 and B15-04 failures. See [Build 16 evidence](uat/BUILD_16_VISUAL_REVIEW.md) and [B16-01..04](uat/ANDROID_UAT.md) for the current retest.
