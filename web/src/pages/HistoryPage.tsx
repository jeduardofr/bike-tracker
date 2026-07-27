import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import TripRowItem from "../components/TripRowItem";
import { isoDay } from "../lib/format";
import type { Trip, TripWeather } from "../types";

const PAGE_SIZE = 20;

function dayHeader(ts: number): string {
  const d = new Date(ts);
  const opts: Intl.DateTimeFormatOptions = { weekday: "short", month: "short", day: "numeric" };
  if (d.getFullYear() !== new Date().getFullYear()) opts.year = "numeric";
  return d.toLocaleDateString([], opts);
}

export default function HistoryPage() {
  const [trips, setTrips] = useState<Trip[]>([]);
  const [weather, setWeather] = useState<Record<string, TripWeather>>({});
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);

  const loadMore = async (before?: number) => {
    setLoading(true);
    try {
      const res = await api.history(before, PAGE_SIZE);
      setTrips((prev) => [...prev, ...res.trips]);
      setWeather((prev) => ({ ...prev, ...res.weather }));
      setHasMore(res.hasMore);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadMore();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  let lastDay = "";
  return (
    <div className="container">
      <div className="topbar">
        <div>
          <Link to="/" className="back-link">
            ← Back
          </Link>
          <h1>History</h1>
        </div>
      </div>

      {trips.length === 0 && loading ? <div className="loading">Loading…</div> : null}

      <div className="trip-list">
        {trips.map((t) => {
          const day = isoDay(new Date(t.startTime));
          const header = day !== lastDay ? <div className="trip-day-header">{dayHeader(t.startTime)}</div> : null;
          lastDay = day;
          return (
            <div key={t.uuid}>
              {header}
              <TripRowItem trip={t} weather={weather[t.uuid]} />
            </div>
          );
        })}
      </div>

      {trips.length > 0 ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "12px 0 24px" }}>
          {hasMore ? (
            <button
              disabled={loading}
              onClick={() => loadMore(trips[trips.length - 1].startTime)}
            >
              {loading ? "Loading…" : "Load older rides"}
            </button>
          ) : (
            <span style={{ color: "var(--muted)", fontSize: 13 }}>
              That&rsquo;s every ride — {trips.length} total.
            </span>
          )}
        </div>
      ) : null}
    </div>
  );
}
