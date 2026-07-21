// Validated against the dark surface with the dataviz six-checks
// (lightness band, chroma, CVD separation, contrast). Hue identities
// match the Android app's weekday map colors.
export const WEEKDAY_COLORS: Record<number, string> = {
  1: "#43A047", // Mon green
  2: "#448AFF", // Tue blue
  3: "#C08A00", // Wed amber
  4: "#C62BE0", // Thu magenta
  5: "#0097A7", // Fri teal
  6: "#8A8F98", // Sat
  0: "#8A8F98" // Sun
};

export const RIDE_COLOR = "#43A047";
export const WALK_COLOR = "#9575CD";

// Insights charts: direction series (validated pair)
export const TO_OFFICE_COLOR = "#43A047";
export const TO_HOME_COLOR = "#448AFF";
// University rides: violet + hollow-ring marks (shape carries identity for CVD)
export const UNIVERSITY_COLOR = "#9575CD";

// Speed-map diverging ramp: slow ↔ median ↔ fast (validated poles + neutral mid)
export const SLOW_COLOR = "#D9700F";
export const NEUTRAL_COLOR = "#8A8F98";
export const FAST_COLOR = "#4287D6";

function hexToRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function mix(a: string, b: string, t: number): string {
  const ca = hexToRgb(a);
  const cb = hexToRgb(b);
  const c = ca.map((v, i) => Math.round(v + (cb[i] - v) * t));
  return `rgb(${c[0]},${c[1]},${c[2]})`;
}

/** t in [-1, 1]: negative = slower than median, positive = faster. */
export function speedRampColor(t: number): string {
  const clamped = Math.max(-1, Math.min(1, t));
  return clamped < 0 ? mix(NEUTRAL_COLOR, SLOW_COLOR, -clamped) : mix(NEUTRAL_COLOR, FAST_COLOR, clamped);
}

export function weekdayColor(date: Date): string {
  return WEEKDAY_COLORS[date.getDay()] ?? "#8A8F98";
}

export function directionLabel(direction: string): string {
  switch (direction) {
    case "HOME_TO_OFFICE":
      return "Home → Office";
    case "OFFICE_TO_HOME":
      return "Office → Home";
    default:
      return "Free ride";
  }
}
