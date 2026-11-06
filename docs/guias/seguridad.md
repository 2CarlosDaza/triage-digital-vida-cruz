# Guía: seguridad

## Autenticación JWT [OBLIGATORIO]
- **Emisor:** `triaje-servicio` (paquete `identidad`, mismo despliegue; no se crea un tercer servicio). `asistente-servicio` solo **valida** con la clave pública, sin llamar a triaje.
- **Endpoints públicos:** `POST /auth/registro` y `POST /auth/login`.
- **Algoritmo RS256.** Par de claves PEM por variables de entorno: `JWT_CLAVE_PRIVADA` solo en triaje, `JWT_CLAVE_PUBLICA` en ambos servicios. Un script `scripts/generar-claves-jwt.sh` las genera; nunca se versionan. En pruebas se generan al vuelo.
- **Claims:** `sub`, `roles`, `iss`, `aud`, `iat`, `exp`, `jti`. Se validan `iss`, `aud`, `exp` y `nbf` con tolerancia de reloj de 30 s como máximo. Se rechaza cualquier algoritmo distinto de RS256, incluido `none`.
- Expiración por propiedad (60 minutos por defecto). **Sin refresh token** (limitación documentada en el README).
- [RECOMENDADO] Encabezado `kid` para poder rotar claves.

## Registro y roles [OBLIGATORIO]
- El registro **no permite elegir rol**. Una cuenta nueva no tiene permisos operativos hasta que un `ADMIN` le asigne roles (`PATCH /auth/usuarios/{id}/roles`).
- Roles: `ENFERMERIA`, `MEDICO`, `ADMIN`. El `ADMIN` inicial se crea al arrancar desde `ADMIN_USUARIO` y `ADMIN_CONTRASENA` (por entorno), de forma idempotente.
- Propiedad `identidad.registro.rol-inicial` (vacía por defecto) para entornos de demostración.
- Contraseñas con BCrypt (costo por propiedad, mínimo 10), largo entre 10 y 72 bytes. Hash y verificación en `boundedElastic`. Nombre de usuario normalizado a minúsculas y único. Las contraseñas nunca se registran en logs.
- Credenciales inválidas siempre devuelven el mismo error (sin revelar si el usuario existe).
- **Fuerza bruta:** contador persistente en PostgreSQL (`intentos_fallidos`, `bloqueado_hasta`). Tras 5 intentos fallidos, bloqueo temporal de 15 minutos (propiedades). [RECOMENDADO] límite por IP en el Ingress.

## Autorización [OBLIGATORIO]
Una sola configuración centralizada (`SecurityWebFilterChain`), sin lógica dispersa por controladores. Matriz propuesta (a confirmar):

| Endpoint | ENFERMERIA | MEDICO | ADMIN |
|---|---|---|---|
| `POST /triage/pacientes` | sí | sí | no |
| `PATCH /triage/pacientes/{id}/estado` | sí | sí | no |
| `GET /triage/pacientes/{id}` | sí | sí | no |
| `GET /triage/reportes/operacion` | no | sí | sí |
| Alertas de escalamiento (RF-06) | no | sí | no |
| `POST /asistente/consulta` | no | sí | no |
| `GET /asistente/estado-urgencias` | sí | sí | no |
| `PATCH /auth/usuarios/{id}/roles` | no | no | sí |
| Actuator distinto de health | no | no | sí |

Pruebas parametrizadas por endpoint y rol que verifican 200, 401 y 403.

## Superficie expuesta [OBLIGATORIO]
- Actuator: solo `health` (liveness y readiness) es público; `management.endpoints.web.exposure.include` es explícito. Métricas y demás, solo `ADMIN` o red interna.
- CORS: orígenes permitidos por propiedad `seguridad.cors.origenes-permitidos` (vacío = denegar). Nunca `*` junto con credenciales. Se mantienen las cabeceras de seguridad por defecto de Spring Security.
- Errores 401, 403 y 500 en `ProblemDetail`, sin detalles internos ni trazas, y con el `correlationId`.

## Límites de entrada [OBLIGATORIO]
- Cuerpo máximo 64 KB (`spring.codec.max-in-memory-size`). `nombreCompleto` 3 a 100 caracteres, `quejaPrincipal` hasta 2000, `pregunta` hasta 500.
- Se rechazan caracteres de control. No se registran cuerpos de solicitud.

## Secretos [OBLIGATORIO]
- Variables de entorno o Secrets de Kubernetes. `.env.example` solo con nombres, sin valores. Ninguna clave ni contraseña en imágenes, en el repositorio ni en los manifests (solo marcadores).
