# API contract

All endpoints use TLS, JSON, bearer access tokens, refresh-token rotation, UTC ISO-8601 timestamps, and server-authoritative employee identity.

Authenticated responses may use HTTP 401 to request token refresh. Refresh rejection with HTTP 400, 401, or 403 ends the session. A protected endpoint may explicitly revoke a session with HTTP 403 plus `X-Session-Revoked: true`; ordinary authorization-denied 403 responses do not automatically destroy credentials.

Core routes: `POST /auth/login`, `POST /auth/refresh`, `POST /devices/register`, `GET /me`, `GET /app-config`, `GET /crm/status`, lead CRUD/timeline, call/call-event creation, dispositions, notes, follow-ups, queue, today stats, `POST /sync/batch`, and `GET /sync/changes?cursor=`.

Dashboard integrations are connector-neutral. Android sends `X-CallFlow-Client: android` and `X-CallFlow-Connector: <connector-id>` with API requests. CFLDashboard should use `cfl-dashboard`; future dashboards can use their own connector id while preserving the same endpoint contract. See [CFL_DASHBOARD_CONNECTOR.md](CFL_DASHBOARD_CONNECTOR.md).

`POST /devices/register` is authenticated with the newly issued access token and accepts only the generated installation ID and display metadata. It returns the server device ID and one of `ACTIVE`, `PENDING_APPROVAL`, `BLOCKED`, or `REVOKED`. Any unrecognized state is treated as pending approval by the client.

Every mutation accepts a UUID idempotency key. Batch sync returns accepted IDs, per-event failures, updated/deleted entities, a next cursor, and server time. Editable DTOs include a version; HTTP 409 responses include the current server representation and must never trigger a silent overwrite.

`GET /sync/changes?cursor=` returns changed leads, calls, call events, notes, follow-ups, pipeline stages, dispositions, application configuration, supported deletion IDs, `nextCursor`, and server time. Editable lead/follow-up changes include `version`, `updatedAt`, and actor metadata where applicable. Cursors are opaque and clients must not manufacture or increment them.

Network DTOs are transport-only and require explicit mapping to local/domain models.
