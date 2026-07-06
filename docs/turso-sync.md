# Turso sync

The phone stays offline-first: Room is the source of truth, and completed trips are
pushed to Turso in the background so other clients (e.g. a web dashboard) can read them.

## How it works

- `TripEntity` carries a `uuid` (stable cross-device id) and `syncedAt` (null = not yet pushed).
  Room schema is at version 2; `MIGRATION_1_2` backfills uuids for existing trips.
- When a trip is stopped, `TripRepositoryImpl` asks `TursoSyncScheduler` to enqueue a
  one-time `TursoSyncWorker` (network-constrained, exponential backoff). A periodic
  6-hour sync scheduled from `BikeTrackerApp` is the safety net for missed pushes.
- `TursoSyncWorker` pushes each unsynced completed trip and its route points in a single
  HTTP request via `TursoClient` (Hrana pipeline API, `POST /v2/pipeline`), then stamps
  `syncedAt`. Re-syncing is idempotent: the trip row is upserted by uuid and route points
  are replaced.
- Deleting a trip in History enqueues `TursoDeleteTripWorker` with the trip's uuid before
  the local row is removed, so the remote copy is cleaned up too (retried if offline).

## Configuration

Credentials live in `local.properties` (gitignored) and reach the app via `BuildConfig`:

```
TURSO_DATABASE_URL=libsql://<db-name>-<org>.turso.io
TURSO_AUTH_TOKEN=<token>
```

If either is missing, all sync code no-ops — the app works fully offline.

To rotate the token: `turso db tokens create bike-tracker`, update `local.properties`,
rebuild in Android Studio.

## Remote schema

See `docs/turso_schema.sql` (already applied to the database on 2026-07-05).
Tables: `trips` (uuid PK) and `route_points` (FK by `trip_uuid`), snake_case columns.

## Web dashboard (next step)

Read the same database from your domain with `@libsql/client` (or plain fetch to the
pipeline endpoint) using a **read-only** token: `turso db tokens create bike-tracker --read-only`.
Weekly summaries are a single query over `trips`; only load `route_points` when
rendering a specific trip's map.
