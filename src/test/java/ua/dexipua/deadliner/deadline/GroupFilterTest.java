package ua.dexipua.deadliner.deadline;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroupFilterTest {

    private static Deadline d(String course, String title) {
        Deadline d = new Deadline(1L, title);
        d.setCourseName(course);
        d.setTitle(title);
        d.setDueAt(Instant.parse("2026-11-20T21:59:00Z"));
        return d;
    }

    private static final String MATH = "MATH101 (2026-2027)";

    @Test
    void withoutGroupsEverythingIsVisible() {
        assertThat(GroupFilter.visible(d(MATH, "Квіз 8 · Група 5"), Map.of())).isTrue();
    }

    @Test
    void ownGroupInThatCourseIsVisible() {
        assertThat(GroupFilter.visible(d(MATH, "Квіз 8 · Заняття 17 · Група 1"), Map.of(MATH, 1))).isTrue();
    }

    @Test
    void otherGroupInThatCourseIsHidden() {
        assertThat(GroupFilter.visible(d(MATH, "Квіз 8 · Заняття 17 · Група 7"), Map.of(MATH, 1))).isFalse();
    }

    @Test
    void groupOfAnotherCourseDoesNotApply() {
        assertThat(GroupFilter.visible(d("CS201 (2026-2027)", "Quiz · Група 7"), Map.of(MATH, 1))).isTrue();
    }

    @Test
    void groupNumberMustMatchWholeNumber() {
        assertThat(GroupFilter.visible(d(MATH, "Квіз · Група 15"), Map.of(MATH, 1))).isFalse();
        assertThat(GroupFilter.visible(d(MATH, "Квіз · Група 15"), Map.of(MATH, 15))).isTrue();
    }

    @Test
    void hugeNumberInTitleDoesNotCrash() {
        assertThat(GroupFilter.visible(d(MATH, "Квіз · Група 99999999999"), Map.of(MATH, 1))).isTrue();
    }

    @Test
    void englishGroupWorksToo() {
        assertThat(GroupFilter.visible(d(MATH, "Quiz 3 · Group 2"), Map.of(MATH, 5))).isFalse();
    }

    @Test
    void deadlineWithoutGroupIsAlwaysVisible() {
        assertThat(GroupFilter.visible(d(MATH, "Mind Map 3"), Map.of(MATH, 1))).isTrue();
    }

    @Test
    void findsCoursesThatHaveGroupQuizzes() {
        List<Deadline> all = List.of(d(MATH, "Квіз · Група 5"), d(MATH, "Квіз · Група 7"),
                d("CS201 (2026-2027)", "Assignment 3"), d("ENG121 (2026-2027)", "Quiz · Group 2"));

        assertThat(GroupFilter.coursesWithGroups(all)).containsExactly("ENG121 (2026-2027)", MATH);
    }
}
