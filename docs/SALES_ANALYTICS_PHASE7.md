# Phase 7 — Sales analytics

## Metric contract

- **Leads created:** leads whose creation time falls inside the selected India business-date range.
- **Contact rate:** created leads with a recorded first contact divided by leads created.
- **Win rate:** created leads currently in the `won` stage divided by leads created.
- **Connection rate:** connected call events divided by all call events started in the period.
- **Fit:** the score stored by the CRM/dashboard (`High` 75–100, `Medium` 40–74, `Low` 0–39, or unscored). Calls never recalculate this score.
- **Interest / quality:** the lead quality value stored by the CRM.
- **Manager exceptions:** current first-response, missing/overdue next action and stalled-lead conditions.

All cards, trends and breakdowns use the same tenant, India date, salesperson and source filters. Lead and call aggregates are calculated independently before joining, preventing one-to-many call joins from inflating lead counts. Each response includes a lead-count reconciliation status and source freshness.

## Performance model

`007_sales_analytics.sql` adds a tenant-isolated daily summary table and covering indexes. Report refreshes are bounded to 367 days and serialized per tenant. Current-day analytics always read and rebuild from PostgreSQL. Completed historical responses may use the optional REST Redis cache for five minutes when `CALLFLOW_REDIS_REST_URL` and `CALLFLOW_REDIS_REST_TOKEN` are configured. Cache reads/writes have a 1.2-second timeout and fail open; app sync, calling, assignment and CRM mutations never depend on Redis.

## Deployment gate

Apply migrations through `007_sales_analytics.sql`, then validate reconciliation and query plans against migrated PostgreSQL data. Load testing at the agreed large-company distribution is still required before a production capacity claim.
