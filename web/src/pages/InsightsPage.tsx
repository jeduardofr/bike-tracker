import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import SpeedMap from "../components/SpeedMap";
import StatTile from "../components/StatTile";
import { TO_HOME_COLOR, TO_OFFICE_COLOR, UNIVERSITY_COLOR } from "../lib/colors";
import { tripCategory, tripLabel } from "../lib/category";
import { addDays, formatDay, formatDuration, formatKm, isoDay, mondayOf } from "../lib/format";
import type { InsightsResponse, Trip } from "../types";

const W = 860;

const durationMin = (t: Trip) => (t.endTime ? (t.endTime - t.startTime) / 60000 : 0);

// ---------- weekly distance bars ----------

function barPath(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.min(r, w / 2, h);
  return `M${x},${y + h} L${x},${y + rr} Q${x},${y} ${x + rr},${y} L${x + w - rr},${y} Q${x + w},${y} ${x + w},${y + rr} L${x + w},${y + h} Z`;
}

function WeeklyBars({ trips }: { trips: Trip[] }) {
  const H = 190;
  const PAD = { top: 20, right: 12, bottom: 24, left: 36 };
  const [hover, setHover] = useState<number | null>(null);

  const weeks = useMemo(() => {
    if (trips.length === 0) return [];
    const first = mondayOf(new Date(Math.min(...trips.map((t) => t.startTime))));
    const out: Array<{ start: Date; km: number; trips: number }> = [];
    for (let w = new Date(first); w <= new Date(); w = addDays(w, 7)) {
      const from = w.getTime();
      const to = addDays(w, 7).getTime();
      const inWeek = trips.filter((t) => t.startTime >= from && t.startTime < to);
      out.push({
        start: new Date(w),
        km: inWeek.reduce((s, t) => s + t.distanceMeters, 0) / 1000,
        trips: inWeek.length
      });
    }
    return out;
  }, [trips]);

  const vMax = Math.max(10, Math.ceil(Math.max(...weeks.map((w) => w.km), 0) / 10) * 10);
  const y = (v: number) => PAD.top + (1 - v / vMax) * (H - PAD.top - PAD.bottom);
  const slot = (W - PAD.left - PAD.right) / Math.max(1, weeks.length);
  const barW = Math.min(28, slot * 0.6);

  return weeks.length === 0 ? null : (
    <div className="card">
      <h2>Distance per week</h2>
      <div className="chart-wrap">
        <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Distance per week">
          {[0, vMax / 2, vMax].map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
              <text x={PAD.left - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)">
                {v}
              </text>
            </g>
          ))}
          <text x={PAD.left - 8} y={12} textAnchor="end" fontSize="10" fill="var(--muted)">
            km
          </text>
          {weeks.map((w, i) => {
            const x = PAD.left + i * slot + (slot - barW) / 2;
            const h = (H - PAD.top - PAD.bottom) * (w.km / vMax);
            return w.km <= 0 ? null : (
              <path
                key={i}
                d={barPath(x, y(w.km), barW, h, 4)}
                fill="#43A047"
                opacity={hover === null || hover === i ? 1 : 0.45}
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover(null)}
              />
            );
          })}
          {weeks.map((w, i) =>
            i % Math.ceil(weeks.length / 8) === 0 ? (
              <text
                key={`t${i}`}
                x={PAD.left + i * slot + slot / 2}
                y={H - 6}
                textAnchor="middle"
                fontSize="11"
                fill="var(--muted)"
              >
                {w.start.toLocaleDateString([], { month: "short", day: "numeric" })}
              </text>
            ) : null
          )}
        </svg>
        {hover === null ? null : (
          <div
            className="chart-tooltip"
            style={{
              left: `${((PAD.left + hover * slot + slot / 2) / W) * 100}%`,
              top: `${(y(weeks[hover].km) / H) * 100}%`
            }}
          >
            <strong>{weeks[hover].km.toFixed(1)} km</strong> · {weeks[hover].trips} trips
            <span className="mode"> · wk of {formatDay(weeks[hover].start.getTime())}</span>
          </div>
        )}
      </div>
    </div>
  );
}

// ---------- commute duration over time ----------

function DurationScatter({ commutes }: { commutes: Trip[] }) {
  const H = 240;
  const PAD = { top: 20, right: 12, bottom: 24, left: 36 };
  const [hover, setHover] = useState<Trip | null>(null);

  const t0 = Math.min(...commutes.map((c) => c.startTime));
  const t1 = Math.max(...commutes.map((c) => c.startTime));
  const vMax = Math.max(20, Math.ceil(Math.max(...commutes.map(durationMin)) / 10) * 10);

  const x = (t: number) => PAD.left + ((t - t0) / Math.max(1, t1 - t0)) * (W - PAD.left - PAD.right);
  const y = (v: number) => PAD.top + (1 - v / vMax) * (H - PAD.top - PAD.bottom);

  const monthTicks = useMemo(() => {
    const ticks: number[] = [];
    const d = new Date(t0);
    d.setDate(1);
    d.setMonth(d.getMonth() + 1);
    for (; d.getTime() < t1; d.setMonth(d.getMonth() + 1)) ticks.push(d.getTime());
    return ticks;
  }, [t0, t1]);

  const mean = (dir: string) => {
    const list = commutes
      .filter((c) => c.direction === dir && tripCategory(c) === "office")
      .map(durationMin);
    return list.length > 0 ? list.reduce((s, v) => s + v, 0) / list.length : null;
  };
  const meanOffice = mean("HOME_TO_OFFICE");
  const meanHome = mean("OFFICE_TO_HOME");

  const onMove = (e: React.MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    const py = ((e.clientY - rect.top) / rect.height) * H;
    let best: Trip | null = null;
    let bestD = 20 * 20;
    for (const c of commutes) {
      const d = (x(c.startTime) - px) ** 2 + (y(durationMin(c)) - py) ** 2;
      if (d < bestD) {
        bestD = d;
        best = c;
      }
    }
    setHover(best);
  };

  return (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2>Commute duration over time</h2>
        <div className="legend">
          <span className="chip">
            <span className="swatch" style={{ background: TO_OFFICE_COLOR }} /> To office
          </span>
          <span className="chip">
            <span className="swatch" style={{ background: TO_HOME_COLOR }} /> To home
          </span>
          <span className="chip">
            <span
              style={{
                width: 10,
                height: 10,
                borderRadius: "50%",
                border: `2px solid ${UNIVERSITY_COLOR}`,
                display: "inline-block"
              }}
            />{" "}
            University
          </span>
        </div>
      </div>
      <div className="chart-wrap">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          width="100%"
          role="img"
          aria-label="Commute duration per trip over time"
          onMouseMove={onMove}
          onMouseLeave={() => setHover(null)}
        >
          {[0, vMax / 2, vMax].map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
              <text x={PAD.left - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)">
                {v}
              </text>
            </g>
          ))}
          <text x={PAD.left - 8} y={12} textAnchor="end" fontSize="10" fill="var(--muted)">
            min
          </text>
          {monthTicks.map((t) => (
            <text key={t} x={x(t)} y={H - 6} textAnchor="middle" fontSize="11" fill="var(--muted)">
              {new Date(t).toLocaleDateString([], { month: "short" })}
            </text>
          ))}
          {meanOffice === null ? null : (
            <line
              x1={PAD.left}
              x2={W - PAD.right}
              y1={y(meanOffice)}
              y2={y(meanOffice)}
              stroke={TO_OFFICE_COLOR}
              strokeWidth="1"
              strokeDasharray="4 5"
              opacity="0.6"
            />
          )}
          {meanHome === null ? null : (
            <line
              x1={PAD.left}
              x2={W - PAD.right}
              y1={y(meanHome)}
              y2={y(meanHome)}
              stroke={TO_HOME_COLOR}
              strokeWidth="1"
              strokeDasharray="4 5"
              opacity="0.6"
            />
          )}
          {commutes.map((c) => {
            const uni = tripCategory(c) === "university";
            return (
              <circle
                key={c.uuid}
                cx={x(c.startTime)}
                cy={y(durationMin(c))}
                r={hover?.uuid === c.uuid ? 5.5 : uni ? 4 : 3.5}
                fill={uni ? "var(--surface)" : c.direction === "HOME_TO_OFFICE" ? TO_OFFICE_COLOR : TO_HOME_COLOR}
                stroke={uni ? UNIVERSITY_COLOR : "var(--surface)"}
                strokeWidth={uni ? 2 : 1.5}
              />
            );
          })}
        </svg>
        {hover === null ? null : (
          <div
            className="chart-tooltip"
            style={{
              left: `${(x(hover.startTime) / W) * 100}%`,
              top: `${(y(durationMin(hover)) / H) * 100}%`
            }}
          >
            <strong>{Math.round(durationMin(hover))} min</strong> · {formatDay(hover.startTime)}
            <span className="mode"> · {tripLabel(hover)}</span>
          </div>
        )}
      </div>
      <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 4 }}>
        Dashed lines are your office-commute averages
        {meanOffice !== null ? ` — to office ${Math.round(meanOffice)} min` : ""}
        {meanHome !== null ? `, to home ${Math.round(meanHome)} min` : ""}. University rides (rings)
        stay out of the averages.
      </div>
    </div>
  );
}

// ---------- average duration by weekday ----------

function WeekdayBars({ commutes }: { commutes: Trip[] }) {
  const H = 190;
  const PAD = { top: 20, right: 12, bottom: 24, left: 36 };
  const [hover, setHover] = useState<{ day: number; dir: string } | null>(null);

  const days = ["Mon", "Tue", "Wed", "Thu", "Fri"];
  const avg = (weekday: number, dir: string): number | null => {
    const list = commutes.filter(
      (c) => new Date(c.startTime).getDay() === weekday && c.direction === dir
    );
    return list.length > 0 ? list.reduce((s, c) => s + durationMin(c), 0) / list.length : null;
  };
  const data = days.map((label, i) => ({
    label,
    office: avg(i + 1, "HOME_TO_OFFICE"),
    home: avg(i + 1, "OFFICE_TO_HOME")
  }));

  const vMax = Math.max(
    20,
    Math.ceil(Math.max(...data.flatMap((d) => [d.office ?? 0, d.home ?? 0])) / 10) * 10
  );
  const y = (v: number) => PAD.top + (1 - v / vMax) * (H - PAD.top - PAD.bottom);
  const slot = (W - PAD.left - PAD.right) / 5;
  const barW = 26;

  const hovered =
    hover === null
      ? null
      : { value: hover.dir === "office" ? data[hover.day].office : data[hover.day].home, ...hover };

  return (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2>Average office commute by weekday</h2>
        <div className="legend">
          <span className="chip">
            <span className="swatch" style={{ background: TO_OFFICE_COLOR }} /> To office
          </span>
          <span className="chip">
            <span className="swatch" style={{ background: TO_HOME_COLOR }} /> To home
          </span>
        </div>
      </div>
      <div className="chart-wrap">
        <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Average commute duration by weekday">
          {[0, vMax / 2, vMax].map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
              <text x={PAD.left - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)">
                {v}
              </text>
            </g>
          ))}
          <text x={PAD.left - 8} y={12} textAnchor="end" fontSize="10" fill="var(--muted)">
            min
          </text>
          {data.map((d, i) => {
            const cx = PAD.left + i * slot + slot / 2;
            return (
              <g key={d.label}>
                {d.office === null ? null : (
                  <path
                    d={barPath(cx - barW - 1, y(d.office), barW, (H - PAD.top - PAD.bottom) * (d.office / vMax), 4)}
                    fill={TO_OFFICE_COLOR}
                    opacity={hover === null || (hover.day === i && hover.dir === "office") ? 1 : 0.45}
                    onMouseEnter={() => setHover({ day: i, dir: "office" })}
                    onMouseLeave={() => setHover(null)}
                  />
                )}
                {d.home === null ? null : (
                  <path
                    d={barPath(cx + 1, y(d.home), barW, (H - PAD.top - PAD.bottom) * (d.home / vMax), 4)}
                    fill={TO_HOME_COLOR}
                    opacity={hover === null || (hover.day === i && hover.dir === "home") ? 1 : 0.45}
                    onMouseEnter={() => setHover({ day: i, dir: "home" })}
                    onMouseLeave={() => setHover(null)}
                  />
                )}
                <text x={cx} y={H - 6} textAnchor="middle" fontSize="11" fill="var(--muted)">
                  {d.label}
                </text>
              </g>
            );
          })}
        </svg>
        {hovered === null || hovered.value === null ? null : (
          <div
            className="chart-tooltip"
            style={{
              left: `${((PAD.left + hovered.day * slot + slot / 2) / W) * 100}%`,
              top: `${(y(hovered.value) / H) * 100}%`
            }}
          >
            <strong>{Math.round(hovered.value)} min</strong>
            <span className="mode"> avg {hovered.dir === "office" ? "to office" : "to home"}</span>
          </div>
        )}
      </div>
    </div>
  );
}

// ---------- page ----------

export default function InsightsPage() {
  const [data, setData] = useState<InsightsResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.insights().then((res) => {
      if (!cancelled) setData(res);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const trips = data?.trips ?? [];
  const commutes = useMemo(
    () => trips.filter((t) => t.endTime !== null && t.direction !== "FREE"),
    [trips]
  );
  const officeCommutes = useMemo(
    () => commutes.filter((t) => tripCategory(t) === "office"),
    [commutes]
  );
  const universityRides = useMemo(
    () => commutes.filter((t) => tripCategory(t) === "university"),
    [commutes]
  );

  const records = useMemo(() => {
    const byMinDuration = (dir: string) =>
      officeCommutes
        .filter((c) => c.direction === dir && durationMin(c) > 5)
        .sort((a, b) => durationMin(a) - durationMin(b))[0] ?? null;
    const longest = [...trips].sort((a, b) => b.distanceMeters - a.distanceMeters)[0] ?? null;
    const fastestAvg = [...trips].sort((a, b) => b.averageSpeedKmh - a.averageSpeedKmh)[0] ?? null;
    return { toOffice: byMinDuration("HOME_TO_OFFICE"), toHome: byMinDuration("OFFICE_TO_HOME"), longest, fastestAvg };
  }, [trips, officeCommutes]);

  const lifetime = useMemo(() => {
    const distance = trips.reduce((s, t) => s + t.distanceMeters, 0);
    const duration = trips.reduce((s, t) => s + (t.endTime ? (t.endTime - t.startTime) / 1000 : 0), 0);
    const activeDays = new Set(trips.map((t) => isoDay(new Date(t.startTime))));

    // longest run of consecutive commute weekdays (weekends don't break it)
    let streak = 0;
    let best = 0;
    if (trips.length > 0) {
      const first = new Date(Math.min(...trips.map((t) => t.startTime)));
      for (let d = new Date(first); d <= new Date(); d = addDays(d, 1)) {
        const weekday = d.getDay() >= 1 && d.getDay() <= 5;
        if (!weekday) continue;
        if (activeDays.has(isoDay(d))) {
          streak++;
          best = Math.max(best, streak);
        } else {
          streak = 0;
        }
      }
    }
    return { distance, duration, count: trips.length, activeDays: activeDays.size, streak: best };
  }, [trips]);

  return (
    <div className="container">
      <div className="topbar">
        <div>
          <Link to="/" className="back-link">
            ← Back
          </Link>
          <h1>Insights</h1>
        </div>
      </div>

      {data === null ? (
        <div className="loading">Crunching {"your"} rides…</div>
      ) : (
        <>
          <div className="tiles">
            <StatTile label="All-time distance" value={formatKm(lifetime.distance)} unit="km" />
            <StatTile label="Time on the bike" value={formatDuration(lifetime.duration)} />
            <StatTile label="Trips" value={String(lifetime.count)} />
            <StatTile label="Active days" value={String(lifetime.activeDays)} />
            <StatTile label="Best commute-day streak" value={String(lifetime.streak)} unit="days" />
            <StatTile label="University rides" value={String(universityRides.length)} />
          </div>

          <div className="tiles">
            {records.toOffice === null ? null : (
              <Link to={`/trip/${records.toOffice.uuid}`} className="tile">
                <div className="value">
                  {Math.round(durationMin(records.toOffice))}
                  <span className="unit">min</span>
                </div>
                <div className="label">
                  ⚡ Fastest to office · {formatDay(records.toOffice.startTime)}
                </div>
              </Link>
            )}
            {records.toHome === null ? null : (
              <Link to={`/trip/${records.toHome.uuid}`} className="tile">
                <div className="value">
                  {Math.round(durationMin(records.toHome))}
                  <span className="unit">min</span>
                </div>
                <div className="label">⚡ Fastest to home · {formatDay(records.toHome.startTime)}</div>
              </Link>
            )}
            {records.fastestAvg === null ? null : (
              <Link to={`/trip/${records.fastestAvg.uuid}`} className="tile">
                <div className="value">
                  {records.fastestAvg.averageSpeedKmh.toFixed(1)}
                  <span className="unit">km/h</span>
                </div>
                <div className="label">🏆 Best average speed · {formatDay(records.fastestAvg.startTime)}</div>
              </Link>
            )}
            {records.longest === null ? null : (
              <Link to={`/trip/${records.longest.uuid}`} className="tile">
                <div className="value">
                  {formatKm(records.longest.distanceMeters)}
                  <span className="unit">km</span>
                </div>
                <div className="label">🛣️ Longest ride · {formatDay(records.longest.startTime)}</div>
              </Link>
            )}
          </div>

          <WeeklyBars trips={trips} />
          {commutes.length > 0 ? <DurationScatter commutes={commutes} /> : null}
          {officeCommutes.length > 0 ? <WeekdayBars commutes={officeCommutes} /> : null}

          <div className="card">
            <h2>Where you fly, where you crawl</h2>
            <div style={{ fontSize: 13, color: "var(--muted)", marginBottom: 8 }}>
              Median speed at each ~27 m stretch of road, across every ride you{"'"}ve logged
              ({data.bins.length.toLocaleString()} road segments).
            </div>
            <SpeedMap bins={data.bins} medianKmh={data.medianKmh} />
          </div>
        </>
      )}
    </div>
  );
}
