# Architecture decisions

The following index covers spec section 16. `accepted` identifies a bounded implementation decision. `planned` requires supporting evidence before its feature is implemented.

| ID | Topic | State |
|---|---|---|
| 001 | Reader SDK bridge and native PDF geometry | Spike, see 001-reader.md |
| 002 | Anonymous local reading and SQLite sessions | Accepted for Android spike |
| 003 | Device-only BYOK and explicit vault delegation | Planned before C |
| 004 | Capability-validated course authoring provider | Planned before D |
| 005 | On-prem protocol and actual engine discovery | Planned before C |
| 006 | Immutable source hash, EPUB locator, PDF points | Accepted for Android spike |
| 007 | Declarative safe visual components | Planned before E |
| 008 | Question validity and mastery evidence | Planned before E |
| 009 | Private content rights and distribution | Planned before public course sharing |
| 010 | Celery/Redis execution with Postgres job state | Planned before D |
| 011 | Local exact search before semantic retrieval | Spike |
| 012 | Deployment residency and egress routing | Planned before C |
| 013 | BYOK usage estimates, budgets and approvals | Planned before C |
| 014 | Actual provider research modes | Planned before E2 |
| 015 | Research credentials, retention and cancellation | Planned before E2 |
| 016 | Authorized edition-aware source previews | Planned before E2 |
| 017 | One citation resolver and safe return navigation | Planned before C |
| 018 | Independent batch imports and exact dedup | Android spike |
| 019 | Passage overlap and evidence independence | Planned before E3 |
| 020 | Qualified claims, review and abstention | Planned before E3 |
| 021 | Opt-in comparative course snapshots | Planned before E3 |

The v1.3 model-switch contract is part of decisions 003, 005, 012 and 013. Saved-session state is part of decisions 002 and 006. Android UAT uses a stable signing identity and app ID with incremental build numbers.
