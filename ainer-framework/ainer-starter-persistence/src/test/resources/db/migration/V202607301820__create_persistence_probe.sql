CREATE TABLE ainer_persistence_probe
(
    id        uuid PRIMARY KEY DEFAULT uuidv7(),
    tenant_id uuid         NOT NULL,
    name      varchar(100) NOT NULL,
    UNIQUE (tenant_id, name)
);
