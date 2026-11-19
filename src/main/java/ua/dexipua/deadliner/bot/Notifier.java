package ua.dexipua.deadliner.bot;

import java.util.List;

/**
 * Sends an HTML message with rows of inline buttons to a Telegram user.
 * Throws {@link UserBlockedException} if they blocked the bot.
 */
public interface Notifier {

    void send(long telegramId, String html, List<List<InlineButton>> buttons);
}
