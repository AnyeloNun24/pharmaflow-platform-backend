-- =========================================================
-- TABLE: OUTBOX_EVENT  (Transactional Outbox)
-- =========================================================
-- Garantiza atomicidad entre el cambio de negocio y la publicacion del evento:
-- la fila se inserta en la MISMA transaccion que crea el usuario / emite el token,
-- y un relay la publica luego a Kafka. Si la transaccion hace rollback no hay fila
-- (no se publica); si el broker esta caido, la fila queda pendiente y se reintenta.
CREATE TABLE iam.outbox_event (

    id           BIGINT        NOT NULL    GENERATED ALWAYS AS IDENTITY,
    event_type   VARCHAR(100)  NOT NULL,
    topic        VARCHAR(150)  NOT NULL,
    message_key  VARCHAR(100)  NULL,
    payload      TEXT          NOT NULL,
    request_id   VARCHAR(100)  NULL,
    created_at   TIMESTAMPTZ   NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ   NULL,

    CONSTRAINT pk__outbox_event__id PRIMARY KEY (id)

);

-- INDEXES

-- Indice parcial: el relay solo consulta pendientes (published_at IS NULL), por antiguedad.
CREATE INDEX idx__outbox_event__pending
    ON iam.outbox_event (created_at)
    WHERE published_at IS NULL;

-- COMMENTS

COMMENT ON TABLE iam.outbox_event IS
    'Bandeja de salida transaccional: eventos de dominio pendientes de publicar en Kafka.';

COMMENT ON COLUMN iam.outbox_event.event_type IS
    'Tipo logico del evento (USER_CREATED, PASSWORD_RESET_REQUESTED). Informativo/diagnostico.';

COMMENT ON COLUMN iam.outbox_event.topic IS
    'Topic de Kafka destino.';

COMMENT ON COLUMN iam.outbox_event.message_key IS
    'Clave del mensaje (id de usuario) para afinidad de particion y orden.';

COMMENT ON COLUMN iam.outbox_event.payload IS
    'Cuerpo JSON del evento ya serializado, con su discriminador "type".';

COMMENT ON COLUMN iam.outbox_event.request_id IS
    'Correlacion (X-Request-Id) capturada al crear el evento; se reenvia como header de Kafka.';

COMMENT ON COLUMN iam.outbox_event.created_at IS
    'Momento de registro del evento en la bandeja.';

COMMENT ON COLUMN iam.outbox_event.published_at IS
    'Momento de publicacion exitosa en Kafka; NULL = pendiente.';
