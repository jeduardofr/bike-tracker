-- Remote schema for the bike-tracker Turso database.
-- Applied via: turso db shell bike-tracker < docs/turso_schema.sql
-- (or through the Hrana HTTP API)

CREATE TABLE IF NOT EXISTS trips (
    uuid TEXT PRIMARY KEY,
    start_time INTEGER NOT NULL,
    end_time INTEGER,
    distance_meters REAL NOT NULL DEFAULT 0,
    average_speed_kmh REAL NOT NULL DEFAULT 0,
    direction TEXT NOT NULL DEFAULT 'FREE',
    is_completed INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS route_points (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_uuid TEXT NOT NULL REFERENCES trips(uuid) ON DELETE CASCADE,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    altitude REAL NOT NULL DEFAULT 0,
    speed_mps REAL NOT NULL DEFAULT 0,
    timestamp INTEGER NOT NULL,
    accuracy REAL NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_route_points_trip ON route_points(trip_uuid);
CREATE INDEX IF NOT EXISTS idx_trips_start_time ON trips(start_time);
