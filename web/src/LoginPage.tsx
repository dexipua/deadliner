import { useEffect, useState } from "react";
import { api } from "./api";

export function LoginPage() {
  const [bot, setBot] = useState<string>("");
  const expired = new URLSearchParams(window.location.search).get("login") === "expired";

  useEffect(() => {
    api.info().then((i) => setBot(i.botUsername), () => setBot(""));
  }, []);

  return (
    <div className="login">
      <div className="login-card">
        <div className="logo big" aria-hidden>✓</div>
        <h1>Deadliner</h1>
        <p className="muted">Дедлайни з Moodle KSE — нагадування в Telegram і все під рукою тут.</p>
        {expired && <p className="notice">Посилання вже використане або застаріло. Отримай нове — це 5 секунд.</p>}
        <ol className="steps">
          <li>Напиши боту команду <code>/web</code></li>
          <li>Відкрий посилання, яке він надішле</li>
        </ol>
        {bot && (
          <a className="button primary" href={`https://t.me/${bot}`} target="_blank" rel="noreferrer">
            Відкрити @{bot}
          </a>
        )}
        <p className="fine muted">Без паролів: посилання одноразове й діє 10 хвилин.</p>
        <p className="fine muted">Відкрилось усередині Telegram? Натисни ⋮ → «Відкрити в браузері», щоб лишитися в системі.</p>
      </div>
    </div>
  );
}
