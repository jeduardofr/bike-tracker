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

export interface ClimbMonth {
  month: string; // "2026-04"
  walkMin: number;
  rideMin: number;
}

export interface ClimbSpot {
  lat: number;
  lng: number;
  gradePct: number;
  walkMin: number; // total walking minutes here (both directions)
  /** share of morning moving time here spent walking (sweat strategy, not fitness) */
  amWalkPct: number | null;
  /** evening-only trend — the actual energy signal */
  months: ClimbMonth[];
}

export interface PacingTarget {
  gradePct: number;
  dryKmh: number; // 90% of comfort effort — flat-cruise sweat level
  pushKmh: number; // 120% of comfort effort — "slightly warm"
  currentKmh: number | null; // what you actually ride at this grade in the morning
}

export interface Pacing {
  massKg: number;
  comfortWatts: number;
  flatKmh: number;
  targets: PacingTarget[];
  /** morning walked-climb strategies, median minutes per trip */
  morning: { walkMinNow: number; dryMin: number; pushMin: number } | null;
}

export interface Insights {
  trips: TripRow[];
  bins: SpeedBin[];
  medianKmh: number;
  gradeSummary: GradeSummary | null;
  climbs: ClimbSpot[];
  pacing: Pacing | null;
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

async function tripDirections(): Promise<Map<string, string>> {
  const rs = await db().execute(
    `SELECT uuid, direction, distance_meters FROM trips WHERE is_completed = 1`
  );
  const map = new Map<string, string>();
  rs.rows.forEach((r) => {
    const direction = String(r.direction);
    // climbs are tracked on office commutes only (university/free rides excluded)
    if (direction !== "FREE" && Number(r.distance_meters) >= 7800) {
      map.set(String(r.uuid), direction);
    }
  });
  return map;
}

const CLIMB_BIN_DEG = 0.0004; // ~44 m cells for locating walked climbs
const CLIMB_MERGE_M = 120; // cells this close belong to the same climb
const MIN_WALK_SEC = 120; // ignore spots with under 2 min of walking total

// Guadalajara is UTC-6 year-round
const monthOf = (ts: number) => new Date(ts - 6 * 3600 * 1000).toISOString().slice(0, 7);

// --- pacing physics (commuter model at ~1550 m altitude) ---
const MASS_KG = Number(process.env.RIDER_MASS_KG ?? 85); // rider + bike + backpack
const GRAV = 9.81;
const CRR = 0.008; // city tires on asphalt
const CDA = 0.55; // upright commuter
const RHO = 0.98; // air density at Guadalajara's altitude
const CLIMB_COOLING_MARGIN = 0.9; // ride climbs at 90% of flat power: less airflow, same sweat

const powerAt = (v: number, grade: number) =>
  MASS_KG * GRAV * v * (grade + CRR) + 0.5 * RHO * CDA * v ** 3;

function speedAtPower(watts: number, grade: number): number {
  let lo = 0.3;
  let hi = 15;
  for (let k = 0; k < 60; k++) {
    const mid = (lo + hi) / 2;
    if (powerAt(mid, grade) > watts) hi = mid;
    else lo = mid;
  }
  return (lo + hi) / 2;
}

async function buildTerrain(): Promise<{
  bins: SpeedBin[];
  medianKmh: number;
  gradeSummary: GradeSummary | null;
  climbs: ClimbSpot[];
  pacing: Pacing | null;
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
  let climbs: ClimbSpot[] = [];
  let pacing: Pacing | null = null;
  const elevations = await elevationsFor(
    points.map((p) => ({ latitude: p.lat, longitude: p.lng }))
  );
  if (elevations.some((e) => e !== null)) {
    const speedsBy: Record<string, number[]> = { uphill: [], flat: [], downhill: [] };
    const secondsBy: Record<string, number> = { uphill: 0, flat: 0, downhill: 0 };
    const commuteDir = await tripDirections();
    const morningFlat: number[] = []; // riding speeds (m/s) on flat, mornings
    const morningByBucket = new Map<number, number[]>(); // grade % -> riding speeds
    const walkByTrip = new Map<string, { sec: number; segs: Array<{ dist: number; grade: number }> }>();
    const climbCells = new Map<
      string,
      {
        lat: number;
        lng: number;
        n: number;
        gradeSum: number;
        am: { walk: number; ride: number };
        months: Map<string, { walk: number; ride: number }>; // evening only
      }
    >();

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
      const stepDist = haversine(p.lat, p.lng, c.lat, c.lng);
      cum += stepDist;

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

      const grade = (eNow - eBack) / windowDist;
      const riding = c.speed > RIDING_MPS;
      if (riding) {
        const cls = grade > GRADE_CUTOFF ? "uphill" : grade < -GRADE_CUTOFF ? "downhill" : "flat";
        speedsBy[cls].push(c.speed * 3.6);
        secondsBy[cls] += dt;
      }

      // morning pacing inputs (office commutes toward the office)
      const direction = commuteDir.get(c.trip);
      if (direction === "HOME_TO_OFFICE") {
        if (riding) {
          if (Math.abs(grade) <= 0.015) morningFlat.push(c.speed);
          const bucket = Math.round(grade * 100);
          if (bucket >= 2 && bucket <= 4) {
            const list = morningByBucket.get(bucket) ?? [];
            list.push(c.speed);
            morningByBucket.set(bucket, list);
          }
        } else if (grade > GRADE_CUTOFF && c.speed > 0.3) {
          const w = walkByTrip.get(c.trip) ?? { sec: 0, segs: [] };
          w.sec += dt;
          w.segs.push({ dist: stepDist, grade });
          walkByTrip.set(c.trip, w);
        }
      }

      // walked-climb detection: any moving pair on a >1.5% uphill of an office commute
      if (direction !== undefined && grade > GRADE_CUTOFF && c.speed > 0.3) {
        const key = `${Math.round(c.lat / CLIMB_BIN_DEG)},${Math.round(c.lng / CLIMB_BIN_DEG)}`;
        let cell = climbCells.get(key);
        if (!cell) {
          cell = { lat: 0, lng: 0, n: 0, gradeSum: 0, am: { walk: 0, ride: 0 }, months: new Map() };
          climbCells.set(key, cell);
        }
        cell.lat += c.lat;
        cell.lng += c.lng;
        cell.n++;
        cell.gradeSum += grade;
        if (direction === "HOME_TO_OFFICE") {
          // mornings: walking here is sweat strategy — tracked separately, no trend
          if (riding) cell.am.ride += dt;
          else cell.am.walk += dt;
        } else {
          const mo = monthOf(c.ts);
          const m = cell.months.get(mo) ?? { walk: 0, ride: 0 };
          if (riding) m.ride += dt;
          else m.walk += dt;
          cell.months.set(mo, m);
        }
      }
    }

    if (speedsBy.uphill.length + speedsBy.flat.length + speedsBy.downhill.length > 100) {
      const cls = (name: string): GradeClass => ({
        medianKmh: speedsBy[name].length > 0 ? Number(median(speedsBy[name]).toFixed(1)) : 0,
        minutes: Number((secondsBy[name] / 60).toFixed(0))
      });
      gradeSummary = { uphill: cls("uphill"), flat: cls("flat"), downhill: cls("downhill") };
    }

    // cluster walked-climb cells into spots
    const cells = [...climbCells.values()]
      .map((c) => {
        let walk = c.am.walk;
        c.months.forEach((m) => {
          walk += m.walk;
        });
        return {
          lat: c.lat / c.n,
          lng: c.lng / c.n,
          grade: c.gradeSum / c.n,
          walk,
          am: c.am,
          months: c.months
        };
      })
      .filter((c) => c.walk >= MIN_WALK_SEC)
      .sort((a, b) => b.walk - a.walk);

    const groups: Array<typeof cells> = [];
    for (const cell of cells) {
      const host = groups.find(
        (g) => haversine(g[0].lat, g[0].lng, cell.lat, cell.lng) < CLIMB_MERGE_M
      );
      if (host) host.push(cell);
      else groups.push([cell]);
    }

    climbs = groups
      .slice(0, 8)
      .map((group) => {
        const monthAgg = new Map<string, { walk: number; ride: number }>();
        let amWalk = 0;
        let amRide = 0;
        group.forEach((c) => {
          amWalk += c.am.walk;
          amRide += c.am.ride;
          c.months.forEach((m, mo) => {
            const t = monthAgg.get(mo) ?? { walk: 0, ride: 0 };
            t.walk += m.walk;
            t.ride += m.ride;
            monthAgg.set(mo, t);
          });
        });
        return {
          lat: Number((group.reduce((s, c) => s + c.lat, 0) / group.length).toFixed(6)),
          lng: Number((group.reduce((s, c) => s + c.lng, 0) / group.length).toFixed(6)),
          gradePct: Number(((group.reduce((s, c) => s + c.grade, 0) / group.length) * 100).toFixed(1)),
          walkMin: Number((group.reduce((s, c) => s + c.walk, 0) / 60).toFixed(1)),
          amWalkPct:
            amWalk + amRide >= 60 ? Number(((100 * amWalk) / (amWalk + amRide)).toFixed(0)) : null,
          months: [...monthAgg.entries()]
            .sort((a, b) => (a[0] < b[0] ? -1 : 1))
            .map(([month, v]) => ({
              month,
              walkMin: Number((v.walk / 60).toFixed(1)),
              rideMin: Number((v.ride / 60).toFixed(1))
            }))
        };
      })
      .sort((a, b) => b.walkMin - a.walkMin);

    // --- morning pacing plan ---
    if (morningFlat.length > 50) {
      const vFlat = median(morningFlat);
      const comfortWatts = powerAt(vFlat, 0);
      const dryWatts = comfortWatts * CLIMB_COOLING_MARGIN;
      const pushWatts = comfortWatts * 1.2;
      const targets = [2, 3, 4].map((gradePct) => {
        const current = morningByBucket.get(gradePct) ?? [];
        return {
          gradePct,
          dryKmh: Number((speedAtPower(dryWatts, gradePct / 100) * 3.6).toFixed(1)),
          pushKmh: Number((speedAtPower(pushWatts, gradePct / 100) * 3.6).toFixed(1)),
          currentKmh: current.length >= 20 ? Number((median(current) * 3.6).toFixed(1)) : null
        };
      });

      const rideMinutes = (w: { segs: Array<{ dist: number; grade: number }> }, watts: number) =>
        w.segs.reduce((s, seg) => s + seg.dist / speedAtPower(watts, Math.max(0.015, seg.grade)), 0) /
        60;
      const walkNow: number[] = [];
      const dryRide: number[] = [];
      const pushRide: number[] = [];
      walkByTrip.forEach((w) => {
        if (w.sec < 60) return;
        walkNow.push(w.sec / 60);
        dryRide.push(rideMinutes(w, dryWatts));
        pushRide.push(rideMinutes(w, pushWatts));
      });

      pacing = {
        massKg: MASS_KG,
        comfortWatts: Number(comfortWatts.toFixed(0)),
        flatKmh: Number((vFlat * 3.6).toFixed(1)),
        targets,
        morning:
          walkNow.length >= 5
            ? {
                walkMinNow: Number(median(walkNow).toFixed(1)),
                dryMin: Number(median(dryRide).toFixed(1)),
                pushMin: Number(median(pushRide).toFixed(1))
              }
            : null
      };
    }
  }

  return { bins, medianKmh, gradeSummary, climbs, pacing };
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
