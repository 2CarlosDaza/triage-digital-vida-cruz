# Requisitos — Vida Cruz · Triaje digital con asistente clínico

> Fuente: enunciado "Prueba Técnica Dev. Webflux" (2026-10-29), nivel Senior / Arquitecto, plazo de 3 días.
> Ortografía: `triaje` en código, módulos, documentación y propiedades. El **contrato público del enunciado se respeta literal**: rutas `/triage/...` y `/asistente/...`, topic `triaje.eventos` (por defecto), nombres de evento y estados tal como aparecen aquí.

## Contexto
Vida Cruz opera 12 salas de urgencias. El triaje es manual, lo que genera: clasificación variable según la enfermera, falta de trazabilidad y de tiempos de espera, y médicos sin protocolos a la mano.
Se construye un sistema de backend (APIs + eventos), sin frontend (debe poder conectarse a uno a futuro), con al menos dos microservicios Java reactivos:

1. **Servicio de Triaje:** registra pacientes con signos vitales, clasifica según reglas objetivas, vigila SLA por nivel y escala automáticamente, con auditoría completa.
2. **Servicio de Asistente clínico:** consulta de protocolos internos en lenguaje natural con IA generativa. Nunca inventa contenido: toda respuesta se sustenta en el corpus oficial con cita de la fuente.

Debe demostrar programación reactiva sin llamadas bloqueantes en el event loop, Kafka asíncrono, resiliencia ante falla del proveedor de IA y observabilidad.

## RF-01 — Registro y clasificación automática
`POST /triage/pacientes`

Cuerpo: nombre completo, edad, frecuencia cardiaca (bpm), presión sistólica (mmHg), presión diastólica (mmHg), SpO2 (%), temperatura (°C), dolor (0-10), GCS (3-15), queja principal (texto).

- Validar rangos (ver Validaciones). Valor fuera de rango → **400** con mensaje claro que indique el campo inválido.
- Clasificar según RN-01.
- Respuesta: `id`, `nivel`, `slaMinutos` (RN-02), `estado: "REGISTRADO"`, `fechaRegistro` (ISO 8601).
- Publicar el evento `paciente_registrado` en el topic Kafka `triaje.eventos`.

## RF-02 — Flujo de estados clínicos
`PATCH /triage/pacientes/{id}/estado` con cuerpo `{"estado":"..."}`

- Estados: `REGISTRADO`, `EN_CONSULTA`, `ALTA`.
- Transiciones válidas: `REGISTRADO → EN_CONSULTA`, `REGISTRADO → ALTA` (alta directa), `EN_CONSULTA → ALTA`.
- Transición inválida (por ejemplo `ALTA → EN_CONSULTA`) → **409** con mensaje descriptivo.
- Cada transición válida: registra auditoría (RN-04) y publica el evento (`paciente_en_consulta` o `paciente_alta`) en `triaje.eventos`.

`GET /triage/pacientes/{id}` → datos completos, estado actual, flags (por ejemplo `escalado`) y tiempo en el estado actual.

## RF-03 — Monitoreo de SLA y escalamiento automático
Un proceso de fondo (scheduler, consumidor o equivalente; decisión del candidato) evalúa periódicamente a los pacientes activos (estado `REGISTRADO`).

- Si un paciente superó su SLA (RN-02) sin haber entrado a consulta: marcarlo como `ESCALADO` (flag persistente), publicar `paciente_escalado` en `triaje.eventos` y registrar auditoría.
- Cumplir RN-03 (idempotencia).
- El intervalo de evaluación y los SLA son **configurables por propiedades** para poder probar el escalamiento sin esperar decenas de minutos.

## RF-04 — Asistente clínico (servicio de Asistente)
**Parte A — Consulta con RAG:** `POST /asistente/consulta` con cuerpo `{ "pregunta": "..." }`.
Respuesta: `{ "respuesta": "...", "fuentes": [ { "protocolo": "PROTO-XXX", "seccion": "..." } ] }`.

- RAG sobre el corpus de protocolos (ver Datos de referencia). Toda respuesta cumple RN-05 (cita obligatoria, sin alucinaciones).
- Sin protocolo relevante: responder de forma explícita que no existe un protocolo disponible y **no generar contenido clínico**.

**Parte B — Estado de la urgencia en tiempo real:** `GET /asistente/estado-urgencias`
Devuelve la cantidad de pacientes activos (todos los que no estén en `ALTA`) por nivel de triaje.

- El asistente reconstruye este estado **consumiendo el topic `triaje.eventos`** (no llamando a la API de triaje) y persiste la vista en Redis o en una base NoSQL.

## RF-05 — Reporte de operación
`GET /triage/reportes/operacion?desde=...&hasta=...` (rango ISO 8601)

Agrupado por hora, devuelve: total de pacientes registrados, conteo por nivel, tiempo promedio de espera (registro → primera entrada a consulta, en minutos) y percentil 95 (p95) de ese tiempo.

- Debe usar **SQL avanzado** (funciones de ventana con `OVER`, `RANK`/`ROW_NUMBER`, agregados con filtros, subconsultas correlacionadas o equivalente). **No se permite iterar en memoria** sobre todos los registros para calcular agregados.

## RF-06 — Alertas de escalamiento al equipo médico (abierto a propósito)
El sistema debe alertar al equipo médico cuando un paciente es escalado. Canal, formato y tecnología no están especificados: se decide, se implementa y se documenta (README o ADR) con justificación. La decisión, los supuestos y su documentación forman parte de la evaluación (autonomía, ownership, tolerancia a la ambigüedad).

## Reglas de negocio

### RN-01 — Clasificación por nivel
Evaluar en orden; el primer nivel que se cumpla determina la clasificación. Si varias condiciones de distintos niveles se cumplen, rige el de mayor gravedad.

| Nivel | Nombre | Condiciones (cualquiera) |
|---|---|---|
| 1 | CRITICO | FC < 40 o FC > 120; SpO2 < 90; P. sistólica < 90; GCS <= 8 |
| 2 | URGENTE | 90 <= SpO2 <= 93; 90 <= P. sistólica <= 100; Temperatura >= 38.5; 9 <= GCS <= 13 |
| 3 | URGENTE_2 | 90 <= FC <= 120; 38.0 <= Temperatura < 38.5; Dolor >= 7 |
| 4 | NO_URGENTE | No cumple ninguna condición de niveles 1-3 |
| 5 | NO_URGENTE_2 | Clasifica como nivel 4 **y** la queja principal pertenece a la lista de quejas administrativas |

### RN-02 — SLA por nivel
Tiempo máximo desde el registro hasta entrar a consulta; también es el momento del escalamiento.

| Nivel | SLA |
|---|---|
| CRITICO | 5 min |
| URGENTE | 10 min |
| URGENTE_2 | 30 min |
| NO_URGENTE | 60 min |
| NO_URGENTE_2 | 120 min |

### RN-03 — Idempotencia del escalamiento
Un paciente se escala una única vez: el segundo intento no publica otro evento ni duplica auditoría. Un paciente en `EN_CONSULTA` o `ALTA` nunca debe ser escalado.

### RN-04 — Auditoría clínica
Todo cambio de estado o flag (registro, entrada a consulta, escalamiento, alta) se registra con: timestamp, id de paciente, estado anterior, estado nuevo y tipo de evento. La auditoría no se puede borrar por API (no existe endpoint de eliminación).

### RN-05 — Anti-alucinación del asistente
Toda respuesta clínica incluye al menos una fuente válida del corpus. Si la fuente no existe en el corpus, la respuesta es inválida. Si no hay coincidencia relevante, la respuesta es exclusivamente el aviso "no existe protocolo disponible", sin contenido clínico generado.

## Validaciones de datos (RF-01)
| Campo | Regla |
|---|---|
| Frecuencia cardíaca | 20 - 250 bpm |
| Presión sistólica | 60 - 260 mmHg |
| Presión diastólica | 30 - 160 mmHg y siempre menor que la sistólica |
| SpO2 | 70 - 100 % |
| Temperatura | 34.0 - 42.0 °C |
| Dolor | 0 - 10 |
| GCS | 3 - 15 |
| Edad | 0 - 120 años |
| Nombre | 3 - 100 caracteres |

## Datos de referencia

**Pacientes de ejemplo (usar en pruebas; el nivel esperado es el resultado correcto según RN-01):**

| # | FC | P. Sist | P. Dias | SpO2 | Temp | Dolor | GCS | Queja principal | Nivel esperado |
|---|---|---|---|---|---|---|---|---|---|
| E1 | 130 | 180 | 110 | 97 | 36.8 | 8 | 15 | Dolor torácico | CRITICO |
| E2 | 95 | 150 | 95 | 91 | 37.0 | 4 | 15 | Dificultad para respirar | URGENTE |
| E3 | 88 | 130 | 85 | 98 | 38.2 | 6 | 15 | Fiebre | URGENTE_2 |
| E4 | 72 | 120 | 80 | 99 | 36.5 | 2 | 15 | Herida por cortar | NO_URGENTE |
| E5 | 70 | 115 | 75 | 99 | 36.4 | 0 | 15 | Renovación de recetario | NO_URGENTE_2 |
| E6 | 35 | 80 | 50 | 85 | 35.9 | 0 | 6 | Inconsciente | CRITICO |

**Quejas administrativas (nivel 5):** documentos, recolección de resultados, renovación de recetario, vacunación, seguimiento.

**Corpus de protocolos (mínimo obligatorio; se puede extender con protocolos propios):**

**PROTO-001 — Crisis hipertensiva, manejo inicial**
- Definición: presión arterial >= 180/120 mmHg. Si hay daño de órgano diana es emergencia; si no, urgencia.
- Medidas iniciales: reconfirmar la presión tras 5 minutos de reposo. No reducir la presión más de un 25 % en la primera hora. Vigilar signos de encefalopatía.
- Contraindicaciones: nifedipino sublingual. Reducción intravenosa rápida sin monitorización en cuidados intensivos.

**PROTO-002 — Sepsis, identificación temprana (qSOFA)**
- Criterios: 2 o más de: frecuencia respiratoria >= 22, alteración del estado mental, presión sistólica <= 100 mmHg.
- Acciones iniciales: lactato y hemocultivos antes del antibiótico. Antibiótico de amplio espectro en la primera hora. Fluidoterapia con 30 ml/kg de cristaloides si hay hipotensión o lactato >= 4.

**PROTO-003 — Deshidratación pediátrica**
- Clasificación: leve (pérdida 3-5 % del peso), moderada (5-8 %), severa (> 8 % o alteración del estado mental).
- Rehidratación: leve o moderada sin vómito → solución oral 50 ml/kg en 4 horas. Severa → vía intravenosa, bolo de 20 ml/kg.

**Consultas de ejemplo del asistente (usar en pruebas):**
1. "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?" → responde con contenido de PROTO-001 y lo cita en `fuentes`.
2. "¿Cuándo se administra el primer antibiótico en sepsis?" → responde con contenido de PROTO-002 y lo cita.
3. "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?" → no hay protocolo en el corpus: responde que no existe protocolo disponible, sin generar contenido clínico.

## Requisitos técnicos
La arquitectura es de libre elección (módulos, capas, patrones, formato de mensajes); se evalúa como indicador de diseño.

- **Servicios:** mínimo 2 microservicios Java reactivos (Triaje y Asistente Clínico). Una descomposición adicional debe justificarse.
- **Stack reactivo obligatorio:** Spring Boot 3 + WebFlux con `Mono`/`Flux`, y R2DBC para PostgreSQL. Las llamadas bloqueantes en el event loop se penalizan.
- **Kafka:** topic `triaje.eventos` con los eventos `paciente_registrado`, `paciente_escalado`, `paciente_en_consulta`, `paciente_alta`. Formato de mensaje libre; documentarlo.
- **Resilience4j:** circuit breaker sobre las llamadas al proveedor de LLM, con fallback explícito (modo degradado: 503 con mensaje claro cuando el LLM no está disponible). El servicio de triaje nunca depende de la disponibilidad del asistente.
- **RAG:** Spring AI o LangChain4j. Vector store libre (in-memory, pgvector, Redis u otro); documentarlo en un ADR.
- **Observabilidad:** probes de health/readiness, logs estructurados con correlación, métricas con Micrometer (mínimo: conteos de clasificación por nivel, escalamientos, latencia del LLM). Trazado distribuido con OpenTelemetry es bonus.
- **Seguridad:** ninguna credencial hardcodeada (API key del LLM por variable de entorno), autenticación básica en los endpoints (JWT o API key; documentarla) y validación de entradas.
- **Pruebas:** unitarias + de integración. El LLM debe poder mockearse. Testcontainers recomendado (no obligatorio) para PostgreSQL y Kafka. Cubrir como mínimo: clasificación de E1-E6, transiciones válidas e inválidas, idempotencia del escalamiento, citas del RAG y el caso "sin coincidencia sin alucinación".
- **Ejecución local:** un `docker-compose` que levante todo con un solo comando (PostgreSQL, Kafka o equivalente, Redis y ambos servicios).
- **Kubernetes:** manifests (Deployment, Service, probes) con Azure como objetivo (AKS o Azure Container Apps).
- **ADR:** mínimo 2 decisiones documentadas.

## Entregables obligatorios
1. Repositorio GitHub (público, o privado con acceso para el evaluador) con el código completo.
2. `README.md`: ejecución local en un solo comando, arquitectura con diagrama, tecnologías y decisiones clave justificadas.
3. Pruebas automatizadas que validen los flujos de negocio.
4. URL de la aplicación desplegada en Azure con las APIs accesibles, más los manifests de Kubernetes / IaC usados.
5. ADR (mínimo 2).

## Criterios de evaluación
Cumplimiento de RF y RN (incluida la calidad del SQL del RF-05); arquitectura de microservicios y diseño; integración reactiva (Reactor, Kafka, backpressure); IA (calidad del RAG, citas, anti-alucinación, modo degradado); resiliencia y observabilidad; manejo de errores y excepciones; seguridad; cobertura y calidad de pruebas; despliegue (Docker, Kubernetes, Azure); documentación, ADR y habilidades demostradas (ownership, autonomía, bias por la ejecución).

## Notas del enunciado
- No se responden preguntas durante la prueba: toda ambigüedad (incluida RF-06) se resuelve documentando decisiones y supuestos.
- Commits progresivos con versiones funcionando se valoran positivamente.
- Proveedor de LLM libre; la API key siempre por variable de entorno.
- Se permite usar IA; el estándar es el de un desarrollador senior que la utiliza: sistema funcionando, decisiones justificadas y estructura de calidad.

## Decisiones de mensajería
Todos los topics y todos los nombres de evento se manejan **por propiedad**, nunca como literales en el código ni en los tests:

```yaml
triaje:
  mensajeria:
    topic-eventos: triaje.eventos
    tipos-evento:
      paciente-registrado: paciente_registrado
      paciente-en-consulta: paciente_en_consulta
      paciente-alta: paciente_alta
      paciente-escalado: paciente_escalado
```

- Valores por defecto en `application.yml`, sobrescribibles con variables de entorno (por ejemplo `TRIAJE_MENSAJERIA_TOPIC_EVENTOS`).
- El dominio solo conoce el tipo lógico `TipoEventoTriaje` (`PACIENTE_REGISTRADO`, `PACIENTE_EN_CONSULTA`, `PACIENTE_ALTA`, `PACIENTE_ESCALADO`). El nombre que viaja en el mensaje se resuelve en el adaptador.
- La bandeja de salida (outbox) guarda el tipo lógico; el relay traduce al nombre configurado al publicar.
- Aplica igual a grupos de consumidores, topics de mensajes fallidos (DLQ) y cualquier identificador de mensajería.
- **Contenido de los eventos:** `eventId`, tipo, versión, `occurredAt`, `pacienteId`, `nivel`, estado y `correlationId`. Sin nombre ni signos vitales. La clave del mensaje es `pacienteId`. El asistente necesita `nivel` para la vista de urgencias.

## Interpretaciones adoptadas (confirmar o corregir)
1. Hay **5 niveles**. `E1`-`E6` son los pacientes de ejemplo (casos de prueba); E6 (inconsciente) es CRITICO por RN-01. Confirmado por el enunciado.
2. El escalamiento ocurre cuando `ahora - fechaRegistro` **supera** el SLA (estrictamente mayor), el paciente sigue en `REGISTRADO` y no estaba escalado.
3. `ESCALADO` es un **flag persistente** (`escalado`): no cambia el nivel ni el estado. La auditoría registra estado anterior = estado nuevo con tipo `ESCALAMIENTO`.
4. Una transición al mismo estado es inválida (409). Un valor de estado desconocido en el PATCH es 400.
5. Queja administrativa: se normaliza el texto (minúsculas, sin tildes) y se busca alguna de las 5 frases como palabra o frase completa. La lista es configurable. Solo se aplica cuando el paciente ya sería nivel 4. "Renovación de recetario" (E5) cumple.
6. El 400 devuelve **todos** los campos inválidos, no solo el primero.
7. La temperatura se maneja como `BigDecimal` (una cifra decimal) para evitar errores en los límites 38.0 y 38.5.
8. La clasificación no usa edad ni presión diastólica (solo la validación cruzada diastólica < sistólica). El papel de la edad queda pendiente de revisión por el usuario.
9. El SLA se calcula al registrar a partir de propiedades (SLA por nivel y intervalo de evaluación) y se guarda en el paciente; cambiar las propiedades después no afecta a los ya registrados.
10. El vigilante de SLA es un `Flux.interval` que ejecuta una sentencia atómica (`UPDATE ... WHERE estado='REGISTRADO' AND escalado=false AND vencido RETURNING ...`), segura con varias réplicas.
11. Asistente sin protocolo relevante: HTTP 200 con el aviso "no existe protocolo disponible" y `fuentes` vacío. LLM no disponible (circuito abierto): 503 con mensaje claro. Respuesta generada con citas inválidas: se descarta y se responde el aviso.
12. `estado-urgencias` devuelve los 5 niveles (con 0 cuando no hay pacientes) y el total. Activos = `REGISTRADO` + `EN_CONSULTA`.
13. Reporte RF-05: cubos horarios en UTC, intervalo `[desde, hasta)`; espera = primera entrada a consulta menos registro; los pacientes que nunca entraron a consulta cuentan en totales y niveles pero no en promedio ni p95; las horas sin registros no aparecen. Respuesta: lista de objetos por hora.
14. Límites de entrada (el enunciado no los define): `quejaPrincipal` hasta 2000 caracteres, `pregunta` del asistente hasta 500, cuerpo de solicitud hasta 64 KB.
15. `POST /triage/pacientes` acepta de forma opcional la cabecera `Idempotency-Key`; sin ella, el comportamiento es el del enunciado.
16. Autenticación: JWT RS256 emitido por `triaje-servicio` (paquete `identidad`). `POST /auth/registro` y `POST /auth/login` son públicos; las cuentas nuevas no tienen permisos operativos hasta que un `ADMIN` asigne roles (`ENFERMERIA`, `MEDICO`, `ADMIN`); el `ADMIN` inicial sale de variables de entorno; sin refresh token. El asistente solo valida tokens con la clave pública.
17. Asistente: si fallan los embeddings o el LLM, la respuesta es 503. El aviso "no existe protocolo disponible" solo se emite cuando la recuperación funcionó y no hubo coincidencia, o cuando se descarta una respuesta que no pasa la verificación determinista.

## Decisiones abiertas
- **RF-06:** canal de alerta (propuesta en conversación: stream SSE reactivo como canal principal y webhook opcional) y su ADR.
- **Autenticación:** JWT con registro público de cuentas (decidido por el usuario). Falta confirmar los detalles de la interpretación 16 y la matriz de autorización de `docs/guias/seguridad.md`. El evaluador debe poder obtener credenciales en la URL desplegada: el README documenta el flujo.
- **Embeddings:** proveedor remoto frente a modelo local (afecta resiliencia y el circuit breaker).
- **Azure:** AKS frente a Container Apps; Kafka administrado frente a Kafka dentro del clúster.
- **Vector store** (propuesta: Redis) y **proveedor de LLM** (propuesta: tier gratuito de Gemini): se documentan en ADR.

## Casos límite obligatorios para pruebas de clasificación
| Entrada (resto normal) | Nivel esperado |
|---|---|
| FC 39 / 40 / 89 / 90 / 120 / 121 | 1 / 4 / 4 / 3 / 3 / 1 |
| SpO2 89 / 90 / 93 / 94 | 1 / 2 / 2 / 4 |
| P. sistólica 89 / 90 / 100 / 101 | 1 / 2 / 2 / 4 |
| Temperatura 37.9 / 38.0 / 38.4 / 38.5 | 4 / 3 / 3 / 2 |
| GCS 8 / 9 / 13 / 14 | 1 / 2 / 2 / 4 |
| GCS 3, resto normal | 1 |
| Dolor 6 / 7 | 4 / 3 |
| Combinación nivel 3 + nivel 2 | 2 |
| Combinación nivel 2 + nivel 1 | 1 |
| Nivel 4 con queja "renovación de recetario" | 5 |
| Nivel 3 con queja "vacunación" | 3 (no baja de nivel) |
| Pacientes E1 a E6 de la tabla de ejemplo | CRITICO / URGENTE / URGENTE_2 / NO_URGENTE / NO_URGENTE_2 / CRITICO |
