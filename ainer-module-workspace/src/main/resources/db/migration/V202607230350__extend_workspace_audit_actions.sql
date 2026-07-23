ALTER TABLE ainer_workspace_authorization_audit
    DROP CONSTRAINT ck_ainer_workspace_audit_action;

ALTER TABLE ainer_workspace_authorization_audit
    ADD CONSTRAINT ck_ainer_workspace_audit_action
        CHECK (action IN (
            'WORKSPACE_CREATE', 'WORKSPACE_READ', 'WORKSPACE_PAGE', 'WORKSPACE_RENAME',
            'MEMBER_INVITE', 'MEMBERSHIP_ACCEPT', 'MEMBER_ROLE_CHANGE',
            'MEMBER_REMOVE', 'OWNERSHIP_TRANSFER', 'AUTHORIZATION_AUDIT_READ'
        ));
