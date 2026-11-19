// All dates are shown in Kyiv time, like in the bot.
const ZONE = "Europe/Kyiv";

const dayKeyFormat = new Intl.DateTimeFormat("en-CA", { timeZone: ZONE, year: "numeric", month: "2-digit", day: "2-digit" });
const timeFormat = new Intl.DateTimeFormat("uk-UA", { timeZone: ZONE, hour: "2-digit", minute: "2-digit" });
const dayFormat = new Intl.DateTimeFormat("uk-UA", { timeZone: ZONE, weekday: "short", day: "numeric", month: "long" });

/** "2026-11-20" in Kyiv. */
export function dayKey(iso: string | Date): string {
  return dayKeyFormat.format(typeof iso === "string" ? new Date(iso) : iso);
}

export function time(iso: string): string {
  return timeFormat.format(new Date(iso));
}

/** "Сьогодні", "Завтра" or "пт, 20 листопада". */
export function dayName(key: string, now = new Date()): string {
  const today = dayKey(now);
  // from the Kyiv date itself, so DST days (23 or 25 hours) don't shift "tomorrow"
  const tomorrow = dayKey(new Date(Date.parse(`${today}T12:00:00Z`) + 24 * 3600 * 1000));
  if (key === today) return "Сьогодні";
  if (key === tomorrow) return "Завтра";
  const label = dayFormat.format(new Date(`${key}T12:00:00Z`));
  return label.charAt(0).toUpperCase() + label.slice(1);
}

function plural(n: number, one: string, few: string, many: string): string {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

/** "через 3 години", "2 дні тому". */
export function relative(iso: string, now = new Date()): string {
  const minutes = Math.round((new Date(iso).getTime() - now.getTime()) / 60000);
  const abs = Math.abs(minutes);
  let text: string;
  if (abs < 60) text = `${Math.max(abs, 1)} хв`;
  else if (abs < 24 * 60) {
    const h = Math.round(abs / 60);
    text = `${h} ${plural(h, "годину", "години", "годин")}`;
  } else {
    const d = Math.floor(abs / 60 / 24);
    text = `${d} ${plural(d, "день", "дні", "днів")}`;
  }
  return minutes >= 0 ? `через ${text}` : `${text} тому`;
}

/** Groups items by Kyiv day, keeping their order. */
export function byDay<T extends { dueAt: string }>(items: T[]): [string, T[]][] {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const key = dayKey(item.dueAt);
    groups.set(key, [...(groups.get(key) ?? []), item]);
  }
  return [...groups.entries()];
}
