-- =========================================================
-- Modelo hibrido de expiracion para refresh tokens.
--   - expiry_at           : ventana deslizante (sliding). Se resetea en cada rotacion.
--   - absolute_expiry_at  : limite duro. Se fija al emitir el primer token de la familia
--                           y se propaga sin cambios a todas las rotaciones siguientes.
-- =========================================================

ALTER TABLE iam.refresh_token
    ADD COLUMN absolute_expiry_at TIMESTAMPTZ NULL;

-- Backfill: para filas existentes, asumimos el mismo expiry_at como tope absoluto
-- (no podemos reconstruir el momento del login original, asi que conservamos el
-- comportamiento previo para esas sesiones ya vivas).
UPDATE iam.refresh_token
SET absolute_expiry_at = expiry_at
WHERE absolute_expiry_at IS NULL;

ALTER TABLE iam.refresh_token
    ALTER COLUMN absolute_expiry_at SET NOT NULL;

COMMENT ON COLUMN iam.refresh_token.absolute_expiry_at IS
    'Limite duro de vida de la sesion. Se fija al emitir el primer token de la familia '
        'y se propaga sin modificarse en cada rotacion. Una vez alcanzado, el usuario '
        'debe reautenticarse aunque el token siga dentro de su ventana deslizante.';
