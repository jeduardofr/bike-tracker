import { createClient } from "@libsql/client";
import { tripsInRange, type TripRow } from "./db.js";

/** [lat, lng, medianKmh] */
export type SpeedBin = [number, number, number];

export interface Insights {
  trips: TripRow[];
  bins: SpeedBin[];
  medianKmh: number;
}

const BIN_DEG = 0.00025; // ~27 m
const MIN_SAMPLES = 5;
const MAX_ACCURACY_M = 50;
const CACHE_TTL_MS = 10 * 60 * 1000;

let cache: { at: number; data: Insights } | null = null;

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

async function buildSpeedBins(): Promise<{ bins: SpeedBin[]; medianKmh: number }> {
  const db = createClient({
    url: process.env.TURSO_DATABASE_URL!,
    authToken: process.env.TURSO_AUTH_TOKEN!
  });
  const rs = await db.execute(
    `SELECT latitude, longitude, speed_mps, accuracy FROM route_points`
  );

  const byBin = new Map<string, number[]>();
  for (const row of rs.rows) {
    const accuracy = Number(row.accuracy);
    if (accuracy > MAX_ACCURACY_M && accuracy !== 0) continue;
    const lat = Number(row.latitude);
    const lng = Number(row.longitude);
    const key = `${Math.round(lat / BIN_DEG)},${Math.round(lng / BIN_DEG)}`;
    const list = byBin.get(key) ?? [];
    list.push(Number(row.speed_mps) * 3.6);
    byBin.set(key, list);
  }

  const bins: SpeedBin[] = [];
  byBin.forEach((speeds, key) => {
    if (speeds.length < MIN_SAMPLES) return;
    const [latIdx, lngIdx] = key.split(",").map(Number);
    bins.push([
      Number((latIdx * BIN_DEG).toFixed(6)),
      Number((lngIdx * BIN_DEG).toFixed(6)),
      Number(median(speeds).toFixed(1))
    ]);
  });

  const medianKmh = bins.length > 0 ? median(bins.map((b) => b[2])) : 0;
  return { bins, medianKmh: Number(medianKmh.toFixed(1)) };
}

export async function getInsights(): Promise<Insights> {
  if (cache !== null && Date.now() - cache.at < CACHE_TTL_MS) return cache.data;
  const [trips, speed] = await Promise.all([
    tripsInRange(0, Date.now() + 24 * 3600 * 1000),
    buildSpeedBins()
  ]);
  const data: Insights = { trips, bins: speed.bins, medianKmh: speed.medianKmh };
  cache = { at: Date.now(), data };
  return data;
}
