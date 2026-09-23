# Phase 8 — Subscription billing

## Delivered

- Per-company subscription profiles with monthly, three-month and yearly intervals.
- Owner-controlled billed seat count and per-seat price, aligned to the operational paid-seat count.
- Manual subscriptions with optional access expiry and an immutable manual billing-event audit entry.
- Razorpay subscription creation using an owner-configured plan ID and server-only credentials.
- Duplicate subscription creation protection and an owner action to cancel safely at the end of the current billing cycle.
- Authenticated Razorpay lifecycle webhooks for authenticated, active/charged, pending, halted, paused, resumed, cancelled, completed and updated states.
- Raw-body HMAC verification, one-megabyte request limit, `x-razorpay-event-id` deduplication, payment deduplication, out-of-order event protection and pre-linked subscription ownership.
- Real-time entitlements: active/current-period and payment-retry grace accounts have full access. Expired accounts retain read access but CRM mutations, mobile uploads, calling and report export are disabled.
- Existing companies migrate as `manual_active`, avoiding an accidental production lockout.

## Razorpay configuration

Set server-side `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_SUBSCRIPTION_WEBHOOK_SECRET`. Configure the production webhook URL as `/api/webhooks/razorpay-subscriptions` and subscribe to the supported subscription lifecycle events. Test and live secrets must remain separate.

The implementation follows Razorpay's documented requirements to validate the HMAC over the unchanged raw request body and deduplicate deliveries by `x-razorpay-event-id`. Webhook delivery order is not assumed.

## Deployment gate

Apply migrations through `008_subscription_billing.sql`. Before production, run Razorpay test-mode activation, successful renewal, failed/pending recovery, halted, cancellation, completion and expired/read-only scenarios. Live webhook secret rotation, reconciliation, refund/chargeback policy and finance-owner approval remain operational release requirements.
