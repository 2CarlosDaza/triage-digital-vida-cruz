# Vida Cruz — Triaje digital

Monorepo backend para el servicio de triaje y el futuro asistente clínico. Esta etapa contiene el scaffold Gradle y el dominio puro de triaje; todavía no implementa adaptadores web, persistencia, mensajería ni seguridad.

## Requisitos

- JDK 21
- Gradle Wrapper: `./gradlew build` (Windows: `gradlew.bat build`)

El módulo actual es `triaje-servicio`. La arquitectura del servicio separa `dominio`, `aplicacion` e `infraestructura`; el dominio no importa frameworks.

## Pruebas

`./gradlew build` ejecuta las pruebas unitarias y ArchUnit, y la tarea `pruebaIntegracion`, separada para futuras pruebas `*PruebaIntegracion`.

## Patrones aplicados

| Patrón | Clase | Justificación |
|---|---|---|
| Strategy | `ReglaClasificacion` | Aísla cada regla clínica de los niveles 1 a 3 y permite inyectar la lista en el clasificador. |
| State simple | `EstadoClinico` | Mantiene las transiciones permitidas en un mapa sin jerarquía de estados. |
