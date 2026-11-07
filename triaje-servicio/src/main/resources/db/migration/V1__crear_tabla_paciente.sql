CREATE TABLE paciente (
    id                  VARCHAR(100) PRIMARY KEY,
    nombre_completo     VARCHAR(100) NOT NULL,
    edad                SMALLINT NOT NULL,
    frecuencia_cardiaca SMALLINT NOT NULL,
    presion_sistolica   SMALLINT NOT NULL,
    presion_diastolica  SMALLINT NOT NULL,
    spo2                SMALLINT NOT NULL,
    temperatura         NUMERIC(3, 1) NOT NULL,
    dolor               SMALLINT NOT NULL,
    gcs                 SMALLINT NOT NULL,
    queja_principal     TEXT NOT NULL,
    nivel               SMALLINT NOT NULL,
    estado              VARCHAR(20) NOT NULL,
    estado_desde        TIMESTAMPTZ NOT NULL,
    escalado            BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_registro      TIMESTAMPTZ NOT NULL,
    sla_minutos         SMALLINT NOT NULL
);

GRANT USAGE ON SCHEMA public TO ${usuario-runtime};
GRANT SELECT, INSERT, UPDATE ON paciente TO ${usuario-runtime};
