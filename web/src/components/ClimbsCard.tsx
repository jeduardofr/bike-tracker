import { useMemo } from "react";
import { CircleMarker, MapContainer, TileLayer, Tooltip } from "react-leaflet";
import { LatLngBounds } from "leaflet";
import { NEUTRAL_COLOR, RIDE_COLOR, SLOW_COLOR } from "../lib/colors";
import type { ClimbSpot } from "../types";

const CONQUERED_COLOR = RIDE_COLOR; // green: you ride it now
const PROGRESS_COLOR = "#C08A00"; // amber: getting there

function walkShare(walkMin: number, rideMin: number): number {
  return walkMin / Math.max(0.01, walkMin + rideMin);
}

// status reflects EVENING rides only — morning walking is deliberate sweat management
function status(spot: ClimbSpot): { color: string; label: string } {
  if (spot.months.length === 0) return { color: NEUTRAL_COLOR, label: "mornings only" };
  const recent = spot.months[spot.months.length - 1];
  const share = walkShare(recent.walkMin, recent.rideMin);
  if (share < 0.2) return { color: CONQUERED_COLOR, label: "riding it" };
  if (share > 0.5) return { color: SLOW_COLOR, label: "drains you" };
  return { color: PROGRESS_COLOR, label: "getting there" };
}

function monthLabel(month: string): string {
  return new Date(`${month}-15T12:00:00`).toLocaleDateString([], { month: "short" });
}

export default function ClimbsCard({ climbs }: { climbs: ClimbSpot[] }) {
  const bounds = useMemo(() => {
    const b = new LatLngBounds([]);
    climbs.forEach((c) => b.extend([c.lat, c.lng]));
    return b.isValid() ? b.pad(0.4) : null;
  }, [climbs]);

  return climbs.length === 0 || bounds === null ? null : (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2>Climbs you walk</h2>
        <div className="legend">
          <span className="chip">
            <span className="dot" style={{ background: SLOW_COLOR }} /> Drains you
          </span>
          <span className="chip">
            <span className="dot" style={{ background: PROGRESS_COLOR }} /> Getting there
          </span>
          <span className="chip">
            <span className="dot" style={{ background: CONQUERED_COLOR }} /> Riding it
          </span>
        </div>
      </div>
      <div style={{ fontSize: 13, color: "var(--muted)", margin: "4px 0 10px" }}>
        Uphill stretches (&gt;1.5%) on your office commute where you dismount. Status and monthly
        trends count <strong>evening rides only</strong> — morning walking is your no-sweat
        strategy, shown separately as &ldquo;AM by choice&rdquo;.
      </div>
      <div className="map-wrap short" style={{ marginBottom: 12 }}>
        <MapContainer bounds={bounds} scrollWheelZoom={true} preferCanvas={true}>
          <TileLayer
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
          />
          {climbs.map((c, i) => (
            <CircleMarker
              key={i}
              center={[c.lat, c.lng]}
              radius={9}
              pathOptions={{
                color: "#ffffff",
                weight: 1.5,
                fillColor: status(c).color,
                fillOpacity: 0.9
              }}
            >
              <Tooltip>
                #{i + 1} · {c.gradePct.toFixed(1)}% grade · {status(c).label}
              </Tooltip>
            </CircleMarker>
          ))}
        </MapContainer>
      </div>
      {climbs.map((c, i) => (
        <div
          key={i}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            padding: "7px 0",
            borderTop: i > 0 ? "1px solid var(--border)" : undefined,
            flexWrap: "wrap"
          }}
        >
          <span className="dot" style={{ background: status(c).color, flex: "none" }} />
          <strong style={{ width: 120, fontSize: 13 }}>
            #{i + 1} · {c.gradePct.toFixed(1)}% grade
          </strong>
          <span style={{ fontSize: 12, color: "var(--muted)", width: 130 }}>
            {c.amWalkPct === null ? "AM: —" : `AM: ${c.amWalkPct}% by choice`}
          </span>
          <span style={{ fontSize: 13, display: "flex", gap: 8, flexWrap: "wrap" }}>
            {c.months.length === 0 ? (
              <span style={{ color: "var(--muted)" }}>no evening passes yet</span>
            ) : (
              c.months.map((m, j) => {
                const share = walkShare(m.walkMin, m.rideMin);
                const color =
                  share > 0.5 ? SLOW_COLOR : share < 0.2 ? CONQUERED_COLOR : NEUTRAL_COLOR;
                return (
                  <span key={m.month}>
                    {j > 0 ? <span style={{ color: "var(--muted)" }}>→ </span> : null}
                    {monthLabel(m.month)}{" "}
                    <strong style={{ color }}>{Math.round(share * 100)}%</strong>
                  </span>
                );
              })
            )}
          </span>
        </div>
      ))}
    </div>
  );
}
