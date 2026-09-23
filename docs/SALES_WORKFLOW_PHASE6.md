# Phase 6 — Sales workflow automation

Phase 6 turns the Company CRM lead pool into an availability-aware working queue.

## Delivered

- New and imported leads are auto-assigned only to active salespeople with an assigned subscription seat.
- A salesperson who closes their shift is excluded immediately. Leave windows, daily assignment capacity and maximum active workload are also enforced.
- Ordered assignment rules can route by source, city, quality and minimum lead score. Remaining leads use least-workload distribution.
- Manual owner changes preserve assignment history and require a reassignment reason.
- Calls update first-contact and last-contact timestamps. Wrong-number disposition opts the lead out and removes it from active work.
- Managers receive first-response SLA, missing/overdue next-action, stalled-lead and ageing-lead alerts.
- Lead workflow supports scheduled next actions, won, lost and opt-out outcomes with auditable activity records.
- Company CRM includes compact workflow counters and manager actions in the lead detail panel.

## Operations

Apply migrations through `006_sales_workflow.sql` before enabling Phase 6. Workflow settings and assignment rules are managed through the authenticated Company CRM APIs. Existing CFL and central Android sync paths remain supported.

The live completion gate requires a configured PostgreSQL environment, migrated tenant data, an active licensed salesperson seat and a real Android device/emulator session.
