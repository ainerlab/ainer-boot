package dev.ainer.persistence.mybatisplus;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("ainer_persistence_probe")
public class PersistenceProbeRow {

    @TableId
    private UUID id;

    private UUID scopeId;

    private String name;

    public PersistenceProbeRow() {
    }

    public PersistenceProbeRow(UUID scopeId, String name) {
        this.scopeId = scopeId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScopeId() {
        return scopeId;
    }

    public void setScopeId(UUID scopeId) {
        this.scopeId = scopeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
