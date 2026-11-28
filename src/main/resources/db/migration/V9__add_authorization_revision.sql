ALTER TABLE javara_tenants
    ADD COLUMN authorization_revision BIGINT NOT NULL DEFAULT 0
        CHECK (authorization_revision >= 0);

ALTER TABLE javara_tenant_memberships
    ADD COLUMN authorization_revision BIGINT NOT NULL DEFAULT 0
        CHECK (authorization_revision >= 0);
