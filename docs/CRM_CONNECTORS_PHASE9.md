# Phase 9 — CRM connectors

## Delivered

- Owner-only external CRM configuration. Credentials never move to the Android app and are encrypted at rest.
- A first generic REST connector with bearer-token or API-key authentication, inbound, outbound or two-way sync, configurable lead field mapping and field ownership.
- Existing Coach For Life/CallFlow CRM paths remain unchanged. Companies can continue without an external CRM.
- Signed raw-body inbound webhooks with a one-megabyte limit, stable event receipts, replay protection and explicit CallFlow-origin loop rejection.
- Stable connector/remote lead links, create/update handling and a manual conflict queue when both systems changed.
- Durable outbound jobs with bounded claims, idempotency keys, timeouts, exponential retry and diagnostics.
- Optional bounded polling for CRMs without webhooks. The worker claims due connectors atomically and imports at most 100 changed leads per run.
- Owner console controls to create, test, enable and disable connectors and inspect recent diagnostics/conflict counts.

## Generic REST contract

- Base URL must be public HTTPS in production.
- Connection test: `GET {baseUrl}/health`.
- Polling: `GET {baseUrl}/leads?limit=100&updated_after=<ISO timestamp>` returning `{ "leads": [...] }`.
- Create: `POST {baseUrl}/leads`; update: `PUT {baseUrl}/leads/{remoteId}`. A successful create must return `{ "id": "..." }`.
- Inbound webhook: `POST /api/webhooks/crm/{connectorId}` with `x-crm-event-id`, `x-crm-signature` and JSON `{ "id": "remote lead id", "lead": {...}, "updatedAt": "ISO time", "version": "optional" }`.
- Signature is lowercase hexadecimal HMAC-SHA256 of the unchanged raw request body using the one-time webhook secret shown at connector creation.
- Outbound requests include `x-callflow-origin: callflow` and an `idempotency-key` header.

## Runtime configuration

Set strong server-only `CRM_CONNECTOR_ENCRYPTION_KEY` and `CONNECTOR_WORKER_SECRET` values. Schedule authenticated `POST /api/internal/connectors/run` calls. Apply migration `009_crm_connectors.sql` before enabling a connector.

## Deployment gate

Use a staging CRM account and synthetic leads to verify create, update, duplicate replay, timeout/retry, conflict review, origin-loop suppression, credential rotation and disable behavior. DNS-level egress allow-listing and provider-specific pagination/rate-limit adapters should be configured before enabling an untrusted CRM endpoint in production.
