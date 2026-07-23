package dev.ainer.module.identity.account.infrastructure.mybatis;

import java.time.Instant;

public class IdentityAccessEventOutboxStatusRow {

    private long pending;
    private long failed;
    private long exhausted;
    private long published;
    private Instant oldestReadyAt;

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getExhausted() {
        return exhausted;
    }

    public void setExhausted(long exhausted) {
        this.exhausted = exhausted;
    }

    public long getPublished() {
        return published;
    }

    public void setPublished(long published) {
        this.published = published;
    }

    public Instant getOldestReadyAt() {
        return oldestReadyAt;
    }

    public void setOldestReadyAt(Instant oldestReadyAt) {
        this.oldestReadyAt = oldestReadyAt;
    }
}
