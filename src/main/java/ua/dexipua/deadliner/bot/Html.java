package ua.dexipua.deadliner.bot;

/** Messages are sent with Telegram's HTML parse mode, so text from Moodle must be escaped. */
public final class Html {

    private Html() {
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
