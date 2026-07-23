ALTER TABLE ainer_identity_access_event
    ADD COLUMN available_at TIMESTAMPTZ,
    ADD COLUMN lease_owner VARCHAR(128),
    ADD COLUMN lease_until TIMESTAMPTZ;

UPDATE ainer_identity_access_event
SET available_at = occurred_at
WHERE available_at IS NULL;

ALTER TABLE ainer_identity_access_event
    ALTER COLUMN available_at SET NOT NULL,
    ADD CONSTRAINT ck_ainer_identity_access_event_available_time
        CHECK (available_at >= occurred_at),
    ADD CONSTRAINT ck_ainer_identity_access_event_lease
        CHECK (
            (lease_owner IS NULL AND lease_until IS NULL)
            OR (
                lease_owner ~ '^[A-Za-z0-9._:@/-]{1,128}$'
                AND lease_until IS NOT NULL
            )
        ),
    ADD CONSTRAINT ck_ainer_identity_access_event_published_lease
        CHECK (publication_status <> 'PUBLISHED' OR lease_owner IS NULL);

DROP INDEX idx_ainer_identity_access_event_pending;

CREATE INDEX idx_ainer_identity_access_event_ready
    ON ainer_identity_access_event (available_at, occurred_at, id)
    WHERE publication_status IN ('PENDING', 'FAILED');
