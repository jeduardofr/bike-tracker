import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";
import ElevationChart, { demClimb, type ElevPoint } from "../components/ElevationChart";
import SpeedChart from "../components/SpeedChart";
import StatTile from "../components/StatTile";
import TripMap, { type MapLine } from "../components/TripMap";
import { FAST_COLOR, NEUTRAL_COLOR, RIDE_COLOR, SLOW_COLOR, WALK_COLOR } from "../lib/colors";
import { tripLabel } from "../lib/category";
import { formatDay, formatDuration, formatKm, formatTime } from "../lib/format";
import { GRADE_WINDOW_M, gradeColor } from "../lib/grade";
import {
  RIDING_THRESHOLD_MPS,
  buildSegments,
  computeMetrics,
  filterAccurate,
  haversineMeters
} from "../lib/segments";
import type { RoutePoint, TripResponse } from "../types";

interface Split {
  km: number;
  seconds: number;
  avgKmh: number;
}

function computeSplits(points: RoutePoint[]): Split[] {
  const splits: Split[] = [];
  let dist = 0;
  let splitStartT = points.length > 0 ? points[0].timestamp : 0;
  let nextMark = 1000;
  for (let i = 1; i < points.length; i++) {
    dist += haversineMeters(points[i - 1], points[i]);
    if (dist >= nextMark) {
      const seconds = (points[i].timestamp - splitStartT) / 1000;
      splits.push({ km: nextMark / 1000, seconds, avgKmh: seconds > 0 ? 3600 / seconds : 0 });
      splitStartT = points[i].timestamp;
      nextMark += 1000;
    }
  }
  return splits;
}

export default function TripPage() {
  const { uuid } = useParams<{ uuid: string }>();
  const [data, setData] = useState<TripResponse | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!uuid) return;
    let cancelled = false;
    api
      .trip(uuid)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [uuid]);

  const points = useMemo(() => (data ? filterAccurate(data.points) : []), [data]);
  const metrics = useMemo(() => (points.length >= 2 ? computeMetrics(data!.points) : null), [data, points]);
  const splits = useMemo(() => computeSplits(points), [points]);

  // accuracy-filtered points zipped with their DEM elevations
  const elevPoints = useMemo<ElevPoint[]>(() => {
    if (!data) return [];
    const out: ElevPoint[] = [];
    data.points.forEach((p, i) => {
      const e = data.elevations?.[i];
      if ((p.accuracy <= 50 || p.accuracy === 0) && e != null) out.push({ point: p, elev: e });
    });
    return out;
  }, [data]);
  const climb = useMemo(() => (elevPoints.length > 2 ? demClimb(elevPoints) : null), [elevPoints]);

  const mapLines: MapLine[] = useMemo(
    () =>
      buildSegments(points).map((seg) => ({
        positions: seg.points.map((p) => [p.latitude, p.longitude] as [number, number]),
        color: seg.isRiding ? RIDE_COLOR : WALK_COLOR,
        dashed: !seg.isRiding
      })),
    [points]
  );

  // linked hover: charts publish a timestamp; map + both charts reflect it
  const [hoverTs, setHoverTs] = useState<number | null>(null);
  const highlight = useMemo<[number, number] | null>(() => {
    if (hoverTs === null || points.length === 0) return null;
    let best = points[0];
    for (const p of points) {
      if (Math.abs(p.timestamp - hoverTs) < Math.abs(best.timestamp - hoverTs)) best = p;
    }
    return [best.latitude, best.longitude];
  }, [hoverTs, points]);

  // grade overlay: color = DEM grade class, dash still = walking
  const [mapMode, setMapMode] = useState<"mode" | "grade">("mode");
  const gradeLines: MapLine[] = useMemo(() => {
    const n = elevPoints.length;
    if (n < 2) return [];
    const cum: number[] = [0];
    for (let i = 1; i < n; i++) {
      cum.push(cum[i - 1] + haversineMeters(elevPoints[i - 1].point, elevPoints[i].point));
    }
    let anchor = 0;
    const styleAt = (i: number) => {
      while (anchor < i - 1 && cum[i] - cum[anchor] > GRADE_WINDOW_M) anchor++;
      const dd = cum[i] - cum[anchor];
      const grade = dd > 20 ? (elevPoints[i].elev - elevPoints[anchor].elev) / dd : 0;
      return {
        color: gradeColor(grade),
        dashed: elevPoints[i].point.speedMps <= RIDING_THRESHOLD_MPS
      };
    };
    const pos = (i: number): [number, number] => [
      elevPoints[i].point.latitude,
      elevPoints[i].point.longitude
    ];
    const lines: MapLine[] = [];
    let run: [number, number][] = [pos(0)];
    let current = styleAt(1);
    for (let i = 1; i < n; i++) {
      const s = styleAt(i);
      run.push(pos(i));
      if (s.color !== current.color || s.dashed !== current.dashed) {
        lines.push({ positions: run, ...current });
        run = [pos(i)];
        current = s;
      }
    }
    if (run.length >= 2) lines.push({ positions: run, ...current });
    return lines;
  }, [elevPoints]);
  const showGrade = mapMode === "grade" && gradeLines.length > 0;

  return (
    <div className="container wide">
      {error ? (
        <div className="loading">Trip not found</div>
      ) : data === null ? (
        <div className="loading">Loading…</div>
      ) : (
        <>
          <div className="topbar">
            <div>
              <Link to="/" className="back-link">
                ← Back
              </Link>
              <h1>{tripLabel(data.trip)}</h1>
              <div style={{ color: "var(--muted)", fontSize: 14 }}>
                {formatDay(data.trip.startTime)} · {formatTime(data.trip.startTime)}
                {data.trip.endTime ? ` – ${formatTime(data.trip.endTime)}` : ""}
                {data.weather === null
                  ? ""
                  : ` · ${data.weather.tempC.toFixed(0)}°C` +
                    (data.weather.precipMm >= 0.2
                      ? ` · 🌧 ${data.weather.precipMm.toFixed(1)} mm/h`
                      : "")}
              </div>
            </div>
          </div>

          {metrics === null ? (
            <div className="loading">Not enough GPS data for this trip</div>
          ) : (
            <>
              <div className="tiles">
                <StatTile label="Distance" value={formatKm(metrics.distanceMeters)} unit="km" />
                <StatTile label="Duration" value={formatDuration(metrics.durationSeconds)} />
                <StatTile label="Avg speed" value={metrics.avgSpeedKmh.toFixed(1)} unit="km/h" />
                <StatTile label="Max speed" value={metrics.maxSpeedKmh.toFixed(1)} unit="km/h" />
              </div>
              <div className="tiles">
                <StatTile
                  label={`Riding · ${formatDuration(metrics.rideSeconds)}`}
                  value={formatKm(metrics.rideDistanceMeters)}
                  unit="km"
                />
                <StatTile
                  label={`Walking · ${formatDuration(metrics.walkSeconds)}`}
                  value={formatKm(metrics.walkDistanceMeters)}
                  unit="km"
                />
                <StatTile label="Stopped" value={formatDuration(metrics.stoppedSeconds)} />
                <StatTile
                  label={climb !== null ? "Climb" : "Climb (GPS est.)"}
                  value={(climb ?? metrics.elevationGainMeters).toFixed(0)}
                  unit="m"
                />
              </div>

              <div className="trip-grid">
                <div className="trip-map-cell">
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      marginBottom: 8,
                      gap: 12,
                      flexWrap: "wrap"
                    }}
                  >
                <div style={{ display: "flex", gap: 6 }}>
                  <button
                    onClick={() => setMapMode("mode")}
                    style={mapMode === "mode" ? { borderColor: "var(--accent)" } : undefined}
                  >
                    Ride/Walk
                  </button>
                  <button
                    onClick={() => setMapMode("grade")}
                    disabled={gradeLines.length === 0}
                    style={showGrade ? { borderColor: "var(--accent)" } : undefined}
                  >
                    Grade
                  </button>
                </div>
                <div className="legend">
                  {showGrade ? (
                    <>
                      <span className="chip">
                        <span className="swatch" style={{ background: SLOW_COLOR }} /> Climb
                      </span>
                      <span className="chip">
                        <span className="swatch" style={{ background: NEUTRAL_COLOR }} /> Flat
                      </span>
                      <span className="chip">
                        <span className="swatch" style={{ background: FAST_COLOR }} /> Descent
                      </span>
                      <span className="chip">
                        <span className="swatch dashed" style={{ color: "var(--muted)" }} />{" "}
                        <span style={{ color: "var(--muted)" }}>Walking</span>
                      </span>
                    </>
                  ) : (
                    <>
                      <span className="chip">
                        <span className="swatch" style={{ background: RIDE_COLOR }} /> Riding
                      </span>
                      <span className="chip" style={{ color: WALK_COLOR }}>
                        <span className="swatch dashed" />{" "}
                        <span style={{ color: "var(--muted)" }}>Walking</span>
                      </span>
                    </>
                  )}
                </div>
              </div>
                  <TripMap lines={showGrade ? gradeLines : mapLines} highlight={highlight} />
                </div>

                <SpeedChart points={points} hoverTs={hoverTs} onHoverTs={setHoverTs} />

                {elevPoints.length > 2 ? (
                  <ElevationChart entries={elevPoints} hoverTs={hoverTs} onHoverTs={setHoverTs} />
                ) : null}
              </div>

              {splits.length > 0 ? (
                <div className="card">
                  <details className="splits">
                    <summary>Kilometer splits ({splits.length})</summary>
                    <table className="splits-table">
                      <thead>
                        <tr>
                          <th>Km</th>
                          <th>Time</th>
                          <th>Avg speed</th>
                        </tr>
                      </thead>
                      <tbody>
                        {splits.map((s) => (
                          <tr key={s.km}>
                            <td>{s.km}</td>
                            <td>{formatDuration(s.seconds)}</td>
                            <td>{s.avgKmh.toFixed(1)} km/h</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </details>
                </div>
              ) : null}
            </>
          )}
        </>
      )}
    </div>
  );
}
