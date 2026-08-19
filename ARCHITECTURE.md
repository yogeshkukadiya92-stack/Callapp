# Architecture

The current single Gradle module is intentionally divided into extractable packages: `core`, `data/local`, `data/remote`, `data/repository`, `domain/repository`, `telecom`, `di`, and `ui`. A multi-module conversion should happen when parallel ownership or build performance justifies its cost.

Data flows one way: Compose → ViewModel → repository/use case → Room/remote data source. Room emits observable state back upward. Critical local mutations and their idempotent outbox event share one Room transaction.

Append-only calls, call events, notes, dispositions, stage history, and audit events are never conflict-merged. Editable records carry `updatedAt`, actor, device, and `version`; server version conflicts must surface for resolution rather than silently overwriting.

The schema starts at version 1 with exported JSON schemas. Future versions must include explicit tested `Migration` objects. `fallbackToDestructiveMigration` is prohibited.
