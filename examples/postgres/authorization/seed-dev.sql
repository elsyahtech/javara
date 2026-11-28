BEGIN;

INSERT INTO javara_user_roles (
    role_key,
    display_name,
    description
)
VALUES (
    'ACCOUNT_READER',
    'Account reader',
    'Read user account records without modifying them.'
)
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO javara_user_role_details (role_id, permission_key)
SELECT role_definition.id, 'users:read'
FROM javara_user_roles role_definition
WHERE role_definition.role_key = 'ACCOUNT_READER'
ON CONFLICT (role_id, permission_key) DO NOTHING;

COMMIT;
