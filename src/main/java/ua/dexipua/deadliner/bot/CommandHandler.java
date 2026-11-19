package ua.dexipua.deadliner.bot;

import org.springframework.stereotype.Component;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.DeadlineStatus;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.deadline.Stats;
import ua.dexipua.deadliner.feed.FeedSyncService;
import ua.dexipua.deadliner.feed.FeedSyncService.ConnectResult;
import ua.dexipua.deadliner.feed.FeedUrlValidator;
import ua.dexipua.deadliner.reminder.MessageFormatter;
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.reminder.Snoozes;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Bot logic without Telegram types, so it can be tested against a real database. */
@Component
public class CommandHandler {

    private static final Duration WEEK = Duration.ofDays(7);
    private static final String HELP_LINK = "help:link";
    private static final String STOP_CONFIRMED = "stop:confirm";
    private static final String STOP_CANCELLED = "stop:cancel";

    private final FeedSyncService feedSync;
    private final AppUserRepository users;
    private final DeadlineRepository deadlines;
    private final ReminderSettings settings;
    private final MessageFormatter formatter;
    private final Snoozes snoozes;
    private final Clock clock;

    public CommandHandler(FeedSyncService feedSync, AppUserRepository users, DeadlineRepository deadlines,
                          ReminderSettings settings, MessageFormatter formatter, Snoozes snoozes, Clock clock) {
        this.feedSync = feedSync;
        this.users = users;
        this.deadlines = deadlines;
        this.settings = settings;
        this.formatter = formatter;
        this.snoozes = snoozes;
        this.clock = clock;
    }

    public BotReply handle(long telegramId, String text) {
        reactivate(telegramId);
        String command = text == null ? "" : text.strip();
        if (command.equals("/group") || command.startsWith("/group ")) {
            return groupCommand(telegramId, command.substring("/group".length()).strip());
        }
        return switch (command) {
            case "/start" -> BotReply.withButtons(Texts.START,
                    List.of(List.of(new InlineButton(Texts.HELP_BUTTON, HELP_LINK))));
            case "/help" -> BotReply.of(Texts.HOW_TO_CONNECT);
            case "/deadlines" -> deadlinesCommand(telegramId);
            case "/stats" -> user(telegramId)
                    .map(u -> BotReply.of(formatter.stats(Stats.of(visible(u, deadlines.findByUserId(u.getId())),
                            clock.instant()))))
                    .orElse(BotReply.of(Texts.NOT_CONNECTED));
            case "/settings" -> user(telegramId)
                    .map(u -> BotReply.withButtons(Texts.SETTINGS, settingsButtons(u)))
                    .orElse(BotReply.of(Texts.NOT_CONNECTED));
            case "/stop" -> BotReply.withButtons(Texts.STOP_CONFIRM, List.of(List.of(
                    new InlineButton(Texts.STOP_BUTTON, STOP_CONFIRMED),
                    new InlineButton(Texts.STOP_CANCEL_BUTTON, STOP_CANCELLED))));
            default -> FeedUrlValidator.normalize(command).isPresent()
                    ? connect(telegramId, command)
                    : new BotReply(Texts.NOT_A_LINK, List.of(), containsToken(command), BotReply.Mode.SEND, null);
        };
    }

    public BotReply handleCallback(long telegramId, String data) {
        if (HELP_LINK.equals(data)) {
            return BotReply.of(Texts.HOW_TO_CONNECT);
        }
        Optional<AppUser> user = user(telegramId);
        if (user.isEmpty() || data == null) {
            return BotReply.toast(Texts.NOT_CONNECTED_SHORT, null);
        }
        if (data.equals(STOP_CONFIRMED)) {
            users.delete(user.get());
            return BotReply.edit(Texts.STOPPED, List.of());
        }
        if (data.equals(STOP_CANCELLED)) {
            return BotReply.edit(Texts.STOP_CANCELLED, List.of());
        }
        if (data.startsWith("done:")) {
            return markDone(user.get(), data.substring("done:".length()));
        }
        if (data.startsWith("snooze:")) {
            return snooze(user.get(), data.substring("snooze:".length()));
        }
        if (data.startsWith("offset:")) {
            settings.toggle(user.get().getId(), Integer.parseInt(data.substring("offset:".length())));
        } else if (data.equals("quiet")) {
            user.get().setQuietHours(!user.get().isQuietHours());
            users.save(user.get());
        } else if (data.equals("digest")) {
            user.get().setDigestEnabled(!user.get().isDigestEnabled());
            users.save(user.get());
        } else {
            return BotReply.toast(Texts.UNKNOWN_BUTTON, null);
        }
        return BotReply.edit(Texts.SETTINGS, settingsButtons(user.get()));
    }

    private BotReply connect(long telegramId, String link) {
        Instant now = clock.instant();
        ConnectResult result = feedSync.connect(telegramId, link);
        String text = switch (result) {
            case ConnectResult.Connected c -> Texts.CONNECTED.formatted(c.count())
                    + c.nearest().map(d -> Texts.NEAREST + formatter.nearest(d, now)).orElse("")
                    + Texts.CONNECTED_FOOTER
                    + (hasGroupQuizzes(telegramId) ? Texts.GROUP_HINT : "");
            case ConnectResult.FeedUnreachable u -> Texts.FEED_UNREACHABLE;
            case ConnectResult.InvalidUrl i -> Texts.NOT_A_LINK;
        };
        return BotReply.deletingUserMessage(text);
    }

    private BotReply deadlinesCommand(long telegramId) {
        Optional<AppUser> user = user(telegramId);
        if (user.isEmpty()) {
            return BotReply.of(Texts.NOT_CONNECTED);
        }
        Instant now = clock.instant();
        List<Deadline> week = deadlines.findOpenDueBetween(user.get().getId(), now, now.plus(WEEK));
        return BotReply.of(formatter.list(visible(user.get(), week), now));
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

    private BotReply snooze(AppUser user, String rawId) {
        Optional<Deadline> deadline = ownDeadline(user, rawId);
        if (deadline.isEmpty() || deadline.get().getStatus() != DeadlineStatus.OPEN) {
            return BotReply.toast(Texts.DEADLINE_NOT_FOUND, null);
        }
        Instant remindAt = clock.instant().plus(Duration.ofHours(1));
        snoozes.snooze(deadline.get().getId(), remindAt);
        return BotReply.toast(Texts.SNOOZED.formatted(formatter.time(remindAt)), null);
    }

    private BotReply markDone(AppUser user, String rawId) {
        Optional<Deadline> deadline = ownDeadline(user, rawId);
        if (deadline.isEmpty()) {
            return BotReply.toast(Texts.DEADLINE_NOT_FOUND, null);
        }
        Deadline d = deadline.get();
        if (d.getStatus() != DeadlineStatus.DONE) {
            d.markDone(clock.instant());
            deadlines.save(d);
        }
        return BotReply.toast(Texts.DONE.formatted(formatter.label(d)), d.getId());
    }

    // only the owner may act on a deadline, whatever id the callback carries
    private Optional<Deadline> ownDeadline(AppUser user, String rawId) {
        try {
            return deadlines.findById(Long.parseLong(rawId)).filter(d -> d.getUserId() == user.getId());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private List<List<InlineButton>> settingsButtons(AppUser user) {
        Set<Integer> on = settings.offsets(user.getId());
        return List.of(
                List.of(toggle("За 3 дні", on.contains(4320), "offset:4320"),
                        toggle("За 1 день", on.contains(1440), "offset:1440"),
                        toggle("За 3 год", on.contains(180), "offset:180")),
                List.of(toggle("План на тиждень", user.isDigestEnabled(), "digest")),
                List.of(toggle("Тихі години 23–08", user.isQuietHours(), "quiet")));
    }

    private static InlineButton toggle(String label, boolean on, String data) {
        return new InlineButton((on ? "✅ " : "▫️ ") + label, data);
    }

    private static List<Deadline> visible(AppUser user, List<Deadline> list) {
        return list.stream().filter(d -> GroupFilter.visible(d, user.getSeminarGroup())).toList();
    }

    private boolean hasGroupQuizzes(long telegramId) {
        return user(telegramId)
                .map(u -> deadlines.findByUserId(u.getId()).stream().anyMatch(GroupFilter::mentionsGroup))
                .orElse(false);
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
