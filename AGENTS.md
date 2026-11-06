# AGENTS.md — Vida Cruz · Triaje digital + Asistente clínico

## Contexto
Monorepo con dos microservicios Java reactivos (solo backend: APIs REST + eventos Kafka, sin frontend; debe poder conectarse a uno a futuro vía API).

- `triaje-servicio`: registro de pacientes, clasificación automática (RN-01), SLA y escalamiento (RN-02/RN-03), auditoría clínica (RN-04), reporte de operación con SQL avanzado (RF-05), alertas de escalamiento (RF-06) y eventos en Kafka.
- `asistente-servicio`: RAG sobre protocolos clínicos con Spring AI (RF-04A), citas obligatorias, anti-alucinación (RN-05), modo degradado, y vista de urgencias en tiempo real reconstruida desde `triaje.eventos` y guardada en Redis (RF-04B).

**Fuente de verdad de requisitos:** `docs/requisitos.md`. **Decisiones de arquitectura:** `docs/adr/`.
Si algo de una tarea contradice estos documentos, detente y pregunta antes de implementar. No inventes reglas clínicas.

## Stack
Java 21 (LTS), Spring Boot 3.x, Spring WebFlux, Project Reactor, Spring Security (JWT resource server), R2DBC + PostgreSQL, Kafka con reactor-kafka, Resilience4j, Spring AI (RAG), Micrometer + OpenTelemetry, Docker, Kubernetes (Azure).
Build: Gradle (DSL Groovy) multi-proyecto, con wrapper versionado (`gradlew`), catálogo de versiones (`gradle/libs.versions.toml`), toolchain de Java 21 y una convención compartida en `buildSrc` (plugin precompilado en Groovy) para la configuración común (ADR-0004).
Paquete base: `co.vidacruz.digital`, con `co.vidacruz.digital.triaje` y `co.vidacruz.digital.asistente` para cada servicio (por ejemplo `co.vidacruz.digital.triaje.dominio`).
Antes de fijar versiones (Spring Boot, Spring AI, etc.) verifica en la documentación oficial que sean compatibles entre sí y registra la elección en un ADR.

## Arquitectura: hexagonal pragmática
Paquetes por servicio:
- `dominio`: modelo y reglas puras. **Prohibido** importar Spring, Reactor, R2DBC, Kafka o Jackson. Sin `Mono`/`Flux`. Código síncrono y determinista.
- `aplicacion`: casos de uso y puertos (`puerto.entrada`, `puerto.salida`). Orquesta con Reactor. Los puertos de salida devuelven `Mono`/`Flux`.
- `infraestructura`: adaptadores (`web`, `persistencia`, `mensajeria`, `ia`, `config`). Aquí viven Spring, R2DBC, Kafka, Resilience4j y Spring AI.

Reglas de dependencia: `infraestructura → aplicacion → dominio`. Nunca al revés. Un test de ArchUnit debe hacerlo cumplir.
Mapeo: un mapper por adaptador; no filtrar tipos de infraestructura (`ChatClient`, `Document`, filas R2DBC) hacia `aplicacion` o `dominio`.
Patrones: usar Strategy, Builder y Outbox donde aporten. No escribir singletons a mano (los beans de Spring ya lo son). No implementar Saga salvo que un ADR lo justifique.

## Reglas de programación reactiva (no negociables)
1. Prohibido `block()`, `blockFirst()`, `blockLast()`, `toFuture().get()`, `Thread.sleep()` y cualquier I/O bloqueante en el event loop. Prohibido JDBC en runtime.
2. Única excepción: Flyway usa JDBC **solo durante el arranque** (migraciones). Documentado en ADR.
3. Política para integraciones potencialmente bloqueantes (Spring AI, vector store, embeddings, etc.), en este orden:
   a. Usar la variante no bloqueante si existe y es pertinente (por ejemplo `stream()` en el modelo de chat, clientes basados en `WebClient`, comandos reactivos de Lettuce).
   b. Si solo hay API bloqueante y un adaptador no bloqueante propio es pequeño y mantenible (una llamada REST con `WebClient`, una consulta a Redis con cliente reactivo), preferir ese adaptador.
   c. Si no, aislar con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` dentro del adaptador.
   Verifica en la documentación de la versión elegida qué existe realmente; no lo asumas. Cada punto de integración con IA (generación, embeddings, vector store, ingesta del corpus) se documenta en el ADR del RAG indicando cuál opción (a, b o c) se usó y por qué. La ingesta del corpus al arranque tampoco debe bloquear el event loop.
4. Backpressure explícita: consumo y publicación en Kafka con reactor-kafka, y operadores acotados (`limitRate`, `concatMap`, `flatMap` con concurrencia limitada). Sin buffers ilimitados ni `onBackpressureBuffer()` sin tope.
5. No llamar `subscribe()` dentro de pipelines de negocio; solo en los bordes (arranque, tareas programadas).
6. Las pruebas de triaje-servicio incluyen BlockHound para detectar bloqueos. En JDK 13 o superior requiere el argumento JVM `-XX:+AllowRedefinitionToAddDeleteMethods` en la tarea de pruebas.
7. Propagar el `correlationId` por el contexto de Reactor (Context), no con `ThreadLocal`.

## Reglas de negocio clave (ver `docs/requisitos.md` para el detalle)
- Clasificación RN-01: reglas evaluadas en orden (nivel 1 → 2 → 3, luego 4, y el ajuste a nivel 5). Cada regla es una estrategia aislada y testeable.
- Escalamiento RN-03 idempotente: actualización condicional en base de datos (`WHERE estado = 'REGISTRADO' AND escalado = false`). Si no afecta filas, no se audita ni se publica.
- Cambio de estado: actualización condicional por estado esperado, para evitar carreras con el escalamiento.
- Auditoría RN-04: solo inserción; no existe endpoint de borrado ni de edición.
- Persistencia y eventos: paciente, auditoría y evento outbox se guardan en **la misma transacción** (`TransactionalOperator`). Un relay publica a Kafka (entrega at-least-once, consumidores idempotentes por `eventId`).
- El `triaje-servicio` **nunca** depende de la disponibilidad del asistente. No hay llamadas HTTP entre servicios: la integración es solo por eventos (el asistente consume `triaje.eventos`).
- Datos clínicos: los eventos no incluyen el nombre completo del paciente. Los logs no imprimen datos personales ni signos vitales identificables.

## Mensajería por propiedades
- Topics, nombres de evento (tipos), grupos de consumidores, topics de reintento/DLQ y cualquier identificador de mensajería se leen de propiedades (`@ConfigurationProperties`, clase `MensajeriaPropiedades`). **Prohibidos los literales** en código y en tests; los tests usan la clase de propiedades.
- Valores por defecto en `application.yml`; sobrescribibles con variables de entorno. Detalle y claves en `docs/requisitos.md` (Decisiones de mensajería).
- El dominio solo conoce `TipoEventoTriaje`; el outbox guarda el tipo lógico y el relay traduce al nombre configurado al publicar.

## Eventos y vista de urgencias (RF-04B)
- Todos los eventos (`paciente_registrado`, `paciente_en_consulta`, `paciente_alta`, `paciente_escalado`) incluyen `pacienteId`, `nivel`, estado y marca de tiempo, y **no** incluyen datos personales. La clave del mensaje es `pacienteId` (orden por paciente).
- El asistente reconstruye la vista consumiendo `triaje.eventos` con reactor-kafka (sin llamar a la API de triaje) y la guarda en Redis. La actualización es **idempotente** (se guarda el estado por paciente, no contadores incrementales) y confirma el offset solo después de escribir en Redis. Mensajes irrecuperables van a un topic de mensajes fallidos (DLQ) configurado por propiedad.
- `GET /asistente/estado-urgencias` debe funcionar aunque el proveedor de LLM esté caído.

## Reglas del asistente (RN-05)
- El corpus se divide por sección; cada fragmento lleva metadatos `protocolo` y `seccion`. Las `fuentes` de la respuesta salen de los metadatos de los fragmentos recuperados, nunca del texto libre del modelo.
- Si ningún fragmento supera el umbral de similitud (propiedad `umbralSimilitud`), se responde exclusivamente el aviso "no existe protocolo disponible" **sin llamar al LLM**.
- Toda respuesta generada se valida: al menos una fuente y todas las fuentes existen en el corpus; si no, se descarta y se responde el aviso.
- Circuit breaker de Resilience4j sobre las llamadas al proveedor (generación y embeddings, si son remotos), con fallback explícito: 503 y mensaje claro. Temperatura 0.

## Reportes (RF-05)
- Las agregaciones (por hora, conteos por nivel, promedio y p95) se calculan **en PostgreSQL** con SQL avanzado (`date_trunc`, `COUNT(*) FILTER`, `percentile_cont ... WITHIN GROUP`, funciones de ventana). Prohibido iterar registros en memoria para agregar. Las pruebas de integración con Testcontainers verifican los números.

## Seguridad
- Cero credenciales en el repositorio. API key del LLM, secretos JWT y contraseñas por variables de entorno. Mantener `.env.example` sin valores reales.
- Autenticación JWT con roles; validación de entradas en el borde web.
- Errores en formato `ProblemDetail` (RFC 7807). 400 indica el/los campos inválidos, 409 transición inválida, 404 no encontrado.

## Pruebas
- JUnit 5, Mockito, `StepVerifier`, `WebTestClient`, Testcontainers (PostgreSQL, Kafka), BlockHound, ArchUnit.
- El LLM siempre debe poder sustituirse por un mock/stub en tests (puerto en `aplicacion`).
- Todo cambio de lógica de negocio viene con tests, incluyendo valores límite.
- Las clases de prueba terminan en `Prueba` (unitarias) y `PruebaIntegracion` (integración). Gradle detecta las pruebas por JUnit y no por nombre; el sufijo `PruebaIntegracion` sirve para separarlas: la tarea `test` excluye `**/*PruebaIntegracion*`, la tarea `pruebaIntegracion` solo las incluye y `check` depende de ambas. Verifica que `./gradlew build` ejecuta realmente las pruebas (número de pruebas mayor que 0).
- No marcar una tarea como terminada si `./gradlew build` no pasa.

## Convenciones
- Idioma del código (ADR-0002, glosario en `docs/glosario.md`): clases, interfaces, enums, métodos, variables, constantes, paquetes, tablas/columnas, campos JSON, nombres de tests y mensajes están en **español**, usando exactamente los términos del dominio de `docs/requisitos.md` (no inventes sinónimos). Sin tildes ni `ñ` en identificadores (`auditoria`, `anios`); los textos visibles al usuario sí llevan tildes.
  - Los sufijos de rol técnico también van **en español**: `Controlador`, `Repositorio`, `Adaptador`, `Puerto`, `Servicio`, `Configuracion`, `Propiedades`, `Mapeador`, `Excepcion`, `Solicitud`/`Respuesta`, `Prueba`/`PruebaIntegracion` (tabla completa en `docs/glosario.md`). Ejemplos: `PacienteControlador`, `PacienteRepositorioAdaptador`, `MensajeriaPropiedades`, `TransicionInvalidaExcepcion`.
  - Se mantiene en inglés solo lo que imponen las librerías: APIs, anotaciones y tipos de Java/Spring/Reactor/Kafka, claves de configuración de frameworks y métodos heredados (`findById`, `save`), que no se renombran.
  - Un test de ArchUnit verifica los sufijos por paquete (`..web..` → `Controlador`, `..persistencia..` → `Repositorio`/`Adaptador`, etc.); agrega las reglas a medida que aparezcan los paquetes.
  - Los puertos de `aplicacion` usan verbos en español (`buscarPorId`, `guardar`); el adaptador traduce hacia los métodos de Spring Data (`findById`, `save`) sin exponerlos.
  - Antes de crear un concepto nuevo, consulta `docs/glosario.md` y agrega el término allí.
- Ortografía: `triaje` (forma correcta en español) en código, módulos, documentación y propiedades, y el estado `REGISTRADO`. Excepción: el contrato público del enunciado se respeta literal (rutas `/triage/...`; topic `triaje.eventos` por defecto).
- ADRs en `docs/adr/`: 0001 arquitectura hexagonal reactiva, 0002 idioma del código, 0003 ramas trunk-based, 0004 herramienta de construcción.
- Commits pequeños y progresivos con Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Cada commit compila y pasa los tests. Etiquetar versiones funcionando (`v0.1.0`, `v0.2.0`, ...).
- ADRs en `docs/adr/NNNN-titulo.md` con formato: contexto, decisión, alternativas, consecuencias.
- No agregar dependencias sin justificarlas en el mensaje de commit o en un ADR.

## Flujo de Git (trunk-based, ADR-0003)
- `main` es el tronco: siempre compila, pasa `./gradlew build` y es desplegable. No existen ramas de larga vida (nada de `develop` ni `release/*`).
- Cada tarea se hace en una rama corta (horas, máximo un día) creada desde `main`, con nombre `tipo/descripcion-corta` (por ejemplo `feat/dominio-triaje`, `fix/validacion-presion`). Rebasa sobre `main` con frecuencia.
- Commits pequeños en Conventional Commits con descripción en español; cada uno compila y pasa las pruebas. **No hagas squash**: el historial progresivo es un entregable.
- Nunca hagas push directo a `main` ni `push --force` sobre ramas compartidas (en tu propia rama, solo `--force-with-lease`). Empuja tu rama; el responsable revisa el diff y la integra con "Rebase and merge" cuando CI está en verde.
- Lo inacabado no se expone: no conectes funcionalidad a medias. Si debe integrarse antes de estar lista, déjala detrás de una propiedad de configuración desactivada por defecto.
- Los hitos funcionales se etiquetan sobre `main` como `vX.Y.Z`.

## Cómo trabajar
1. Lee este archivo y `docs/requisitos.md`.
2. Implementa solo el alcance de la tarea actual; no adelantes etapas.
3. Al terminar entrega: resumen de cambios, lista de commits, decisiones tomadas y dudas abiertas.
