# ADR-0002 — Idioma del código: español, alineado con el dominio

- **Estado:** Aceptada
- **Fecha:** 2026-11-05
- **Decisores:** Carlos Daza
- **Aplica a:** `triaje-servicio`, `asistente-servicio` y cualquier módulo futuro

## Contexto
Vida Cruz es un grupo de salud colombiano. El sistema se usa dentro del país, y sus reglas clínicas y de negocio (niveles de triaje, SLA, estados `REGISTRADO` / `EN_CONSULTA` / `ALTA`, escalamiento, auditoría, protocolos) están definidas y se discuten en español por enfermería, médicos, dirección y equipo técnico.

La práctica habitual en Java es nombrar el código en inglés. Aquí eso obligaría a traducir cada término del dominio (por ejemplo, "escalamiento", "queja principal", "alta"), con riesgo de usar sinónimos distintos según quién programe y de perder la trazabilidad entre el requisito y el código.

## Decisión
1. **El código se escribe en español, usando los mismos términos del dominio** que aparecen en `docs/requisitos.md`: clases, interfaces, records, enums, métodos, variables, constantes, paquetes, tablas y columnas, campos JSON, nombres de tests y mensajes de error. Ejemplos: `Paciente`, `SignosVitales`, `Nivel`, `Clasificador`, `EntradaAuditoria`, `clasificar()`, `escalar()`, `fechaRegistro`, `slaMinutos`.
2. **Un solo término por concepto.** Se prohíben sinónimos y traducciones paralelas. La referencia es `docs/glosario.md`, que se actualiza antes de introducir un concepto nuevo.
3. **Sin tildes ni `ñ` en identificadores** (`auditoria`, `presionSistolica`, `anios`) para evitar problemas de codificación y de herramientas. Los textos visibles al usuario (mensajes, respuestas, documentación) sí llevan ortografía correcta.
4. **Los sufijos de rol técnico también van en español:** `Controlador`, `Repositorio`, `Adaptador`, `Puerto`, `Servicio`, `CasoDeUso`, `Configuracion`, `Propiedades`, `Mapeador`, `Excepcion`, `Solicitud`/`Respuesta`, `Prueba`/`PruebaIntegracion` (tabla en `docs/glosario.md`). Ejemplos: `PacienteControlador`, `PacienteRepositorioAdaptador`, `MensajeriaPropiedades`.
5. **Se mantiene en inglés solo lo que imponen las librerías:**
   - APIs, anotaciones y tipos de Java, Spring, Reactor, Kafka, Resilience4j y Spring AI.
   - Claves de configuración de frameworks (`spring.r2dbc...`).
   - Métodos heredados de librerías (`findById`, `save`, `map`, `flatMap`...), que se usan tal cual.
6. **Frontera con las librerías.** Los puertos de la capa `aplicacion` usan nombres en español (`buscarPorId`, `guardar`). Los adaptadores traducen hacia métodos de Spring Data u otros clientes en inglés (`findById`, `save`) sin exponerlos hacia adentro.
7. **Paquetes:** `dominio`, `aplicacion` (`puerto.entrada`, `puerto.salida`) e `infraestructura` (`web`, `persistencia`, `mensajeria`, `ia`, `config`).
8. **Contrato público.** Los campos JSON siguen el español del enunciado (`nombreCompleto`, `quejaPrincipal`). Los nombres de eventos y de topics no son literales en el código: se leen de propiedades de configuración, con valores por defecto en español (`paciente_registrado`, `triaje.eventos`).
9. **Documentación** (README, ADRs, requisitos) y **mensajes de commit descriptivos** en español; se conserva el prefijo de Conventional Commits en inglés (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
10. **Ortografía de «triaje».** Se usa `triaje` (forma recomendada en español) en código, módulos, documentación y propiedades. Excepción: el contrato público definido por el enunciado se mantiene literal (rutas `/triage/...`; topic `triaje.eventos` por defecto).

## Alternativas consideradas
- **Todo en inglés (convención habitual).** Pros: coincide con el ecosistema y es legible para equipos internacionales. Contras: exige traducir el dominio, rompe la correspondencia 1:1 con los requisitos y aleja el código de la terminología que usan los expertos clínicos del país. Descartada porque el producto es de uso local.
- **Dominio en español y sufijos técnicos en inglés** (`PacienteController`, `PacienteRepository`). Pros: coincide con las convenciones de Spring y con la documentación oficial. Contras: produce nombres mezclados en cada clase y rompe la coherencia buscada. Fue la primera propuesta; se descartó por decisión del responsable del proyecto en favor de español total.
- **Dominio en español e infraestructura en inglés sin regla de frontera.** Descartada: genera mezclas inconsistentes dentro de una misma clase. La regla 6 la reemplaza con una frontera explícita.

## Consecuencias
**Positivas**
- Trazabilidad directa entre requisito, código, API y eventos; el glosario es una sola fuente de vocabulario.
- Mejor comunicación con personal clínico y de negocio, y revisiones de reglas (RN-01 a RN-05) más claras.
- El contrato de la API, en español, es natural para el futuro frontend y para integraciones locales.

**Negativas / costos**
- Convive código en español con APIs de librerías en inglés (`repositorio.save(...)`, `Mono<Paciente>`). Es inevitable y se acota con la regla de frontera.
- Los métodos derivados de Spring Data (`findBy...`) permanecen en inglés dentro de los adaptadores; las consultas con nombres en español se escriben con `@Query`.
- Algunas herramientas de construcción asumen sufijos en inglés en las pruebas (Maven detecta `*Test` e `*IT`). Con Gradle las pruebas se detectan por JUnit y no por nombre; el sufijo `PruebaIntegracion` solo se usa para separarlas en su propia tarea (ADR-0004). Aun así hay que verificar que las pruebas se ejecutan..
- Requiere disciplina para no mezclar idiomas en un mismo concepto; se verifica en revisión de código y con las instrucciones de `AGENTS.md`.

## Seguimiento
- `AGENTS.md` incorpora esta convención para las herramientas de generación de código.
- Revisar la decisión si se incorporan equipos externos o el producto se internacionaliza.

