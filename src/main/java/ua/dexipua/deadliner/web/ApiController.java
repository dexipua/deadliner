package ua.dexipua.deadliner.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.dexipua.deadliner.deadline.CourseGroups;
import ua.dexipua.deadliner.deadline.DeadlineRepository;
import ua.dexipua.deadliner.deadline.GroupFilter;
import ua.dexipua.deadliner.reminder.MessageFormatter;
import ua.dexipua.deadliner.reminder.ReminderSettings;
import ua.dexipua.deadliner.user.AppUser;
import ua.dexipua.deadliner.user.AppUserRepository;

import java.util.List;
import java.util.Map;

/** JSON for the web dashboard. The session filter has already checked who is calling. */
@RestController
@RequestMapping("/api")
public class ApiController {

    public record CourseGroup(String key, String course, Integer group) {
    }

    public record Me(boolean quietHours, boolean digestEnabled, List<Integer> offsets, List<CourseGroup> groups) {
    }

    private final AppUserRepository users;
    private final DeadlineRepository deadlines;
    private final ReminderSettings settings;
    private final CourseGroups courseGroups;

    public ApiController(AppUserRepository users, DeadlineRepository deadlines, ReminderSettings settings,
                         CourseGroups courseGroups) {
        this.users = users;
        this.deadlines = deadlines;
        this.settings = settings;
        this.courseGroups = courseGroups;
    }

    @GetMapping("/me")
    public Me me(@RequestAttribute(SessionFilter.USER_ID) long userId) {
        AppUser user = users.findById(userId).orElseThrow();
        Map<String, Integer> groups = courseGroups.of(userId);
        List<CourseGroup> courses = GroupFilter.coursesWithGroups(deadlines.findByUserId(userId)).stream()
                .map(c -> new CourseGroup(courseKey(c), MessageFormatter.shortCourse(c), groups.get(c)))
                .toList();
        return new Me(user.isQuietHours(), user.isDigestEnabled(), List.copyOf(settings.offsets(userId)), courses);
    }

    private static String courseKey(String course) {
        return GroupFilter.courseKey(course);
    }
}
