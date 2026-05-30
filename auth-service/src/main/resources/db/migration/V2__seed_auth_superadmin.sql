INSERT INTO iam.auth_role (role_name, description, active, created_by) VALUES
    ('SUPER_ADMIN', 'Administrador total del sistema', TRUE, NULL),
    ('ADMIN',       'Administrador operativo (gestion de usuarios y roles)', TRUE, NULL);

INSERT INTO iam.auth_user (
    names, surnames, email, password_hash,
    active, account_locked, account_expired, credentials_expired,
    force_password_change, failed_attempts, created_by
)
VALUES (
    'Administrador', 'Sistema', 'admin@empresa.com',
    '$2a$12$10R4bKBaOmTVXdEfcqBVbey.JxLYYRTWk9KCwSrR1uf0whI4wzUIO',
    TRUE, FALSE, FALSE, FALSE, FALSE, 0, NULL
);

UPDATE iam.auth_role
SET created_by = (SELECT id_user FROM iam.auth_user WHERE email = 'admin@empresa.com')
WHERE role_name IN ('SUPER_ADMIN', 'ADMIN');

UPDATE iam.auth_user SET created_by = id_user
WHERE email = 'admin@empresa.com';

INSERT INTO iam.auth_user_role (id_user, id_role, active, assigned_by)
SELECT u.id_user, r.id_role, TRUE, u.id_user
FROM iam.auth_user u, iam.auth_role r
WHERE u.email = 'admin@empresa.com' AND r.role_name = 'SUPER_ADMIN';

INSERT INTO iam.auth_permission (resource, action, description, active)
VALUES ('USER', 'READ', 'Consultar usuarios', TRUE);

INSERT INTO iam.auth_role_permission (id_role, id_permission, granted_by)
SELECT r.id_role, p.id_permission, u.id_user
FROM iam.auth_role r, iam.auth_permission p, iam.auth_user u
WHERE r.role_name = 'SUPER_ADMIN'
  AND p.resource = 'USER' AND p.action = 'READ'
  AND u.email = 'admin@empresa.com';