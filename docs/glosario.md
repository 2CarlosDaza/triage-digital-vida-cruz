# Glosario del dominio

Fuente de vocabulario para el código (ADR-0002). Un término por concepto: si hace falta uno nuevo, se agrega aquí antes de usarlo. Sin tildes ni `ñ` en identificadores.

## Triaje

| Término | Identificador | Notas |
|---|---|---|
| Paciente | `Paciente`, `pacienteId` | Agregado principal |
| Signos vitales | `SignosVitales` | Value object |
| Excepción de dominio | `DominioExcepcion` | Base de las excepciones del dominio |
| Datos inválidos | `DatosInvalidosExcepcion`, `camposInvalidos` | Acumula todos los campos fuera de rango de RF-01 |
| Nombre completo | `nombreCompleto` | 3-100 caracteres |
| Edad | `edad` | 0-120 años |
| Frecuencia cardiaca (bpm) | `frecuenciaCardiaca` | |
| Presión sistólica (mmHg) | `presionSistolica` | |
| Presión diastólica (mmHg) | `presionDiastolica` | Menor que la sistólica |
| Saturación de oxígeno (SpO2, %) | `spo2` | Se conserva la sigla del enunciado |
| Temperatura (°C) | `temperatura` | `BigDecimal` |
| Dolor (0-10) | `dolor` | |
| Escala de Glasgow (GCS) | `gcs` | Se conserva la sigla |
| Queja principal | `quejaPrincipal` | Texto extenso |
| Queja administrativa | `quejaAdministrativa` | Lista configurable (nivel 5) |
| Nivel de triaje | `Nivel` | `CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2` |
| Clasificación / clasificar | `Clasificador`, `clasificar()` | RN-01 |
| Regla de clasificación | `ReglaClasificacion` | Estrategia por nivel |
| SLA (minutos) | `slaMinutos` | RN-02 |
| Estado clínico | `EstadoClinico` | `REGISTRADO`, `EN_CONSULTA`, `ALTA` |
| Transición de estado | `transicion`, `cambiarEstado()` | |
| Transición inválida | `TransicionInvalidaExcepcion` | HTTP 409 |
| Fecha de registro | `fechaRegistro` | ISO 8601 |
| Tiempo en el estado actual | `estadoDesde`, `tiempoEnEstado` | |
| Escalamiento / escalar | `escalado`, `escalar()`, `Escalamiento` | RN-03; es un flag |
| Auditoría | `EntradaAuditoria`, `auditoria` | RN-04; solo inserción |
| Tipo de evento de auditoría | `TipoEventoAuditoria` | `REGISTRO`, `ENTRADA_A_CONSULTA`, `ESCALAMIENTO`, `ALTA` (a confirmar) |
| Estado anterior / nuevo | `estadoAnterior`, `estadoNuevo` | |

## Eventos y mensajería

| Término | Identificador | Notas |
|---|---|---|
| Evento | `EventoTriaje` | Sobre común |
| Tipo de evento (lógico) | `TipoEventoTriaje` | `PACIENTE_REGISTRADO`, `PACIENTE_EN_CONSULTA`, `PACIENTE_ALTA`, `PACIENTE_ESCALADO` |
| Nombre del evento en el mensaje | `triaje.mensajeria.tipos-evento.*` | Por propiedad; valores por defecto `paciente_registrado`, `paciente_en_consulta`, `paciente_alta`, `paciente_escalado` |
| Topic | `triaje.mensajeria.topic-eventos` | Por propiedad; valor por defecto `triaje.eventos`; nunca literal en el código |
| Propiedades de mensajería | `MensajeriaPropiedades` | Topics, tipos de evento, grupos de consumidores, DLQ |
| Bandeja de salida (Outbox) | `EventoSalida`, `bandejaSalida` | Se usa "Outbox" en documentación |
| Identificador de correlación | `idCorrelacion` | Propaga por el contexto de Reactor |

## Asistente clínico

| Término | Identificador | Notas |
|---|---|---|
| Protocolo | `Protocolo`, `protocoloId` | Por ejemplo `PROTO-001` |
| Corpus | `Corpus` | Protocolos oficiales |
| Fragmento | `Fragmento` | Trozo recuperado del corpus |
| Consulta | `Consulta`, `pregunta` | Pregunta del médico |
| Respuesta | `Respuesta` | |
| Fuente / cita | `Fuente` (`protocolo`, `seccion`), `fuentes` | Obligatoria (RN-05) |
| Sin protocolo disponible | `SinProtocoloDisponible` | Respuesta exclusiva de aviso |
| Modo degradado | `ModoDegradado` | HTTP 503 con mensaje claro |
| Umbral de similitud | `umbralSimilitud` | Parámetro anti-alucinación |

## Reportes, alertas y vista de urgencias

| Término | Identificador | Notas |
|---|---|---|
| Reporte de operación | `ReporteOperacion`, `FilaReporteOperacion` | RF-05; agrupado por hora |
| Tiempo de espera | `esperaMinutos` | Registro hasta primera entrada a consulta |
| Percentil 95 | `esperaP95Minutos` | Calculado en SQL |
| Alerta de escalamiento | `AlertaEscalamiento`, `AlertaPuerto` | RF-06 |
| Vista de urgencias | `EstadoUrgencias`, `VistaUrgenciasPuerto` | RF-04B; pacientes activos por nivel |
| Paciente activo | `activo` | Estado distinto de `ALTA` |

## Identidad y seguridad

| Término | Identificador | Notas |
|---|---|---|
| Usuario | `Usuario`, `nombreUsuario` | Paquete `identidad` de triaje |
| Rol | `Rol` | `ENFERMERIA`, `MEDICO`, `ADMIN` |
| Cuenta sin permisos | `SIN_ROL` | Estado de una cuenta recién registrada |
| Clave de idempotencia | `claveIdempotencia` | Cabecera `Idempotency-Key` (nombre del contrato) |
| Respuesta descartada | `RespuestaDescartada` | No pasó la verificación determinista del asistente |

## Convenciones de nombres
- Casos de uso como verbo + objeto: `RegistrarPacienteCasoDeUso`, `CambiarEstadoPacienteCasoDeUso`.
- Puertos de salida: `<Concepto>Puerto` con métodos en español, por ejemplo `PacienteRepositorioPuerto` (método `buscarPorId`), `PublicadorEventosPuerto`, `GeneradorRespuestaPuerto`, `BuscadorFragmentosPuerto`.
- Adaptadores: `<Concepto><Tecnologia>Adaptador`, por ejemplo `PacienteRepositorioAdaptador` (envuelve la interfaz de Spring Data `PacienteR2dbcRepositorio`) y `PublicadorEventosKafkaAdaptador`.
- Controladores web: `PacienteControlador`; objetos de entrada/salida: `RegistrarPacienteSolicitud`, `PacienteRespuesta`.
- Implementación de un caso de uso: `RegistrarPacienteServicio`.
- Tests: `debeClasificarComoCriticoCuandoFrecuenciaCardiacaEsMenorA40`.
- Constantes: `MAYUSCULAS_CON_GUION_BAJO` (por ejemplo `SLA_NIVEL_CRITICO`).

## Sufijos de rol técnico (en español)

| Inglés habitual | Sufijo en este proyecto | Ejemplo |
|---|---|---|
| Controller | `Controlador` | `PacienteControlador` |
| Repository | `Repositorio` | `PacienteR2dbcRepositorio` |
| Adapter | `Adaptador` | `PacienteRepositorioAdaptador` |
| Port | `Puerto` | `PublicadorEventosPuerto` |
| Service | `Servicio` | `RegistrarPacienteServicio` |
| UseCase | `CasoDeUso` | `RegistrarPacienteCasoDeUso` |
| Config / Configuration | `Configuracion` | `SeguridadConfiguracion` |
| Properties | `Propiedades` | `MensajeriaPropiedades` |
| Mapper | `Mapeador` | `PacienteMapeador` |
| Exception | `Excepcion` | `TransicionInvalidaExcepcion` |
| Exception handler / Advice | `Manejador` | `ManejadorErroresGlobal` |
| Request / Response (DTO) | `Solicitud` / `Respuesta` | `RegistrarPacienteSolicitud` |
| Publisher / Consumer | `Publicador` / `Consumidor` | `PublicadorEventosKafkaAdaptador` |
| Validator | `Validador` | `RangosSignosValidador` |
| Filter | `Filtro` | `CorrelacionFiltro` |
| Test / Integration test | `Prueba` / `PruebaIntegracion` | `ClasificadorPrueba` |

Métodos heredados de librerías (`findById`, `save`, `map`, `flatMap`...) se usan tal cual; no se renombran ni se envuelven salvo en la frontera de un adaptador.
