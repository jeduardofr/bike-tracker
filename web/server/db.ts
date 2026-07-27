import { createClient, type Client } from "@libsql/client";

let client: Client | null = null;

export function db(): Client {
  if (!client) {
    const url = process.env.TURSO_DATABASE_URL;
    const authToken = process.env.TURSO_AUTH_TOKEN;
    if (!url || !authToken) throw new Error("TURSO_DATABASE_URL / TURSO_AUTH_TOKEN not set");
    client = createClient({ url, authToken });
  }
  return client;
}

export interface TripRow {
  uuid: string;
  startTime: number;
  endTime: number | null;
  distanceMeters: number;
  averageSpeedKmh: number;
  direction: string;
}

export interface PointRow {
  latitude: number;
  longitude: number;
  altitude: number;
  speedMps: number;
  timestamp: number;
  accuracy: number;
}

export async function tripsInRange(from: number, to: number): Promise<TripRow[]> {
  const rs = await db().execute({
    sql: `SELECT uuid, start_time, end_time, distance_meters, average_speed_kmh, direction
          FROM trips
          WHERE is_completed = 1 AND start_time >= ? AND start_time < ?
          ORDER BY start_time ASC`,
    args: [from, to]
  });
  return rs.rows.map((r) => ({
    uuid: String(r.uuid),
    startTime: Number(r.start_time),
    endTime: r.end_time == null ? null : Number(r.end_time),
    distanceMeters: Number(r.distance_meters),
    averageSpeedKmh: Number(r.average_speed_kmh),
    direction: String(r.direction)
  }));
}

export async function tripByUuid(uuid: string): Promise<TripRow | null> {
  const rs = await db().execute({
    sql: `SELECT uuid, start_time, end_time, distance_meters, average_speed_kmh, direction
          FROM trips WHERE uuid = ? LIMIT 1`,
    args: [uuid]
  });
  const r = rs.rows[0];
  if (!r) return null;
  return {
    uuid: String(r.uuid),
    startTime: Number(r.start_time),
    endTime: r.end_time == null ? null : Number(r.end_time),
    distanceMeters: Number(r.distance_meters),
    averageSpeedKmh: Number(r.average_speed_kmh),
    direction: String(r.direction)
  };
}

export async function pointsForTrip(uuid: string): Promise<PointRow[]> {
  const rs = await db().execute({
    sql: `SELECT latitude, longitude, altitude, speed_mps, timestamp, accuracy
          FROM route_points WHERE trip_uuid = ? ORDER BY timestamp ASC`,
    args: [uuid]
  });
  return rs.rows.map((r) => ({
    latitude: Number(r.latitude),
    longitude: Number(r.longitude),
    altitude: Number(r.altitude),
    speedMps: Number(r.speed_mps),
    timestamp: Number(r.timestamp),
    accuracy: Number(r.accuracy)
  }));
}

/** Downsampled per-trip traces for the week-overview map. */
export async function overviewForTrips(
  uuids: string[],
  maxPointsPerTrip = 300
): Promise<Record<string, Array<[number, number, number]>>> {
  const result: Record<string, Array<[number, number, number]>> = {};
  for (const uuid of uuids) {
    const points = await pointsForTrip(uuid);
    const step = points.length > maxPointsPerTrip ? Math.ceil(points.length / maxPointsPerTrip) : 1;
    result[uuid] = points
      .filter((_, i) => i % step === 0 || i === points.length - 1)
      .map((p) => [p.latitude, p.longitude, p.speedMps]);
  }
  return result;
}
