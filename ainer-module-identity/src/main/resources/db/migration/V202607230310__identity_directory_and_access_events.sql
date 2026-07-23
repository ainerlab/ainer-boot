ALTER TABLE ainer_identity_membership
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE ainer_identity_membership
SET updated_at = joined_at
WHERE updated_at IS NULL;

ALTER TABLE ainer_identity_membership
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT ck_ainer_identity_membership_time
        CHECK (updated_at >= joined_at);

CREATE TABLE ainer_identity_access_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(48) NOT NULL,
    tenant_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    payload_version SMALLINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL,
    published_at TIMESTAMPTZ,
    last_error_code VARCHAR(96),
    CONSTRAINT ck_ainer_identity_access_event_type
        CHECK (event_type IN ('IDENTITY_USER_DISABLED', 'IDENTITY_MEMBERSHIP_REVOKED')),
    CONSTRAINT ck_ainer_identity_access_event_payload_version
        CHECK (payload_version = 1),
    CONSTRAINT ck_ainer_identity_access_event_publication_status
        CHECK (publication_status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_ainer_identity_access_event_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_ainer_identity_access_event_published
        CHECK (
            (publication_status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (publication_status <> 'PUBLISHED' AND published_at IS NULL)
        ),
    CONSTRAINT ck_ainer_identity_access_event_last_error
        CHECK (
            last_error_code IS NULL
            OR last_error_code ~ '^AINER\.[A-Z0-9_.]{1,89}$'
        )
);

CREATE INDEX idx_ainer_identity_access_event_pending
    ON ainer_identity_access_event (occurred_at, id)
    WHERE publication_status IN ('PENDING', 'FAILED');

CREATE INDEX idx_ainer_identity_access_event_subject
    ON ainer_identity_access_event (tenant_id, subject_id, occurred_at DESC, id DESC);
