# ADR-0003 — Gestión de ramas: trunk-based development

- **Estado:** Aceptada
- **Fecha:** 2026-11-05
- **Decisores:** Carlos Daza

## Contexto
El proyecto se desarrolla en pocos días por una sola persona que trabaja con un agente de generación de código. Se exige un historial de commits iterativo, con versiones funcionando, y despliegue en Azure. Se necesita integrar rápido, mantener siempre una versión estable y revisar lo que el agente produce antes de que llegue al tronco.

## Decisión
1. **`main` es el tronco.** Siempre compila, pasa `./gradlew build` y es desplegable. No hay ramas de larga vida (`develop`, `release/*`).
2. **Ramas de vida corta** (horas, máximo un día) por tarea, creadas desde `main` y con nombre `tipo/descripcion-corta` (`feat/dominio-triaje`, `fix/validacion-presion`, `docs/adr-vector-store`). Se rebasan sobre `main` con frecuencia.
3. **Integración por pull request con "Rebase and merge".** Mantiene el historial lineal y conserva los commits pequeños (no se hace squash, porque el historial progresivo es un entregable). El responsable revisa el diff antes de integrar, lo que sirve como puerta de revisión del código generado.
4. **Integración continua** (GitHub Actions): `./gradlew build` en cada pull request y en cada push a `main`.
5. **Protección de `main`:** sin push forzado, historial lineal, CI en verde obligatorio y borrado automático de ramas integradas. Sin aprobaciones obligatorias de terceros (hay un solo desarrollador).
6. **Lo inacabado no se expone:** no se conecta funcionalidad a medias; si debe integrarse antes de estar lista, queda detrás de una propiedad de configuración desactivada por defecto.
7. **Versionado:** etiquetas `vX.Y.Z` sobre `main` en cada hito funcional; el despliegue sale de `main` o de una etiqueta.
8. **Commits:** Conventional Commits con descripción en español (ADR-0002).

## Alternativas consideradas
- **GitFlow** (`develop`, `release`, `hotfix`). Descartada: demasiada ceremonia para un equipo de una persona y un plazo de días; retrasa la integración.
- **GitHub Flow con squash merge.** Descartada: colapsa los commits pequeños en uno por pull request y debilita el historial iterativo que se pide.
- **Commits directos a `main` sin ramas.** Es trunk-based puro y más rápido, pero elimina la puerta de revisión del código generado. Se descartó; puede usarse para cambios triviales de documentación.

## Consecuencias
**Positivas**
- Integración frecuente y `main` siempre estable; cada hito queda etiquetado y desplegable.
- Historial lineal y legible, con commits pequeños que cuentan la evolución del proyecto.
- Cada tarea del agente tiene una rama y un pull request revisable.

**Negativas / costos**
- Exige commits pequeños y CI rápido; una rama que se alarga genera conflictos.
- Sin squash, los commits intermedios deben ser limpios (cada uno compila y pasa pruebas), lo que requiere disciplina.
- La protección de `main` con verificaciones obligatorias solo puede activarse después de que el flujo de CI se haya ejecutado al menos una vez.
