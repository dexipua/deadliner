package ua.dexipua.deadliner.bot;

import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.DeadlineStatus;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.feed.FeedSyncService;
import ua.dexipua.deadliner.feed.FeedSyncService.ConnectResult;
import ua.dexipua.deadliner.feed.FeedUrlValidator;
import ua.dexipua.deadliner.reminder.MessageFormatter;
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Bot logic without Telegram types, so it can be tested against a real database. */
@Component
public class CommandHandler {

    private static final Duration WEEK = Duration.ofDays(7);
    private static final String STOP_CONFIRMED = "stop:confirm";

    private final FeedSyncService feedSync;
    private final AppUserRepository users;
    private final DeadlineRepository deadlines;
    private final ReminderSettings settings;
    private final MessageFormatter formatter;
    private final Clock clock;

    public CommandHandler(FeedSyncService feedSync, AppUserRepository users, DeadlineRepository deadlines,
                          ReminderSettings settings, MessageFormatter formatter, Clock clock) {
        this.feedSync = feedSync;
        this.users = users;
        this.deadlines = deadlines;
        this.settings = settings;
        this.formatter = formatter;
        this.clock = clock;
    }

    public BotReply handle(long telegramId, String text) {
        reactivate(telegramId);
        String command = text == null ? "" : text.strip();
        if (command.equals("/group") || command.startsWith("/group ")) {
            return groupCommand(telegramId, command.substring("/group".length()).strip());
        }
        return switch (command) {
            case "/start" -> BotReply.of(Texts.START);
            case "/deadlines" -> deadlinesCommand(telegramId);
            case "/settings" -> user(telegramId).map(this::settingsReply).orElse(BotReply.of(Texts.NOT_CONNECTED));
            case "/stop" -> BotReply.withButtons(Texts.STOP_CONFIRM,
                    List.of(new InlineButton(Texts.STOP_BUTTON, STOP_CONFIRMED)));
            default -> FeedUrlValidator.normalize(command).isPresent()
                    ? connect(telegramId, command)
                    : new BotReply(Texts.NOT_A_LINK, List.of(), containsToken(command));
        };
    }

    public BotReply handleCallback(long telegramId, String data) {
        Optional<AppUser> user = user(telegramId);
        if (user.isEmpty() || data == null) {
            return BotReply.of(Texts.NOT_CONNECTED);
        }
        if (data.equals(STOP_CONFIRMED)) {
            users.delete(user.get());
            return BotReply.of(Texts.STOPPED);
        }
        if (data.startsWith("done:")) {
            return markDone(user.get(), data.substring("done:".length()));
        }
        if (data.startsWith("offset:")) {
            settings.toggle(user.get().getId(), Integer.parseInt(data.substring("offset:".length())));
            return settingsReply(user.get());
        }
        if (data.equals("digest")) {
            user.get().setDigestEnabled(!user.get().isDigestEnabled());
            users.save(user.get());
            return settingsReply(user.get());
        }
        return BotReply.of(Texts.NOT_A_LINK);
    }

    private BotReply connect(long telegramId, String link) {
        ConnectResult result = feedSync.connect(telegramId, link);
        String text = switch (result) {
            case ConnectResult.Connected c -> Texts.CONNECTED.formatted(c.count())
                    + c.nearest().map(d -> Texts.NEAREST.formatted(formatter.line(d))).orElse("")
                    + Texts.CONNECTED_FOOTER
                    + (hasGroupQuizzes(telegramId) ? Texts.GROUP_HINT : "");
            case ConnectResult.FeedUnreachable u -> Texts.FEED_UNREACHABLE;
            case ConnectResult.InvalidUrl i -> Texts.NOT_A_LINK;
        };
        return new BotReply(text, List.of(), true);
    }

    private BotReply deadlinesCommand(long telegramId) {
        Optional<AppUser> user = user(telegramId);
        if (user.isEmpty()) {
            return BotReply.of(Texts.NOT_CONNECTED);
        }
        Instant now = clock.instant();
        return BotReply.of(formatter.list(deadlines.findOpenDueBetween(user.get().getId(), now, now.plus(WEEK)).stream()
                .filter(d -> GroupFilter.visible(d, user.get().getSeminarGroup()))
                .toList()));
    }

    private BotReply groupCommand(long telegramId, String argument) {
        Optional<AppUser> user = user(telegramId);
        if (user.isEmpty()) {
            return BotReply.of(Texts.NOT_CONNECTED);
        }
        if (argument.isEmpty()) {
            user.get().setSeminarGroup(null);
            users.save(user.get());
            return BotReply.of(Texts.GROUP_CLEARED);
        }
        if (!argument.matches("\\d{1,3}")) {
            return BotReply.of(Texts.GROUP_USAGE);
        }
        int group = Integer.parseInt(argument);
        user.get().setSeminarGroup(group);
        users.save(user.get());
        return BotReply.of(Texts.GROUP_SET.formatted(group));
    }

    private boolean hasGroupQuizzes(long telegramId) {
        return user(telegramId)
                .map(u -> deadlines.findByUserId(u.getId()).stream().anyMatch(GroupFilter::mentionsGroup))
                .orElse(false);
    }

    private BotReply markDone(AppUser user, String rawId) {
        Optional<Deadline> deadline;
        try {
            deadline = deadlines.findById(Long.parseLong(rawId));
        } catch (NumberFormatException e) {
            deadline = Optional.empty();
        }
        // only the owner may mark it, whatever id the callback carries
        if (deadline.isEmpty() || deadline.get().getUserId() != user.getId()) {
            return BotReply.of(Texts.DEADLINE_NOT_FOUND);
        }
        Deadline d = deadline.get();
        if (d.getStatus() != DeadlineStatus.DONE) {
            d.markDone(clock.instant());
            deadlines.save(d);
        }
        return BotReply.of(Texts.DONE.formatted(formatter.label(d)));
    }

    private BotReply settingsReply(AppUser user) {
        Set<Integer> on = settings.offsets(user.getId());
        List<InlineButton> buttons = new ArrayList<>();
        buttons.add(toggle("За 3 дні", on.contains(4320), "offset:4320"));
        buttons.add(toggle("За 1 день", on.contains(1440), "offset:1440"));
        buttons.add(toggle("За 3 години", on.contains(180), "offset:180"));
        buttons.add(toggle("План на тиждень (нд 19:00)", user.isDigestEnabled(), "digest"));
        return BotReply.withButtons(Texts.SETTINGS, buttons);
    }

    private static InlineButton toggle(String label, boolean on, String data) {
        return new InlineButton((on ? "✅ " : "❌ ") + label, data);
    }

    /** Any text with a Moodle token must not stay in the chat, even if it is not a valid link. */
    public static boolean containsToken(String text) {
        return text != null && text.contains("authtoken=");
    }

    private Optional<AppUser> user(long telegramId) {
        return users.findByTelegramId(telegramId);
    }

    // a user who blocked and then wrote again wants messages again
    private void reactivate(long telegramId) {
        user(telegramId).filter(u -> !u.isActive()).ifPresent(u -> {
            u.setActive(true);
            users.save(u);
        });
    }
}
