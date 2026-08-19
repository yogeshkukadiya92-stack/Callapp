# CallFlow implementation plan

CallFlow will be delivered incrementally, with Room as the client source of truth and Android Telecom isolated behind domain interfaces. The initial single-module layout uses package boundaries that can become Gradle modules once build-time or team ownership warrants it.

## Assumptions

- Minimum Android 8.0 (API 26), target/compile SDK 35.
- India is the initial dialing region; normalization remains country-aware through an injected policy.
- The fake backend is available only through repository/data-source bindings and never from UI or business logic.
- Automatic call capture is feature-flagged and requires the default dialer role. Manual CRM operation always remains available.
- Server timestamps are UTC `Instant` values; presentation uses the configured/user timezone.

## Delivery phases

1. **Foundation** — Gradle project, Compose/Material 3, navigation shell, Hilt, Room schema, Retrofit contract, DataStore session/config foundation, repository boundaries, fake backend binding, baseline tests and documentation.
2. **Leads** — indexed lead persistence, Paging search, detail/timeline, duplicate detection, local calling queue.
3. **Calling** — `CallIntegrationManager`, dialer-role onboarding, `InCallService`, lifecycle timestamps, number matching and safe manual fallback.
4. **Post-call workflow** — configurable disposition, notes, follow-up scheduling, transactional Save & Next.
5. **Offline sync** — durable outbox, idempotent batch upload, cursor delta sync, WorkManager retry/backoff and conflict surfacing.
6. **Daily operation** — home metrics, history, follow-up queues, targets and support-facing sync status.
7. **Hardening** — security review, migrations, performance profiling, process-death/offline/Compose end-to-end tests and accessibility polish.

## Current status

- Foundation and leads are implemented.
- Durable call attempts, lifecycle persistence, configurable dispositions, follow-ups, Save & Next, outbox sync, history, metrics, and support sync status are implemented.
- Default-dialer integration includes role onboarding, an external dial entry, Telecom call placement, lock-screen incoming/ongoing UI, CallStyle notifications, core call/audio controls, lifecycle persistence, and safe manual fallback. Physical OEM/device certification remains a release gate.
- Authentication/session encryption and emulator process-death verification are implemented.
- Device registration and approval enforcement are implemented with a generated install identifier and fail-closed state parsing. Authenticated interception, single-flight refresh rotation, request replay, and explicit revocation handling are implemented. Cursor-based delta application now covers leads, calls, call events, notes, follow-ups, stages, dispositions, and app configuration with append-only or versioned conflict semantics as appropriate. Physical-device/OEM certification remains a release blocker.
- First-run disclosure onboarding and centralized, contextual permission-state reporting are implemented without startup permission prompts; manual CRM remains the fallback.
- Outgoing call creation now commits the call record, initial lifecycle event, and idempotent outbox event in one Room transaction. File-backed integration tests verify restart durability and rollback on duplicate event keys.
- Telecom correlation now fails safe: only a unique normalized lead and unique recent outgoing attempt are linked. Ambiguous calls become durable unlinked records instead of being assigned to an arbitrary lead or attempt.
- The encrypted session boundary is independently testable; local HTTP integration tests cover bearer injection, single-flight refresh/replay, token rotation, rejected-refresh logout, bounded retries, and explicit server revocation.
- Android Test Orchestrator now covers the complete missing-dialer-role manual journey from call intent through disposition, note, follow-up, and Save & Next; the OEM dialer is isolated while ACTION_DIAL is asserted.
- A 10,000-lead Room regression test enforces bounded queue/search behavior and caught a text-only query bug, which is fixed. Lead rows now expose merged TalkBack descriptions and errors announce through accessibility live regions.
- Android Test Orchestrator, a real exported-schema 1→3 migration test, and a clean-install onboarding-to-calling Compose test are implemented and pass on Android 37.1. Physical-device/OEM Telecom certification remains the primary release blocker.

## Phase gates

Each phase must compile, pass unit/static checks, keep business writes durable before UI success, and add tests for its failure paths. No destructive Room migration fallback is permitted.
