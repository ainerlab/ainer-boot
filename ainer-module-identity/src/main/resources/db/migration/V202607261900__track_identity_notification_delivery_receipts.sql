CREATE TABLE ainer_identity_notification_delivery_receipt (
    id UUID NOT NULL DEFAULT uuidv7(),
    notification_id UUID NOT NULL,
    gateway_client_id VARCHAR(128) NOT NULL,
    gateway_event_id VARCHAR(128) NOT NULL,
    delivery_status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(96),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    request_id VARCHAR(128) NOT NULL,

    CONSTRAINT pk_ainer_identity_notification_delivery_receipt
        PRIMARY KEY (id),
    CONSTRAINT uk_ainer_identity_notification_delivery_receipt_notification
        UNIQUE (notification_id),
    CONSTRAINT uk_ainer_identity_notification_delivery_receipt_gateway_event
        UNIQUE (gateway_client_id, gateway_event_id),
    CONSTRAINT fk_ainer_identity_notification_delivery_receipt_notification
        FOREIGN KEY (notification_id)
        REFERENCES ainer_identity_notification_outbox (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_id_version
        CHECK (uuid_extract_version(id) = 7),
    CONSTRAINT ck_ainer_identity_notification_receipt_notification_version
        CHECK (uuid_extract_version(notification_id) = 7),
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_gateway
        CHECK (gateway_client_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_event
        CHECK (gateway_event_id ~ '^[A-Za-z0-9._:@/-]{1,128}$'),
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_status
        CHECK (delivery_status IN ('DELIVERED', 'FAILED')),
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_failure
        CHECK (
            (delivery_status = 'DELIVERED' AND failure_code IS NULL)
            OR (
                delivery_status = 'FAILED'
                AND failure_code IS NOT NULL
                AND failure_code ~ '^[A-Z0-9][A-Z0-9._:-]{0,95}$'
            )
        ),
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_time
        CHECK (occurred_at <= received_at + INTERVAL '5 minutes'),
    CONSTRAINT ck_ainer_identity_notification_delivery_receipt_request
        CHECK (request_id ~ '^[A-Za-z0-9._:@/-]{1,128}$')
);

CREATE INDEX idx_ainer_identity_notification_delivery_receipt_received
    ON ainer_identity_notification_delivery_receipt (received_at DESC, id DESC);
