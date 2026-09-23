# CallFlow SaaS — Canonical Master Plan

Last updated: 2026-09-06
Status: Planning approved; Phases 1–8 are implemented locally, with deployment gates tracked per phase.

This file preserves the user's agreed product requirements and phased roadmap. Read it before starting a phase. Existing code may implement portions of these requirements; Phase 0 must establish the actual baseline. Do not treat planned features as completed features.

## Working agreements

- Work phase by phase; implement only the phase the user requests.
- Preserve the currently working CFL Dashboard connection. No untested replacement or migration.
- Verify each phase with relevant automated, UI, device, security and performance tests; record evidence and remaining issues.
- Do not build a distributable APK unless the user requests it. Diagnostic test builds are distinct from distributable releases.
- Do not claim a feature, deployment, capacity or test is verified without evidence.
- Update this file's progress log as phases are genuinely completed. A saved file is the durable reference, not a guarantee of automatic conversational memory.

## 1. Product and access model

### Owner Admin Panel

For the service owner and authorized staff:

- Create companies, company managers and salesperson accounts; no public user signup initially.
- Activate/suspend accounts, reset passwords, allocate paid seats and revoke sessions.
- Set per-salesperson prices manually, including negotiated company-specific per-salesperson prices.
- Manage subscriptions, payments, renewals, integrations and service health.
- View authorized company/user activity, call numbers, history and notes with audited sensitive-data access.
- Manage external CRM credentials, field mappings and connection configuration centrally, never in the mobile app.
- This is not the day-to-day lead-assignment dashboard.

### Company CRM Dashboard

- Included free with the CallFlow subscription; separate company-manager login.
- Strict company data separation and role-based access.
- Lead entry/import, filtering, assignment, pipelines, timelines, follow-ups and sales reports.
- Company managers assign leads; creation of paid salesperson accounts and pricing remains under Owner Admin control.
- Preserve activity history when a salesperson leaves or a lead is reassigned.

### CallFlow Android App

- Salesperson login, assigned leads, calls, manual dial, notes, dispositions, follow-ups and reports.
- Compact, readable layouts, efficient lists and clear pending/failed/last-sync status.
- Standalone usage without an external CRM remains supported.
- One active phone per user: successful login on a new phone revokes the previous server session.
- An offline old phone cannot be notified immediately; enforce bounded offline session validity and revoke access on reconnect.
- Securely handle account switching and old-device unsynced records; never expose another user's local data.

## 2. Data flows and compatibility

External company CRM -> Our Company CRM/backend -> Assigned salesperson's CallFlow app.

CallFlow -> Our backend/CRM -> External CRM where its API supports the required updates.

- Direct entry and Excel/CSV import into our CRM are supported.
- External CRM import must include new leads and updates, not repeated full-list duplication.
- Support connector-specific field mapping, stable external IDs, retries and diagnostics.
- Define field ownership, assignment authority, conflict resolution and deletion handling.
- Prevent update loops and duplicate events. Phone matching must be company-scoped and ambiguous matches reviewed, not blindly merged.
- Universal API compatibility is not promised: different CRM APIs require adapters.
- Existing CFL integration stays operational until a separately approved, tested migration with rollback.

## 3. Leads, calls and sales workflow

- Leads: manual entry, bulk import, duplicate review/merge, source, stage, score, quality, owner and date filters.
- Dates: compact Today/custom date-range controls; distinguish created/imported/assigned/updated dates explicitly.
- Manual and bulk assignment; automatic assignment considers availability, leave and workload.
- Configurable company stages, dispositions and qualification fields.
- Lead timeline: source/import, assignment changes, calls, salesperson, date/time, SIM, duration in minutes/seconds, notes, disposition, follow-ups and stage changes.
- Correct incoming/outgoing/missed/not-connected classification; opening a dialer must not be counted as a completed outgoing call.
- Capture permitted selected-work-SIM call metadata, including unmatched numbers separately, and safely associate matching leads.
- Post-call disposition capture with appropriate next action/follow-up; validate Android lifecycle limitations on real devices.
- Custom follow-up date/time; Save + WhatsApp and WhatsApp Business support.
- Every active lead should have an owner, next action and due date, with appropriate exceptions for closed/opted-out leads.
- Daily queue: untouched leads, callbacks, due/overdue follow-ups and stalled leads.
- First-contact timing, lead ageing, manager alerts and controlled reassignment/escalation.
- Invalid-number, do-not-contact/opt-out, lost and unqualified reasons.
- Deal value and explicit won/lost outcomes to connect activity to sales.
- Audio recording is NOT included in this initial scope.

## 4. Quality and analytics

- Separate lead quality from salesperson performance.
- Explainable rule-based qualification: Fit (need, budget, geography, suitability) and Interest (response, meeting, quotation, purchase timeline).
- Unknown data stays unknown; long call duration alone does not imply lead or conversation quality.
- Report calls, connection rate, talk time, first-contact time, untouched leads, timely follow-ups, ageing, qualified leads, conversions, deal value and lost reasons.
- Source/campaign and salesperson comparisons must account for lead mix and measurement periods.
- Actionable manager exceptions: untouched leads, overdue work, missing next actions and sync/permission failures.
- Prioritize reliable tracking and workflow before AI scoring or sales prediction.

## 5. Subscription and billing

Confirmed:

- Price per salesperson, including company accounts; company-specific price is manually configurable.
- Monthly, three-month and yearly plans.
- Razorpay auto-renewal with required customer authorization.
- Separate purchased seats from assigned users; replacing staff preserves history.
- Free bundled Company CRM does not create unlimited paid salesperson accounts.
- Price changes use explicit effective renewal dates and required consent; do not retroactively change a paid period.
- Support renewal reminders, payment history, cancellation, failures and verified/idempotent payment webhooks.
- Expiry: existing data read-only, no export. Proposed agreed-roadmap default also stops new edits and new activity collection/sync; normal phone calls remain unaffected.
- Enforce entitlements on the server, not just by hiding app buttons.
- Define how valid pre-expiry offline events are accepted without enabling new expired-period activity.

Details to settle before billing implementation:

- Seat addition/reduction effective dates, proration and charge preview.
- Retry/grace-period and cancellation effective-date rules.
- Storage, API, retention and manager-login limits; do not promise unlimited usage.
- Current payment-provider and distribution-policy requirements must be verified before release.

## 6. Performance architecture

Proposed architecture, subject to Phase 0 code/hosting audit:

- PostgreSQL as the durable source of truth for business records.
- Modular backend with separately scalable workers; avoid premature microservices.
- Redis for selective short-lived caching and rate limiting, not the sole store for calls, payments or pending sync.
- Durable database outbox/jobs for background integration work, with idempotent consumers and retry visibility.
- Android local database for offline reads and pending operations.
- Separate object storage for imports, exports and attachments.

### Database design

- Company-scoped records and authorization, including safe cross-table relationships.
- Separate companies, users/memberships, seats, leads, call events, notes, follow-ups, assignments, subscriptions, payments, integration mappings and audit history.
- Composite indexes based on real company/salesperson/date/filter query patterns; measure query plans before adding indexes.
- Cursor pagination, typically 25–50 rows, with stable ordering.
- Indexed number/name search; avoid full-table scans and per-row extra queries.
- Connection pooling, bounded queries and slow-query monitoring.
- Incrementally maintained daily summaries for reports, with reconciliation to source records.
- Batch imports/writes and background exports, with progress and error reporting.
- Add time partitioning to large history tables only when measured scale warrants it.

### Redis and cache safety

- Company-specific keys, bounded TTLs and invalidation on relevant writes.
- Cache expensive summaries selectively; prevent simultaneous cache misses from overwhelming the database.
- Cache failure should allow safe database fallback with load controls.
- Session revocation/subscription authorization must not depend on stale cached permission data.
- Show report freshness; immediate activity and asynchronous aggregates may have different update times.

### Sync and UI

- Delta/cursor sync, small batched payloads, retry backoff, stable event IDs and server acknowledgements.
- Preserve deletes/reassignments through appropriate change markers; do not rely solely on timestamps.
- Foreground requests approximately every 10 seconds; background scheduling follows Android limitations, not a guaranteed 10-second timer.
- Use webhooks when supported and controlled polling otherwise.
- Paginated/virtualized lists, debounced search and cancellation of obsolete requests.
- External CRM or heavy report failure must not block local call capture or core CRM work.

## 7. Security and operations

- Company isolation, least-privilege roles, secure sessions and owner-admin two-step verification.
- Server-only encrypted integration secrets and audited sensitive-data access.
- Clear work-SIM/call-data disclosure and consent; privacy-aware collection boundaries.
- Backup plus restore tests, retention, deletion requests and account closure procedures.
- Monitoring for sync failures, queue lag, errors, database saturation and app versions.
- No production demo data. Controlled migration, feature flags where useful and rollback procedures.
- Support email: Yogeshkukadiya92@gmail.com
- Support phone: 9825344428

## 8. Phased implementation roadmap

Each phase includes tests, performance checks, documentation and a user-facing summary. Do not automatically advance without the user's requested scope.

| Phase | Scope | Completion gate |
| --- | --- | --- |
| 0 — Audit and baseline | Inspect existing Android/CFL implementation, APIs, database, hosting, bugs, timings and migration paths | Evidence-backed inventory, baseline and safe compatibility/rollback plan |
| 1 — Foundation | Database schema/migrations, tenant isolation, core API modules, pooling, indexes, durable jobs, logs and health checks | Isolation and core database/API tests pass |
| 2 — Owner Admin | Secure admin login, companies, managers, salespersons, seats, manual pricing configuration and audit log | Admin can safely create and manage companies/users/access |
| 3 — App accounts | Central login, one-phone sessions, offline validity, secure account switching and migration | Two-device and offline/online session tests pass |
| 4 — Company CRM | Leads, import, duplicate review, filters, stages, manual/bulk assignment and basic timeline | Large-dataset lead workflows remain responsive and isolated |
| 5 — App/CRM sync | Assigned leads, calls, SIM/duration, notes, dispositions, follow-ups, unmatched calls, delta/offline sync | Restart/network/retry tests show no lost acknowledged data or duplicates |
| 6 — Sales workflow | Auto-assignment, availability, next actions, first contact, alerts, ageing, opt-out and reassignment | End-to-end lead-to-closure workflow verified |
| 7 — Analytics | Fit/interest, sources, deal outcomes, manager exceptions, summary tables and selective Redis caching | Reports reconcile to source data and do not impair core sync |
| 8 — Billing | Razorpay, renewal, manual prices, seats, payment events and expiry enforcement | Test activation/renewal/failure/cancellation/expiry cycles verified |
| 9 — CRM connectors | Admin configuration, mappings, webhooks/polling, field ownership, retries and diagnostics | First connector passes create/update/retry/conflict/loop tests; CFL preserved |
| 10 — Release/pilot | Security, regression, real phones, load tests, restore/recovery, consent/policies and pilot rollout | Agreed release gates pass with documented evidence and known limitations |

## 9. Provisional performance test targets

These are proposed targets, NOT existing measurements or capacity guarantees. Finalize with the Phase 0 infrastructure budget and realistic workload.

| Measure | Initial target |
| --- | --- |
| Typical paginated list/filter API | p95 within 500 ms under agreed load |
| First usable dashboard content | Approximately 2 seconds on the agreed connection/device |
| Online foreground app-to-central update | Approximately 10–15 seconds under normal conditions; not an external CRM guarantee |
| Large imports/exports | Prompt background-job acknowledgement with progress |
| App interaction | Smooth paginated scrolling; no network-blocked main UI |

Initial load-test scenario: 500 concurrently active users, 1 million total leads and 10 million call records, including an uneven distribution with one large company. Define request mix, polling cadence, write rate, hardware and cold/warm cache conditions before interpreting results. Test cache outages and noisy-neighbor effects as well as steady state. Measure costs and bottlenecks rather than claiming capacity from record counts alone.

## 10. Progress log

| Date | Phase | Status | Evidence / next action |
| --- | --- | --- | --- |
| 2026-09-06 | Planning | Saved | User requested a durable Markdown roadmap. Next implementation step, when requested: Phase 0. No phase implementation performed by saving this document. |
| 2026-09-06 | Pre-roadmap product enhancement | In progress, locally verified | Added post-call meeting/quotation/payment outcomes, Google Meet/Zoom link capture, calendar handoff, company-configurable quick-note templates, user-triggered voice-to-text notes, prior-activity handover sync and salesperson handover acknowledgement. Android compilation/unit tests and 105 CFL tests passed. Dashboard typecheck did not finish in the available verification run; deployment and physical-device UI validation remain pending. |
| 2026-09-06 | Phase 1 — Foundation | In progress | Added additive versioned PostgreSQL foundation migration, forced tenant RLS, tenant-consistent foreign keys, composite indexes, bounded pooling, durable SKIP LOCKED outbox primitives, migration checksum/lock runner and health API. New foundation tests passed 5/5 and the full CRM suite passed 111/111. Live migration/isolation execution is pending because no DATABASE_URL, Docker or local PostgreSQL is available. Full TypeScript and Next builds were stopped after producing no output/artifacts for an extended period; the workspace contains 15 duplicate “2” source artifacts that require a separate safe cleanup decision. |
| 2026-09-06 | Phase 2 — Owner Admin | In progress | Added a separate owner-only login and console, production TOTP requirement, scrypt passwords, revocable database sessions, company lifecycle controls, purchased/assigned salesperson seats, company and salesperson price settings, company user creation/password reset, session revocation on access changes, and append-only privileged audit history. Owner/security plus full CRM tests passed (116/116), and 12 Phase 2 files passed isolated syntax compilation. Live database migration and authenticated browser workflow verification remain pending until PostgreSQL is configured. Next/Turbopack and webpack both stalled before binding a local server, consistent with the unresolved duplicate-source workspace issue recorded in Phase 1. |
| 2026-09-06 | Phase 3 — App accounts and sessions | Implemented locally; live DB gate pending | Added atomic central salesperson login with device binding, database-enforced one-active-phone sessions, prior-device revocation, rotating opaque refresh tokens, 15-minute access tokens, a renewable 72-hour offline-validity window, server-side session validation on protected CallFlow routes, logout revocation, and legacy CFL login compatibility. Android now sends device identity in the login request, enforces the offline deadline, handles revoked refresh sessions, and clears prior-account local CRM data/cursors during logout or account switching. Android clean compilation/unit tests passed and the complete dashboard suite passed 120/120. Two-device and offline/online integration tests still require migrated PostgreSQL plus physical/emulated clients connected to the live API. |
| 2026-09-06 | Phase 4 — Company CRM | Implemented locally; live data/performance gate pending | Added a separate company-manager/viewer login and `/company/leads` workspace backed by tenant-forced RLS. Includes bounded cursor pagination, indexed name/company/phone and structured filters, manual lead creation, 5,000-row Excel/CSV batch import, duplicate reporting without unsafe auto-merge, configurable stages, up-to-500-lead bulk assignment and a calls/notes/follow-ups/activity timeline. Existing CFL `app_state` routes remain unchanged. Phase 4 security/performance-contract tests and the full dashboard suite pass locally; live PostgreSQL migration, cross-tenant integration testing and million-lead query-plan/load verification remain pending. |
| 2026-09-06 | Phase 5 — App/CRM sync | Implemented locally; live restart/network gate pending | Connected central app sessions to tenant-scoped PostgreSQL sync while preserving legacy CFL token routes. Added server-validated device and assigned-lead ownership, idempotent event receipts, durable integration outbox jobs, bounded per-event savepoints, calls with SIM/duration, notes, dispositions, follow-ups, unmatched calls, unique assigned-phone matching, targeted reassignment removals, deletion markers and independent stable cursors for lossless bounded delta batches. Android now atomically marks acknowledged outbox events and corresponding local records synced. Full dashboard tests pass 130/130 and Android compilation/unit tests pass. Live migrated-PostgreSQL tests for response loss, process restart, network interruption, cross-tenant rejection and multi-batch replay remain required before the Phase 5 completion gate can be closed. |
| 2026-09-06 | Phase 7 — Analytics | Implemented locally; live data/load gate pending | Added reconciled daily summaries, India business-date filters, funnel/call/source/salesperson/fit/interest/outcome reporting, live manager exceptions, data freshness, explicit reconciliation status and a fail-open five-minute Redis REST cache limited to completed historical ranges. Current data and core sync never depend on cache. Automated Phase 7 and full dashboard tests are the local gate; migrated PostgreSQL reconciliation, query plans and agreed load distribution remain required. |
| 2026-09-06 | Phase 8 — Billing | Implemented locally; Razorpay test-mode gate pending | Added tenant-isolated subscription profiles and ledgers, monthly/quarterly/yearly terms, manual seat pricing/access, owner billing controls, server-side Razorpay subscription creation, duplicate-create protection, cycle-end cancellation, signed raw-body webhook processing, event/payment idempotency, out-of-order protection, renewal/grace/cancel/expiry policy and read-only enforcement across Company CRM and central Android sync. Android receives entitlement state and disables calling/export after expiry. Automated suites are the local gate; live migrated-PostgreSQL and Razorpay test-mode lifecycle validation remain required. |
| 2026-09-06 | Phase 9 — CRM connectors | Implemented locally; staging-provider gate pending | Added owner-controlled generic REST connectors with encrypted server-only credentials, field mappings and ownership, signed/idempotent inbound webhooks, stable remote links, manual conflict review, origin-loop suppression, durable outbound create/update jobs, bounded polling, exponential retries and diagnostics. Existing CFL integration remains untouched. Automated tests are the local gate; migrated PostgreSQL and a staging CRM are required for live create/update/retry/conflict/loop verification. |
| 2026-09-06 | Phase 10 — Release/pilot | Implemented locally; conditional internal-pilot gate | Added release provenance and staged rollout controls, pilot-company assignment, queue/connector/session/privacy readiness signals, versioned Android call-tracking disclosure, privacy/terms/data-request surfaces, and a guarded realistic k6 scenario. Broad release remains blocked until migrated staging, Razorpay/CRM lifecycle, physical dual-SIM devices, backup restore and authorised 500-user tests pass with evidence. |

## Technical references used during planning

- PostgreSQL indexes: https://www.postgresql.org/docs/18/indexes.html
- Redis cache-aside: https://redis.io/docs/latest/develop/use-cases/cache-aside/

Verify current documentation and actual installed stack versions during implementation.
# Implementation progress

- Phase 6 implemented locally: licensed availability-aware auto-assignment, assignment rules, leave/shift exclusion, first-contact and next-action tracking, SLA/stalled/ageing alerts, opt-out controls and reasoned reassignment. Automated regression coverage is included; live PostgreSQL + Android end-to-end validation remains the deployment gate.
