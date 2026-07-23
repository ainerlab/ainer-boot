package dev.ainer.module.workspace.workspace.infrastructure.mybatis;

import java.time.Instant;

public class WorkspaceAuthorizationAuditOperationalStatusRow {
    private long hot;
    private long archived;
    private long deniedInWindow;
    private long ownerlessWorkspaces;
    private Instant oldestHotAt;

    public long getHot() { return hot; }
    public void setHot(long hot) { this.hot = hot; }
    public long getArchived() { return archived; }
    public void setArchived(long archived) { this.archived = archived; }
    public long getDeniedInWindow() { return deniedInWindow; }
    public void setDeniedInWindow(long deniedInWindow) { this.deniedInWindow = deniedInWindow; }
    public long getOwnerlessWorkspaces() { return ownerlessWorkspaces; }
    public void setOwnerlessWorkspaces(long ownerlessWorkspaces) { this.ownerlessWorkspaces = ownerlessWorkspaces; }
    public Instant getOldestHotAt() { return oldestHotAt; }
    public void setOldestHotAt(Instant oldestHotAt) { this.oldestHotAt = oldestHotAt; }
}
