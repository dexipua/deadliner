package ua.dexipua.deadliner.support;

import ua.dexipua.deadliner.bot.InlineButton;
import ua.dexipua.deadliner.bot.Notifier;
import ua.dexipua.deadliner.bot.UserBlockedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Test double: records messages instead of calling Telegram. */
public class RecordingNotifier implements Notifier {

    public record Sent(long telegramId, String text, List<List<InlineButton>> buttons) {
    }

    public final List<Sent> sent = new ArrayList<>();
    public final Set<Long> blocked = new HashSet<>();
    /** Simulates a Telegram outage or any other non-403 error for these users. */
    public final Set<Long> failing = new HashSet<>();
    /** Runs before each send; lets a test change the world mid-job (e.g. the user hits /stop). */
    public Consumer<Long> beforeSend = id -> { };

    @Override
    public void send(long telegramId, String text, List<List<InlineButton>> buttons) {
        beforeSend.accept(telegramId);
        if (failing.contains(telegramId)) {
            throw new IllegalStateException("Telegram is down");
        }
        if (blocked.contains(telegramId)) {
            throw new UserBlockedException(telegramId);
        }
        sent.add(new Sent(telegramId, text, buttons));
    }

    public void reset() {
        sent.clear();
        blocked.clear();
        failing.clear();
        beforeSend = id -> { };
    }
}
