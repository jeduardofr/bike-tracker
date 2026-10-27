import { db } from "./db.js";

// Traffic signals from OpenStreetMap (highway=traffic_signals), shown on maps as a
// reference layer. OSM often tags every approach of one junction, so nodes are
// merged into intersections before serving.

// Metro box around the commute corridors, with margin for detours
const BBOX = "20.660,-103.420,20.730,-103.280";
const OVERPASS = "https://overpass-api.de/api/interpreter";
const QUERY =
  `[out:json][timeout:60];` +
  `(node["highway"="traffic_signals"](${BBOX});node["crossing"="traffic_signals"](${BBOX}););out;`;
// signals change rarely; refresh monthly
const MAX_AGE_MS = 30 * 24 * 3600 * 1000;
const MERGE_METERS = 35;
// after a failed refresh, leave the public Overpass instance alone for a while
const RETRY_AFTER_MS = 10 * 60 * 1000;

/** 0 = junction light, 1 = mid-block pedestrian crossing light */
export type SignalKind = 0 | 1;
export type Signal = [lat: number, lng: number, kind: SignalKind];

let cache: { signals: Signal[]; fetchedAt: number } | null = null;
let refreshing: Promise<void> | null = null;
let lastFailure = 0;

async function ensureTable(): Promise<void> {
  await db().execute(
    `CREATE TABLE IF NOT EXISTS traffic_signals (
       osm_id INTEGER PRIMARY KEY,
       lat REAL NOT NULL,
       lng REAL NOT NULL,
       fetched_at INTEGER NOT NULL
     )`
  );
  // kind was added later; rows without it make the cache stale so it gets refetched
  await db()
    .execute(`ALTER TABLE traffic_signals ADD COLUMN kind INTEGER`)
    .catch(() => undefined);
}

async function loadFromDb(): Promise<void> {
  await ensureTable();
  const rs = await db().execute(`SELECT lat, lng, kind, fetched_at FROM traffic_signals`);
  const nodes = rs.rows.map(
    (r) => [Number(r.lat), Number(r.lng), Number(r.kind ?? 0) as SignalKind] as Signal
  );
  const stale = rs.rows.length === 0 || rs.rows.some((r) => r.kind === null);
  const fetchedAt = rs.rows.reduce((m, r) => Math.min(m, Number(r.fetched_at)), Infinity);
  cache = { signals: mergeIntersections(nodes), fetchedAt: stale ? 0 : fetchedAt };
}

async function fetchFromOverpass(): Promise<void> {
  const res = await fetch(OVERPASS, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", "User-Agent": "bike-tracker" },
    body: `data=${encodeURIComponent(QUERY)}`
  });
  // the public instance answers 429/504 when busy — keep the stale copy and retry next request
  if (!res.ok) throw new Error(`overpass ${res.status}`);
  const json = (await res.json()) as {
    elements: Array<{ id: number; lat: number; lon: number; tags?: Record<string, string> }>;
  };
  const now = Date.now();
  // a crossing node that isn't also a junction signal is a stand-alone pedestrian light
  const kindOf = (tags: Record<string, string> = {}): SignalKind =>
    tags.highway === "crossing" ? 1 : 0;
  await db().batch(
    [
      "DELETE FROM traffic_signals",
      ...json.elements.map((e) => ({
        sql: "INSERT OR REPLACE INTO traffic_signals (osm_id, lat, lng, kind, fetched_at) VALUES (?, ?, ?, ?, ?)",
        args: [e.id, e.lat, e.lon, kindOf(e.tags), now]
      }))
    ],
    "write"
  );
  cache = {
    signals: mergeIntersections(json.elements.map((e) => [e.lat, e.lon, kindOf(e.tags)] as Signal)),
    fetchedAt: now
  };
  console.log(`traffic signals: ${json.elements.length} nodes → ${cache.signals.length} intersections`);
}

/**
 * Greedy merge of nodes within MERGE_METERS into their running centroid. Junction lights
 * go first, so a pedestrian light right next to a junction is folded into it.
 */
function mergeIntersections(nodes: Signal[]): Signal[] {
  const mPerLat = 111_000;
  const mPerLng = 111_000 * Math.cos((20.69 * Math.PI) / 180);
  const clusters: Array<{ lat: number; lng: number; n: number; kind: SignalKind }> = [];
  const ordered = [...nodes].sort((a, b) => a[2] - b[2]);
  for (const [lat, lng, kind] of ordered) {
    const hit = clusters.find(
      (c) => Math.hypot((c.lat - lat) * mPerLat, (c.lng - lng) * mPerLng) < MERGE_METERS
    );
    if (hit && hit.kind === 0 && kind === 1) {
      continue; // the junction's own crosswalk — absorbed without moving the junction
    } else if (hit) {
      hit.lat = (hit.lat * hit.n + lat) / (hit.n + 1);
      hit.lng = (hit.lng * hit.n + lng) / (hit.n + 1);
      hit.n += 1;
    } else {
      clusters.push({ lat, lng, n: 1, kind });
    }
  }
  return clusters.map((c) => [Number(c.lat.toFixed(6)), Number(c.lng.toFixed(6)), c.kind]);
}

export async function trafficSignals(): Promise<Signal[]> {
  if (cache === null) await loadFromDb();
  if (Date.now() - cache!.fetchedAt > MAX_AGE_MS && Date.now() - lastFailure > RETRY_AFTER_MS) {
    refreshing ??= fetchFromOverpass()
      .catch((e) => {
        lastFailure = Date.now();
        console.warn("traffic signals refresh failed:", e);
      })
      .finally(() => {
        refreshing = null;
      });
    // first run has nothing to show yet — wait for it; otherwise serve the stale copy
    if (cache!.signals.length === 0) await refreshing;
  }
  return cache!.signals;
}
