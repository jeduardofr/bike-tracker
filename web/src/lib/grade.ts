import { FAST_COLOR, NEUTRAL_COLOR, SLOW_COLOR } from "./colors";

// Shared by the elevation profile chart and the grade-colored map overlay
export const GRADE_CUTOFF = 0.015; // ±1.5% separates climb/flat/descent
export const GRADE_WINDOW_M = 100; // grade measured over a trailing ~100 m window

export const gradeColor = (g: number): string =>
  g > GRADE_CUTOFF ? SLOW_COLOR : g < -GRADE_CUTOFF ? FAST_COLOR : NEUTRAL_COLOR;
