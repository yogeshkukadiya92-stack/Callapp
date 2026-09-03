# CallFlow Live Wiring & QA Report

## Overall Status

Mostly ready. The Android app now defaults to the live Coach For Life connector, critical data/auth/sync defects found in this pass are fixed, Android compilation and unit tests pass, dashboard type-check and 31 CRM/call tests pass, and the live-config debug build is installed on the Android emulator. A physical-device call lifecycle and a clean dashboard production build remain release gates.

## Fixed

- **P0 — Normal builds opened in demo mode:** changed the default API to `https://dashboard.coachforlife.in/api/callflow/` and disabled the fake backend by default. Fake data remains available only through an explicit developer build property.
- **P0 — Incremental sync ignored its saved cursor:** `GET sync/changes` now receives the current cursor instead of always requesting a full initial delta.
- **P0 — Sync used a hard-coded device id:** batch uploads now use the registered device id stored in the authenticated session.
- **P0 — Successful outbox events could become failed again:** a later delta-download failure can no longer regress an already accepted event and trigger a duplicate resend.
- **P0 — Unmatched calls could link to another salesperson's lead:** dashboard phone matching now considers only leads assigned to the authenticated salesperson.
- **P1 — Call-to-lead backfill was incomplete:** dashboard sync now processes call `UPDATE` events and updates the existing call record instead of creating a false call-start entry.
- **P1 — Login displayed raw `HTTP 401`:** validation, duplicate-submit protection, and clear 400/401/403/404/429/5xx/offline messages were added.
- **P1 — Emulator tests targeted old/unstable UI labels:** the navigation has a stable semantic tag and the tests now match the current Home and Leads experience.
- **P2 — Generated build cache corruption:** isolated QA builds avoid conflict-copy files created by background folder synchronization in the workspace.

## Live Controls Verified

The durable control inventory is in `docs/live-wiring-inventory.csv`. It covers authentication, Home KPI navigation, direct lead calling, manual dial, call categories, lead search/date/advanced filters, lead score, WhatsApp, post-call save, follow-ups, reports/CSV, shift availability/location, phone setup, and bidirectional sync.

## Screens and Areas Checked

- Onboarding and login validation/error states
- Home KPIs, daily targets, priority queue, and navigation
- Calls analytics, filters, outcome breakdown, and empty state
- Assigned Leads, lead count, search, date range, advanced filters, score, stage, and direct call action
- Lead detail, call performance, WhatsApp, and activity timeline
- Follow-ups: Today, Overdue, Done, Open, Call, and Edit actions
- More, profile, reports, Team Hub, and Settings
- Sync health, connector status, shift availability, and phone-permission setup
- Android 17 system Phone-role prompt and permission boundaries
- Dashboard CallFlow API auth, delta/batch sync, call analytics, lead assignment, and auto-assignment availability

## Validation Done

- Android live compile and unit tests: **pass** (`compileDebugKotlin`, `testDebugUnitTest`)
- Android database migration emulator test: **pass**
- Android onboarding → login → Home → Leads navigation emulator test: **pass**
- Live-config app installation and cold launch on emulator: **pass**; onboarding rendered and crash-log scan was clean
- Live login/manual QA: **pass**; authenticated salesperson dashboard loaded 29 assigned leads and live KPI data during this audit
- Dashboard TypeScript type-check: **pass**
- Dashboard CRM/call test suite: **31/31 pass**
- Git whitespace/error check: **pass**
- Dashboard optimized production build: **not completed**. Source-folder builds are affected by background conflict copies; isolated Turbopack rejects an external `node_modules` symlink, and a full dependency copy was removed by the temporary-directory environment before completion.

## Still Needs Attention

- **P1 release gate — Physical call lifecycle:** grant the default Phone role and phone/call-log permissions on a dedicated test handset, then make connected, rejected, unanswered outgoing, missed incoming, dual-SIM, app-killed, reboot, and offline calls. Android 17's role prompt grants broad Phone/SMS/Contacts/Microphone access, so it was not approved automatically during QA.
- **P1 release gate — Dashboard production build:** run `npm run build` from a non-synchronized local checkout with a real local `node_modules` folder. Type-check and all dashboard tests already pass.
- **P2 tooling — Generated-file conflict copies:** move the repository or at least build outputs out of cloud-synchronized `Documents`, or exclude `app/build`, `.gradle`, `.next`, and `node_modules` from synchronization.
- **P3 maintenance — Moshi warning:** migrate Moshi Kotlin code generation from KAPT to KSP before Moshi 2.0.

## Files Changed in This Audit

- `app/build.gradle.kts` — live defaults
- `app/src/main/java/com/callflow/app/ui/auth/AuthViewModel.kt` — safe login validation/messages
- `app/src/main/java/com/callflow/app/data/repository/OutboxSyncRepository.kt` — registered device id and cursor continuity
- `app/src/main/java/com/callflow/app/data/local/CallFlowDao.kt` — synced-event regression guard
- `app/src/main/java/com/callflow/app/ui/CallFlowApp.kt` — stable navigation semantics
- `app/src/androidTest/java/com/callflow/app/ui/CriticalCallingFlowTest.kt` — current stable critical UI coverage
- `app/src/test/java/com/callflow/app/data/remote/LiveEndpointConfigurationTest.kt` — live-default regression tests
- `app/src/test/java/com/callflow/app/ui/auth/LoginErrorMessageTest.kt` — login error regression tests
- `app/src/test/java/com/callflow/app/data/repository/OfflineCallDurabilityTest.kt` — outbox durability regression test
- `CFLdashboard-source/app/api/callflow/sync/batch/route.ts` — authorized lead linking and call update wiring
- `CFLdashboard-source/tests/callflow-connector.test.ts` — connector type-safe test data
- `README.md` and `CFL_DASHBOARD_CONNECTOR.md` — live-build documentation
