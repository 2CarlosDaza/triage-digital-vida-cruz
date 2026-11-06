# AGENTS.md — Vida Cruz · Triaje digital + Asistente clínico

## Contexto
Monorepo con dos microservicios Java reactivos, solo backend (APIs REST + eventos Kafka; debe poder conectarse a un frontend a futuro):
- `triaje-servicio`: pacientes, clasificación, SLA y escalamiento, auditoría, reporte de operación, alertas, eventos y emisión de JWT (paquete `identidad`).
- `asistente-servicio`: RAG sobre protocolos clínicos, modo degradado y vista de urgencias en Redis alimentada por `triaje.eventos`.

Requisitos: `docs/requisitos.md` (fuente única; no se repiten aquí). Decisiones: `docs/adr/` (criterios en `docs/adr/README.md`). Vocabulario: `docs/glosario.md`.

## Cómo trabajar
1. **Planifica antes de editar** si la tarea toca más de una capa o más de 5 archivos: escribe un plan corto (pasos, archivos, riesgos) al inicio de tu respuesta.
2. **Dudas.** Detente y pregunta solo si la duda afecta una regla clínica (RN-01 a RN-05), la seguridad o una acción irreversible o destructiva. En los demás casos adopta la interpretación más conservadora, continúa con lo no afectado y regístrala en "Dudas abiertas" del resumen final.
3. **No inventes.** Antes de usar una API o dependencia, comprueba que existe en la versión del catálogo (compila y consulta la documentación o las fuentes); no la uses de memoria. No agregues dependencias sin justificarlas en el mensaje del commit.
4. **Alcance.** Implementa solo la tarea actual; no adelantes etapas. No modifiques `docs/requisitos.md` ni ADRs existentes salvo que la tarea lo pida: propón el cambio en el resumen. Puedes agregar términos a `docs/glosario.md`.
5. **Entrega.** Resumen de cambios, lista de commits, decisiones tomadas y dudas abiertas.

## Guías: léelas antes de tocar el tema
| Si tocas... | Lee |
|---|---|
| Capas, dominio, patrones, Lombok | `docs/guias/arquitectura-y-dominio.md` |
| Base de datos, SQL, migraciones, auditoría | `docs/guias/persistencia.md` |
| Reactor, Kafka, outbox, resiliencia | `docs/guias/reactividad-y-mensajeria.md` |
| RAG, prompts, anti-alucinación | `docs/guias/asistente-rag.md` |
| Autenticación, autorización, entradas | `docs/guias/seguridad.md` |
| Pruebas, formato, cobertura | `docs/guias/pruebas-y-calidad.md` |
| Logs, métricas, Docker, Kubernetes, README | `docs/guias/operacion-y-despliegue.md` |

Cada regla de las guías está marcada **[OBLIGATORIO]** (se verifica en el build o en la revisión) o **[RECOMENDADO]**. Lo obligatorio no se omite. Lo recomendado se puede recortar si hay que ajustar alcance; en ese caso anótalo en el resumen.

## Stack
Java 21 (LTS), Spring Boot 3.x, Spring WebFlux, Project Reactor, Spring Security (JWT), R2DBC + PostgreSQL, Kafka con reactor-kafka, Resilience4j, Spring AI (RAG), Redis, Micrometer + OpenTelemetry, Lombok, Docker, Kubernetes (Azure).
Build: Gradle (DSL Groovy) multi-proyecto, wrapper versionado, catálogo `gradle/libs.versions.toml`, toolchain de Java 21 y convención compartida en `buildSrc` (ADR-0004).
Paquete base `co.vidacruz.digital`: `co.vidacruz.digital.triaje` y `co.vidacruz.digital.asistente` (por ejemplo `co.vidacruz.digital.triaje.dominio`).
Las versiones viven solo en el catálogo; verifica que sean compatibles entre sí (Spring Boot, Spring AI, etc.). Cambiar una versión no requiere ADR.

## Reglas obligatorias (resumen; el detalle está en las guías)
1. **Hexagonal:** `infraestructura → aplicacion → dominio`. El dominio no importa Spring, Reactor, R2DBC, Kafka ni Jackson. ArchUnit lo hace cumplir.
2. **Cero bloqueo en el event loop:** sin `block*()`, `Thread.sleep` ni JDBC en runtime (única excepción: Flyway al arrancar). Se verifica con ArchUnit y con BlockHound en **ambos** servicios.
3. **El tiempo entra por argumento:** `Clock` inyectado en `aplicacion`; `Instant` como parámetro en `dominio` y en el SQL. Prohibido `Instant.now()` y `now()` en lógica de negocio.
4. **Dominio con invariantes:** objetos de valor autovalidados y sin setters públicos.
5. **Patrones:** solo los autorizados en la guía de arquitectura (lista cerrada). Otro patrón se propone en el resumen, no se implementa.
6. **Mensajería:** topics y nombres de evento por propiedades; eventos sin datos personales; outbox en la misma transacción que el cambio.
7. **Resiliencia:** timeout en toda E/S; circuit breaker y bulkhead sobre el LLM; fallback explícito 503.
8. **Asistente:** las citas salen de los fragmentos recuperados, la respuesta pasa una verificación determinista, sin coincidencia responde el aviso sin llamar al LLM, y la entrada tiene límites.
9. **Seguridad:** JWT RS256, autorización por matriz, límites de entrada, sin credenciales en el repo, BCrypt fuera del event loop.
10. **SQL:** las agregaciones del reporte se hacen en PostgreSQL; la auditoría es inmutable también a nivel de base de datos.
11. **Observabilidad:** logs JSON con `correlationId`, métricas mínimas, probes sin dependencias externas indebidas.
12. **Calidad:** `spotlessCheck`, pruebas y umbrales de cobertura dentro de `./gradlew build`.
13. **Configuración** tipada y validada al arrancar (`@ConfigurationProperties` + `@Validated`).
14. **Datos clínicos:** los logs no imprimen datos personales ni signos vitales identificables.

## Convenciones
- **Idioma del código y sufijos de rol en español** (`Controlador`, `Repositorio`, `Adaptador`, `Puerto`, `Prueba`...): ADR-0002 y `docs/glosario.md`. Consulta el glosario antes de crear un concepto nuevo. Sin tildes ni `ñ` en identificadores. Los métodos heredados de librerías (`findById`, `save`) no se renombran.
- **Ortografía:** `triaje` en código, módulos, documentación y propiedades. Excepción: el contrato público del enunciado va literal (rutas `/triage/...`, topic `triaje.eventos` por defecto).
- Commits Conventional (`feat:`, `fix:`, `test:`, `docs:`, `chore:`) con descripción en español; pequeños, y cada uno compila y pasa las pruebas.
- ADRs: solo según `docs/adr/README.md`. No crees ADRs por versiones ni por detalles menores.

## Flujo de Git (trunk-based, ADR-0003)
- `main` siempre compila, pasa `./gradlew build` y es desplegable. Sin ramas de larga vida.
- Una rama corta por tarea, desde `main`, con nombre `tipo/descripcion-corta` (por ejemplo `feat/dominio-triaje`). Rebasa sobre `main` con frecuencia.
- **Sin squash:** el historial progresivo es un entregable.
- Nunca hagas push a `main` ni `push --force` sobre ramas compartidas (en tu rama, solo `--force-with-lease`). Empuja tu rama; el responsable revisa el diff e integra con "Rebase and merge" cuando el CI está en verde.
- **Las etiquetas `vX.Y.Z` las crea el responsable sobre `main`, después de integrar. El agente no crea etiquetas.**
- Lo inacabado no se expone: déjalo detrás de una propiedad desactivada por defecto.

## Definición de terminado
- `./gradlew build` en verde (formato, pruebas, cobertura y reglas de arquitectura).
- Si cambió un contrato, una decisión o una regla, se actualizaron en el mismo PR la guía, el README o `docs/requisitos.md` (esto último solo si la tarea lo pedía).
- Resumen de entrega completo (punto 5 de "Cómo trabajar").
