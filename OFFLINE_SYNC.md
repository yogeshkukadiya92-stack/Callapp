# Offline synchronization

Local writes commit their entity and a UUID-keyed `sync_events` record atomically. A network-constrained WorkManager worker will claim bounded batches, send them to `/sync/batch`, and use exponential backoff. The server must treat `eventUuid` as an idempotency key.

The worker is installed with `HiltWorkerFactory`, runs uniquely at the platform minimum periodic interval, and never polls continuously. Development fake mode acknowledges through the same outbox repository.

Production sync uploads pending UUID-keyed events with the last stored cursor, then requests `/sync/changes?cursor=...`. Lead deltas are applied in one Room transaction. Newer server versions replace local cached values only when no pending local lead mutation exists. Otherwise CallFlow writes an `OPEN` `sync_conflicts` record containing diagnostic version/stage metadata and preserves the employee’s local change. Server deletions follow the same rule.

The same delta transaction now covers calls, call events, notes, follow-ups, pipeline stages, dispositions, and application configuration. Calls/events/notes are append-only and use insert-ignore semantics during replay. Follow-ups use optimistic versions and conflict capture. Stages, dispositions, and configuration are server-authoritative cached records. Locally created or completed follow-ups have their own UUID outbox events.

The next cursor is persisted only after the Room transaction commits. A process death between commit and cursor persistence may replay the delta, but version checks make that replay idempotent. Network failure never advances the cursor.

Successful events become `SYNCED`; retryable errors return to `PENDING`; diagnosable terminal responses become `FAILED` without deleting payloads. A cursor in DataStore drives `/sync/changes` delta downloads. App/process restarts do not lose the outbox.

Append-only activity avoids merge conflicts. Versioned editable entities use optimistic concurrency and expose critical conflicts to the user/support tooling.
