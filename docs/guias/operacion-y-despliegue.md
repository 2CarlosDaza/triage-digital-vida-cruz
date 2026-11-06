# Guía: observabilidad, contenedores, Kubernetes y documentación

## Logs [OBLIGATORIO]
- JSON estructurado por consola (formato estructurado de Spring Boot si la versión lo soporta, 3.4 o superior; si no, un encoder de logback). Campos mínimos: marca de tiempo, nivel, servicio, `correlationId`, `traceId` si existe y mensaje.
- Sin datos personales ni cuerpos de solicitud. Sin trazas de pila en las respuestas HTTP.

## Métricas (Micrometer) [OBLIGATORIO]
Etiquetas solo de cardinalidad baja (`nivel`, `resultado`); nunca identificadores.

| Métrica | Servicio |
|---|---|
| `triaje.clasificaciones` (etiqueta `nivel`) | triaje |
| `triaje.escalamientos` | triaje |
| `triaje.outbox.pendientes`, `triaje.outbox.fallidos` | triaje |
| `asistente.llm.latencia` (Timer con histograma) | asistente |
| `asistente.consultas` (etiqueta `resultado`: respondida, sin_protocolo, degradado) | asistente |
| `asistente.respuestas.descartadas` | asistente |
| Métricas del circuit breaker y bulkhead de Resilience4j; errores y último ciclo de los procesos de fondo | ambos |

## Health [OBLIGATORIO]
- **Liveness:** sin dependencias externas.
- **Readiness de triaje:** base de datos y migraciones terminadas. **No** incluye Kafka (el outbox acumula mientras tanto).
- **Readiness de asistente:** Redis y corpus cargado. **Nunca** incluye el proveedor de LLM: una caída del LLM es modo degradado, no un pod fuera de servicio.
- Probe de arranque (`startupProbe`) que tolera migraciones y carga del corpus.

## Docker [OBLIGATORIO]
- `Dockerfile` multietapa por servicio, JAR en capas, imagen base JRE 21, usuario no root, `.dockerignore`.
- JVM: `-XX:MaxRAMPercentage=75` y `-XX:+ExitOnOutOfMemoryError`.
- Sin secretos en la imagen. Etiquetas de imagen con el SHA del commit.

## docker-compose [OBLIGATORIO]
- `docker compose up --build` levanta PostgreSQL, Kafka (KRaft, un nodo), Redis con búsqueda vectorial si se usa como vector store, y ambos servicios.
- `healthcheck` en cada dependencia y `depends_on` con `condition: service_healthy`. Variables desde `.env` (a partir de `.env.example`).

## Kubernetes (Azure) [OBLIGATORIO]
- Manifests con Kustomize: `k8s/base` y `k8s/overlays/azure`. Deployment, Service, ConfigMap y marcadores de Secret (sin valores reales).
- Probes de arranque, vida y disponibilidad; `resources` con requests y limits.
- `securityContext`: `runAsNonRoot`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false` y sin capacidades.
- Parada ordenada: `terminationGracePeriodSeconds` mayor que `spring.lifecycle.timeout-per-shutdown-phase`, más un `preStop` corto.
- Etiquetas `app.kubernetes.io/*`. Las imágenes salen de un registro (ACR).
- [RECOMENDADO] Secretos desde Azure Key Vault; HPA; PodDisruptionBudget.

## Configuración [OBLIGATORIO]
- Perfiles `local`, `test` y `azure`. Propiedades propias en clases `@ConfigurationProperties` con `@Validated` y restricciones, para que una configuración errónea falle al arrancar.
- Todas las variables de entorno se documentan en una tabla del README.

## README y documentación [OBLIGATORIO]
- Secciones: ejecución local con un solo comando, arquitectura con diagrama (Mermaid, versionado), tecnologías, decisiones clave con enlaces a los ADR, tabla de patrones aplicados, limitaciones conocidas, cómo probar la API (ejemplos `curl`, incluido el flujo de registro, asignación de rol y login), URL desplegada, y **uso de IA** (qué hizo la herramienta, qué decidió el responsable y cómo se validó el resultado).
- Al cambiar una decisión o un contrato, se actualizan el README, la guía correspondiente y el ADR en el mismo PR.
- [RECOMENDADO] Trazas distribuidas con OpenTelemetry (bonus del enunciado).
