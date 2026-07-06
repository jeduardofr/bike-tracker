import type { RoutePoint } from "../types";

// Mirrors the Android app: RouteSegments.kt + ComputeRouteMetricsUseCase.kt
export const RIDING_THRESHOLD_MPS = 1.94;
export const MAX_ACCURACY_M = 50;

export interface Segment {
  points: RoutePoint[];
  isRiding: boolean;
}

export function filterAccurate(points: RoutePoint[]): RoutePoint[] {
  return points.filter((p) => p.accuracy <= MAX_ACCURACY_M || p.accuracy === 0);
}

export function buildSegments(points: RoutePoint[]): Segment[] {
  if (points.length < 2) return [];
  const segments: Segment[] = [];
  let current: RoutePoint[] = [points[0]];
  let currentIsRiding = points[0].speedMps > RIDING_THRESHOLD_MPS;

  for (let i = 1; i < points.length; i++) {
    const isRiding = points[i].speedMps > RIDING_THRESHOLD_MPS;
    if (isRiding !== currentIsRiding) {
      current.push(points[i]); // overlap for visual continuity
      segments.push({ points: current, isRiding: currentIsRiding });
      current = [points[i]];
      currentIsRiding = isRiding;
    } else {
      current.push(points[i]);
    }
  }
  if (current.length >= 2) segments.push({ points: current, isRiding: currentIsRiding });
  return segments;
}

export function haversineMeters(a: RoutePoint, b: RoutePoint): number {
  const r = 6_371_000;
  const lat1 = (a.latitude * Math.PI) / 180;
  const lat2 = (b.latitude * Math.PI) / 180;
  const dLat = ((b.latitude - a.latitude) * Math.PI) / 180;
  const dLng = ((b.longitude - a.longitude) * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

export interface TripMetrics {
  distanceMeters: number;
  durationSeconds: number;
  avgSpeedKmh: number;
  maxSpeedKmh: number;
  movingSeconds: number;
  stoppedSeconds: number;
  rideDistanceMeters: number;
  walkDistanceMeters: number;
  rideSeconds: number;
  walkSeconds: number;
  elevationGainMeters: number;
}

export function computeMetrics(rawPoints: RoutePoint[]): TripMetrics | null {
  const points = filterAccurate(rawPoints);
  if (points.length < 2) return null;

  let distance = 0;
  let maxSpeedMps = 0;
  let movingSeconds = 0;
  let rideDistance = 0;
  let walkDistance = 0;
  let rideSeconds = 0;
  let walkSeconds = 0;
  let elevationGain = 0;
  // smooth altitude (5-point window) — raw GPS altitude is far too jittery for climb totals
  const altitude = points.map((_, i) => {
    let sum = 0;
    let n = 0;
    for (let j = Math.max(0, i - 2); j <= Math.min(points.length - 1, i + 2); j++) {
      sum += points[j].altitude;
      n++;
    }
    return sum / n;
  });
  let lastAltitude = altitude[0];

  for (let i = 1; i < points.length; i++) {
    const prev = points[i - 1];
    const cur = points[i];
    const d = haversineMeters(prev, cur);
    const dt = (cur.timestamp - prev.timestamp) / 1000;
    distance += d;
    if (cur.speedMps > maxSpeedMps) maxSpeedMps = cur.speedMps;

    const moving = cur.speedMps > 0.5;
    if (moving && dt > 0 && dt < 120) movingSeconds += dt;

    if (cur.speedMps > RIDING_THRESHOLD_MPS) {
      rideDistance += d;
      if (dt > 0 && dt < 120) rideSeconds += dt;
    } else {
      walkDistance += d;
      if (dt > 0 && dt < 120) walkSeconds += dt;
    }

    // 5m hysteresis so GPS altitude noise doesn't inflate the climb
    const dAlt = altitude[i] - lastAltitude;
    if (Math.abs(dAlt) >= 5) {
      if (dAlt > 0) elevationGain += dAlt;
      lastAltitude = altitude[i];
    }
  }

  const durationSeconds = (points[points.length - 1].timestamp - points[0].timestamp) / 1000;
  const avgSpeedKmh = durationSeconds > 0 ? (distance / durationSeconds) * 3.6 : 0;

  return {
    distanceMeters: distance,
    durationSeconds,
    avgSpeedKmh,
    maxSpeedKmh: maxSpeedMps * 3.6,
    movingSeconds,
    stoppedSeconds: Math.max(0, durationSeconds - movingSeconds),
    rideDistanceMeters: rideDistance,
    walkDistanceMeters: walkDistance,
    rideSeconds,
    walkSeconds,
    elevationGainMeters: elevationGain
  };
}
