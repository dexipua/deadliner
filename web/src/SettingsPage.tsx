import { useState } from "react";
import { api, type Me } from "./api";
import { useLoad } from "./App";

const OFFSETS = [
  { minutes: 4320, label: "За 3 дні" },
  { minutes: 1440, label: "За 1 день" },
  { minutes: 180, label: "За 3 години" },
];

export function SettingsPage({ onUnauthorized }: { onUnauthorized: () => void }) {
  const { data, setData, error, setError } = useLoad(api.me, onUnauthorized);
  const [saved, setSaved] = useState(false);

  if (error) return <p className="notice">{error}</p>;
  if (!data) return <p className="muted">Завантаження…</p>;

  const save = async (next: Me) => {
    setData(next); // feels instant; the server answer replaces it
    try {
      setData(await api.saveSettings({ offsets: next.offsets, digestEnabled: next.digestEnabled, quietHours: next.quietHours }));
      setSaved(true);
      window.setTimeout(() => setSaved(false), 1500);
    } catch (e) {
      setError(String((e as Error).message));
    }
  };

  const toggleOffset = (minutes: number) =>
    save({
      ...data,
      offsets: data.offsets.includes(minutes) ? data.offsets.filter((m) => m !== minutes) : [...data.offsets, minutes],
    });

  const setGroup = async (key: string, value: string) => {
    try {
      setData(await api.setGroup(key, value === "" ? null : Number(value)));
    } catch (e) {
      setError(String((e as Error).message));
    }
  };

  return (
    <div className="stack">
      <div className="page-head">
        <h1>Налаштування</h1>
        <p className="muted" aria-live="polite">{saved ? "Збережено ✓" : "Зміни зберігаються одразу."}</p>
      </div>

      <section className="group">
        <h2>Нагадування</h2>
        {OFFSETS.map((o) => (
          <Switch key={o.minutes} id={`offset-${o.minutes}`} label={o.label} checked={data.offsets.includes(o.minutes)} onChange={() => toggleOffset(o.minutes)} />
        ))}
        <Switch id="digest" label="План на тиждень (неділя, 19:00)" checked={data.digestEnabled} onChange={() => save({ ...data, digestEnabled: !data.digestEnabled })} />
        <Switch
          id="quiet"
          label="Тихі години 23:00–08:00"
          hint="Нічні нагадування прийдуть о 08:00, якщо дедлайн не раніше."
          checked={data.quietHours}
          onChange={() => save({ ...data, quietHours: !data.quietHours })}
        />
      </section>

      <section className="group">
        <h2>Групи по курсах</h2>
        {data.groups.length === 0 ? (
          <p className="muted">У твоїх курсах немає квізів по групах — нічого налаштовувати не треба.</p>
        ) : (
          <>
            <p className="fine muted">Квізи інших груп буде сховано в нагадуваннях, списку і статистиці.</p>
            {data.groups.map((g) => (
              <label className="setting" key={g.key} htmlFor={`group-${g.key}`}>
                <span>{g.course}</span>
                <select id={`group-${g.key}`} value={g.group ?? ""} onChange={(e) => setGroup(g.key, e.target.value)}>
                  <option value="">Усі групи</option>
                  {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>
                      Група {n}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </>
        )}
      </section>
    </div>
  );
}

function Switch({ id, label, hint, checked, onChange }: { id: string; label: string; hint?: string; checked: boolean; onChange: () => void }) {
  return (
    <label className="setting" htmlFor={id}>
      <span>
        {label}
        {hint && <span className="fine muted block">{hint}</span>}
      </span>
      <input id={id} type="checkbox" role="switch" className="switch" checked={checked} onChange={onChange} />
    </label>
  );
}
