import { useState } from "react";
import { api, type Deadline } from "./api";
import { useLoad } from "./App";
import { byDay, dayName, relative, time } from "./time";

export function DeadlinesPage({ onUnauthorized }: { onUnauthorized: () => void }) {
  const { data, setData, error, setError } = useLoad(api.deadlines, onUnauthorized);
  const [busy, setBusy] = useState<ReadonlySet<number>>(new Set());

  if (error) return <p className="notice">{error}</p>;
  if (!data) return <p className="muted">Завантаження…</p>;

  const toggle = async (d: Deadline) => {
    setBusy((b) => new Set(b).add(d.id));
    try {
      const updated = d.status === "DONE" ? await api.undo(d.id) : await api.done(d.id);
      // functional update: several toggles can be in flight, each must apply to the latest state
      setData((prev) =>
        prev && {
          // done overdue items leave the overdue list; everything else updates in place
          overdue: prev.overdue.filter((x) => x.id !== d.id || updated.status !== "DONE"),
          upcoming: prev.upcoming.map((x) => (x.id === d.id ? updated : x)),
        },
      );
    } catch (e) {
      setError(String((e as Error).message));
    } finally {
      setBusy((b) => {
        const next = new Set(b);
        next.delete(d.id);
        return next;
      });
    }
  };

  const open = data.upcoming.filter((d) => d.status === "OPEN").length;

  return (
    <div className="stack">
      <div className="page-head">
        <h1>Дедлайни</h1>
        <p className="muted">
          {open === 0 ? "Найближчі 14 днів вільні 🎉" : `Попереду ${open} — найближчі 14 днів`}
        </p>
      </div>

      {data.overdue.length > 0 && (
        <section className="group warn">
          <h2>Прострочені</h2>
          <p className="fine muted">Moodle не знає, що ти здав. Якщо здав — відміть, і в статистиці це буде «із запізненням».</p>
          <ul className="list">
            {data.overdue.map((d) => (
              <Row key={d.id} d={d} busy={busy.has(d.id)} onToggle={toggle} />
            ))}
          </ul>
        </section>
      )}

      {byDay(data.upcoming).map(([day, items]) => (
        <section className="group" key={day}>
          <h2>{dayName(day)}</h2>
          <ul className="list">
            {items.map((d) => (
              <Row key={d.id} d={d} busy={busy.has(d.id)} onToggle={toggle} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function Row({ d, busy, onToggle }: { d: Deadline; busy: boolean; onToggle: (d: Deadline) => void }) {
  const done = d.status === "DONE";
  return (
    <li className={done ? "row done" : "row"}>
      <button
        className={done ? "check on" : "check"}
        onClick={() => onToggle(d)}
        disabled={busy}
        aria-pressed={done}
        aria-label={done ? `Скасувати «здано»: ${d.title}` : `Позначити здане: ${d.title}`}
        title={done ? "Скасувати" : "Здано"}
      >
        {done ? "✓" : ""}
      </button>
      <div className="row-main">
        {d.course && <span className="course">{d.course}</span>}
        <span className="title">{d.title}</span>
      </div>
      <div className="row-time">
        <span className="clock">{time(d.dueAt)}</span>
        <span className="fine muted">{done ? "здано" : relative(d.dueAt)}</span>
      </div>
    </li>
  );
}
