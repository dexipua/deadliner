package ua.dexipua.deadliner.bot;

/** Fixed bot texts (Telegram HTML), in one place so they are easy to review and edit. */
final class Texts {

    static final String START = """
            👋 <b>Привіт! Я Deadliner.</b>

            Нагадую про дедлайни з Moodle KSE, щоб ти нічого не проґавив.

            Щоб почати, надішли мені посилання на свій календар Moodle. \
            Як його отримати — натисни кнопку нижче, це 1 хвилина.""";
    static final String HELP_BUTTON = "📖 Як отримати посилання";
    static final String HOW_TO_CONNECT = """
            📖 <b>Як підключити Moodle</b>

            1. Відкрий Moodle → <b>Календар</b> → <b>Експорт календаря</b>
            2. Обери <b>«Усі події»</b> і <b>«Нещодавні та наступні 60 днів»</b>
            3. Натисни <b>«Отримати URL календаря»</b> і скопіюй посилання
            4. Надішли його сюди

            🔒 <i>Посилання — це ключ до твого календаря Moodle (не пароль). \
            Я зберігаю його зашифрованим і одразу видаляю твоє повідомлення з чату. \
            /stop — видалити всі твої дані.</i>""";

    static final String NOT_A_LINK = "🤔 Не схоже на посилання з календаря Moodle KSE. Як його отримати — /help";
    static final String NOT_CONNECTED = "Спершу підключи календар Moodle — /help";
    static final String NOT_CONNECTED_SHORT = "Спершу підключи Moodle — /help";
    static final String FEED_UNREACHABLE = """
            😕 <b>Не вдалося прочитати календар</b>

            Можливо, посилання застаріло. Отримай нове в Moodle (/help) і надішли ще раз.""";
    static final String CONNECTED = "✅ <b>Готово, Moodle підключено!</b>\nДедлайнів попереду: <b>%d</b>";
    static final String NEAREST = "\n\nНайближчий:\n";
    static final String CONNECTED_FOOTER = """


            Нагадаю за 3 дні, за 1 день і за 3 години, а в неділю о 19:00 надішлю план на тиждень.
            Змінити — /settings""";
    static final String GROUP_HINT =
            "\n\n👥 Бачу квізи для різних груп. Обери свою групу в кожному курсі — /group, і я не нагадуватиму про чужі.";
    static final String GROUP_PICK =
            "👥 <b>Групи по курсах</b>\n\nОбери курс і вкажи свою групу — квізи інших груп я ховатиму.";
    static final String GROUP_COURSE = "👥 <b>%s</b>\n\nЯка твоя група в цьому курсі?";
    static final String GROUP_NONE = "👍 У твоїх курсах немає квізів по групах — нічого налаштовувати не треба.";
    static final String STATS_GROUP_HINT =
            "\n\n👥 Квізи чужих груп зараз рахуються як прострочені. Обери свої групи — /group";
    static final String GROUP_STALE = "Список курсів змінився — відкрий /group ще раз";

    static final String SETTINGS = "⚙️ <b>Налаштування</b>\n\nНатисни, щоб увімкнути або вимкнути:";
    static final String STOP_CONFIRM =
            "🗑 <b>Видалити всі твої дані?</b>\n\nПосилання, дедлайни й налаштування зникнуть назавжди.";
    static final String STOP_BUTTON = "Так, видалити";
    static final String STOP_CANCEL_BUTTON = "Скасувати";
    static final String STOPPED = "Готово, усі твої дані видалено. Щоб почати знову — /start";
    static final String STOP_CANCELLED = "👌 Добре, нічого не видаляю.";

    // toasts: short plain text, shown as a pop-up
    static final String SNOOZED = "⏰ Нагадаю ще раз о %s";
    static final String SNOOZE_TOO_LATE = "Дедлайн уже зовсім скоро — нагадувати пізно";
    static final String DONE = "✅ Здано: %s";
    static final String UNDONE = "↩️ Повернув у список: %s";
    static final String DEADLINE_NOT_FOUND = "Не знайшов цей дедлайн — можливо, його вже прибрали";
    static final String UNKNOWN_BUTTON = "Ця кнопка вже не працює";

    private Texts() {
    }
}
