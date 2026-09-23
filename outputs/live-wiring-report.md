# Live Wiring Report

## Completed

- Audited Android interaction surfaces and CallFlow dashboard contracts.
- Kept fake login/data behind the explicit `callflow.useFakeBackend=true` QA build flag; normal builds default to false.
- Verified production endpoint is `https://dashboard.coachforlife.in/api/callflow/`.
- Fixed Analytics drill-down navigation so filtered call lists retain the bottom navigation and Calls selection.
- Updated stale emulator selectors after Home was converted to Analytics.

## Backend/API Connected

- Authentication and logout: `/auth/login`, `/auth/refresh`, `/auth/logout`.
- Device binding: `/devices/register`.
- Two-way durable sync: `/sync/changes`, `/sync/batch`.
- Live availability, performance, shift, engagement and location-check-in routes.
- Live server probes returned authenticated `401` responses instead of `404`, confirming route availability and access control.

## Database/Auth/Environment

- Production only displays dashboard-assigned leads with server IDs.
- Login clears prior account rows and sync cursors before pulling the new account.
- Logout revokes the server session where reachable, then clears local account rows, cursors and encrypted session.
- Calls, notes, dispositions and follow-ups use the local Room outbox before dashboard sync.

## Tests Performed

- Android production build and unit tests.
- Android emulator navigation/tap-through across Analytics, Dial, Calls, Call details, notes, Leads, Follow-ups, More, Profile, Reports, Team Hub and Settings.
- Dashboard TypeScript typecheck initiated with no reported diagnostics.
- Dashboard CRM test set exercised authentication, tenant isolation, sync idempotency, calls, notes, follow-ups, availability, location and reporting with passing assertions and no reported failure.
- Live route probes for CRM status, sync changes and login access control.

## Remaining External Requirement

- Each salesperson phone must grant CallFlow the Android default Phone role plus Phone, Call Log and notification permissions. Android does not allow an app to silently grant these permissions.
- A valid salesperson credential and active server-side device approval are required for a complete production login and authenticated mutation test.
