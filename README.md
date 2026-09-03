# CallFlow

CallFlow is an offline-first native Android application for a sales calling and follow-up team. The current MVP includes local lead search/detail/timeline, a prioritized calling queue, call-attempt persistence, Telecom lifecycle capture when supported, post-call dispositions, notes, follow-ups, Save & Next, call history, live daily metrics, and a WorkManager outbox.

Startup now routes through authentication. Development fake login and real API login share `AuthRepository`; passwords are not stored and resulting tokens are AES-GCM encrypted with an Android Keystore key before DataStore persistence.

## Build

Requirements: JDK 17+, Android SDK 35, and Android Studio Ladybug or newer.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Set the SDK path in an untracked `local.properties`. The application ID can be changed with `callflow.applicationId=com.yourcompany.callflow` in `gradle.properties`. Normal builds use the live Coach For Life endpoint with demo mode disabled. The API base URL, dashboard connector id, and fake-backend switch remain overridable non-secret Gradle properties for controlled environments.

```bash
./gradlew assembleDebug \
  -Pcallflow.useFakeBackend=false \
  -Pcallflow.apiBaseUrl=https://your-cfl-dashboard.example.com/api/ \
  -Pcallflow.dashboardConnectorId=cfl-dashboard
```

See [CFL_DASHBOARD_CONNECTOR.md](CFL_DASHBOARD_CONNECTOR.md) for the CFLDashboard and custom dashboard integration contract.

For a local working API:

```bash
python3 cfl-dashboard-server/server.py --host 0.0.0.0 --port 8010
./gradlew assembleDebug \
  -Pcallflow.useFakeBackend=false \
  -Pcallflow.apiBaseUrl=http://10.0.2.2:8010/ \
  -Pcallflow.dashboardConnectorId=cfl-dashboard
```

## Architecture

Room is the UI-facing source of truth. UI state is delivered by repositories as `Flow`; network changes update Room, and local mutations pair their entity write with a durable `sync_events` outbox write. Retrofit DTOs never enter UI code directly. Hilt owns bindings and makes fake/real data sources replaceable.

The optional development fake repository seeds two local records only when a developer explicitly builds with `-Pcallflow.useFakeBackend=true`. Normal builds never accept fake login or seed demo leads. Fake sync acknowledges durable outbox events through `SyncRepository`; it does not bypass Room or UI architecture.

## Call integration

Phase 1 deliberately uses `ACTION_DIAL`, which needs no sensitive runtime permission and preserves manual CRM mode. Automatic lifecycle capture will be enabled only after explicit disclosure and successful `ROLE_DIALER` acquisition. See [CALL_INTEGRATION.md](CALL_INTEGRATION.md).

Android/OEM behavior varies: dialer-role prompts, background activity starts, dual-SIM behavior, and Telecom callbacks may differ. Automatic capture cannot be promised unless the app is the enabled default phone app and the device implements Telecom correctly.

First launch now presents a three-step disclosure covering offline business data and optional call tracking. It requests no permissions: dialer role and runtime permissions remain contextual, user initiated, and independently reported in More. Manual CRM remains available when access is denied. No call recording or Accessibility Service is present.

## Container deployment

The root `Dockerfile` builds the debug APK in an Android SDK container and serves it from an Nginx download page at `/callflow-debug.apk`. This is intended for controlled QA distribution; production distribution still requires a release signing key and Play Console workflow.
