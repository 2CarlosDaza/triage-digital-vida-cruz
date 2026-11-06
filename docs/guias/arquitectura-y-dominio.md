# Guía: arquitectura y dominio

## Capas [OBLIGATORIO]
- `dominio`: modelo y reglas puras. Prohibido importar Spring, Reactor, R2DBC, Kafka o Jackson. Sin `Mono`/`Flux`. Código síncrono y determinista.
- `aplicacion`: casos de uso y puertos (`puerto.entrada`, `puerto.salida`). Orquesta con Reactor. Los puertos de salida devuelven `Mono`/`Flux` y tienen nombres en español (`buscarPorId`, `guardar`).
- `infraestructura`: adaptadores (`web`, `persistencia`, `mensajeria`, `ia`, `config`). Aquí viven Spring, R2DBC, Kafka, Resilience4j y Spring AI. El adaptador traduce hacia los métodos en inglés de las librerías sin exponerlos hacia adentro.
- Dependencias: `infraestructura → aplicacion → dominio`. Un test de ArchUnit lo hace cumplir.
- Un mapeador por adaptador. No filtrar tipos de infraestructura (`ChatClient`, `Document`, filas R2DBC) hacia `aplicacion` o `dominio`.
- El paquete `identidad` de `triaje-servicio` es un hexágono propio (usuarios, roles, tokens) con las mismas reglas.

## Dominio con invariantes [OBLIGATORIO]
- **Objetos de valor** (`SignosVitales`, `Nivel`, ...): inmutables y autovalidados; no existe una instancia inválida.
- **Agregados** (`Paciente`): sin setters públicos. Se crean solo con fábricas con nombre (`registrar(...)`; `reconstruir(...)` para la persistencia) y cambian solo mediante métodos de comportamiento (`cambiarEstado(nuevo, ahora)`, `escalar(ahora)`). Cada método valida la regla y devuelve el resultado: nuevo estado, `EntradaAuditoria` y evento de dominio.
- **Un solo lugar para los rangos de RF-01:** el dominio. Acumula todas las violaciones y lanza `DatosInvalidosExcepcion` con la lista de campos. La capa web solo valida estructura (campos presentes, formato) y traduce a 400. No se duplican rangos en anotaciones.
- **Excepciones de dominio:** jerarquía `DominioExcepcion`, sin conocer HTTP. La capa web las traduce a `ProblemDetail`.
- **Eventos de dominio** son valores; el adaptador de mensajería los convierte a mensajes.
- **Tiempo:** el dominio recibe `Instant ahora` como argumento. `aplicacion` inyecta `java.time.Clock` y lo pasa. Prohibido `Instant.now()`, `LocalDateTime.now()` y similares fuera de la configuración del `Clock`. Un test de ArchUnit lo verifica.

## Patrones autorizados [OBLIGATORIO]
Lista cerrada. Cualquier otro patrón se propone en el resumen y no se implementa.

| Patrón | Dónde | Notas |
|---|---|---|
| Strategy | `ReglaClasificacion` en `dominio` | Una estrategia por nivel; la lista ordenada la construye `aplicacion` |
| State simple | Mapa de transiciones en `EstadoClinico` | Sin jerarquía de clases |
| Builder | Solo en DTOs, eventos y construcción de prompts | Con Lombok `@Builder`; nunca en agregados |
| Outbox | Solo para publicar eventos de dominio | Ver `reactividad-y-mensajeria.md` |

No se usan: singletons manuales (los beans de Spring ya lo son), Saga (necesitaría un ADR), Factory abstracta, Decorator, Visitor, Template Method.
**Registro:** cada patrón aplicado se anota en una tabla del README (patrón, clase, justificación). Así el responsable sabe siempre dónde se aplicó cada uno.

## Lombok [OBLIGATORIO]
- Permitido: `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Value` y `@Builder` (solo donde indica la tabla de patrones).
- Prohibido: `@Data` y `@Setter` en `dominio`; `@AllArgsConstructor` público en agregados; `@SneakyThrows`; `@EqualsAndHashCode` automático en agregados (la igualdad es por identidad).
- Preferir `record` para objetos de valor y DTOs. En clases con nombre o signos vitales, el `toString` no debe imprimir datos personales.
