import type { HistoryResponse, InsightsResponse, OverviewResponse, TripResponse } from "./types";

class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(path, { credentials: "same-origin" });
  if (res.status === 401) {
    window.location.href = "/login";
    throw new ApiError(401, "unauthorized");
  }
  if (!res.ok) throw new ApiError(res.status, `request failed: ${res.status}`);
  return res.json() as Promise<T>;
}

export const api = {
  me: () => get<{ authenticated: boolean }>("/api/me"),
  login: async (password: string): Promise<boolean> => {
    const res = await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
      credentials: "same-origin"
    });
    return res.ok;
  },
  overview: (from: number, to: number) =>
    get<OverviewResponse>(`/api/overview?from=${from}&to=${to}`),
  insights: () => get<InsightsResponse>("/api/insights"),
  history: (before?: number, limit = 20) =>
    get<HistoryResponse>(
      `/api/history?limit=${limit}${before !== undefined ? `&before=${before}` : ""}`
    ),
  trip: (uuid: string) => get<TripResponse>(`/api/trip/${encodeURIComponent(uuid)}`)
};
