# Connecting another CRM

On a new installation, open CRM connection on the sign-in screen and configure the adapter's HTTPS base URL and connector ID before the first API request. Existing installations stay locked to their original endpoint. Never clear app data to migrate until pending work has been exported/synced and an administrator has planned migration.

This is a configurable CallFlow adapter client, not a universal translator for arbitrary vendor APIs. Implement the routes and exact request/response DTOs in `app/src/main/java/com/callflow/app/data/remote/CallFlowApi.kt` and `API_CONTRACT.md` on your server. Map vendor fields and authentication there; keep CRM master API credentials on the server. Mobile users authenticate with the adapter's login/refresh flow. Both authenticated traffic and refresh use the configured destination; redirects are disabled.

Required mapping: vendor contact ID -> lead id/serverId; assigned salesperson -> authenticated employee; phone -> normalizedPhone; stage -> stageId; next task -> nextFollowUpAt. Map all call, call-event, disposition, note and follow-up batch events bidirectionally. Preserve event UUID idempotency, deleted IDs, opaque cursors, and record versions. Reject writes to unassigned leads. Never infer salesperson identity from client payloads.

Acceptance: sign in, register a device, retrieve only assigned leads, upload a confirmed call with duration/SIM metadata, save status and follow-up, verify vendor-side records, then retry the same batch without duplication. Verify offline retry, revoked credentials, deleted/reassigned leads, and account isolation. A successful URL connection alone is not certification of this workflow.

Support: Yogeshkukadiya92@gmail.com / 9825344428.

Launch gate: in-app policy text is an initial disclosure, not Play approval. Publish a public privacy policy matching actual server retention/processing and developer identity; complete Play Data Safety, call-log/default-handler declarations and any required public deletion URL. Verify consent before call-log/location processing. Current mandatory permission gating and legacy call-log import require a focused policy review before Play distribution.
