package ua.dexipua.deadliner.deadline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "deadline")
public class Deadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "external_uid", nullable = false)
    private String externalUid;

    @Column(name = "course_name")
    private String courseName;

    @Column(nullable = false)
    private String title;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadlineStatus status = DeadlineStatus.OPEN;

    @Column(name = "done_at")
    private Instant doneAt;

    protected Deadline() {
    }

    public Deadline(long userId, String externalUid) {
        this.userId = userId;
        this.externalUid = externalUid;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getExternalUid() {
        return externalUid;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public DeadlineStatus getStatus() {
        return status;
    }

    public void setStatus(DeadlineStatus status) {
        this.status = status;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void markDone(Instant at) {
        this.status = DeadlineStatus.DONE;
        this.doneAt = at;
    }

    /** Undo of {@link #markDone}: "✅ Здано" was pressed by mistake. */
    public void reopen() {
        this.status = DeadlineStatus.OPEN;
        this.doneAt = null;
    }
}
