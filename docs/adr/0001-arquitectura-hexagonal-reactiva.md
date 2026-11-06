# ADR-0001 — Arquitectura hexagonal reactiva

- **Estado:** Aceptada
- **Fecha:** 2026-11-06
- **Aplica a:** `triaje-servicio` y `asistente-servicio`

## Contexto

Vida Cruz requiere APIs reactivas, persistencia y mensajería asíncronas, mientras que las reglas clínicas deben ser deterministas, fáciles de probar y ajenas a detalles de infraestructura. El asistente también integrará componentes de IA cuyo comportamiento de bloqueo depende de las APIs concretas elegidas.

## Decisión

1. Cada servicio separa `dominio`, `aplicacion` e `infraestructura`, con dependencias dirigidas hacia dentro: `infraestructura → aplicacion → dominio`.
2. `dominio` contiene modelo y reglas síncronas puras. No importa Spring, Reactor, R2DBC, Kafka ni Jackson, y no expone `Mono` o `Flux`.
3. `aplicacion` define casos de uso y puertos. Orquesta flujos con Reactor y expresa las dependencias externas mediante puertos.
4. `infraestructura` contiene adaptadores y configuración de frameworks, bases de datos, mensajería y proveedores externos. Cada adaptador mapea sus propios tipos y no filtra tipos de infraestructura hacia las capas internas.
5. ArchUnit verifica que el dominio no dependa de frameworks ni de infraestructura y que aplicación no dependa de infraestructura.
6. En runtime se prohíben `block()`, `blockFirst()`, `blockLast()`, `toFuture().get()`, `Thread.sleep()` e I/O bloqueante en el event loop; también se prohíbe JDBC salvo Flyway durante el arranque.
7. Para integraciones potencialmente bloqueantes se sigue este orden: (a) API no bloqueante pertinente; (b) adaptador pequeño propio con cliente no bloqueante; (c) aislamiento con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` dentro del adaptador cuando las opciones anteriores no sean viables. Se documentará la opción concreta para generación, embeddings, vector store e ingesta RAG cuando se decidan sus versiones y proveedores.
8. Kafka utiliza reactor-kafka con backpressure y concurrencia acotadas. No se invoca `subscribe()` dentro de pipelines de negocio. `correlationId` se propaga mediante Reactor Context, nunca `ThreadLocal`.
9. Flyway JDBC, si se incorpora, solo corre durante el arranque. La ingesta del corpus al iniciar el asistente tampoco bloqueará el event loop.

## Alternativas consideradas

- **Arquitectura en capas con dependencias hacia infraestructura.** Descartada porque haría que las reglas clínicas dependieran de frameworks y dificultaría probarlas de forma aislada.
- **Dominio completamente reactivo.** Descartada porque acoplaría las reglas de negocio a Reactor y permitiría que detalles de concurrencia contaminasen el modelo puro.
- **Arquitectura hexagonal estricta con muchos puertos y adaptadores pequeños.** Se adopta una variante pragmática: se definen límites explícitos y se crean puertos donde existe una dependencia externa, evitando abstracciones sin consumidor concreto.

## Consecuencias

**Positivas**

- Las reglas RN-01, RN-02 y RN-03 se pueden probar con JUnit sin contexto Spring ni infraestructura.
- Se puede sustituir persistencia, Kafka o proveedor de IA sin cambiar el dominio.
- Las reglas ArchUnit protegen la dirección de dependencias.

**Costos y límites**

- Los adaptadores deben mapear modelos entre infraestructura, aplicación y dominio.
- Las integraciones con APIs bloqueantes requieren aislamiento explícito o un adaptador no bloqueante y documentación de la decisión.
- La arquitectura no garantiza por sí sola que el event loop esté libre de bloqueos; se añaden pruebas con BlockHound a los flujos reactivos del servicio.
