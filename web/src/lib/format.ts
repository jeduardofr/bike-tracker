export function formatKm(meters: number): string {
  return `${(meters / 1000).toFixed(1)}`;
}

export function formatDuration(seconds: number): string {
  const s = Math.round(seconds);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export function formatTime(ts: number): string {
  return new Date(ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });
}

export function formatDay(ts: number): string {
  return new Date(ts).toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" });
}

/** Monday 00:00 of the week containing `date` (local time). */
export function mondayOf(date: Date): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const shift = (d.getDay() + 6) % 7; // Mon=0 … Sun=6
  d.setDate(d.getDate() - shift);
  return d;
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function windCardinal(fromDeg: number): string {
  const dirs = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  return dirs[Math.round(((fromDeg % 360) + 360) % 360 / 45) % 8];
}

export function isoDay(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}
