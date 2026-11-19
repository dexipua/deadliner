import { useEffect, useState } from "react";
import { api, Unauthorized } from "./api";
import { DeadlinesPage } from "./DeadlinesPage";
import { StatsPage } from "./StatsPage";
import { SettingsPage } from "./SettingsPage";
import { LoginPage } from "./LoginPage";

type Tab = "deadlines" | "stats" | "settings";
const TABS: { id: Tab; label: string }[] = [
  { id: "deadlines", label: "Дедлайни" },
  { id: "stats", label: "Статистика" },
  { id: "settings", label: "Налаштування" },
];

function tabFromHash(): Tab {
  const hash = window.location.hash.slice(1);
  return TABS.some((t) => t.id === hash) ? (hash as Tab) : "deadlines";
}

export function App() {
  const [state, setState] = useState<"loading" | "in" | "out">("loading");
  const [tab, setTab] = useState<Tab>(tabFromHash);

  useEffect(() => {
    api.me().then(() => setState("in"), (e) => setState(e instanceof Unauthorized ? "out" : "in"));
    const onHash = () => setTab(tabFromHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  const onUnauthorized = () => setState("out");

  if (state === "loading") return <div className="center muted">Завантаження…</div>;
  if (state === "out") return <LoginPage />;

  return (
    <div className="shell">
      <header className="top">
        <a className="brand" href="#deadlines">
          <span className="logo" aria-hidden>✓</span> Deadliner
        </a>
        <nav className="tabs" aria-label="Розділи">
          {TABS.map((t) => (
            <a key={t.id} href={`#${t.id}`} className={t.id === tab ? "tab active" : "tab"} aria-current={t.id === tab ? "page" : undefined}>
              {t.label}
            </a>
          ))}
        </nav>
        <button className="link" onClick={() => api.logout().finally(onUnauthorized)}>Вийти</button>
      </header>
      <main className="content">
        {tab === "deadlines" && <DeadlinesPage onUnauthorized={onUnauthorized} />}
        {tab === "stats" && <StatsPage onUnauthorized={onUnauthorized} />}
        {tab === "settings" && <SettingsPage onUnauthorized={onUnauthorized} />}
      </main>
    </div>
  );
}

/** Loads data for a page; switches to the login screen if the session ended. */
export function useLoad<T>(load: () => Promise<T>, onUnauthorized: () => void) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const reload = () => {
    setError(null);
    return load().then(setData, (e) => (e instanceof Unauthorized ? onUnauthorized() : setError(String(e.message ?? e))));
  };
  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return { data, setData, error, setError, reload };
}
