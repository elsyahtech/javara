ALTER TABLE users
    ADD COLUMN password_reset_support_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE admin_password_reset_audit
    ADD COLUMN reason VARCHAR(500);

ALTER TABLE admin_password_reset_audit
    DROP CONSTRAINT admin_password_reset_audit_action_check,
    ADD CONSTRAINT chk_admin_password_reset_audit_action
        CHECK (action IN (
            'REQUESTED',
            'FORCE_REQUIRED',
            'COMPLETED',
            'EMERGENCY_RANDOMIZED',
            'SUPPORT_AUTHORIZED',
            'RECOVERY_COMPLETED'
        ));
