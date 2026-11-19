package ua.dexipua.deadliner.deadline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroupFilterTest {

    private static Deadline titled(String title) {
        Deadline d = new Deadline(1L, title);
        d.setTitle(title);
        return d;
    }

    @Test
    void withoutGroupEverythingIsVisible() {
        assertThat(GroupFilter.visible(titled("Квіз 8 · Група 5"), null)).isTrue();
    }

    @Test
    void ownGroupIsVisible() {
        assertThat(GroupFilter.visible(titled("Квіз 8 · Заняття 17 · Група 5"), 5)).isTrue();
    }

    @Test
    void otherGroupIsHidden() {
        assertThat(GroupFilter.visible(titled("Квіз 8 · Заняття 17 · Група 7"), 5)).isFalse();
    }

    @Test
    void groupNumberMustMatchWholeNumber() {
        assertThat(GroupFilter.visible(titled("Квіз · Група 15"), 5)).isFalse();
        assertThat(GroupFilter.visible(titled("Квіз · Група 15"), 15)).isTrue();
    }

    @Test
    void englishGroupWorksToo() {
        assertThat(GroupFilter.visible(titled("Quiz 3 · Group 2"), 5)).isFalse();
    }

    @Test
    void deadlineWithoutGroupIsAlwaysVisible() {
        assertThat(GroupFilter.visible(titled("Assignment 3 - API & Trading"), 5)).isTrue();
    }

    @Test
    void detectsGroupSpecificDeadlines() {
        assertThat(GroupFilter.mentionsGroup(titled("Квіз 8 · Група 7"))).isTrue();
        assertThat(GroupFilter.mentionsGroup(titled("HW1"))).isFalse();
    }
}
