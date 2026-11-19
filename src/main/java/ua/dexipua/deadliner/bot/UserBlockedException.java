package ua.dexipua.deadliner.bot;

/** Telegram answered 403: the user blocked the bot, so we must stop messaging them. */
public class UserBlockedException extends RuntimeException {

    private final long telegramId;

    public UserBlockedException(long telegramId) {
        super("User " + telegramId + " blocked the bot");
        this.telegramId = telegramId;
    }

    public long getTelegramId() {
        return telegramId;
    }
}
