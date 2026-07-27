import { useMemo, useRef, useState } from "react";
import { RIDE_COLOR, WALK_COLOR } from "../lib/colors";
import { RIDING_THRESHOLD_MPS } from "../lib/segments";
import { formatTime } from "../lib/format";
import type { RoutePoint } from "../types";

const W = 860;
const H = 240;
const PAD = { top: 24, right: 12, bottom: 26, left: 40 };
const THRESHOLD_KMH = RIDING_THRESHOLD_MPS * 3.6;

interface ChartPoint {
  t: number; // timestamp ms
  v: number; // km/h (smoothed)
  riding: boolean;
}

interface Props {
  points: RoutePoint[]; // accuracy-filtered, timestamp-ascending
  /** shared hover timestamp for cross-chart/map linking */
  hoverTs?: number | null;
  onHoverTs?: (ts: number | null) => void;
}

function prepare(points: RoutePoint[]): ChartPoint[] {
  // cap resolution for rendering, then a light 3-point moving average
  const maxPoints = 600;
  const step = points.length > maxPoints ? Math.ceil(points.length / maxPoints) : 1;
  const sampled = points.filter((_, i) => i % step === 0 || i === points.length - 1);
  return sampled.map((p, i) => {
    const prev = sampled[i - 1] ?? p;
    const next = sampled[i + 1] ?? p;
    const v = ((prev.speedMps + p.speedMps + next.speedMps) / 3) * 3.6;
    return { t: p.timestamp, v, riding: p.speedMps > RIDING_THRESHOLD_MPS };
  });
}

function niceMax(v: number): number {
  return Math.max(5, Math.ceil(v / 5) * 5);
}

export default function SpeedChart({ points, hoverTs, onHoverTs }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [localHover, setLocalHover] = useState<ChartPoint | null>(null);

  const data = useMemo(() => prepare(points), [points]);

  // controlled by the shared timestamp when provided, else local state
  const hover = useMemo(() => {
    const ts = hoverTs;
    if (ts == null) return onHoverTs ? null : localHover;
    let best: ChartPoint | null = null;
    for (const p of data) {
      if (best === null || Math.abs(p.t - ts) < Math.abs(best.t - ts)) best = p;
    }
    return best;
  }, [hoverTs, localHover, data, onHoverTs]);

  const setHover = (p: ChartPoint | null) => {
    if (onHoverTs) onHoverTs(p === null ? null : p.t);
    else setLocalHover(p);
  };
  const t0 = data.length > 0 ? data[0].t : 0;
  const t1 = data.length > 0 ? data[data.length - 1].t : 1;
  const vMax = useMemo(() => niceMax(Math.max(...data.map((d) => d.v), 0)), [data]);

  const x = (t: number) => PAD.left + ((t - t0) / Math.max(1, t1 - t0)) * (W - PAD.left - PAD.right);
  const y = (v: number) => PAD.top + (1 - v / vMax) * (H - PAD.top - PAD.bottom);

  // one <path> per contiguous mode run, so color identity follows the mode
  const runs = useMemo(() => {
    const out: Array<{ riding: boolean; d: string }> = [];
    let current: ChartPoint[] = [];
    let mode: boolean | null = null;
    const flush = () => {
      if (current.length >= 2 && mode !== null) {
        out.push({
          riding: mode,
          d: current.map((p, i) => `${i === 0 ? "M" : "L"}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join("")
        });
      }
    };
    for (const p of data) {
      if (mode === null) mode = p.riding;
      if (p.riding !== mode) {
        current.push(p); // overlap point for continuity
        flush();
        current = [p];
        mode = p.riding;
      } else {
        current.push(p);
      }
    }
    flush();
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, vMax]);

  const yTicks = useMemo(() => {
    const step = vMax <= 30 ? 5 : vMax <= 60 ? 10 : 20;
    const ticks: number[] = [];
    for (let v = 0; v <= vMax; v += step) ticks.push(v);
    return ticks;
  }, [vMax]);

  const xTicks = useMemo(() => {
    const spanMin = (t1 - t0) / 60000;
    const stepMin = spanMin > 50 ? 15 : spanMin > 25 ? 10 : 5;
    const ticks: number[] = [];
    const first = Math.ceil(t0 / (stepMin * 60000)) * stepMin * 60000;
    for (let t = first; t < t1; t += stepMin * 60000) ticks.push(t);
    return ticks;
  }, [t0, t1]);

  const onMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (data.length === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const px = ((e.clientX - rect.left) / rect.width) * W;
    const t = t0 + ((px - PAD.left) / (W - PAD.left - PAD.right)) * (t1 - t0);
    let best = data[0];
    let bestD = Infinity;
    for (const p of data) {
      const d = Math.abs(p.t - t);
      if (d < bestD) {
        bestD = d;
        best = p;
      }
    }
    setHover(best);
  };

  return data.length < 2 ? null : (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2>Speed</h2>
        <div className="legend">
          <span className="chip">
            <span className="swatch" style={{ background: RIDE_COLOR }} /> Riding
          </span>
          <span className="chip" style={{ color: WALK_COLOR }}>
            <span className="swatch dashed" /> <span style={{ color: "var(--muted)" }}>Walking</span>
          </span>
        </div>
      </div>
      <div className="chart-wrap" ref={wrapRef}>
        <svg
          viewBox={`0 0 ${W} ${H}`}
          width="100%"
          role="img"
          aria-label="Speed over time, colored by riding versus walking"
          onMouseMove={onMove}
          onMouseLeave={() => setHover(null)}
        >
          {yTicks.map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke="var(--border)" strokeWidth="1" />
              <text x={PAD.left - 8} y={y(v) + 4} textAnchor="end" fontSize="11" fill="var(--muted)">
                {Math.round(v)}
              </text>
            </g>
          ))}
          <text x={PAD.left - 8} y={12} textAnchor="end" fontSize="10" fill="var(--muted)">
            km/h
          </text>
          {xTicks.map((t) => (
            <text key={t} x={x(t)} y={H - 8} textAnchor="middle" fontSize="11" fill="var(--muted)">
              {formatTime(t)}
            </text>
          ))}
          <line
            x1={PAD.left}
            x2={W - PAD.right}
            y1={y(THRESHOLD_KMH)}
            y2={y(THRESHOLD_KMH)}
            stroke="var(--muted)"
            strokeWidth="1"
            strokeDasharray="3 5"
            opacity="0.5"
          />
          {runs.map((run, i) => (
            <path
              key={i}
              d={run.d}
              fill="none"
              stroke={run.riding ? RIDE_COLOR : WALK_COLOR}
              strokeWidth="2"
              strokeDasharray={run.riding ? undefined : "4 4"}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}
          {hover === null ? null : (
            <g>
              <line x1={x(hover.t)} x2={x(hover.t)} y1={PAD.top} y2={H - PAD.bottom} stroke="var(--muted)" strokeWidth="1" opacity="0.6" />
              <circle
                cx={x(hover.t)}
                cy={y(hover.v)}
                r="4"
                fill={hover.riding ? RIDE_COLOR : WALK_COLOR}
                stroke="var(--surface)"
                strokeWidth="2"
              />
            </g>
          )}
        </svg>
        {hover === null ? null : (
          <div
            className="chart-tooltip"
            style={{
              left: `${(x(hover.t) / W) * 100}%`,
              top: `${(y(hover.v) / H) * 100}%`
            }}
          >
            <strong>{hover.v.toFixed(1)} km/h</strong> · {formatTime(hover.t)}{" "}
            <span className="mode">{hover.riding ? "riding" : "walking"}</span>
          </div>
        )}
      </div>
    </div>
  );
}
