import { useEffect, useState } from "react";
import { CircleMarker, Marker } from "react-leaflet";
import { divIcon } from "leaflet";
import { api } from "../api";
import type { Signal } from "../types";

// Traffic signals (OpenStreetMap) as a reference overlay: outlined marks so they read as
// map furniture, distinct by shape from the filled route/speed marks underneath.
// Junction lights are rings; stand-alone pedestrian crossing lights are diamonds.
const RING_COLOR = "#E6E8EB";
const STORAGE_KEY = "bike.showSignals";
// a signal counts as "on the route" within this distance of any drawn point
const NEAR_METERS = 30;
const M_PER_LAT = 111_000;
const M_PER_LNG = 111_000 * Math.cos((20.69 * Math.PI) / 180);

// the diamond is an inner span: rotating the icon itself would also rotate Leaflet's
// positioning transform and fling the marker off its spot
const PEDESTRIAN_ICON = divIcon({ className: "ped-signal", html: "<span></span>", iconSize: [8, 8] });

let cached: Promise<Signal[]> | null = null;
function loadSignals(): Promise<Signal[]> {
  cached ??= api
    .signals()
    .then((r) => r.signals)
    .catch(() => {
      cached = null;
      return [];
    });
  return cached;
}

function storedVisible(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== "0";
  } catch {
    return true;
  }
}

/** Signals within NEAR_METERS of any of the given points (grid-hashed). */
export function signalsNear(signals: Signal[], points: [number, number][]) {
  const cell = (lat: number, lng: number) =>
    `${Math.floor((lat * M_PER_LAT) / NEAR_METERS)},${Math.floor((lng * M_PER_LNG) / NEAR_METERS)}`;
  const grid = new Set(points.map(([lat, lng]) => cell(lat, lng)));
  return signals.filter(([lat, lng]) => {
    const cy = Math.floor((lat * M_PER_LAT) / NEAR_METERS);
    const cx = Math.floor((lng * M_PER_LNG) / NEAR_METERS);
    for (let dy = -1; dy <= 1; dy++)
      for (let dx = -1; dx <= 1; dx++) if (grid.has(`${cy + dy},${cx + dx}`)) return true;
    return false;
  });
}

/** Shared on/off state for the overlay, remembered per browser. */
export function useSignals() {
  const [signals, setSignals] = useState<Signal[]>([]);
  const [visible, setVisible] = useState(storedVisible);
  useEffect(() => {
    let cancelled = false;
    void loadSignals().then((s) => {
      if (!cancelled) setSignals(s);
    });
    return () => {
      cancelled = true;
    };
  }, []);
  const toggle = () =>
    setVisible((v) => {
      try {
        localStorage.setItem(STORAGE_KEY, v ? "0" : "1");
      } catch {
        // private mode — the toggle still works for this page view
      }
      return !v;
    });
  return { signals, visible, toggle };
}

export function SignalsLayer({ signals }: { signals: Signal[] }) {
  return (
    <>
      {signals.map(([lat, lng, kind], i) =>
        kind === 1 ? (
          <Marker key={i} position={[lat, lng]} icon={PEDESTRIAN_ICON} interactive={false} />
        ) : (
          <CircleMarker
            key={i}
            center={[lat, lng]}
            radius={3.5}
            interactive={false}
            pathOptions={{ color: RING_COLOR, weight: 1.5, opacity: 0.85, fill: false }}
          />
        )
      )}
    </>
  );
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

export function SignalsToggle({
  visible,
  signals,
  onToggle
}: {
  visible: boolean;
  signals: Signal[];
  onToggle: () => void;
}) {
  const crossings = signals.filter((s) => s[2] === 1).length;
  return (
    <button
      type="button"
      className={`signals-toggle${visible ? " on" : ""}`}
      onClick={onToggle}
      aria-pressed={visible}
      title="Traffic lights on these roads (OpenStreetMap): junctions and pedestrian crossings"
    >
      <span className="signals-ring" />
      {plural(signals.length - crossings, "junction")}
      {crossings > 0 ? (
        <>
          <span className="signals-diamond" />
          {plural(crossings, "crossing")}
        </>
      ) : null}
    </button>
  );
}
