import { useMemo } from "react";
import { tripCategory } from "../lib/category";
import { RAINY_MM, type Trip, type TripWeather } from "../types";

const durationMin = (t: Trip) => (t.endTime ? (t.endTime - t.startTime) / 60000 : 0);

function median(values: number[]): number {
  const s = [...values].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 === 0 ? (s[m - 1] + s[m]) / 2 : s[m];
}

interface Props {
  trips: Trip[];
  weather: Record<string, TripWeather>;
}

export default function WeatherCard({ trips, weather }: Props) {
  const stats = useMemo(() => {
    const evenings = trips.filter(
      (t) =>
        t.direction === "OFFICE_TO_HOME" &&
        t.endTime !== null &&
        tripCategory(t) === "office" &&
        weather[t.uuid] !== undefined
    );
    if (evenings.length < 10) return null;
    const rainy = evenings.filter((t) => weather[t.uuid].precipMm >= RAINY_MM);
    const dry = evenings.filter((t) => weather[t.uuid].precipMm < RAINY_MM);
    const heavy = rainy.filter((t) => weather[t.uuid].precipMm >= 1);
    const cool = dry.filter((t) => weather[t.uuid].tempC < 26);
    const hot = dry.filter((t) => weather[t.uuid].tempC >= 29);
    return {
      dry: { n: dry.length, med: median(dry.map(durationMin)) },
      rainy: rainy.length >= 3 ? { n: rainy.length, med: median(rainy.map(durationMin)) } : null,
      heavy: heavy.length >= 2 ? { n: heavy.length, med: median(heavy.map(durationMin)) } : null,
      cool: cool.length >= 3 ? { n: cool.length, med: median(cool.map(durationMin)) } : null,
      hot: hot.length >= 3 ? { n: hot.length, med: median(hot.map(durationMin)) } : null
    };
  }, [trips, weather]);

  const row = (label: string, s: { n: number; med: number } | null) =>
    s === null ? null : (
      <div style={{ display: "flex", gap: 10, padding: "5px 0", fontSize: 13 }}>
        <span style={{ width: 210 }}>{label}</span>
        <strong style={{ width: 80 }}>{s.med.toFixed(1)} min</strong>
        <span style={{ color: "var(--muted)" }}>{s.n} rides</span>
      </div>
    );

  return stats === null ? null : (
    <div className="card">
      <h2>Weather & the ride home</h2>
      <div style={{ fontSize: 13, color: "var(--muted)", margin: "4px 0 8px" }}>
        Median evening commute by conditions at departure (Open-Meteo, one model point for the
        corridor — hyper-local downpours may read low).
      </div>
      {row("Dry", stats.dry)}
      {row(`Rain (≥${RAINY_MM} mm/h)`, stats.rainy)}
      {row("Heavy rain (≥1 mm/h)", stats.heavy)}
      {row("Dry & cool (<26°C)", stats.cool)}
      {row("Dry & hot (≥29°C)", stats.hot)}
    </div>
  );
}
