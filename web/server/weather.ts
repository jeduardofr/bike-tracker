import { db } from "./db.js";

// One representative point on the commute corridor — Open-Meteo model data is
// ~10-25 km resolution, so a single point covers the whole route.
const LAT = 20.695;
const LNG = -103.34;
const TZ = "America%2FMexico_City";
const HOURLY = "precipitation,temperature_2m,wind_speed_10m,wind_direction_10m";

export interface HourWeather {
  precipMm: number;
  tempC: number;
  windKmh: number;
  /** meteorological: degrees the wind blows FROM */
  windDirDeg: number;
}

const memory = new Map<string, HourWeather>();
let loaded = false;

// Guadalajara is UTC-6 year-round
const hourKey = (ts: number) => new Date(ts - 6 * 3600 * 1000).toISOString().slice(0, 13);

async function ensureLoaded(): Promise<void> {
  if (loaded) return;
  await db().execute(
    `CREATE TABLE IF NOT EXISTS weather (
       hour TEXT PRIMARY KEY,
       precip_mm REAL NOT NULL,
       temp_c REAL NOT NULL,
       wind_kmh REAL NOT NULL
     )`
  );
  // wind direction was added later; rows without it are treated as missing and refetched
  await db()
    .execute(`ALTER TABLE weather ADD COLUMN wind_dir REAL`)
    .catch(() => undefined);
  const rs = await db().execute(
    `SELECT hour, precip_mm, temp_c, wind_kmh, wind_dir FROM weather WHERE wind_dir IS NOT NULL`
  );
  rs.rows.forEach((r) =>
    memory.set(String(r.hour), {
      precipMm: Number(r.precip_mm),
      tempC: Number(r.temp_c),
      windKmh: Number(r.wind_kmh),
      windDirDeg: Number(r.wind_dir)
    })
  );
  loaded = true;
}

interface HourlyPayload {
  time: string[];
  precipitation: Array<number | null>;
  temperature_2m: Array<number | null>;
  wind_speed_10m: Array<number | null>;
  wind_direction_10m: Array<number | null>;
}

function collect(payload: HourlyPayload, into: Map<string, HourWeather>): void {
  payload.time.forEach((t, i) => {
    const p = payload.precipitation[i];
    const tmp = payload.temperature_2m[i];
    const w = payload.wind_speed_10m[i];
    const d = payload.wind_direction_10m[i];
    if (p === null || tmp === null || w === null || d === null) return;
    into.set(t.slice(0, 13), { precipMm: p, tempC: tmp, windKmh: w, windDirDeg: d });
  });
}

async function fetchMissing(keys: string[]): Promise<void> {
  const fetched = new Map<string, HourWeather>();
  const dates = keys.map((k) => k.slice(0, 10)).sort();
  const oldest = dates[0];
  const cutoff = new Date(Date.now() - 8 * 24 * 3600 * 1000).toISOString().slice(0, 10);

  if (oldest < cutoff) {
    const res = await fetch(
      `https://archive-api.open-meteo.com/v1/archive?latitude=${LAT}&longitude=${LNG}` +
        `&start_date=${oldest}&end_date=${cutoff}&hourly=${HOURLY}&timezone=${TZ}`
    );
    if (res.ok) collect(((await res.json()) as { hourly: HourlyPayload }).hourly, fetched);
  }
  if (dates[dates.length - 1] >= cutoff) {
    const res = await fetch(
      `https://api.open-meteo.com/v1/forecast?latitude=${LAT}&longitude=${LNG}` +
        `&past_days=92&forecast_days=1&hourly=${HOURLY}&timezone=${TZ}`
    );
    if (res.ok) collect(((await res.json()) as { hourly: HourlyPayload }).hourly, fetched);
  }

  const rows: Array<{ sql: string; args: (string | number)[] }> = [];
  for (const k of keys) {
    const w = fetched.get(k);
    if (w === undefined || memory.has(k)) continue;
    memory.set(k, w);
    rows.push({
      sql: `INSERT OR REPLACE INTO weather (hour, precip_mm, temp_c, wind_kmh, wind_dir) VALUES (?, ?, ?, ?, ?)`,
      args: [k, w.precipMm, w.tempC, w.windKmh, w.windDirDeg]
    });
  }
  for (let i = 0; i < rows.length; i += 200) {
    await db().batch(rows.slice(i, i + 200), "write");
  }
}

/** Weather at each timestamp's hour; null where unavailable. */
export async function weatherAt(timestamps: number[]): Promise<Array<HourWeather | null>> {
  try {
    await ensureLoaded();
    const missing = [...new Set(timestamps.map(hourKey).filter((k) => !memory.has(k)))];
    if (missing.length > 0) await fetchMissing(missing);
    return timestamps.map((ts) => memory.get(hourKey(ts)) ?? null);
  } catch (e) {
    console.warn("weather lookup failed:", e);
    return timestamps.map(() => null);
  }
}
