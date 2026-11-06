# ADR-0004 — Herramienta de construcción: Gradle con DSL Groovy

- **Estado:** Aceptada
- **Fecha:** 2026-11-05
- **Decisores:** Carlos Daza

## Contexto
El proyecto es un monorepo con al menos dos microservicios Spring Boot reactivos (`triaje-servicio` y `asistente-servicio`) que comparten configuración de Java, pruebas (JUnit, Testcontainers, ArchUnit, BlockHound) y empaquetado en contenedores. El responsable del proyecto prefiere Gradle con DSL Groovy. Spring Boot, Spring AI, Testcontainers y las herramientas de CI soportan Gradle y Maven con el mismo nivel de madurez.

## Decisión
1. **Gradle con DSL Groovy**, multi-proyecto (`settings.gradle` con `triaje-servicio` y `asistente-servicio`), con el **wrapper versionado** (`./gradlew`) como única forma de construir, localmente y en CI.
2. **Catálogo de versiones** en `gradle/libs.versions.toml`. Las versiones de Spring Boot y de Spring AI se fijan allí tras verificar su compatibilidad en la documentación oficial.
3. **Configuración común en `buildSrc`** mediante un plugin precompilado en Groovy (por ejemplo `vidacruz.convenciones-java.gradle`): toolchain de Java 21, dependencias de prueba, tareas de prueba y argumentos de JVM. Se evita el bloque `subprojects {}` en el `build.gradle` raíz.
4. **Pruebas:** Gradle detecta las pruebas por JUnit Platform, no por el nombre de la clase. El sufijo `PruebaIntegracion` (ADR-0002) se usa para separar las pruebas de integración: la tarea `test` las excluye, la tarea `pruebaIntegracion` solo las incluye y `check` depende de ambas.
5. **Comando de verificación:** `./gradlew build`, con la misma exigencia que el resto de ADRs: debe ejecutar pruebas reales (número mayor que 0).
6. **Imágenes de contenedor** con `Dockerfile` multietapa por servicio. La integración con Jib o `bootBuildImage` puede evaluarse más adelante.

## Alternativas consideradas
- **Maven.** Más declarativo y predecible, y fue la opción propuesta inicialmente. Descartada por preferencia del responsable del proyecto; no hay una razón técnica de peso en contra de Gradle.
- **Gradle con DSL Kotlin.** Ofrece tipado estático y mejor autocompletado en el IDE. Descartada por preferencia del responsable; puede migrarse más adelante, ya que la estructura del proyecto no cambia.

## Consecuencias
**Positivas**
- Configuración común en un solo lugar y construcciones incrementales con caché.
- El separador de pruebas por sufijo se resuelve sin depender de convenciones de nombres de Maven.
- Un solo comando (`./gradlew build`) sirve para desarrollo local y CI.

**Negativas / costos**
- El DSL Groovy es dinámico y tiene menos validación en el IDE que el DSL Kotlin; los errores de configuración aparecen al ejecutar.
- La primera construcción es más lenta por el arranque del demonio de Gradle.
- BlockHound en JDK 13 o superior exige el argumento JVM `-XX:+AllowRedefinitionToAddDeleteMethods`, que debe fijarse en la configuración de pruebas.