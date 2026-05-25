-- =========================================================
-- SCHEMA
-- =========================================================
CREATE SCHEMA IF NOT EXISTS iam;

-- =========================================================
-- TABLE: AUTH_ROLE
-- =========================================================
CREATE TABLE iam.auth_role (

    id_role     BIGINT          NOT NULL    GENERATED ALWAYS AS IDENTITY,
    role_name   VARCHAR(50)     NOT NULL,
    description VARCHAR(255)    NULL,
    active      BOOLEAN         NOT NULL    DEFAULT TRUE,
    created_by  BIGINT          NULL,
    created_at  TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT          NULL,
    updated_at  TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key
    CONSTRAINT pk__auth_role__id_role PRIMARY KEY (id_role),

    -- Unique
    CONSTRAINT uk__auth_role__role_name UNIQUE (role_name),

    -- Check: nombre solo mayúsculas, números y guion bajo
    CONSTRAINT ck__auth_role__role_name_format
        CHECK (role_name ~ '^[A-Z][A-Z0-9_]{1,49}$')

);

-- INDEXES

-- Indice de role_name ya creado por el constraint Unique

CREATE INDEX idx__auth_role__active_true
    ON iam.auth_role (active)
    WHERE active = TRUE;

-- COMMENTS

COMMENT ON TABLE iam.auth_role IS
    'Define los roles disponibles en el sistema (ej. SUPER_ADMIN, ADMIN).';

COMMENT ON COLUMN iam.auth_role.id_role IS
    'Identificador único del rol.';

COMMENT ON COLUMN iam.auth_role.role_name IS
    'Nombre del rol en mayúsculas. Ejemplo: SUPER_ADMIN, ADMIN.';

COMMENT ON COLUMN iam.auth_role.description IS
    'Descripción funcional del rol para uso administrativo y documentación.';

COMMENT ON COLUMN iam.auth_role.active IS
    'Estado del rol: TRUE = activo y asignable. FALSE = desactivado; '
        'no puede asignarse a nuevos usuarios aunque los existentes no se ven afectados inmediatamente.';

COMMENT ON COLUMN iam.auth_role.created_by IS
    'Identificador del usuario que creó el rol.';

COMMENT ON COLUMN iam.auth_role.created_at IS
    'Timestamp de creación del rol en UTC.';

COMMENT ON COLUMN iam.auth_role.updated_by IS
    'Identificador del usuario que realizó la última modificación del rol.';

COMMENT ON COLUMN iam.auth_role.updated_at IS
    'Timestamp de la última modificación del rol en UTC.';

-- =============================================================================
-- TABLE: AUTH_PERMISSION
-- =============================================================================
CREATE TABLE iam.auth_permission (
    id_permission   BIGINT          NOT NULL GENERATED ALWAYS AS IDENTITY,
    resource        VARCHAR(50)     NOT NULL,
    action          VARCHAR(20)     NOT NULL,
    description     VARCHAR(255)    NULL,
    active          BOOLEAN         NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL    DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key
    CONSTRAINT pk__auth_permission__id_permission
        PRIMARY KEY (id_permission),

    -- Unique
    CONSTRAINT uk__auth_permission__resource_action UNIQUE (resource, action),

    -- Check: recurso solo mayúsculas, números y guion bajo
    CONSTRAINT ck__auth_permission__resource_format
        CHECK (resource ~ '^[A-Z][A-Z0-9_]{0,49}$'),

    -- Check: acciones permitidas
    CONSTRAINT ck__auth_permission__action_values
        CHECK (action IN (
            'CREATE',   -- Crear registros
            'READ',     -- Leer / listar
            'UPDATE',   -- Modificar registros existentes
            'DELETE',   -- Eliminar / desactivar
            'EXPORT',   -- Exportar datos (Excel, PDF, CSV)
            'IMPORT',   -- Importar datos masivos
            'APPROVE',  -- Aprobar flujos de trabajo
            'REJECT',   -- Rechazar flujos de trabajo
            'PRINT',    -- Generar impresiones / comprobantes
            'EXECUTE'   -- Ejecutar procesos o reportes especiales
        ))

);

-- INDEXES

-- Indice de "resource" y "action" ya creado por el constraint Unique

-- COMMENTS

COMMENT ON TABLE iam.auth_permission IS
    'Catálogo de permisos granulares del ERP siguiendo el patrón RESOURCE:ACTION. '
    'Cada permiso define qué operación se puede ejecutar sobre qué módulo de negocio. '
    'Los permisos se asignan a roles, no a usuarios directamente.';

COMMENT ON COLUMN iam.auth_permission.id_permission IS
    'Identificador único del permiso (clave primaria).';

COMMENT ON COLUMN iam.auth_permission.resource IS
    'Módulo o entidad de negocio sobre el que aplica el permiso '
        '(ej: INVOICE, USER, INVENTORY, PAYMENT, REPORT). Solo mayúsculas con guion bajo.';

COMMENT ON COLUMN iam.auth_permission.action IS
    'Operación permitida sobre el recurso. Valores controlados por CHECK constraint: '
        'CREATE, READ, UPDATE, DELETE, EXPORT, IMPORT, APPROVE, REJECT, PRINT, EXECUTE.';

COMMENT ON COLUMN iam.auth_permission.description IS
    'Descripción legible del permiso para la interfaz administrativa '
        '(ej: "Permite exportar el listado de facturas a Excel").';

COMMENT ON COLUMN iam.auth_permission.active IS
    'Estado del permiso: TRUE = activo y evaluado en runtime. '
        'FALSE = desactivado globalmente (ningún rol lo tendrá efectivo).';

COMMENT ON COLUMN iam.auth_permission.created_at IS
    'Timestamp de creación del permiso en UTC.';

-- =============================================================================
-- TABLE: AUTH_ROLE_PERMISSION
-- =============================================================================

CREATE TABLE iam.auth_role_permission (
    id_role         BIGINT          NOT NULL,
    id_permission   BIGINT          NOT NULL,
    granted_by      BIGINT          NULL,
    granted_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key compuesta
    CONSTRAINT pk__auth_role_permission__id_role__id_permission
        PRIMARY KEY (id_role, id_permission),

    -- Foreign Keys
    CONSTRAINT fk__auth_role_permission__auth_role__id_role
        FOREIGN KEY (id_role)
        REFERENCES iam.auth_role (id_role)
        ON DELETE CASCADE,

    CONSTRAINT fk__auth_role_permission__auth_permission__id_permission
        FOREIGN KEY (id_permission)
        REFERENCES iam.auth_permission (id_permission)
        ON DELETE CASCADE

    -- fk_role_permission_granted_by se añade después de crear auth_user
);

-- INDEXES

-- Índice de id_role ya creada por el primary compuesto

-- Índices para joins y navegación inversa
CREATE INDEX idx__auth_role_permission__id_permission
    ON iam.auth_role_permission (id_permission);

-- COMMENTS

COMMENT ON TABLE iam.auth_role_permission IS
    'Relación N:M entre roles y permisos. Define exactamente qué operaciones '
    'puede ejecutar cada rol en el ERP. Registra quién asignó el permiso para auditoría.';

COMMENT ON COLUMN iam.auth_role_permission.id_role IS
    'Referencia al rol que recibe el permiso.';

COMMENT ON COLUMN iam.auth_role_permission.id_permission IS
    'Referencia al permiso asignado al rol.';

COMMENT ON COLUMN iam.auth_role_permission.granted_at IS
    'Timestamp del momento en que se asignó el permiso al rol en UTC.';

COMMENT ON COLUMN iam.auth_role_permission.granted_by IS
    'ID del usuario administrador que realizó la asignación. '
    'NULL permitido para datos iniciales (seed/migrations). '
    'FK a auth_user.id_user añadida mediante ALTER después de crear la tabla.';

-- =========================================================
-- TABLE: AUTH_USER
-- =========================================================
CREATE TABLE iam.auth_user (

    id_user                 BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    names                   VARCHAR(80) NOT NULL,
    surnames                VARCHAR(80) NOT NULL,
    phone_number            VARCHAR(20) NULL,
    birth_date              DATE NULL,
    gender                  CHAR(1),
    profile_photo_url       VARCHAR(500)    NULL,
    email                   VARCHAR(100)    NOT NULL,
    password_hash           VARCHAR(255)    NULL,
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    account_locked          BOOLEAN         NOT NULL DEFAULT FALSE,
    account_expired         BOOLEAN         NOT NULL DEFAULT FALSE,
    credentials_expired     BOOLEAN         NOT NULL DEFAULT FALSE,
    force_password_change   BOOLEAN         NOT NULL DEFAULT TRUE,
    failed_attempts         SMALLINT        NOT NULL DEFAULT 0,
    locked_at               TIMESTAMPTZ     NULL,
    last_login_at           TIMESTAMPTZ     NULL,
    password_changed_at     TIMESTAMPTZ     NULL,
    account_expires_at      TIMESTAMPTZ     NULL,
    credentials_expires_at  TIMESTAMPTZ     NULL,
    created_by              BIGINT          NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT          NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key
    CONSTRAINT pk__auth_user__id_user PRIMARY KEY (id_user),

    -- Check: formato de email RFC 5322 básico
    CONSTRAINT ck__auth_user__email_format
        CHECK (email ~ '^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$'),

    -- Check: contador de intentos no puede ser negativo
    CONSTRAINT ck__auth_user__failed_attempts_non_negative
        CHECK (failed_attempts >= 0),

    -- Check: formato de teléfono permisivo
    CONSTRAINT ck__auth_user__phone_number_format
        CHECK (phone_number IS NULL OR phone_number ~ '^\+?[0-9\s\-\(\)]{7,20}$'),

    -- Check: género
    CONSTRAINT ck__auth_user__gender_values
        CHECK (gender IS NULL OR gender IN ('M', 'F', 'O', 'N')),

    -- Check: consistencia entre account_locked y locked_at
    --   Si está bloqueado  → locked_at debe tener valor
    --   Si no está bloqueado → locked_at debe ser NULL
    CONSTRAINT ck__auth_user__locked_at_consistency
        CHECK (
            (account_locked = FALSE AND locked_at IS NULL)
                OR
            (account_locked = TRUE  AND locked_at IS NOT NULL)
            )

);

-- INDEXES

-- Unique case-insensitive sobre email (motor de autenticación principal)
CREATE UNIQUE INDEX idx__auth_user__email_ci
    ON iam.auth_user (LOWER(email));

-- Índice parcial: consultas de usuarios activos (la mayoría de las queries operativas)
CREATE INDEX idx__auth_user__active_true
    ON iam.auth_user (active)
    WHERE active = TRUE;

-- Índice parcial: monitoreo de cuentas bloqueadas (panel de administración)
CREATE INDEX idx__auth_user__account_locked_true
    ON iam.auth_user (account_locked)
    WHERE account_locked = TRUE;

-- Índice parcial: usuarios que deben cambiar contraseña (dashboard admin)
CREATE INDEX idx__auth_user__force_password_change
    ON iam.auth_user (force_password_change)
    WHERE force_password_change = TRUE;

-- Índice para consultas de expiración (job programado de mantenimiento)
CREATE INDEX idx__auth_user__account_expires_at
    ON iam.auth_user (account_expires_at)
    WHERE account_expires_at IS NOT NULL;

CREATE INDEX idx__auth_user__credentials_expires_at
    ON iam.auth_user (credentials_expires_at)
    WHERE credentials_expires_at IS NOT NULL;

-- COMMENTS

COMMENT ON TABLE iam.auth_user IS
    'Usuarios del ERP. Creados exclusivamente por administradores (no hay auto-registro). '
    'Implementa completamente la interfaz UserDetails de Spring Security. '
    'Para múltiples roles usar la tabla auth_user_role (N:M).';

COMMENT ON COLUMN iam.auth_user.id_user IS
    'Identificador único del usuario (clave primaria). BIGINT para escala a largo plazo.';
COMMENT ON COLUMN iam.auth_user.names IS
    'Nombre(s) del usuario.';
COMMENT ON COLUMN iam.auth_user.surnames IS
    'Apellido(s) del usuario.';
COMMENT ON COLUMN iam.auth_user.phone_number IS
    'Teléfono de contacto. Opcional. Formato internacional permisivo (+51 999 888 777).';
COMMENT ON COLUMN iam.auth_user.birth_date IS
    'Fecha de nacimiento. Opcional. Usada para validaciones de edad o reportes de RRHH.';
COMMENT ON COLUMN iam.auth_user.gender IS
    'Género del usuario: M = Masculino, F = Femenino, O = Otro, N = No especificado. Opcional.';
COMMENT ON COLUMN iam.auth_user.profile_photo_url IS
    'URL de la foto de perfil almacenada en servicio externo (S3, GCS, etc.). '
    'NULL cuando el usuario no ha subido foto. VARCHAR(500) para URLs largas con paths y tokens.';
COMMENT ON COLUMN iam.auth_user.email IS
    'Correo electrónico. Identificador principal de login. Único case-insensitive por índice parcial.';
COMMENT ON COLUMN iam.auth_user.password_hash IS
    'Contraseña hasheada (BCrypt strength 12 recomendado). '
    'NULL para futura integración OAuth2/SSO/LDAP donde el proveedor externo gestiona la credencial.';
COMMENT ON COLUMN iam.auth_user.active IS
    'Mapea a UserDetails.isEnabled(). FALSE = usuario desactivado sin eliminarlo (soft-delete).';
COMMENT ON COLUMN iam.auth_user.account_locked IS
    'Mapea a !UserDetails.isAccountNonLocked(). TRUE = cuenta bloqueada por intentos o admin.';
COMMENT ON COLUMN iam.auth_user.account_expired IS
    'Mapea a !UserDetails.isAccountNonExpired(). TRUE = cuenta vencida administrativamente.';
COMMENT ON COLUMN iam.auth_user.credentials_expired IS
    'Mapea a !UserDetails.isCredentialsNonExpired(). TRUE = credenciales vencidas por política.';
COMMENT ON COLUMN iam.auth_user.force_password_change IS
    'TRUE = el usuario debe cambiar su contraseña en el próximo login. '
    'Se activa en creación de cuenta y en reset por admin. '
    'Acción administrativa puntual, distinto de credentials_expired que es política de rotación.';
COMMENT ON COLUMN iam.auth_user.failed_attempts IS
    'Intentos de login fallidos consecutivos. Se reinicia a 0 en login exitoso. '
    'Cuando alcanza max_failed_attempts la app activa account_locked.';
COMMENT ON COLUMN iam.auth_user.locked_at IS
    'Timestamp del bloqueo. NULL si no está bloqueada. '
    'Consistencia garantizada con account_locked por CHECK ck__auth_user__locked_at_consistency.';
COMMENT ON COLUMN iam.auth_user.last_login_at IS
    'Último login exitoso. NULL si nunca inició sesión. Útil para detectar cuentas inactivas.';
COMMENT ON COLUMN iam.auth_user.password_changed_at IS
    'Último cambio de contraseña. NULL si usa aún la contraseña inicial del admin.';
COMMENT ON COLUMN iam.auth_user.account_expires_at IS
    'Expiración de cuenta. NULL = nunca expira. '
    'Job programado evalúa NOW() > account_expires_at y activa account_expired.';
COMMENT ON COLUMN iam.auth_user.credentials_expires_at IS
    'Expiración de credenciales. NULL = nunca expiran. '
    'Se recalcula al cambiar contraseña (ej: NOW() + INTERVAL 90 days). '
    'Job programado evalúa NOW() > credentials_expires_at y activa credentials_expired.';
COMMENT ON COLUMN iam.auth_user.created_by IS
    'Admin que provisionó la cuenta. NULL solo para el usuario inicial (bootstrap). '
    'FK auto-referencial → auth_user(id_user).';
COMMENT ON COLUMN iam.auth_user.created_at IS
    'Timestamp de creación en UTC.';
COMMENT ON COLUMN iam.auth_user.updated_by IS
    'Último admin que modificó el registro. FK auto-referencial → auth_user(id_user).';
COMMENT ON COLUMN iam.auth_user.updated_at IS
    'Timestamp de última modificación en UTC. Actualizado por trigger automático.';

-- FK: auth_role_permission.granted_by → auth_user.id_user
ALTER TABLE iam.auth_role_permission
    ADD CONSTRAINT fk__auth_role_permission__auth_user__granted_by
        FOREIGN KEY (granted_by)
            REFERENCES iam.auth_user (id_user)
            ON DELETE SET NULL;

-- FK: auth_user.created_by → auth_user.id_user (auto-referencial)
ALTER TABLE iam.auth_user
    ADD CONSTRAINT fk__auth_user__auth_user__created_by
        FOREIGN KEY (created_by)
            REFERENCES iam.auth_user (id_user)
            ON DELETE SET NULL;

ALTER TABLE iam.auth_user
    ADD CONSTRAINT fk__auth_user__auth_user__updated_by
        FOREIGN KEY (updated_by)
            REFERENCES iam.auth_user (id_user)
            ON DELETE SET NULL;

-- FK: auth_role.created_by → auth_user.id_user (auto-referencial)
ALTER TABLE iam.auth_role
    ADD CONSTRAINT fk__auth_role__auth_user__created_by
        FOREIGN KEY (created_by)
            REFERENCES iam.auth_user (id_user)
            ON DELETE SET NULL;

ALTER TABLE iam.auth_role
    ADD CONSTRAINT fk__auth_role__auth_user__updated_by
        FOREIGN KEY (updated_by)
            REFERENCES iam.auth_user (id_user)
            ON DELETE SET NULL;

-- =========================================================
-- TABLE: AUTH_USER_ROLE
-- =========================================================
CREATE TABLE iam.auth_user_role (

    id_user         BIGINT          NOT NULL,
    id_role         BIGINT          NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    expires_at      TIMESTAMPTZ     NULL,
    assigned_by     BIGINT          NULL,
    assigned_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by      BIGINT          NULL,
    revoked_at      TIMESTAMPTZ     NULL,

    -- Primary key
    CONSTRAINT pk__auth_user_role__id_user__id_role
        PRIMARY KEY (id_user, id_role),

    -- Foreign key
    CONSTRAINT fk__auth_user_role__auth_user__id_user
        FOREIGN KEY (id_user)
        REFERENCES iam.auth_user (id_user)
        ON DELETE CASCADE,

    CONSTRAINT fk__auth_user_role__auth_role__id_role
        FOREIGN KEY (id_role)
        REFERENCES iam.auth_role (id_role)
        ON DELETE CASCADE,

    CONSTRAINT fk__auth_user_role__assigned_by
        FOREIGN KEY (assigned_by)
        REFERENCES iam.auth_user (id_user)
        ON DELETE SET NULL,

    CONSTRAINT fk__auth_user_role__revoked_by
        FOREIGN KEY (revoked_by)
        REFERENCES iam.auth_user (id_user)
        ON DELETE SET NULL,

    -- Check: consistencia activo/revocado
    CONSTRAINT ck__auth_user_role__revoked_consistency
        CHECK (
            (active = TRUE  AND revoked_at IS NULL)
            OR
            (active = FALSE AND revoked_at IS NOT NULL)
        )
);

-- INDEXES

-- Consulta principal de Spring Security: cargar todos los roles activos de un usuario
CREATE INDEX idx__auth_user_role__id_user_active
    ON iam.auth_user_role (id_user)
    WHERE active = TRUE;

-- Navegación inversa: todos los usuarios con un rol específico
CREATE INDEX idx__auth_user_role__id_role
    ON iam.auth_user_role (id_role);

-- Job programado: asignaciones de roles que vencen próximamente
CREATE INDEX idx__auth_user_role__expires_at
    ON iam.auth_user_role (expires_at)
    WHERE expires_at IS NOT NULL AND active = TRUE;

-- COMMENTS

COMMENT ON TABLE iam.auth_user_role IS
    'Tabla pivote N:M para asignación de múltiples roles a un usuario. ';

COMMENT ON COLUMN iam.auth_user_role.id_user IS
    'Referencia al usuario que recibe el rol.';
COMMENT ON COLUMN iam.auth_user_role.id_role IS
    'Referencia al rol asignado.';
COMMENT ON COLUMN iam.auth_user_role.active IS
    'Estado de la asignación: TRUE = rol vigente, FALSE = revocado. '
    'Se preserva en FALSE para historial de auditoría, nunca se borra.';
COMMENT ON COLUMN iam.auth_user_role.expires_at IS
    'Fecha de vencimiento del rol. NULL = permanente. '
    'Útil para accesos temporales: consultores, cobertura de ausencia, permisos de emergencia. '
    'Job programado evalúa NOW() > expires_at y desactiva la asignación automáticamente.';
COMMENT ON COLUMN iam.auth_user_role.assigned_at IS
    'Timestamp de cuando se asignó el rol en UTC.';
COMMENT ON COLUMN iam.auth_user_role.assigned_by IS
    'Admin que realizó la asignación. NULL para datos de migración/seed inicial.';
COMMENT ON COLUMN iam.auth_user_role.revoked_at IS
    'Timestamp de cuando se revocó el rol. NULL si la asignación sigue activa. '
    'Consistente con active por CHECK ck__auth_user_role__revoked_consistency.';
COMMENT ON COLUMN iam.auth_user_role.revoked_by IS
    'Admin que revocó el rol. NULL si aún no fue revocado.';

-- =========================================================
-- TABLE: REFRESH_TOKEN
-- =========================================================
CREATE TABLE iam.refresh_token (

    id_refresh_token    BIGINT          NOT NULL GENERATED ALWAYS AS IDENTITY,
    id_user             BIGINT          NOT NULL,
    token_hash          VARCHAR(64)     NOT NULL,
    token_family        UUID            NOT NULL DEFAULT gen_random_uuid(),
    replaced_by_hash    VARCHAR(64)     NULL,
    ip_address          INET            NULL,
    user_agent          VARCHAR(512)    NULL,
    expiry_at           TIMESTAMPTZ     NOT NULL,
    revoked             BOOLEAN NOT     NULL DEFAULT FALSE,
    revoked_at          TIMESTAMPTZ     NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key
    CONSTRAINT pk__refresh_token__id_refresh_token
        PRIMARY KEY (id_refresh_token),

    -- Foreign key
    CONSTRAINT fk__refresh_token__auth_user__id_user
        FOREIGN KEY (id_user)
            REFERENCES iam.auth_user(id_user),

    -- Unique
    CONSTRAINT uk__refresh_token__token_hash
        UNIQUE (token_hash),

    -- Check
    CONSTRAINT ck__refresh_token__revocation
        CHECK (
            (revoked = FALSE AND revoked_at IS NULL)
                OR
            (revoked = TRUE AND revoked_at IS NOT NULL)
            )

);

-- INDEXES

-- Sesiones activas de un usuario (listar dispositivos, revocar todo)
CREATE INDEX idx__refresh_token__id_user
    ON iam.refresh_token(id_user);

-- Detección de reutilización de tokens ya rotados
CREATE INDEX idx__refresh_token__token_family
    ON iam.refresh_token(token_family);

-- Limpieza de tokens expirados (solo los activos importan)
CREATE INDEX idx__refresh_token__expiry_at__active
    ON iam.refresh_token(expiry_at)
    WHERE revoked = FALSE;

-- COMMENTS

COMMENT ON TABLE iam.refresh_token IS
    'Tokens de larga duración emitidos para renovar access tokens JWT sin reautenticación.';

COMMENT ON COLUMN iam.refresh_token.id_refresh_token IS
    'Identificador único del refresh token.';

COMMENT ON COLUMN iam.refresh_token.id_user IS
    'Referencia al usuario dueño de la sesión.';

COMMENT ON COLUMN iam.refresh_token.token_hash IS
    'Hash SHA-256 del refresh token en formato hexadecimal (64 chars). '
        'El token original no se persiste; solo el hash, por seguridad.';

COMMENT ON COLUMN iam.refresh_token.token_family IS
    'UUID compartido entre un token y todos sus descendientes por rotación. '
        'Si se detecta reutilización de un token ya rotado, se revocan todos los de la misma familia.';

COMMENT ON COLUMN iam.refresh_token.replaced_by_hash IS
    'Hash del token hijo que reemplazó a este durante la rotación. '
        'Permite auditar la cadena completa de renovaciones de una sesión.';

COMMENT ON COLUMN iam.refresh_token.ip_address IS
    'Dirección IP desde donde se creó la sesión. Útil para auditoría y detección de anomalías.';

COMMENT ON COLUMN iam.refresh_token.user_agent IS
    'User-Agent del cliente que originó la sesión.';

COMMENT ON COLUMN iam.refresh_token.revoked IS
    'Indica si el token fue invalidado: FALSE = válido, TRUE = revocado.';

COMMENT ON COLUMN iam.refresh_token.revoked_at IS
    'Fecha y hora en que el token fue revocado. NULL si aún está activo.';

COMMENT ON COLUMN iam.refresh_token.expiry_at IS
    'Fecha y hora límite de validez del token en UTC.';

COMMENT ON COLUMN iam.refresh_token.created_at IS
    'Fecha y hora de emisión del token en UTC.';

COMMENT ON COLUMN iam.refresh_token.updated_at IS
    'Fecha y hora de la última modificación del registro en UTC.';

-- =========================================================
-- TABLE: PASSWORD_TOKEN
-- =========================================================
CREATE TABLE iam.password_token (

    id_password_token   BIGINT          NOT NULL GENERATED ALWAYS AS IDENTITY,
    id_user             BIGINT          NOT NULL,
    token               VARCHAR(100)         NOT NULL,
    type                VARCHAR(20)     NOT NULL,
    used                BOOLEAN NOT     NULL DEFAULT FALSE,
    used_at             TIMESTAMPTZ     NULL,
    expiry_at           TIMESTAMPTZ     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key
    CONSTRAINT pk__password_token__id_password_token
        PRIMARY KEY (id_password_token),

    -- Foreign Key
    CONSTRAINT fk__password_token__auth_user__id_user
        FOREIGN KEY (id_user)
        REFERENCES iam.auth_user(id_user),

    -- Unique
    CONSTRAINT uk__password_token__token
        UNIQUE (token),

    -- Check constraint
    CONSTRAINT ck__password_token__type
        CHECK (type IN ('SET_PASSWORD', 'RESET_PASSWORD')),

    CONSTRAINT ck__password_token__used_consistency
        CHECK (used = FALSE OR used_at IS NOT NULL)

);

-- INDEXES

CREATE INDEX idx__password_token__id_user
    ON iam.password_token(id_user);

CREATE INDEX idx__password_token__expiry_at__active_false
    ON iam.password_token(expiry_at)
    WHERE used = FALSE;

-- COMMENTS

COMMENT ON TABLE iam.password_token IS
    'Tokens de un solo uso para creación y recuperación de contraseña, enviados al correo del usuario.';

COMMENT ON COLUMN iam.password_token.id_password_token IS
    'Identificador único del token.';

COMMENT ON COLUMN iam.password_token.id_user IS
    'Referencia al usuario dueño del token.';

COMMENT ON COLUMN iam.password_token.token IS
    'Valor único del token (UUID). Se envía dentro del link al correo.';

COMMENT ON COLUMN iam.password_token.type IS
    'Propósito del token: set_password = primer acceso, reset_password = recuperación.';

COMMENT ON COLUMN iam.password_token.used IS
    'Indica si el token ya fue consumido. TRUE = usado, FALSE = disponible.';

COMMENT ON COLUMN iam.password_token.used_at IS
    'Fecha y hora en que el token fue consumido. NULL si aún no se ha usado.';

COMMENT ON COLUMN iam.password_token.expiry_at IS
    'Fecha y hora límite de validez del token en UTC.';

COMMENT ON COLUMN iam.password_token.created_at IS
    'Fecha y hora de creación del token en UTC.';

-- =========================================================
-- TABLE: AUTH_AUDIT_LOG
-- =========================================================
CREATE TABLE iam.auth_audit_log (

    id_audit_log    BIGINT          NOT NULL GENERATED ALWAYS AS IDENTITY,
    id_user         BIGINT          NULL,
    request_id      UUID            NOT NULL,
    action_type     VARCHAR(30)     NOT NULL,
    description     VARCHAR(300)    NOT NULL,
    success         BOOLEAN         NOT NULL,
    ip_address      INET            NOT NULL,
    user_agent      VARCHAR(255)    NULL,
    failure_reason  VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Primary Key
    CONSTRAINT pk__auth_audit_log__id_audit_log
        PRIMARY KEY (id_audit_log),

    -- Foreign Key
    CONSTRAINT fk__auth_audit_log__auth_user__id_user
        FOREIGN KEY (id_user)
        REFERENCES iam.auth_user(id_user),

    -- Check constraints
    CONSTRAINT ck__auth_audit_log__action_type
        CHECK (action_type IN (
            'LOGIN', 'LOGOUT', 'LOGIN_FAILED',
            'REFRESH_TOKEN', 'REFRESH_TOKEN_REUSE',
            'SET_PASSWORD', 'RESET_PASSWORD',
            'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED',
            'ROLE_ASSIGNED', 'ROLE_REVOKED',
            'PASSWORD_CHANGED'
        )),

    CONSTRAINT ck__auth_audit_log__failure_reason
        CHECK (
            (success = TRUE AND failure_reason IS NULL)
                OR
            (success = FALSE AND failure_reason IS NOT NULL)
            )

);

CREATE INDEX idx__auth_audit_log__id_user
    ON iam.auth_audit_log(id_user);

CREATE INDEX idx__auth_audit_log__created_at
    ON iam.auth_audit_log(created_at);

CREATE INDEX idx__auth_audit_log__request_id
    ON iam.auth_audit_log(request_id);

COMMENT ON TABLE iam.auth_audit_log IS
    'Registro inmutable de eventos de autenticación y autorización, exitosos y fallidos.';

COMMENT ON COLUMN iam.auth_audit_log.id_audit_log IS
    'Identificador único del evento de auditoría.';

COMMENT ON COLUMN iam.auth_audit_log.id_user IS
    'Usuario autenticado que ejecutó la acción.';

COMMENT ON COLUMN iam.auth_audit_log.request_id IS
    'Identificador único de correlación de la petición para trazabilidad distribuida.';

COMMENT ON COLUMN iam.auth_audit_log.action_type IS
    'Tipo de acción: LOGIN, LOGOUT, REGISTER, etc.';

COMMENT ON COLUMN iam.auth_audit_log.description IS
    'Detalle adicional sobre la acción realizada.';

COMMENT ON COLUMN iam.auth_audit_log.success IS
    'Indica si la acción fue exitosa: TRUE = éxito, FALSE = fallo.';

COMMENT ON COLUMN iam.auth_audit_log.ip_address IS
    'Dirección IP desde la cual se realizó la acción de autenticación.';

COMMENT ON COLUMN iam.auth_audit_log.user_agent IS
    'Información del cliente o navegador que realizó la petición.';

COMMENT ON COLUMN iam.auth_audit_log.failure_reason IS
    'Motivo del fallo de autenticación o autorización, si aplica.';

COMMENT ON COLUMN iam.auth_audit_log.created_at IS
    'Fecha y hora de creación del registro de auditoría en UTC.';

CREATE OR REPLACE FUNCTION iam.fn_set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iam.fn_set_updated_at IS
    'Trigger reutilizable: actualiza updated_at a CURRENT_TIMESTAMP en cada UPDATE.';

CREATE TRIGGER trg__auth_role__updated_at
    BEFORE UPDATE ON iam.auth_role
    FOR EACH ROW
EXECUTE FUNCTION iam.fn_set_updated_at();

CREATE TRIGGER trg__auth_user__updated_at
    BEFORE UPDATE ON iam.auth_user
    FOR EACH ROW
EXECUTE FUNCTION iam.fn_set_updated_at();

CREATE TRIGGER trg__refresh_token__updated_at
    BEFORE UPDATE ON iam.refresh_token
    FOR EACH ROW
EXECUTE FUNCTION iam.fn_set_updated_at();
