import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import StatTile from "../components/StatTile";
import TripMap, { type MapLine } from "../components/TripMap";
import { weekdayColor } from "../lib/colors";
import { tripLabel } from "../lib/category";
import { addDays, formatDay, formatDuration, formatKm, formatTime, isoDay, mondayOf } from "../lib/format";
import { RIDING_THRESHOLD_MPS } from "../lib/segments";
import type { OverviewResponse, TracePoint, Trip } from "../types";

/** Split an overview trace into solid (riding) / dashed (walking) runs. */
function traceToLines(trace: TracePoint[], color: string): MapLine[] {
  if (trace.length < 2) return [];
  const lines: MapLine[] = [];
  let positions: [number, number][] = [[trace[0][0], trace[0][1]]];
  let riding = trace[0][2] > RIDING_THRESHOLD_MPS;
  for (let i = 1; i < trace.length; i++) {
    const isRiding = trace[i][2] > RIDING_THRESHOLD_MPS;
    positions.push([trace[i][0], trace[i][1]]);
    if (isRiding !== riding) {
      lines.push({ positions, color, dashed: !riding });
      positions = [[trace[i][0], trace[i][1]]];
      riding = isRiding;
    }
  }
  if (positions.length >= 2) lines.push({ positions, color, dashed: !riding });
  return lines;
}

export default function DashboardPage() {
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [data, setData] = useState<OverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const weekEnd = useMemo(() => addDays(weekStart, 7), [weekStart]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api
      .overview(weekStart.getTime(), weekEnd.getTime())
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [weekStart, weekEnd]);

  const days = useMemo(() => {
    const map = new Map<string, Trip[]>();
    (data?.trips ?? []).forEach((t) => {
      const key = isoDay(new Date(t.startTime));
      const list = map.get(key) ?? [];
      list.push(t);
      map.set(key, list);
    });
    return map;
  }, [data]);

  const totals = useMemo(() => {
    const trips = data?.trips ?? [];
    const distance = trips.reduce((s, t) => s + t.distanceMeters, 0);
    const duration = trips.reduce((s, t) => s + (t.endTime ? (t.endTime - t.startTime) / 1000 : 0), 0);
    const avg = trips.length > 0 ? trips.reduce((s, t) => s + t.averageSpeedKmh, 0) / trips.length : 0;
    return { distance, duration, avg, count: trips.length };
  }, [data]);

  const mapLines = useMemo(() => {
    if (data === null) return [];
    return data.trips.flatMap((t) =>
      traceToLines(data.traces[t.uuid] ?? [], weekdayColor(new Date(t.startTime)))
    );
  }, [data]);

  const isCurrentWeek = mondayOf(new Date()).getTime() === weekStart.getTime();
  const rangeLabel = `${formatDay(weekStart.getTime())} – ${formatDay(addDays(weekStart, 6).getTime())}`;

  return (
    <div className="container">
      <div className="topbar">
        <h1>🚲 Bike Tracker</h1>
        <div className="week-nav">
          <Link to="/insights" className="btn">
            Insights
          </Link>
          <button onClick={() => setWeekStart(addDays(weekStart, -7))}>←</button>
          <span className="range">{isCurrentWeek ? "This week" : rangeLabel}</span>
          <button onClick={() => setWeekStart(addDays(weekStart, 7))} disabled={isCurrentWeek}>
            →
          </button>
        </div>
      </div>

      {loading && data === null ? (
        <div className="loading">Loading…</div>
      ) : (
        <>
          <div className="tiles">
            <StatTile label="Distance" value={formatKm(totals.distance)} unit="km" />
            <StatTile label="Trips" value={String(totals.count)} unit="rides" />
            <StatTile label="Time" value={formatDuration(totals.duration)} />
            <StatTile label="Avg speed" value={totals.avg.toFixed(1)} unit="km/h" />
          </div>

          {mapLines.length > 0 ? <TripMap lines={mapLines} /> : null}

          <div className="day-cards">
            {Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))
              .map((day) => ({ day, trips: days.get(isoDay(day)) ?? [] }))
              .filter(({ trips }) => trips.length > 0)
              .map(({ day, trips }) => {
                const color = weekdayColor(day);
                const km = trips.reduce((s, t) => s + t.distanceMeters, 0);
                const dur = trips.reduce(
                  (s, t) => s + (t.endTime ? (t.endTime - t.startTime) / 1000 : 0),
                  0
                );
                return (
                  <div className="day-card" key={isoDay(day)}>
                    <div className="day-head">
                      <span className="day-name" style={{ color }}>
                        {day.toLocaleDateString([], { weekday: "short" })}
                      </span>
                      <span className="dots">
                        {trips.map((t) => (
                          <Link to={`/trip/${t.uuid}`} key={t.uuid} title={tripLabel(t)}>
                            <span
                              className="dot"
                              style={
                                t.direction === "OFFICE_TO_HOME"
                                  ? { border: `2px solid ${color}` }
                                  : { background: color }
                              }
                            />
                          </Link>
                        ))}
                      </span>
                    </div>
                    <div className="km">{formatKm(km)} km</div>
                    <div className="sub">
                      {trips.length} trip{trips.length !== 1 ? "s" : ""} · {formatDuration(dur)}
                    </div>
                  </div>
                );
              })}
          </div>

          <div className="trip-list">
            {Array.from(days.entries())
              .sort((a, b) => (a[0] < b[0] ? 1 : -1))
              .map(([key, trips]) => (
                <div key={key}>
                  <div className="trip-day-header">{formatDay(trips[0].startTime)}</div>
                  {trips
                    .slice()
                    .sort((a, b) => b.startTime - a.startTime)
                    .map((t) => (
                      <Link to={`/trip/${t.uuid}`} className="trip-row" key={t.uuid}>
                        <span
                          className="bar"
                          style={{ background: weekdayColor(new Date(t.startTime)) }}
                        />
                        <span className="main">
                          <div className="title">{tripLabel(t)}</div>
                          <div className="sub">
                            {formatTime(t.startTime)}
                            {t.endTime
                              ? ` · ${formatDuration((t.endTime - t.startTime) / 1000)}`
                              : ""}{" "}
                            · {formatKm(t.distanceMeters)} km
                          </div>
                        </span>
                        <span className="speed">{t.averageSpeedKmh.toFixed(1)} km/h</span>
                      </Link>
                    ))}
                </div>
              ))}
            {totals.count === 0 ? <div className="loading">No trips this week</div> : null}
          </div>
        </>
      )}
    </div>
  );
}
