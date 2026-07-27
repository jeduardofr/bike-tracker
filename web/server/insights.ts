import { db, tripsInRange, type TripRow } from "./db.js";
import { elevationsFor } from "./elevation.js";

/** [lat, lng, medianKmh] */
export type SpeedBin = [number, number, number];

export interface GradeClass {
  medianKmh: number;
  minutes: number;
}

export interface GradeSummary {
  uphill: GradeClass;
  flat: GradeClass;
  downhill: GradeClass;
}

export interface Insights {
  trips: TripRow[];
  bins: SpeedBin[];
  medianKmh: number;
  gradeSummary: GradeSummary | null;
}

const BIN_DEG = 0.00025; // ~27 m
const MIN_SAMPLES = 5;
const MAX_ACCURACY_M = 50;
const RIDING_MPS = 1.94;
const GRADE_WINDOW_M = 80; // grade measured over a rolling ≥80 m stretch
const GRADE_CUTOFF = 0.015; // ±1.5% separates uphill/flat/downhill
const CACHE_TTL_MS = 10 * 60 * 1000;

let cache: { at: number; data: Insights } | null = null;

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

function haversine(la1: number, lo1: number, la2: number, lo2: number): number {
  const r = Math.PI / 180;
  const h =
    Math.sin(((la2 - la1) * r) / 2) ** 2 +
    Math.cos(la1 * r) * Math.cos(la2 * r) * Math.sin(((lo2 - lo1) * r) / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
}

interface Pt {
  trip: string;
  lat: number;
  lng: number;
  speed: number;
  ts: number;
}

async function buildTerrain(): Promise<{
  bins: SpeedBin[];
  medianKmh: number;
  gradeSummary: GradeSummary | null;
}> {
  const rs = await db().execute(
    `SELECT trip_uuid, latitude, longitude, speed_mps, timestamp, accuracy
     FROM route_points ORDER BY trip_uuid, timestamp`
  );

  const points: Pt[] = [];
  const byBin = new Map<string, number[]>();
  for (const row of rs.rows) {
    const accuracy = Number(row.accuracy);
    if (accuracy > MAX_ACCURACY_M && accuracy !== 0) continue;
    const p: Pt = {
      trip: String(row.trip_uuid),
      lat: Number(row.latitude),
      lng: Number(row.longitude),
      speed: Number(row.speed_mps),
      ts: Number(row.timestamp)
    };
    points.push(p);
    const key = `${Math.round(p.lat / BIN_DEG)},${Math.round(p.lng / BIN_DEG)}`;
    const list = byBin.get(key) ?? [];
    list.push(p.speed * 3.6);
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
  const medianKmh = bins.length > 0 ? Number(median(bins.map((b) => b[2])).toFixed(1)) : 0;

  // --- terrain: classify riding time by grade over a rolling window ---
  let gradeSummary: GradeSummary | null = null;
  const elevations = await elevationsFor(
    points.map((p) => ({ latitude: p.lat, longitude: p.lng }))
  );
  if (elevations.some((e) => e !== null)) {
    const speedsBy: Record<string, number[]> = { uphill: [], flat: [], downhill: [] };
    const secondsBy: Record<string, number> = { uphill: 0, flat: 0, downhill: 0 };

    let anchor = 0;
    let anchorDist = 0; // cumulative meters at anchor
    let cum = 0;
    for (let i = 1; i < points.length; i++) {
      const p = points[i - 1];
      const c = points[i];
      if (c.trip !== p.trip) {
        anchor = i;
        anchorDist = 0;
        cum = 0;
        continue;
      }
      const dt = (c.ts - p.ts) / 1000;
      if (!(dt > 0 && dt < 120)) continue;
      cum += haversine(p.lat, p.lng, c.lat, c.lng);

      // advance anchor to keep the window near GRADE_WINDOW_M
      while (anchor < i - 1 && cum - anchorDist > GRADE_WINDOW_M * 1.5) {
        anchor++;
        anchorDist += haversine(
          points[anchor - 1].lat, points[anchor - 1].lng,
          points[anchor].lat, points[anchor].lng
        );
      }
      const windowDist = cum - anchorDist;
      const eNow = elevations[i];
      const eBack = elevations[anchor];
      if (windowDist < GRADE_WINDOW_M * 0.5 || eNow === null || eBack === null) continue;
      if (c.speed <= RIDING_MPS) continue; // riding segments only

      const grade = (eNow - eBack) / windowDist;
      const cls = grade > GRADE_CUTOFF ? "uphill" : grade < -GRADE_CUTOFF ? "downhill" : "flat";
      speedsBy[cls].push(c.speed * 3.6);
      secondsBy[cls] += dt;
    }

    if (speedsBy.uphill.length + speedsBy.flat.length + speedsBy.downhill.length > 100) {
      const cls = (name: string): GradeClass => ({
        medianKmh: speedsBy[name].length > 0 ? Number(median(speedsBy[name]).toFixed(1)) : 0,
        minutes: Number((secondsBy[name] / 60).toFixed(0))
      });
      gradeSummary = { uphill: cls("uphill"), flat: cls("flat"), downhill: cls("downhill") };
    }
  }

  return { bins, medianKmh, gradeSummary };
}

export async function getInsights(): Promise<Insights> {
  if (cache !== null && Date.now() - cache.at < CACHE_TTL_MS) return cache.data;
  const [trips, terrain] = await Promise.all([
    tripsInRange(0, Date.now() + 24 * 3600 * 1000),
    buildTerrain()
  ]);
  const data: Insights = { trips, ...terrain };
  // don't pin a failed elevation lookup for the full TTL
  if (terrain.gradeSummary !== null) cache = { at: Date.now(), data };
  return data;
}
