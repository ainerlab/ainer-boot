package dev.ainer.persistence.mybatisplus;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("ainer_persistence_probe")
public class PersistenceProbeRow {

    @TableId
    private UUID id;

    private UUID tenantId;

    private String name;

    public PersistenceProbeRow() {
    }

    public PersistenceProbeRow(UUID tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
