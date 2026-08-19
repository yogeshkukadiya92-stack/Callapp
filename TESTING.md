# Testing

Run `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, and `./gradlew assembleDebug`.

Current unit coverage verifies country-aware number normalization and duration derivation from lifecycle timestamps. Each subsequent phase adds repository/DAO/ViewModel tests with fake clocks and data sources.

`OfflineCallDurabilityTest` uses a file-backed Room database and fake clock to verify that an initiated call, its lifecycle event, and its pending outbox event survive a database close/reopen. It also forces a duplicate outbox idempotency key and verifies the entire transaction rolls back, preventing partial call records.

`CallMatchResolverTest` verifies exact lead/open-attempt matching and the fail-safe ambiguous path. Duplicate leads or multiple recent open attempts remain unlinked rather than being silently attributed to an arbitrary record.

`AuthHttpTest` uses a local HTTP server and in-memory token store to verify bearer injection, 401 refresh/replay with access and refresh token rotation, session clearing after a rejected refresh, bounded retries, and explicit server revocation handling.

`LeadSearchScaleTest` loads 10,000 leads into Room, verifies the calling queue remains capped at 50, confirms a unique text query returns only its matching lead, and enforces a two-second search ceiling. This test exposed and now guards against an empty-phone-filter bug that previously made text-only searches match every lead. Instrumentation also asserts that each lead card exposes one merged, actionable TalkBack description; asynchronous call errors use assertive live regions.

## Emulator smoke verification

On 19 August 2026 the debug APK was installed on the available Android 37.1 arm64 emulator. The accessibility tree verified Login → Home, the fake-repository leads, metrics/navigation surfaces, and encrypted-session restoration after a forced process stop and relaunch. `agent-device` was unavailable on the host, so deterministic SDK `adb` discovery/install/UIAutomator inspection was used as the documented fallback.

Android Test Orchestrator now runs instrumentation tests with cleared app data. `CallFlowMigrationTest` creates the exported version-1 schema, inserts data, migrates through versions 2 and 3, validates the final schema, and confirms the lead remains. `CriticalCallingFlowTest` verifies clean-install Onboarding → Login → Home → Start Calling → Leads → Lead Detail, plus missing-dialer-role manual fallback through ACTION_DIAL → Disposition → Note → Follow-up → Save & Next. Espresso Intents isolates the OEM dialer while asserting the correct external action. All three instrumentation tests pass on the available Android 37.1 emulator.

Release gates include reconnect and real-server idempotency verification; explicit runtime-permission denial and role-removal transitions; and representative low-end/OEM devices. Large local search and baseline lead-card/error accessibility are now covered automatically, alongside the complete manual workflow, local durability, migrations, matching, and session security.
