package ua.dexipua.deadliner.web;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ua.dexipua.deadliner.deadline.CourseGroups;
import ua.dexipua.deadliner.deadline.Deadline;
import ua.dexipua.deadliner.deadline.DeadlineActions;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.DeadlineStatus;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.deadline.Stats;
import ua.dexipua.deadliner.reminder.MessageFormatter;
import ua.dexipua.deadliner.reminder.QuietHours;
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** JSON for the web dashboard. The session filter has already checked who is calling. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Duration OVERDUE_WINDOW = Duration.ofDays(14);
    private static final Duration UPCOMING_WINDOW = Duration.ofDays(14);
    private static final int MAX_GROUP = 10;

    public record CourseGroup(String key, String course, Integer group) {
    }

    public record Me(boolean quietHours, int quietStart, int quietEnd, boolean digestEnabled, List<Integer> offsets,
                     List<CourseGroup> groups) {
    }

    public record DeadlineView(long id, String course, String title, Instant dueAt, String status) {
    }

    public record Deadlines(List<DeadlineView> overdue, List<DeadlineView> upcoming) {
    }

    public record Counts(int onTime, int late, int overdue, int upcoming, Integer onTimePercent) {
    }

    public record CourseStats(String course, Counts counts) {
    }

    public record StatsView(Counts total, List<CourseStats> courses, boolean groupHint) {
    }

    /** quietStart/quietEnd are optional: leave them out to keep the current window. */
    public record SettingsRequest(List<Integer> offsets, boolean digestEnabled, boolean quietHours,
                                  Integer quietStart, Integer quietEnd) {
    }

    public record GroupRequest(Integer group) {
    }

    private final AppUserRepository users;
    private final DeadlineRepository deadlines;
    private final DeadlineActions actions;
    private final ReminderSettings settings;
    private final CourseGroups courseGroups;
    private final Clock clock;

    public ApiController(AppUserRepository users, DeadlineRepository deadlines, DeadlineActions actions,
                         ReminderSettings settings, CourseGroups courseGroups, Clock clock) {
        this.users = users;
        this.deadlines = deadlines;
        this.actions = actions;
        this.settings = settings;
        this.courseGroups = courseGroups;
        this.clock = clock;
    }

    @GetMapping("/me")
    public Me me(@RequestAttribute(SessionFilter.USER_ID) long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Map<String, Integer> groups = courseGroups.of(userId);
        List<CourseGroup> courses = GroupFilter.coursesWithGroups(deadlines.findByUserId(userId)).stream()
                .map(c -> new CourseGroup(GroupFilter.courseKey(c), MessageFormatter.shortCourse(c), groups.get(c)))
                .toList();
        return new Me(user.isQuietHours(), user.getQuietStart(), user.getQuietEnd(), user.isDigestEnabled(),
                List.copyOf(settings.offsets(userId)), courses);
    }

    /** Recent overdue (open) and the next two weeks (open or done, so a mistaken ✅ can be undone). */
    @GetMapping("/deadlines")
    public Deadlines deadlines(@RequestAttribute(SessionFilter.USER_ID) long userId) {
        Instant now = clock.instant();
        List<Deadline> visible = visible(userId).stream()
                .filter(d -> d.getStatus() != DeadlineStatus.GONE)
                .sorted(Comparator.comparing(Deadline::getDueAt))
                .toList();
        List<DeadlineView> overdue = visible.stream()
                .filter(d -> d.getStatus() == DeadlineStatus.OPEN)
                .filter(d -> !d.getDueAt().isAfter(now) && d.getDueAt().isAfter(now.minus(OVERDUE_WINDOW)))
                .map(ApiController::view)
                .toList();
        List<DeadlineView> upcoming = visible.stream()
                .filter(d -> d.getDueAt().isAfter(now) && !d.getDueAt().isAfter(now.plus(UPCOMING_WINDOW)))
                .map(ApiController::view)
                .toList();
        return new Deadlines(overdue, upcoming);
    }

    @PostMapping("/deadlines/{id}/done")
    public DeadlineView done(@RequestAttribute(SessionFilter.USER_ID) long userId, @PathVariable long id) {
        return actions.markDone(userId, id).map(ApiController::view).orElseThrow(ApiController::notFound);
    }

    @PostMapping("/deadlines/{id}/undo")
    public DeadlineView undo(@RequestAttribute(SessionFilter.USER_ID) long userId, @PathVariable long id) {
        return actions.undo(userId, id).map(ApiController::view).orElseThrow(ApiController::notFound);
    }

    @GetMapping("/stats")
    public StatsView stats(@RequestAttribute(SessionFilter.USER_ID) long userId) {
        Stats stats = Stats.of(visible(userId), clock.instant());
        List<CourseStats> courses = stats.byCourse().entrySet().stream()
                .map(e -> new CourseStats(MessageFormatter.shortCourse(e.getKey()), counts(e.getValue())))
                .toList();
        Map<String, Integer> groups = courseGroups.of(userId);
        boolean groupHint = GroupFilter.coursesWithGroups(deadlines.findByUserId(userId)).stream()
                .anyMatch(c -> !groups.containsKey(c));
        return new StatsView(counts(stats.total()), courses, groupHint);
    }

    @PutMapping("/settings")
    @Transactional
    public Me settings(@RequestAttribute(SessionFilter.USER_ID) long userId, @RequestBody SettingsRequest request) {
        if (request.offsets() == null || request.offsets().contains(null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offsets must be a list of minutes");
        }
        Set<Integer> offsets = new HashSet<>(request.offsets());
        if (!ReminderSettings.DEFAULT_OFFSETS.containsAll(offsets)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown reminder offset");
        }
        QuietHours window = null;
        if (request.quietStart() != null || request.quietEnd() != null) {
            try {
                window = new QuietHours(request.quietStart(), request.quietEnd());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quiet hours must be two different hours 0-23");
            }
        }
        // the row lock makes two fast saves run one after another instead of colliding
        AppUser user = users.lockById(userId).orElseThrow(ApiController::notFound);
        if (window != null) {
            user.setQuietWindow(window);
        }
        settings.replace(userId, offsets);
        user.setDigestEnabled(request.digestEnabled());
        user.setQuietHours(request.quietHours());
        users.save(user);
        return me(userId);
    }

    @PutMapping("/groups/{key}")
    public Me group(@RequestAttribute(SessionFilter.USER_ID) long userId, @PathVariable String key,
                    @RequestBody GroupRequest request) {
        String course = GroupFilter.coursesWithGroups(deadlines.findByUserId(userId)).stream()
                .filter(c -> GroupFilter.courseKey(c).equals(key))
                .findFirst()
                .orElseThrow(ApiController::notFound);
        if (request.group() == null) {
            courseGroups.clear(userId, course);
        } else if (request.group() < 1 || request.group() > MAX_GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "group must be 1-" + MAX_GROUP);
        } else {
            courseGroups.set(userId, course, request.group());
        }
        return me(userId);
    }

    private List<Deadline> visible(long userId) {
        return GroupFilter.visible(deadlines.findByUserId(userId), courseGroups.of(userId));
    }

    private static DeadlineView view(Deadline d) {
        return new DeadlineView(d.getId(), d.getCourseName() == null ? null : MessageFormatter.shortCourse(d.getCourseName()),
                d.getTitle(), d.getDueAt(), d.getStatus().name());
    }

    private static Counts counts(Stats.Counts c) {
        return new Counts(c.onTime(), c.late(), c.overdue(), c.upcoming(), c.onTimePercent());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
