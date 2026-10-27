import { useEffect, useState } from "react";
import { CircleMarker } from "react-leaflet";
import { api } from "../api";

// Traffic signals (OpenStreetMap) as a reference overlay: hollow rings so they read as
// map furniture, distinct by shape from the filled route/speed marks underneath.
const RING_COLOR = "#E6E8EB";
const STORAGE_KEY = "bike.showSignals";
// a signal counts as "on the route" within this distance of any drawn point
const NEAR_METERS = 30;
const M_PER_LAT = 111_000;
const M_PER_LNG = 111_000 * Math.cos((20.69 * Math.PI) / 180);

let cached: Promise<[number, number][]> | null = null;
function loadSignals(): Promise<[number, number][]> {
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
export function signalsNear(signals: [number, number][], points: [number, number][]) {
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
  const [signals, setSignals] = useState<[number, number][]>([]);
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

export function SignalsLayer({ signals }: { signals: [number, number][] }) {
  return (
    <>
      {signals.map((p, i) => (
        <CircleMarker
          key={i}
          center={p}
          radius={3.5}
          interactive={false}
          pathOptions={{ color: RING_COLOR, weight: 1.5, opacity: 0.85, fill: false }}
        />
      ))}
    </>
  );
}

export function SignalsToggle({
  visible,
  count,
  onToggle
}: {
  visible: boolean;
  count: number;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      className={`signals-toggle${visible ? " on" : ""}`}
      onClick={onToggle}
      aria-pressed={visible}
      title="Traffic signals on these roads (OpenStreetMap)"
    >
      <span className="signals-ring" />
      {count} signals
    </button>
  );
}
