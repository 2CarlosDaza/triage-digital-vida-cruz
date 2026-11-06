# Guía: reactividad, resiliencia y mensajería

## 1. Cero bloqueo [OBLIGATORIO]
- Prohibido `block()`, `blockFirst()`, `blockLast()`, `toFuture().get()`, `Thread.sleep()` y cualquier I/O bloqueante en el event loop. Prohibido JDBC en runtime (excepción: Flyway, ver `persistencia.md`).
- **Verificación estática:** una regla de ArchUnit en cada servicio prohíbe en el código de `main` llamar a `Mono.block*`, `Flux.block*`, `Thread.sleep` y a `java.sql` (por ejemplo con `callMethod`).
- **Verificación dinámica:** BlockHound en las pruebas de **ambos** servicios (en JDK 13 o superior requiere `-XX:+AllowRedefinitionToAddDeleteMethods` en la tarea de pruebas). Las excepciones se declaran en una sola clase (`BlockHoundConfiguracion`), cada una con su justificación.
- Trabajo de CPU o bloqueante legítimo (BCrypt, lectura de archivos de arranque) va en `Schedulers.boundedElastic()`.

## 2. Integraciones potencialmente bloqueantes (Spring AI, vector store, embeddings) [OBLIGATORIO]
Orden de preferencia:
- a. Variante no bloqueante si existe y es pertinente (`stream()` en el modelo de chat, clientes con `WebClient`, comandos reactivos de Lettuce).
- b. Adaptador no bloqueante propio, solo si cumple **todo** esto: unas 150 líneas o menos, prueba de integración, y no pierde funcionalidad necesaria (índice vectorial, filtros por metadatos). **Límite de tiempo: 2 horas.** Si se excede, se pasa a (c).
- c. Aislar con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` dentro del adaptador.
Verifica en la documentación de la versión elegida qué existe realmente. La opción elegida por cada punto (generación, embeddings, vector store, ingesta) se documenta en el ADR del RAG.

## 3. Suscripciones de larga duración [OBLIGATORIO]
Aplica al relay del outbox, a los consumidores Kafka y al vigilante de SLA.
- Cada una es un componente que implementa `SmartLifecycle`. Es el único sitio donde se llama `subscribe()`.
- **Parada ordenada:** al detenerse, cancela la suscripción y espera lo que esté en curso hasta un límite. Configura `server.shutdown=graceful` y `spring.lifecycle.timeout-per-shutdown-phase`.
- **Errores:** el error de un elemento se maneja dentro del pipeline interno (`onErrorResume`, con métrica y log) para que no termine el flujo. Los errores del flujo completo se reintentan con `retryWhen(Retry.backoff(...))`. No usar `onErrorContinue`.
- Cada proceso expone una métrica de errores y un indicador del último ciclo exitoso.

## 4. Backpressure [OBLIGATORIO]
- `flatMap` siempre con concurrencia explícita (nunca la predeterminada); `limitRate` o prefetch explícitos. Los valores salen de propiedades.
- Sin `onBackpressureBuffer()` sin tope. `onBackpressureDrop` solo para los ticks de `Flux.interval` del vigilante, con métrica y log del descarte.
- **Orden por paciente:** el relay del outbox procesa por lotes ordenados con `concatMap` (concurrencia 1) salvo que se justifique paralelizar por clave (`groupBy(pacienteId)`).
- Consumidor Kafka: `receive()` con `concatMap`, confirmación manual del offset solo después del efecto, y prefetch pequeño.

## 5. Resiliencia [OBLIGATORIO]
- **Timeout en toda E/S**, por propiedades. Valores iniciales ajustables: LLM 20 s, embeddings 10 s, Redis 2 s, adquisición de conexión R2DBC y sentencias 5 s, entrega de Kafka 30 s.
- **Circuit breaker** sobre el proveedor de IA, también por llamadas **lentas** (no solo fallidas). Valores iniciales: ventana de 10 llamadas, tasa de fallos 50 %, llamada lenta a partir de 10 s con tasa lenta 50 %, 30 s en estado abierto y 3 llamadas en semiabierto.
- **Bulkhead** (concurrencia máxima por propiedad, por ejemplo 10) sobre las llamadas al LLM. Si se llena, 503 con `Retry-After`.
- **Reintentos** solo en operaciones idempotentes, con backoff y un máximo de intentos. Nunca reintentar con el circuito abierto.
- **Fallback explícito:** 503 con mensaje claro (modo degradado). Detalle del asistente en `asistente-rag.md`.

## 6. Mensajería y outbox
**[OBLIGATORIO]**
- Topics, nombres de evento, grupos de consumidores y topics de mensajes fallidos (DLQ) por propiedades (`MensajeriaPropiedades`). Prohibidos los literales en código y en pruebas. El dominio solo conoce `TipoEventoTriaje`; el relay traduce al nombre configurado al publicar.
- **Sobre del evento:** `eventId`, `tipo`, `version`, `occurredAt` (ISO 8601 UTC), `pacienteId`, `nivel`, `estado`, `correlationId`. Sin datos personales. Clave del mensaje: `pacienteId`.
- **Outbox:** tabla `evento_salida` (`id`, `tipo`, `agregado_id`, `payload jsonb`, `creado_en`, `estado`, `intentos`, `publicado_en`), escrita en la misma transacción que el cambio de negocio. El relay toma lotes con `SELECT ... FOR UPDATE SKIP LOCKED` ordenados por `id`, publica y marca `PUBLICADO` tras la confirmación de Kafka. Tras N intentos fallidos marca `FALLIDO`, con métrica y log. Una limpieza periódica borra los `PUBLICADO` anteriores a la retención configurada (7 días por defecto).
- **Productor:** `acks=all`, `enable.idempotence=true` y `delivery.timeout.ms` explícito.
- **Consumidor:** `auto.offset.reset=earliest`, confirmación manual y DLQ tras N reintentos. Mensajes con tipo desconocido se ignoran con un log.
- **Versionado:** dentro de una `version` solo se agregan campos opcionales. Un cambio incompatible es una `version` nueva. Los consumidores ignoran campos desconocidos.
- **Contratos:** ejemplos JSON en `docs/contratos/eventos/<tipo>.v1.json`. Ambos servicios los incluyen como recurso de pruebas: el productor comprueba que su serialización coincide, y el consumidor comprueba que los parsea.

## 7. Vista de urgencias (RF-04B) [OBLIGATORIO]
- El asistente consume `triaje.eventos` (sin llamar a la API de triaje) y guarda la vista en Redis: un conjunto por nivel (`SADD`/`SREM`) y el nivel actual por paciente. Contar es `SCARD`. Es idempotente por construcción.
- Registrado agrega al conjunto de su nivel; alta lo quita; en consulta y escalado no cambian la pertenencia. Se guarda `ultimoEventoEn` por paciente para ignorar eventos más viejos que el ya aplicado.
- Confirma el offset solo después de escribir en Redis. `GET /asistente/estado-urgencias` funciona aunque el LLM esté caído.

## 8. Contexto y correlación [OBLIGATORIO]
- Un `WebFilter` lee o genera `X-Correlation-Id` (se valida formato y longitud), lo coloca en el Context de Reactor y lo devuelve en la respuesta.
- Se activa la propagación automática de contexto (`Hooks.enableAutomaticContextPropagation()` con `context-propagation` de Micrometer) para que llegue a MDC y a los logs.
- El productor Kafka agrega la cabecera `correlationId` y el consumidor la restaura en el Context.
- [RECOMENDADO] Trazas OpenTelemetry con Micrometer Tracing y `traceparent` en las cabeceras de Kafka.
