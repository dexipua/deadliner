package ua.dexipua.deadliner.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "calendar_feed")
public class CalendarFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private long userId;

    @Column(name = "url_encrypted", nullable = false)
    private byte[] urlEncrypted;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_status")
    private String lastStatus;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    protected CalendarFeed() {
    }

    public CalendarFeed(long userId, byte[] urlEncrypted) {
        this.userId = userId;
        this.urlEncrypted = urlEncrypted;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public byte[] getUrlEncrypted() {
        return urlEncrypted;
    }

    public void setUrlEncrypted(byte[] urlEncrypted) {
        this.urlEncrypted = urlEncrypted;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void recordSuccess(Instant at) {
        lastSyncedAt = at;
        lastStatus = "OK";
        consecutiveFailures = 0;
    }

    public void recordFailure(String status) {
        lastStatus = status;
        consecutiveFailures++;
    }

    public void recordUnavailable(String status) {
        lastStatus = status;
    }
}
