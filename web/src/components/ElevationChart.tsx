import { useMemo, useState } from "react";
import { FAST_COLOR, NEUTRAL_COLOR, SLOW_COLOR } from "../lib/colors";
import { GRADE_WINDOW_M, gradeColor } from "../lib/grade";
import { haversineMeters } from "../lib/segments";
import type { RoutePoint } from "../types";

const W = 860;
const H = 200;
const PAD = { top: 24, right: 12, bottom: 26, left: 44 };

export interface ElevPoint {
  point: RoutePoint;
  elev: number;
}

interface Sample {
  km: number;
  m: number;
  grade: number;
  ts: number;
}

export function demClimb(entries: ElevPoint[]): number {
  let gain = 0;
  let last = entries.length > 0 ? entries[0].elev : 0;
  for (const e of entries) {
    const d = e.elev - last;
    if (Math.abs(d) >= 3) {
      if (d > 0) gain += d;
      last = e.elev;
    }
  }
  return gain;
}

function prepare(entries: ElevPoint[]): Sample[] {
  if (entries.length < 2) return [];
  // cumulative distance
  const raw: Array<{ km: number; m: number; ts: number }> = [
    { km: 0, m: entries[0].elev, ts: entries[0].point.timestamp }
  ];
  let cum = 0;
  for (let i = 1; i < entries.length; i++) {
    cum += haversineMeters(entries[i - 1].point, entries[i].point);
    raw.push({ km: cum / 1000, m: entries[i].elev, ts: entries[i].point.timestamp });
  }
  const step = raw.length > 400 ? Math.ceil(raw.length / 400) : 1;
  const sampled = raw.filter((_, i) => i % step === 0 || i === raw.length - 1);
  // light smoothing + grade over a trailing ~100 m window
  return sampled.map((s, i) => {
    const lo = Math.max(0, i - 2);
    const hi = Math.min(sampled.length - 1, i + 2);
    let sum = 0;
    for (let j = lo; j <= hi; j++) sum += sampled[j].m;
    const m = sum / (hi - lo + 1);
    let back = i;
    while (back > 0 && (s.km - sampled[back].km) * 1000 < GRADE_WINDOW_M) back--;
    const dd = (s.km - sampled[back].km) * 1000;
    const grade = dd > 20 ? (s.m - sampled[back].m) / dd : 0;
    return { km: s.km, m, grade, ts: s.ts };
  });
}

interface Props {
  entries: ElevPoint[];
  /** shared hover timestamp for cross-chart/map linking */
  hoverTs?: number | null;
  onHoverTs?: (ts: number | null) => void;
}

export default function ElevationChart({ entries, hoverTs, onHoverTs }: Props) {
  const [localHover, setLocalHover] = useState<Sample | null>(null);
  const data = useMemo(() => prepare(entries), [entries]);

  const hover = useMemo(() => {
    const ts = hoverTs;
    if (ts == null) return onHoverTs ? null : localHover;
    let best: Sample | null = null;
    for (const s of data) {
      if (best === null || Math.abs(s.ts - ts) < Math.abs(best.ts - ts)) best = s;
    }
    return best;
  }, [hoverTs, localHover, data, onHoverTs]);

  const setHover = (s: Sample | null) => {
    if (onHoverTs) onHoverTs(s === null ? null : s.ts);
    else setLocalHover(s);
  };

  const kmMax = data.length > 0 ? data[data.length - 1].km : 1;
  const mLo = Math.floor((Math.min(...data.map((d) => d.m)) - 5) / 10) * 10;
  const mHi = Math.ceil((Math.max(...data.map((d) => d.m)) + 5) / 10) * 10;

  const x = (km: number) => PAD.left + (km / Math.max(0.001, kmMax)) * (W - PAD.left - PAD.right);
  const y = (m: number) => PAD.top + (1 - (m - mLo) / Math.max(1, mHi - mLo)) * (H - PAD.top - PAD.bottom);

  // contiguous same-class runs, overlapping one point for continuity
  const runs = useMemo(() => {
    const out: Array<{ color: string; d: string }> = [];
    let current: Sample[] = [];
    let color: string | null = null;
    const flush = () => {
      if (current.length >= 2 && color !== null) {
        out.push({
          color,
          d: current.map((p, i) => `${i === 0 ? "M" : "L"}${x(p.km).toFixed(1)},${y(p.m).toFixed(1)}`).join("")
        });
      }
    };
    for (const s of data) {
      const c = gradeColor(s.grade);
      if (color === null) color = c;
      if (c !== color) {
        current.push(s);
        flush();
        current = [s];
        color = c;
      } else {
        current.push(s);
      }
    }
    flush();
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, mLo, mHi]);

  const yTicks = useMemo(() => {
    const span = mHi - mLo;
    const step = span <= 40 ? 10 : span <= 100 ? 20 : 50;
    const ticks: number[] = [];
    for (let v = mLo; v <= mHi; v += step) ticks.push(v);
    return ticks;
  }, [mLo, mHi]);

  const onMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (data.length === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    const km = ((px - PAD.left) / (W - PAD.left - PAD.right)) * kmMax;
    let best = data[0];
    for (const s of data) if (Math.abs(s.km - km) < Math.abs(best.km - km)) best = s;
    setHover(best);
  };

  return data.length < 2 ? null : (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2>Elevation</h2>
        <div className="legend">
          <span className="chip">
            <span className="swatch" style={{ background: SLOW_COLOR }} /> Climb &gt;1.5%
          </span>
          <span className="chip">
            <span className="swatch" style={{ background: NEUTRAL_COLOR }} /> Flat
          </span>
          <span className="chip">
            <span className="swatch" style={{ background: FAST_COLOR }} /> Descent
          </span>
        </div>
      </div>
      <div className="chart-wrap">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          width="100%"
          role="img"
          aria-label="Elevation profile along the route, colored by grade"
          onMouseMove={onMove}
          onMouseLeave={() => setHover(null)}
        >
          {yTicks.map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
              <text x={PAD.left - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)">
                {v}
              </text>
            </g>
          ))}
          <text x={PAD.left - 8} y={12} textAnchor="end" fontSize="10" fill="var(--muted)">
            m
          </text>
          {Array.from({ length: Math.floor(kmMax) }, (_, i) => i + 1).map((km) => (
            <text key={km} x={x(km)} y={H - 8} textAnchor="middle" fontSize="11" fill="var(--muted)">
              {km} km
            </text>
          ))}
          {runs.map((run, i) => (
            <path key={i} d={run.d} fill="none" stroke={run.color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
          ))}
          {hover === null ? null : (
            <g>
              <line x1={x(hover.km)} x2={x(hover.km)} y1={PAD.top} y2={H - PAD.bottom} stroke="var(--muted)" strokeWidth="1" opacity="0.6" />
              <circle cx={x(hover.km)} cy={y(hover.m)} r="4" fill={gradeColor(hover.grade)} stroke="var(--surface)" strokeWidth="2" />
            </g>
          )}
        </svg>
        {hover === null ? null : (
          <div
            className="chart-tooltip"
            style={{ left: `${(x(hover.km) / W) * 100}%`, top: `${(y(hover.m) / H) * 100}%` }}
          >
            <strong>{hover.m.toFixed(0)} m</strong> · {hover.km.toFixed(1)} km
            <span className="mode"> · {(hover.grade * 100).toFixed(1)}% grade</span>
          </div>
        )}
      </div>
    </div>
  );
}
