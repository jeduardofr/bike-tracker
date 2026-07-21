import type { Trip } from "../types";

// Commute-labeled trips shorter than this are university rides, not office
// commutes (the app only knows home/office; university shares the "office"
// buttons). Real office commutes measure 7.9–9.0 km; university 5.1–7.6 km —
// the gap sits at ~7.8 km. Adjust here if a ride lands on the wrong side.
export const UNIVERSITY_MAX_METERS = 7800;

export type TripCategory = "office" | "university" | "free";

export function tripCategory(t: Trip): TripCategory {
  if (t.direction === "FREE") return "free";
  return t.distanceMeters < UNIVERSITY_MAX_METERS ? "university" : "office";
}

export function tripLabel(t: Trip): string {
  const category = tripCategory(t);
  if (category === "free") return "Free ride";
  const place = category === "university" ? "University" : "Office";
  return t.direction === "HOME_TO_OFFICE" ? `Home → ${place}` : `${place} → Home`;
}
