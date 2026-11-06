# Guía: pruebas y calidad

## Herramientas dentro de `./gradlew build`
- [OBLIGATORIO] **Spotless** con google-java-format: `spotlessCheck` forma parte de `check`. Antes de cada commit se ejecuta `spotlessApply`.
- [OBLIGATORIO] **JaCoCo** con `jacocoTestCoverageVerification`. Mínimos iniciales: `dominio` 90 % de líneas y 80 % de ramas, `aplicacion` 80 % de líneas, y 70 % de líneas por módulo. Los umbrales viven en `buildSrc`. Las exclusiones (clases generadas o de configuración trivial) se listan explícitamente y se justifican.
- [OBLIGATORIO] **ArchUnit:** capas, sufijos por paquete, prohibición de `block*`, `Thread.sleep` y `java.sql` en `main`, y de `Instant.now()`/`LocalDateTime.now()` fuera de la configuración del `Clock`.
- [RECOMENDADO] Análisis estático (SpotBugs con reglas de alta confianza, o Error Prone). Si genera ruido, se desactivan reglas puntuales con justificación en el `build.gradle`.

## Estrategia de pruebas [OBLIGATORIO]
- **Pirámide:** dominio (rápidas, sin Spring) → `aplicacion` con puertos simulados → adaptadores contra contenedores reales → pocos flujos de extremo a extremo.
- **Contenedores compartidos:** un contenedor por tipo y por JVM de pruebas (patrón singleton con `@DynamicPropertySource`), no uno por clase. Sin `reuse` en CI.
- **Asincronía:** `StepVerifier`, con tiempo virtual cuando haya retrasos, y Awaitility con tiempo límite. Prohibido `Thread.sleep` también en pruebas.
- **Kafka:** Testcontainers; topics y tipos de evento tomados de `MensajeriaPropiedades`; esperas acotadas.
- **Circuit breaker:** con un LLM simulado que falla o tarda, y una configuración reducida en el perfil de pruebas, se comprueba la respuesta 503, el estado `OPEN`, el paso a `HALF_OPEN` y el retorno a `CLOSED`. También el bulkhead lleno.
- **Contratos de eventos:** ejemplos JSON compartidos (ver `reactividad-y-mensajeria.md`).
- **Seguridad:** 200/401/403 por endpoint y rol; JWT inválido, expirado y con algoritmo `none`.
- **Determinismo:** `Clock.fixed` en las pruebas; ninguna depende del texto real de un LLM.
- **Nombres:** descriptivos y en español, con sufijos `Prueba` (unitarias) y `PruebaIntegracion` (integración).

## Casos de negocio mínimos [OBLIGATORIO]
Pacientes E1 a E6; valores límite de la clasificación; transiciones válidas e inválidas; idempotencia del escalamiento, incluida la concurrente; auditoría inmutable (el trigger rechaza `UPDATE` y `DELETE`); reporte con números conocidos; citas del RAG; "sin coincidencia sin alucinación"; respuesta descartada por una cifra inventada; vista de urgencias reconstruida desde eventos; modo degradado.
