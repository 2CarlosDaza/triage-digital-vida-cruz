# Guía: persistencia (R2DBC, PostgreSQL, Flyway)

## Acceso [OBLIGATORIO]
- R2DBC. Las consultas complejas (reporte, escalamiento) son SQL explícito con `DatabaseClient`, no consultas derivadas.
- Pool R2DBC con tamaño y timeouts por propiedades.
- Cambio de estado: `UPDATE ... WHERE id = :id AND estado = :esperado`. Si afecta 0 filas se relee: no existe → 404; existe en otro estado → 409.

## Tiempo [OBLIGATORIO]
- El instante entra como parámetro (`:ahora`), tomado del `Clock`. Columnas `timestamptz` en UTC.
- Prohibido `now()` en condiciones de negocio. Solo se tolera en valores por defecto de columnas técnicas.

## Escalamiento atómico (RN-03) [OBLIGATORIO]
- Una sola sentencia marca y devuelve los vencidos, por ejemplo `UPDATE paciente SET escalado = true ... WHERE estado = 'REGISTRADO' AND escalado = false AND fecha_registro + sla_minutos * interval '1 minute' < :ahora RETURNING ...`.
- La auditoría y el evento de outbox se insertan en la misma transacción (o en la misma sentencia con CTE). Si no afecta filas, no hay auditoría ni evento.
- El SLA se guarda en el paciente al registrar (`sla_minutos`), calculado desde las propiedades. Es seguro con varias réplicas.

## Migraciones con Flyway [OBLIGATORIO]
- Es la única excepción a "sin JDBC": solo al arrancar, con datasource y driver JDBC propios para migrar (`spring.flyway.*`), con un usuario distinto del de runtime. Verifica que no queda un pool JDBC abierto después de migrar.
- Varias réplicas pueden arrancar a la vez: Flyway serializa las migraciones con un bloqueo. Compruébalo arrancando dos instancias contra la misma base. Las probes de arranque deben tolerar el tiempo de migración.
- Archivos `V<n>__descripcion_en_espanol.sql`, inmutables una vez integrados (un cambio es una migración nueva). Sin DDL manual.

## Auditoría inmutable (RN-04)
- [OBLIGATORIO] Solo `INSERT` desde la aplicación. Sin endpoints de borrado ni edición.
- [OBLIGATORIO] Trigger `BEFORE UPDATE OR DELETE` sobre `auditoria` que lanza excepción, con prueba de integración que lo verifica.
- [RECOMENDADO] El usuario de runtime solo tiene `INSERT` y `SELECT` sobre `auditoria`; el dueño del esquema se usa únicamente para migrar.

## Reporte de operación (RF-05) [OBLIGATORIO]
- Todo en SQL: `date_trunc('hour', ...)` en UTC, `COUNT(*) FILTER (WHERE nivel = ...)`, `AVG` y `percentile_cont(0.95) WITHIN GROUP (ORDER BY espera)`. Para la primera entrada a consulta, `ROW_NUMBER() OVER (PARTITION BY paciente_id ORDER BY ...)` sobre la auditoría, o un `SUM(...) OVER` para acumulados.
- Prohibido traer filas para agregarlas en memoria. El resultado es un `Flux` de filas por hora.
- Intervalo `[desde, hasta)`. Se valida `desde < hasta` y un rango máximo por propiedad.
- Índices sobre `fecha_registro` y sobre la auditoría por paciente, con el motivo en un comentario de la migración.
- Pruebas de integración con Testcontainers y datos conocidos que verifican los números, incluido el p95 y los pacientes que nunca entraron a consulta.

## Idempotencia del registro [RECOMENDADO]
- `POST /triage/pacientes` acepta la cabecera opcional `Idempotency-Key`. Tabla `idempotencia` (clave única, hash del cuerpo, `paciente_id`, `creado_en`), escrita en la misma transacción que el paciente.
- Misma clave y mismo cuerpo → respuesta original. Misma clave y cuerpo distinto → 422. Limpieza por TTL configurable (24 h por defecto). Sin cabecera, el comportamiento es el del enunciado.
