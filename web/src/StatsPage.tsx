import { api, type Counts } from "./api";
import { useLoad } from "./App";

export function StatsPage({ onUnauthorized }: { onUnauthorized: () => void }) {
  const { data, error } = useLoad(api.stats, onUnauthorized);

  if (error) return <p className="notice">{error}</p>;
  if (!data) return <p className="muted">Завантаження…</p>;
  if (data.courses.length === 0) return <p className="muted">Поки що немає даних для статистики.</p>;

  const t = data.total;
  return (
    <div className="stack">
      <div className="page-head">
        <h1>Статистика</h1>
        <p className="muted">«Здано» — це те, що ти відмітив ✅ у боті чи тут.</p>
      </div>

      <section className="summary">
        <div className="percent">
          <span className="big-number">{t.onTimePercent ?? "—"}{t.onTimePercent !== null && "%"}</span>
          <span className="muted">вчасно здано</span>
          {t.onTimePercent !== null && <Bar percent={t.onTimePercent} />}
        </div>
        <dl className="tiles">
          <Tile label="Вчасно" value={t.onTime} kind="ok" />
          <Tile label="Із запізненням" value={t.late} kind="late" />
          <Tile label="Прострочено" value={t.overdue} kind="bad" />
          <Tile label="Попереду" value={t.upcoming} kind="next" />
        </dl>
      </section>

      {data.groupHint && (
        <p className="notice">
          Квізи чужих груп зараз рахуються як прострочені. Обери свої групи в <a href="#settings">налаштуваннях</a>.
        </p>
      )}

      <section className="group">
        <h2>По курсах</h2>
        <ul className="courses">
          {data.courses.map((c) => (
            <li key={c.course}>
              <div className="course-line">
                <strong>{c.course}</strong>
                <span className="muted">{c.counts.onTimePercent === null ? "ще нічого не здано" : `${c.counts.onTimePercent}%`}</span>
              </div>
              {c.counts.onTimePercent !== null && <Bar percent={c.counts.onTimePercent} />}
              <CountsLine c={c.counts} />
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function Bar({ percent }: { percent: number }) {
  return (
    <div className="bar" role="img" aria-label={`${percent}% вчасно`}>
      <div className="bar-fill" style={{ width: `${percent}%` }} />
    </div>
  );
}

function Tile({ label, value, kind }: { label: string; value: number; kind: string }) {
  return (
    <div className={`tile ${kind}`}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function CountsLine({ c }: { c: Counts }) {
  const parts = [
    c.onTime && `✅ ${c.onTime}`,
    c.late && `⏰ ${c.late}`,
    c.overdue && `❌ ${c.overdue}`,
    c.upcoming && `🔜 ${c.upcoming}`,
  ].filter(Boolean);
  return <p className="fine muted">{parts.join(" · ")}</p>;
}
