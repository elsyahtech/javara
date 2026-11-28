CREATE TABLE javara_tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_key VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO javara_tenants (tenant_key, display_name)
VALUES ('default', 'Default organization');

CREATE TABLE javara_installation_settings (
    setting_id SMALLINT PRIMARY KEY CHECK (setting_id = 1),
    tenancy_mode VARCHAR(16) NOT NULL
        CHECK (tenancy_mode IN ('SINGLE', 'SHARED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE javara_tenant_memberships (
    user_id UUID NOT NULL REFERENCES javara_users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES javara_tenants(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id)
);

CREATE INDEX idx_tenant_memberships_tenant_status
    ON javara_tenant_memberships (tenant_id, status);

CREATE TABLE javara_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_key VARCHAR(64) NOT NULL UNIQUE
        CHECK (role_key ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE javara_permissions (
    permission_key VARCHAR(100) PRIMARY KEY
        CHECK (permission_key ~ '^[a-z][a-z0-9:-]{1,99}$'),
    description VARCHAR(300) NOT NULL
);

CREATE TABLE javara_user_role_details (
    role_id UUID NOT NULL REFERENCES javara_user_roles(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL REFERENCES javara_permissions(permission_key)
        ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_key)
);

CREATE TABLE javara_user_role_assignments (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    role_id UUID NOT NULL REFERENCES javara_user_roles(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES javara_users(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, tenant_id, role_id),
    FOREIGN KEY (user_id, tenant_id)
        REFERENCES javara_tenant_memberships(user_id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_role_assignments_role
    ON javara_user_role_assignments (role_id, tenant_id);

INSERT INTO javara_user_roles (role_key, display_name, description, system_managed)
VALUES
    ('USER', 'User', 'Standard authenticated account', TRUE),
    ('ADMIN', 'Administrator', 'Built-in administrator for the organization', TRUE);

INSERT INTO javara_permissions (permission_key, description)
VALUES
    ('auth:self', 'Use authenticated self-service endpoints'),
    ('users:read', 'Read user accounts'),
    ('users:manage', 'Create and manage user accounts'),
    ('roles:manage', 'Manage role definitions and user role assignments'),
    ('service-clients:manage', 'Manage service client credentials'),
    ('sessions:manage', 'Manage user sessions'),
    ('password-resets:manage', 'Initiate administrative password recovery');

INSERT INTO javara_user_role_details (role_id, permission_key)
SELECT user_role.id, permission.permission_key
FROM javara_user_roles user_role
CROSS JOIN javara_permissions permission
WHERE user_role.role_key = 'ADMIN';

INSERT INTO javara_user_role_details (role_id, permission_key)
SELECT user_role.id, 'auth:self'
FROM javara_user_roles user_role
WHERE user_role.role_key = 'USER';
