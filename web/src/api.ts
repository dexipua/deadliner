// Thin client for the Spring Boot API. Session = HttpOnly cookie set by /auth; we never see it here.

export type Status = "OPEN" | "DONE" | "GONE";

export interface Deadline {
  id: number;
  course: string | null;
  title: string;
  dueAt: string; // ISO-8601 UTC
  status: Status;
}

export interface Deadlines {
  overdue: Deadline[];
  upcoming: Deadline[];
}

export interface Counts {
  onTime: number;
  late: number;
  overdue: number;
  upcoming: number;
  onTimePercent: number | null;
}

export interface Stats {
  total: Counts;
  courses: { course: string; counts: Counts }[];
  groupHint: boolean;
}

export interface CourseGroup {
  key: string;
  course: string;
  group: number | null;
}

export interface Me {
  quietHours: boolean;
  digestEnabled: boolean;
  offsets: number[];
  groups: CourseGroup[];
}

export class Unauthorized extends Error {}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== "GET") headers["X-Deadliner"] = "1"; // CSRF guard the server checks
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(path, {
    method,
    headers,
    credentials: "same-origin",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.status === 401) throw new Unauthorized();
  if (!response.ok) throw new Error(`Сервер відповів ${response.status}`);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const api = {
  info: () => call<{ botUsername: string }>("GET", "/public/info"),
  me: () => call<Me>("GET", "/api/me"),
  deadlines: () => call<Deadlines>("GET", "/api/deadlines"),
  done: (id: number) => call<Deadline>("POST", `/api/deadlines/${id}/done`),
  undo: (id: number) => call<Deadline>("POST", `/api/deadlines/${id}/undo`),
  stats: () => call<Stats>("GET", "/api/stats"),
  saveSettings: (s: { offsets: number[]; digestEnabled: boolean; quietHours: boolean }) =>
    call<Me>("PUT", "/api/settings", s),
  setGroup: (key: string, group: number | null) => call<Me>("PUT", `/api/groups/${key}`, { group }),
  logout: () => call<void>("POST", "/api/logout"),
};
