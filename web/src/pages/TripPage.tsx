import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api";
import SpeedChart from "../components/SpeedChart";
import StatTile from "../components/StatTile";
import TripMap, { type MapLine } from "../components/TripMap";
import { RIDE_COLOR, WALK_COLOR } from "../lib/colors";
import { tripLabel } from "../lib/category";
import { formatDay, formatDuration, formatKm, formatTime } from "../lib/format";
import { buildSegments, computeMetrics, filterAccurate, haversineMeters } from "../lib/segments";
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

  const mapLines: MapLine[] = useMemo(
    () =>
      buildSegments(points).map((seg) => ({
        positions: seg.points.map((p) => [p.latitude, p.longitude] as [number, number]),
        color: seg.isRiding ? RIDE_COLOR : WALK_COLOR,
        dashed: !seg.isRiding
      })),
    [points]
  );

  return (
    <div className="container">
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
                <StatTile label="Elevation gain" value={metrics.elevationGainMeters.toFixed(0)} unit="m" />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
                <div className="legend">
                  <span className="chip">
                    <span className="swatch" style={{ background: RIDE_COLOR }} /> Riding
                  </span>
                  <span className="chip" style={{ color: WALK_COLOR }}>
                    <span className="swatch dashed" />{" "}
                    <span style={{ color: "var(--muted)" }}>Walking</span>
                  </span>
                </div>
              </div>
              <TripMap lines={mapLines} />

              <SpeedChart points={points} />

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
