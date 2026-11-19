package ua.dexipua.deadliner.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private long telegramId;

    private String username;

    @Column(name = "first_name")
    private String firstName;

    private boolean active = true;

    @Column(name = "digest_enabled")
    private boolean digestEnabled = true;

    @Column(name = "seminar_group")
    private Integer seminarGroup;

    @Column(name = "quiet_hours")
    private boolean quietHours = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(long telegramId, Instant createdAt) {
        this.telegramId = telegramId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDigestEnabled() {
        return digestEnabled;
    }

    public void setDigestEnabled(boolean digestEnabled) {
        this.digestEnabled = digestEnabled;
    }

    public Integer getSeminarGroup() {
        return seminarGroup;
    }

    public void setSeminarGroup(Integer seminarGroup) {
        this.seminarGroup = seminarGroup;
    }

    public boolean isQuietHours() {
        return quietHours;
    }

    public void setQuietHours(boolean quietHours) {
        this.quietHours = quietHours;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
