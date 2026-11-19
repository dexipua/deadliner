package ua.dexipua.deadliner.bot;

import java.util.List;

/** Sends a message to a Telegram user. Throws {@link UserBlockedException} if they blocked the bot. */
public interface Notifier {

    void send(long telegramId, String text, List<InlineButton> buttons);
}
