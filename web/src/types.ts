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

export interface OverviewResponse {
  trips: Trip[];
  traces: Record<string, TracePoint[]>;
}

export interface TripResponse {
  trip: Trip;
  points: RoutePoint[];
}

/** [lat, lng, medianKmh] */
export type SpeedBin = [number, number, number];

export interface InsightsResponse {
  trips: Trip[];
  bins: SpeedBin[];
  medianKmh: number;
}
