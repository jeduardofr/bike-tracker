import { db } from "./db.js";

// Terrain never changes: elevations are fetched from Open-Meteo (~90 m DEM)
// once per ~55 m map bin, persisted in Turso, and mirrored in memory.
export const ELEV_BIN_DEG = 0.0005;

const memory = new Map<string, number>();
let loaded = false;

const key = (latIdx: number, lngIdx: number) => `${latIdx},${lngIdx}`;

export function binOf(lat: number, lng: number): [number, number] {
  return [Math.round(lat / ELEV_BIN_DEG), Math.round(lng / ELEV_BIN_DEG)];
}

async function ensureLoaded(): Promise<void> {
  if (loaded) return;
  await db().execute(
    `CREATE TABLE IF NOT EXISTS elevation (
       bin_lat INTEGER NOT NULL,
       bin_lng INTEGER NOT NULL,
       meters REAL NOT NULL,
       PRIMARY KEY (bin_lat, bin_lng)
     )`
  );
  const rs = await db().execute(`SELECT bin_lat, bin_lng, meters FROM elevation`);
  rs.rows.forEach((r) => memory.set(key(Number(r.bin_lat), Number(r.bin_lng)), Number(r.meters)));
  loaded = true;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function fetchChunk(lats: string, lngs: string): Promise<{ elevation: number[] }> {
  for (let attempt = 0; ; attempt++) {
    const res = await fetch(
      `https://api.open-meteo.com/v1/elevation?latitude=${lats}&longitude=${lngs}`
    );
    if (res.ok) return (await res.json()) as { elevation: number[] };
    if (res.status === 429 && attempt < 2) {
      await sleep(30_000); // rate limited — back off and retry
      continue;
    }
    throw new Error(`open-meteo ${res.status}`);
  }
}

async function fetchMissing(bins: Array<[number, number]>): Promise<void> {
  for (let i = 0; i < bins.length; i += 100) {
    if (i > 0) await sleep(2000); // stay well under the free-tier rate limit
    const chunk = bins.slice(i, i + 100);
    const lats = chunk.map(([la]) => (la * ELEV_BIN_DEG).toFixed(5)).join(",");
    const lngs = chunk.map(([, lo]) => (lo * ELEV_BIN_DEG).toFixed(5)).join(",");
    const body = await fetchChunk(lats, lngs);
    const stmts = chunk.map(([la, lo], j) => {
      memory.set(key(la, lo), body.elevation[j]);
      return {
        sql: `INSERT OR REPLACE INTO elevation (bin_lat, bin_lng, meters) VALUES (?, ?, ?)`,
        args: [la, lo, body.elevation[j]] as (number | string)[]
      };
    });
    await db().batch(stmts, "write");
  }
}

/** Elevation in meters for each point; null on lookup failure. */
export async function elevationsFor(
  points: Array<{ latitude: number; longitude: number }>
): Promise<Array<number | null>> {
  try {
    await ensureLoaded();
    const missing = new Map<string, [number, number]>();
    for (const p of points) {
      const [la, lo] = binOf(p.latitude, p.longitude);
      const k = key(la, lo);
      if (!memory.has(k)) missing.set(k, [la, lo]);
    }
    if (missing.size > 0) await fetchMissing([...missing.values()]);
    return points.map((p) => {
      const [la, lo] = binOf(p.latitude, p.longitude);
      return memory.get(key(la, lo)) ?? null;
    });
  } catch (e) {
    console.warn("elevation lookup failed:", e);
    return points.map(() => null);
  }
}
