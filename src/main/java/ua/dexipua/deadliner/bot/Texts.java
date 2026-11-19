package ua.dexipua.deadliner.bot;

/** Fixed bot texts, in one place so they are easy to review and edit. */
final class Texts {

    static final String START = """
            👋 Привіт! Я Deadliner — нагадую про дедлайни з Moodle KSE.

            Як підключити (1 хвилина):
            1. Відкрий Moodle → Календар → «Експорт календаря».
            2. Обери «Усі події» та «Нещодавні та наступні 60 днів».
            3. Натисни «Отримати URL календаря» і скопіюй посилання.
            4. Надішли його сюди.

            🔒 Приватність: посилання містить твій особистий ключ доступу до календаря Moodle (не пароль). \
            Я зберігаю його зашифрованим, видаляю твоє повідомлення з ним із чату і бачу лише дедлайни. \
            /stop — видалити всі твої дані.

            Команди: /deadlines — дедлайни на 7 днів, /group — твоя група, /stats — статистика, /settings — нагадування, /stop — видалити дані.""";

    static final String NOT_A_LINK =
            "Не схоже на посилання з календаря Moodle KSE 🤔 Як його отримати — дивись /start.";
    static final String NOT_CONNECTED = "Спершу підключи календар Moodle — інструкція в /start.";
    static final String FEED_UNREACHABLE = """
            😕 Не вдалося прочитати календар за цим посиланням. Можливо, воно застаріло.
            Отримай нове посилання в Moodle (інструкція в /start) і надішли ще раз.""";
    static final String CONNECTED = "✅ Підключено! Знайшов дедлайнів попереду: %d.";
    static final String NEAREST = "\nНайближчий: %s";
    static final String CONNECTED_FOOTER =
            "\n\nНагадаю за 3 дні, за 1 день і за 3 години, а в неділю о 19:00 надішлю план на тиждень. Змінити — /settings.";
    static final String STOP_CONFIRM = "Видалити всі твої дані (посилання, дедлайни, налаштування)? Це не можна скасувати.";
    static final String STOP_BUTTON = "🗑 Так, видалити";
    static final String STOPPED = "Готово, усі твої дані видалено. Щоб почати знову — /start.";
    static final String DONE = "✅ Позначено як здане: %s";
    static final String DEADLINE_NOT_FOUND = "Не знайшов цей дедлайн — можливо, його вже прибрали.";
    static final String GROUP_HINT =
            "\n\n👥 Бачу квізи для різних груп. Напиши /group 5 (свій номер), і я не нагадуватиму про чужі.";
    static final String GROUP_SET = "👥 Готово: показую квізи лише групи %d. Скинути — /group";
    static final String GROUP_CLEARED = "👥 Показую квізи всіх груп.";
    static final String GROUP_USAGE = "Напиши номер своєї групи, наприклад: /group 5";
    static final String SETTINGS = "⚙️ Налаштування. Натисни, щоб увімкнути або вимкнути:";

    private Texts() {
    }
}
