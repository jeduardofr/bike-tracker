export interface Trip {
  uuid: string;
  startTime: number;
  endTime: number | null;
  distanceMeters: number;
  averageSpeedKmh: number;
  direction: "HOME_TO_OFFICE" | "OFFICE_TO_HOME" | "FREE" | string;
}

export interface RoutePoint {
  latitude: number;
  longitude: number;
  altitude: number;
  speedMps: number;
  timestamp: number;
  accuracy: number;
}

/** [lat, lng, speedMps] — compact trace point for the overview map */
export type TracePoint = [number, number, number];

export interface TripWeather {
  precipMm: number;
  tempC: number;
  windKmh: number;
}

export const RAINY_MM = 0.2;

export interface OverviewResponse {
  trips: Trip[];
  traces: Record<string, TracePoint[]>;
  weather: Record<string, TripWeather>;
}

export interface TripResponse {
  trip: Trip;
  points: RoutePoint[];
  /** DEM elevation (m) parallel to points; null where lookup failed */
  elevations: Array<number | null>;
  weather: TripWeather | null;
}

export interface GradeClass {
  medianKmh: number;
  minutes: number;
}

export interface GradeSummary {
  uphill: GradeClass;
  flat: GradeClass;
  downhill: GradeClass;
}

/** [lat, lng, medianKmh] */
export type SpeedBin = [number, number, number];

export interface ClimbMonth {
  month: string; // "2026-04"
  walkMin: number;
  rideMin: number;
}

export interface ClimbSpot {
  lat: number;
  lng: number;
  gradePct: number;
  walkMin: number;
  /** morning walk share (sweat strategy) — null when the spot is evening-only */
  amWalkPct: number | null;
  /** evening-only monthly trend */
  months: ClimbMonth[];
}

export interface PacingTarget {
  gradePct: number;
  dryKmh: number;
  pushKmh: number;
  currentKmh: number | null;
}

export interface Pacing {
  massKg: number;
  comfortWatts: number;
  flatKmh: number;
  targets: PacingTarget[];
  morning: { walkMinNow: number; dryMin: number; pushMin: number } | null;
}

export interface InsightsResponse {
  trips: Trip[];
  bins: SpeedBin[];
  medianKmh: number;
  gradeSummary: GradeSummary | null;
  climbs: ClimbSpot[];
  pacing: Pacing | null;
  tripWeather: Record<string, TripWeather>;
}
