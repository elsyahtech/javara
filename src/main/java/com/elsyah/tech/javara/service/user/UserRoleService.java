package com.elsyah.tech.javara.service.user;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elsyah.tech.javara.exception.ApiException;
import com.elsyah.tech.javara.request.user.RoleRequest;
import com.elsyah.tech.javara.response.user.RoleResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class UserRoleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserRoleService.class);
    private static final String ROLE_MANAGE_PERMISSION = "roles:manage";
    private static final String DEFAULT_TENANT_KEY = "default";
    private static final String AUTHORIZATION_CACHE_KEY_PREFIX = "javara:authorization:v1";
    private static final Duration AUTHORIZATION_CACHE_TTL = Duration.ofMinutes(5);
    private static final String ROLE_KEY_PATTERN = "[A-Z][A-Z0-9_]{1,63}";
    private static final String PERMISSION_KEY_PATTERN = "[a-z][a-z0-9:-]{1,99}";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UserRoleService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RoleResponse> findAllRoles() {
        List<RoleResponse> roleDefinitions = jdbcTemplate.query(
                """
                SELECT role_key, display_name, description, system_managed
                FROM javara_user_roles
                WHERE active = TRUE
                ORDER BY role_key
                """,
                (result, rowNumber) -> new RoleResponse(
                        result.getString("role_key"),
                        result.getString("display_name"),
                        result.getString("description"),
                        result.getBoolean("system_managed"),
                        List.of()));
        return roleDefinitions.stream()
                .map(role -> withPermissions(role))
                .toList();
    }

    public RoleResponse findRole(String roleKey) {
        List<RoleResponse> roles = jdbcTemplate.query(
                """
                SELECT role_key, display_name, description, system_managed
                FROM javara_user_roles
                WHERE role_key = ? AND active = TRUE
                """,
                (result, rowNumber) -> new RoleResponse(
                        result.getString("role_key"),
                        result.getString("display_name"),
                        result.getString("description"),
                        result.getBoolean("system_managed"),
                        List.of()),
                roleKey);
        if (roles.isEmpty()) {
            throw new ApiException("Role not found", HttpStatus.NOT_FOUND);
        }
        return withPermissions(roles.getFirst());
    }

    public List<String> findAllPermissions() {
        return jdbcTemplate.queryForList(
                "SELECT permission_key FROM javara_permissions ORDER BY permission_key",
                String.class);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        lockDefaultTenant();
        if (roleExists(request.key())) {
            throw new ApiException("Role key already exists", HttpStatus.CONFLICT);
        }
        validatePermissions(request.permissions());
        UUID roleId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO javara_user_roles (id, role_key, display_name, description)
                VALUES (?, ?, ?, ?)
                """,
                roleId,
                request.key(),
                request.displayName(),
                request.description());
        insertRolePermissions(roleId, request.permissions());
        return findRole(request.key());
    }

    @Transactional
    public RoleResponse updateRole(String roleKey, RoleRequest request) {
        UUID defaultTenantId = lockDefaultTenant();
        RoleResponse current = findRole(roleKey);
        if (current.systemManaged()) {
            throw new ApiException("Built-in roles cannot be changed", HttpStatus.CONFLICT);
        }
        validatePermissions(request.permissions());
        if (current.permissions().contains(ROLE_MANAGE_PERMISSION)
                && !request.permissions().contains(ROLE_MANAGE_PERMISSION)
                && activeRoleManagerCount() <= 1) {
            throw new ApiException(
                    "The final role manager cannot lose role-management permission",
                    HttpStatus.CONFLICT);
        }
        jdbcTemplate.update(
                """
                UPDATE javara_user_roles
                SET display_name = ?, description = ?, updated_at = CURRENT_TIMESTAMP
                WHERE role_key = ? AND active = TRUE
                """,
                request.displayName(),
                request.description(),
                roleKey);
        UUID roleId = roleId(roleKey);
        jdbcTemplate.update("DELETE FROM javara_user_role_details WHERE role_id = ?", roleId);
        insertRolePermissions(roleId, request.permissions());
        bumpTenantAuthorizationRevision(defaultTenantId);
        return findRole(roleKey);
    }

    @Transactional
    public void deleteRole(String roleKey) {
        UUID defaultTenantId = lockDefaultTenant();
        RoleResponse role = findRole(roleKey);
        if (role.systemManaged()) {
            throw new ApiException("Built-in roles cannot be deleted", HttpStatus.CONFLICT);
        }
        if (role.permissions().contains(ROLE_MANAGE_PERMISSION)
                && activeRoleManagerCount() <= 1) {
            throw new ApiException(
                    "The final role manager cannot be deleted",
                    HttpStatus.CONFLICT);
        }
        int deleted = jdbcTemplate.update(
                """
                DELETE FROM javara_user_roles
                WHERE role_key = ?
                  AND active = TRUE
                  AND NOT EXISTS (
                      SELECT 1
                      FROM javara_user_role_assignments assignment
                      WHERE assignment.role_id = javara_user_roles.id
                  )
                """,
                roleKey);
        if (deleted == 0) {
            throw new ApiException(
                    "Assigned roles cannot be deleted; remove their user assignments first",
                    HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void replaceUserRoles(UUID targetUserId, UUID actorUserId, List<String> roleKeys) {
        UUID defaultTenantId = lockDefaultTenant();
        if (roleKeys == null) {
            throw new ApiException("At least one role is required", HttpStatus.BAD_REQUEST);
        }
        List<String> normalizedKeys = roleKeys.stream().distinct().sorted().toList();
        if (normalizedKeys.size() != roleKeys.size()) {
            throw new ApiException("Role assignments must not contain duplicates", HttpStatus.BAD_REQUEST);
        }
        if (normalizedKeys.isEmpty()) {
            throw new ApiException("At least one role is required", HttpStatus.BAD_REQUEST);
        }
        if (!normalizedKeys.contains("USER")) {
            normalizedKeys = java.util.stream.Stream.concat(
                            normalizedKeys.stream(), java.util.stream.Stream.of("USER"))
                    .sorted()
                    .toList();
        }
        validateRoleKeys(normalizedKeys);

        if (!membershipExists(targetUserId)) {
            throw new ApiException("User not found or has no active organization membership", HttpStatus.NOT_FOUND);
        }

        boolean grantsRoleManagement = rolesGrantPermission(normalizedKeys, ROLE_MANAGE_PERMISSION);
        if (targetUserId.equals(actorUserId) && !grantsRoleManagement) {
            throw new ApiException(
                    "You cannot remove your own role-management access",
                    HttpStatus.CONFLICT);
        }
        if (hasPermission(targetUserId, ROLE_MANAGE_PERMISSION)
                && !grantsRoleManagement
                && activeRoleManagerCount() <= 1) {
            throw new ApiException(
                    "The final role manager cannot lose role-management permission",
                    HttpStatus.CONFLICT);
        }

        jdbcTemplate.update(
                "DELETE FROM javara_user_role_assignments WHERE user_id = ? AND tenant_id = ?",
                targetUserId,
                defaultTenantId);
        for (String roleKey : normalizedKeys) {
            insertRoleAssignment(targetUserId, defaultTenantId, actorUserId, roleKey);
        }
        bumpMembershipAuthorizationRevision(targetUserId);
    }

    @Transactional
    public void assignInitialRoles(UUID userId, List<String> roleKeys) {
        LinkedHashSet<String> initialRoleKeys = new LinkedHashSet<>(roleKeys);
        initialRoleKeys.add("USER");
        List<String> normalizedKeys = initialRoleKeys.stream().sorted().toList();
        validateRoleKeys(normalizedKeys);

        UUID defaultTenantId = lockDefaultTenant();
        jdbcTemplate.update(
                """
                INSERT INTO javara_tenant_memberships
                    (user_id, tenant_id, authorization_revision)
                VALUES (?, ?, 1)
                """,
                userId,
                defaultTenantId);
        for (String roleKey : normalizedKeys) {
            insertRoleAssignment(userId, defaultTenantId, null, roleKey);
        }
    }

    public List<String> rolesForUser(UUID userId) {
        return authorizationSnapshot(userId).roles();
    }

    public Collection<SimpleGrantedAuthority> authoritiesForUser(UUID userId) {
        return authorizationSnapshot(userId).authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Transactional
    public void recordAuthorizationChange(UUID userId) {
        lockDefaultTenant();
        bumpMembershipAuthorizationRevision(userId);
    }

    private UserAuthorizationSnapshot authorizationSnapshot(UUID userId) {
        TenantAuthorizationState tenant = jdbcTemplate.queryForObject(
                """
                SELECT tenant.id,
                       tenant.authorization_revision,
                       COALESCE(membership.authorization_revision, 0) AS membership_revision
                FROM javara_tenants tenant
                LEFT JOIN javara_tenant_memberships membership
                  ON membership.tenant_id = tenant.id
                 AND membership.user_id = ?
                WHERE tenant.tenant_key = ? AND tenant.status = 'ACTIVE'
                """,
                (result, rowNumber) -> new TenantAuthorizationState(
                        result.getObject("id", UUID.class),
                        result.getLong("authorization_revision"),
                        result.getLong("membership_revision")),
                userId,
                DEFAULT_TENANT_KEY);
        if (tenant == null) {
            throw new IllegalStateException("The default organization is unavailable");
        }

        String cacheKey = AUTHORIZATION_CACHE_KEY_PREFIX
                + ":tenant:" + tenant.id()
                + ":policy-revision:" + tenant.policyRevision()
                + ":membership-revision:" + tenant.membershipRevision()
                + ":user:" + userId;
        UserAuthorizationSnapshot cachedSnapshot = readAuthorizationCache(cacheKey);
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }

        UserAuthorizationSnapshot snapshot = loadAuthorizationSnapshot(userId, tenant.id());
        writeAuthorizationCache(cacheKey, snapshot);
        return snapshot;
    }

    private UserAuthorizationSnapshot loadAuthorizationSnapshot(UUID userId, UUID tenantId) {
        List<RolePermissionRow> rows = jdbcTemplate.query(
                """
                SELECT DISTINCT role.role_key, detail.permission_key
                FROM javara_user_role_assignments assignment
                JOIN javara_tenant_memberships membership
                  ON membership.user_id = assignment.user_id
                 AND membership.tenant_id = assignment.tenant_id
                JOIN javara_tenants tenant ON tenant.id = membership.tenant_id
                JOIN javara_user_roles role ON role.id = assignment.role_id
                JOIN javara_users user_record ON user_record.id = assignment.user_id
                LEFT JOIN javara_user_role_details detail ON detail.role_id = role.id
                WHERE assignment.user_id = ?
                  AND assignment.tenant_id = ?
                  AND tenant.id = ?
                  AND tenant.status = 'ACTIVE'
                  AND membership.status = 'ACTIVE'
                  AND role.active = TRUE
                  AND user_record.is_active = TRUE
                  AND user_record.deleted_at IS NULL
                ORDER BY role.role_key, detail.permission_key
                """,
                (result, rowNumber) -> new RolePermissionRow(
                        result.getString("role_key"),
                        result.getString("permission_key")),
                userId,
                tenantId,
                tenantId);
        Set<String> roles = new TreeSet<>();
        Set<String> authorities = new TreeSet<>();
        for (RolePermissionRow row : rows) {
            roles.add(row.roleKey());
            authorities.add("ROLE_" + row.roleKey());
            if (row.permissionKey() != null) {
                authorities.add(row.permissionKey());
            }
        }
        return new UserAuthorizationSnapshot(
                List.copyOf(roles),
                List.copyOf(authorities));
    }

    private UserAuthorizationSnapshot readAuthorizationCache(String cacheKey) {
        String cachedValue;
        try {
            cachedValue = redisTemplate.opsForValue().get(cacheKey);
        } catch (DataAccessException exception) {
            LOGGER.warn("Authorization cache read failed; loading permissions from PostgreSQL", exception);
            return null;
        }
        if (cachedValue == null) {
            return null;
        }
        try {
            UserAuthorizationSnapshot snapshot =
                    objectMapper.readValue(cachedValue, UserAuthorizationSnapshot.class);
            if (isValidSnapshot(snapshot)) {
                return snapshot;
            }
        } catch (JacksonException exception) {
            LOGGER.warn("Authorization cache entry is invalid; loading permissions from PostgreSQL");
        }
        try {
            redisTemplate.delete(cacheKey);
        } catch (DataAccessException exception) {
            LOGGER.warn("Invalid authorization cache entry could not be removed", exception);
        }
        return null;
    }

    private void writeAuthorizationCache(String cacheKey, UserAuthorizationSnapshot snapshot) {
        try {
            String serialized = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(cacheKey, serialized, AUTHORIZATION_CACHE_TTL);
        } catch (JacksonException exception) {
            LOGGER.warn("Authorization cache entry could not be serialized", exception);
        } catch (DataAccessException exception) {
            LOGGER.warn("Authorization cache write failed; PostgreSQL remains authoritative", exception);
        }
    }

    private static boolean isValidSnapshot(UserAuthorizationSnapshot snapshot) {
        return snapshot != null
                && snapshot.roles() != null
                && snapshot.authorities() != null
                && snapshot.roles().stream().allMatch(UserRoleService::isRoleKey)
                && snapshot.authorities().stream().allMatch(authority ->
                        authority != null
                                && (authority.matches("ROLE_" + ROLE_KEY_PATTERN)
                                        || authority.matches(PERMISSION_KEY_PATTERN)));
    }

    private static boolean isRoleKey(String roleKey) {
        return roleKey != null && roleKey.matches(ROLE_KEY_PATTERN);
    }

    private void bumpTenantAuthorizationRevision(UUID tenantId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE javara_tenants
                SET authorization_revision = authorization_revision + 1
                WHERE id = ?
                """,
                tenantId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Authorization revision could not be advanced for the default organization");
        }
    }

    private void bumpMembershipAuthorizationRevision(UUID userId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE javara_tenant_memberships membership
                SET authorization_revision = membership.authorization_revision + 1
                FROM javara_tenants tenant
                WHERE membership.tenant_id = tenant.id
                  AND membership.user_id = ?
                  AND tenant.tenant_key = ?
                """,
                userId,
                DEFAULT_TENANT_KEY);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Authorization revision could not be advanced for the user's membership");
        }
    }

    public boolean hasRole(UUID userId, String roleKey) {
        return rolesForUser(userId).contains(roleKey);
    }

    private List<String> permissionsForRole(String roleKey) {
        return jdbcTemplate.queryForList(
                """
                SELECT detail.permission_key
                FROM javara_user_role_details detail
                JOIN javara_user_roles role ON role.id = detail.role_id
                WHERE role.role_key = ?
                ORDER BY detail.permission_key
                """,
                String.class,
                roleKey);
    }

    private RoleResponse withPermissions(RoleResponse role) {
        return new RoleResponse(
                role.key(),
                role.displayName(),
                role.description(),
                role.systemManaged(),
                permissionsForRole(role.key()));
    }

    private void insertRolePermissions(UUID roleId, List<String> permissionKeys) {
        for (String permissionKey : permissionKeys.stream().distinct().toList()) {
            jdbcTemplate.update(
                    "INSERT INTO javara_user_role_details (role_id, permission_key) VALUES (?, ?)",
                    roleId,
                    permissionKey);
        }
    }

    private void insertRoleAssignment(
            UUID userId,
            UUID tenantId,
            UUID actorUserId,
            String roleKey) {
        UUID roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM javara_user_roles WHERE role_key = ? AND active = TRUE",
                UUID.class,
                roleKey);
        jdbcTemplate.update(
                """
                INSERT INTO javara_user_role_assignments
                    (user_id, tenant_id, role_id, assigned_by)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                tenantId,
                roleId,
                actorUserId);
    }

    private void validatePermissions(List<String> permissionKeys) {
        Set<String> requested = new LinkedHashSet<>(permissionKeys);
        if (requested.isEmpty()) {
            throw new ApiException("At least one permission is required", HttpStatus.BAD_REQUEST);
        }
        if (requested.size() != permissionKeys.size()) {
            throw new ApiException(
                    "Permission list must not contain duplicates",
                    HttpStatus.BAD_REQUEST);
        }
        List<String> requestedList = List.copyOf(requested);
        String placeholders = String.join(
                ",", java.util.Collections.nCopies(requestedList.size(), "?"));
        List<String> available = jdbcTemplate.queryForList(
                "SELECT permission_key FROM javara_permissions WHERE permission_key IN ("
                        + placeholders + ")",
                String.class,
                requestedList.toArray());
        List<String> unknown = requested.stream()
                .filter(key -> !available.contains(key))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ApiException(
                    "Unknown permissions: " + String.join(", ", unknown),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateRoleKeys(List<String> roleKeys) {
        if (roleKeys.isEmpty()) {
            throw new ApiException("At least one role is required", HttpStatus.BAD_REQUEST);
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(roleKeys.size(), "?"));
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM javara_user_roles WHERE active = TRUE AND role_key IN ("
                        + placeholders + ")",
                Integer.class,
                roleKeys.toArray());
        if (count == null || count != roleKeys.size()) {
            throw new ApiException("One or more roles are unavailable", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean rolesGrantPermission(List<String> roleKeys, String permission) {
        String placeholders = String.join(",", java.util.Collections.nCopies(roleKeys.size(), "?"));
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT role.id)
                FROM javara_user_roles role
                JOIN javara_user_role_details detail ON detail.role_id = role.id
                WHERE role.active = TRUE
                  AND detail.permission_key = ?
                  AND role.role_key IN (
                """ + placeholders + ")",
                Integer.class,
                java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(permission),
                                roleKeys.stream())
                        .toArray());
        return count != null && count > 0;
    }

    private boolean membershipExists(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM javara_tenant_memberships membership
                JOIN javara_tenants tenant ON tenant.id = membership.tenant_id
                WHERE membership.user_id = ?
                  AND tenant.tenant_key = 'default'
                  AND membership.status = 'ACTIVE'
                  AND tenant.status = 'ACTIVE'
                """,
                Integer.class,
                userId);
        return count != null && count > 0;
    }

    private boolean hasPermission(UUID userId, String permission) {
        Boolean hasPermission = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM javara_user_role_assignments assignment
                    JOIN javara_tenant_memberships membership
                      ON membership.user_id = assignment.user_id
                     AND membership.tenant_id = assignment.tenant_id
                    JOIN javara_tenants tenant ON tenant.id = membership.tenant_id
                    JOIN javara_user_role_details detail ON detail.role_id = assignment.role_id
                    JOIN javara_user_roles role ON role.id = assignment.role_id
                    JOIN javara_users user_record ON user_record.id = assignment.user_id
                    WHERE assignment.user_id = ?
                      AND tenant.tenant_key = 'default'
                      AND membership.status = 'ACTIVE'
                      AND tenant.status = 'ACTIVE'
                      AND role.active = TRUE
                      AND user_record.is_active = TRUE
                      AND user_record.deleted_at IS NULL
                      AND detail.permission_key = ?
                )
                """,
                Boolean.class,
                userId,
                permission);
        return Boolean.TRUE.equals(hasPermission);
    }

    private int activeRoleManagerCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT assignment.user_id)
                FROM javara_user_role_assignments assignment
                JOIN javara_tenant_memberships membership
                  ON membership.user_id = assignment.user_id
                 AND membership.tenant_id = assignment.tenant_id
                JOIN javara_tenants tenant ON tenant.id = membership.tenant_id
                JOIN javara_user_role_details detail ON detail.role_id = assignment.role_id
                JOIN javara_user_roles role ON role.id = assignment.role_id
                JOIN javara_users user_record ON user_record.id = assignment.user_id
                WHERE tenant.tenant_key = 'default'
                  AND tenant.status = 'ACTIVE'
                  AND membership.status = 'ACTIVE'
                  AND role.active = TRUE
                  AND user_record.is_active = TRUE
                  AND user_record.deleted_at IS NULL
                  AND detail.permission_key = ?
                """,
                Integer.class,
                ROLE_MANAGE_PERMISSION);
        return count == null ? 0 : count;
    }

    private boolean roleExists(String roleKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM javara_user_roles WHERE role_key = ?",
                Integer.class,
                roleKey);
        return count != null && count > 0;
    }

    private UUID roleId(String roleKey) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM javara_user_roles WHERE role_key = ? AND active = TRUE",
                UUID.class,
                roleKey);
    }

    private UUID lockDefaultTenant() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM javara_tenants WHERE tenant_key = ? FOR UPDATE",
                UUID.class,
                DEFAULT_TENANT_KEY);
    }

    private record TenantAuthorizationState(
            UUID id, long policyRevision, long membershipRevision) {}

    private record RolePermissionRow(String roleKey, String permissionKey) {}
}
