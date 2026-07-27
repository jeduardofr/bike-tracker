import { serve } from "@hono/node-server";
import { serveStatic } from "@hono/node-server/serve-static";
import { Hono } from "hono";
import { isAuthenticated, login, requireAuth } from "./auth.js";
import { overviewForTrips, pointsForTrip, tripByUuid, tripsInRange } from "./db.js";
import { ensureDem } from "./dem.js";
import { elevationsFor } from "./elevation.js";
import { getInsights } from "./insights.js";
import { weatherAt } from "./weather.js";
import type { TripRow } from "./db.js";

async function weatherMap(trips: TripRow[]): Promise<Record<string, unknown>> {
  const wx = await weatherAt(trips.map((t) => t.startTime));
  const map: Record<string, unknown> = {};
  trips.forEach((t, i) => {
    if (wx[i] !== null) map[t.uuid] = wx[i];
  });
  return map;
}

const app = new Hono();

app.post("/api/login", async (c) => {
  const body = await c.req.json<{ password?: string }>().catch(() => ({}) as { password?: string });
  if (!body.password || !login(c, body.password)) {
    return c.json({ error: "wrong password" }, 401);
  }
  return c.json({ ok: true });
});

app.get("/api/me", (c) => c.json({ authenticated: isAuthenticated(c) }));

app.use("/api/*", requireAuth);

function parseRange(c: { req: { query: (k: string) => string | undefined } }) {
  const from = Number(c.req.query("from"));
  const to = Number(c.req.query("to"));
  if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) return null;
  // sanity cap: at most ~3 months per request
  if (to - from > 1000 * 60 * 60 * 24 * 95) return null;
  return { from, to };
}

app.get("/api/trips", async (c) => {
  const range = parseRange(c);
  if (!range) return c.json({ error: "invalid from/to" }, 400);
  return c.json({ trips: await tripsInRange(range.from, range.to) });
});

app.get("/api/overview", async (c) => {
  const range = parseRange(c);
  if (!range) return c.json({ error: "invalid from/to" }, 400);
  const trips = await tripsInRange(range.from, range.to);
  const traces = await overviewForTrips(trips.map((t) => t.uuid));
  return c.json({ trips, traces, weather: await weatherMap(trips) });
});

app.get("/api/insights", async (c) => {
  const insights = await getInsights();
  return c.json({ ...insights, tripWeather: await weatherMap(insights.trips) });
});

app.get("/api/trip/:uuid", async (c) => {
  const uuid = c.req.param("uuid");
  const trip = await tripByUuid(uuid);
  if (!trip) return c.json({ error: "not found" }, 404);
  const points = await pointsForTrip(uuid);
  const elevations = await elevationsFor(points);
  const [weather] = await weatherAt([trip.startTime]);
  return c.json({ trip, points, elevations, weather });
});

// Static SPA (production build)
app.use("/*", serveStatic({ root: "./dist" }));
app.use("/*", serveStatic({ root: "./dist", path: "index.html" }));

const port = Number(process.env.PORT ?? 8787);
serve({ fetch: app.fetch, port }, () => {
  console.log(`bike-tracker web listening on http://localhost:${port}`);
});

// warm the DEM in the background so the first elevation request doesn't pay for the download
void ensureDem();
