# Vida Cruz — Triaje digital

Monorepo backend para el servicio de triaje y el futuro asistente clínico. El servicio de triaje cuenta con el dominio inicial y la conexión a PostgreSQL mediante R2DBC, con migraciones Flyway.

## Requisitos

- JDK 21
- Gradle Wrapper: `./gradlew build` (Windows: `gradlew.bat build`)

El módulo actual es `triaje-servicio`. La arquitectura del servicio separa `dominio`, `aplicacion` e `infraestructura`; el dominio no importa frameworks.

## PostgreSQL local

1. Copia `.env.example` a `.env` y cambia los dos valores `CAMBIAR_LOCALMENTE` por contraseñas locales.
2. Inicia PostgreSQL: `docker compose up -d postgresql`.
3. Comprueba disponibilidad: `docker compose exec postgresql pg_isready -U vida_cruz_migracion -d vida_cruz`.
4. Desde la raíz del repositorio, ejecuta `./gradlew :triaje-servicio:bootRun` (Windows: `gradlew.bat :triaje-servicio:bootRun`). La aplicación carga `.env`; Flyway aplicará la migración al iniciar.

El contenedor crea el usuario de runtime separado del usuario de migración. Los scripts de inicialización de PostgreSQL solo se ejecutan al crear el volumen por primera vez. Para reiniciar la base local desde cero, elimina el volumen con `docker compose down -v`.

## Pruebas

`./gradlew build` ejecuta las pruebas unitarias y ArchUnit, y la tarea `pruebaIntegracion`, separada para futuras pruebas `*PruebaIntegracion`.

## Patrones aplicados

| Patrón | Clase | Justificación |
|---|---|---|
| Strategy | `ReglaClasificacion` | Aísla cada regla clínica de los niveles 1 a 3 y permite inyectar la lista en el clasificador. |
| State simple | `EstadoClinico` | Mantiene las transiciones permitidas en un mapa sin jerarquía de estados. |
