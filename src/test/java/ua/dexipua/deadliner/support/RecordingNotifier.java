package ua.dexipua.deadliner.support;

import ua.dexipua.deadliner.bot.InlineButton;
import ua.dexipua.deadliner.bot.Notifier;
import ua.dexipua.deadliner.bot.UserBlockedException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Test double: records messages instead of calling Telegram. */
public class RecordingNotifier implements Notifier {

    public record Sent(long telegramId, String text, List<InlineButton> buttons) {
    }

    public final List<Sent> sent = new ArrayList<>();
    public final Set<Long> blocked = new HashSet<>();

    @Override
    public void send(long telegramId, String text, List<InlineButton> buttons) {
        if (blocked.contains(telegramId)) {
            throw new UserBlockedException(telegramId);
        }
        sent.add(new Sent(telegramId, text, buttons));
    }

    public void reset() {
        sent.clear();
        blocked.clear();
    }
}
