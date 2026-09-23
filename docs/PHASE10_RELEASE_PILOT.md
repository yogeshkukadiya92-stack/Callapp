# Phase 10 — Release and pilot

## Implemented

- Owner release registry with Android version code/name, minimum supported version, artifact SHA-256, release notes, staged rollout percentage, pause control and company pilot assignment.
- Release-health view covering durable queue depth/age, failing CRM connectors, active app sessions and overdue privacy requests.
- Versioned, prominent Android call-tracking disclosure before requesting Phone/Call log/default-phone access. It explains captured fields, real-call-only recording, dashboard sync and limited location capture.
- Public privacy notice, service terms and bounded data access/correction/deletion/account-closure request workflow with owner audit.
- Database records for release provenance, pilot companies, user consent evidence and privacy requests.
- Safe k6 staging script with explicit authorization switch, production block, smoke/baseline/500-user stages, realistic company-lead/app-sync traffic, think time and p95/p99/error thresholds.

## Risk-based release matrix

| Area | Local evidence | Live gate |
|---|---|---|
| Tenant/auth/session isolation | Automated regression suite | Migrated staging database; two companies and two devices |
| Billing expiry | Unit/static policy tests | Razorpay test-mode renewal/failure/cancellation |
| CRM connectors | Create/update/retry/conflict/loop contract tests | Staging provider credentials and webhook |
| Call capture | Android unit/source tests | Two physical dual-SIM phones and supported OEMs |
| Permission disclosure | Source regression test | Fresh install emulator and physical-device review |
| Restore/recovery | Schema/migration checks | Encrypted backup restore drill with timing/evidence |
| Performance | Guarded k6 scenario supplied | Authorized staging smoke → baseline → target → soak |

## Current decision

**CONDITIONAL GO for a controlled internal pilot only. NO-GO for broad production rollout until the live gates below are evidenced.**

Conditions:

1. Apply migrations through `010_release_pilot.sql` in staging and production using the checksum migration runner.
2. Configure production-only encryption, session, Razorpay, connector worker and webhook secrets.
3. Pass fresh-install/login/default-phone/real-call/post-call/offline/retry/logout tests on emulator and at least two representative physical phones.
4. Complete a staging backup restore drill and verify tenant isolation after restore.
5. Run the supplied k6 smoke stage first; only ramp to 500 after monitoring and stop conditions are approved. Do not test production without explicit approval.
6. Register the final signed APK version and SHA-256, select pilot companies, monitor errors/queue age/sync lag, and retain a pause/rollback owner.

## Rollback

- Pause the release in Owner Admin and disable affected connectors without deleting data.
- Keep database migrations additive; application rollback must remain schema-compatible.
- Preserve pending outbox jobs so a transient rollback does not lose acknowledged call/lead changes.
- Re-enable the previous signed app build for the pilot cohort and document the incident/time window.
