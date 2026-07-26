# MANAGER - estado del proyecto

> Ultima actualizacion: 2026-07-26
> Branch backend: `feat/v25d99.20.3.1-runtime-fixes`
> Branch frontend: `feat/v25d99.20.3.1-runtime-fixes`

## Que es

MANAGER es un manager de futbol por turnos con:

- Frontend Angular en `front-ciber/project`.
- Backend Spring Boot WebFlux en la raiz del repo.
- PostgreSQL para datos relacionales.
- Redis para carreras, sesiones, cache y estado de partido.
- Motor de partido con simulacion minuto a minuto, tacticas, formaciones, cambios, lesiones y harness de comparacion.

## Estado actual

El MVP jugable esta en la rama de runtime fixes. La prioridad reciente fue que el modal de DT, el harness y el motor compartan una misma verdad: formacion, slots, posiciones por pixel, cambios de jugadores y tacticas deben afectar lo que se ve y lo que simula el partido.

Lo ultimo cerrado:

- Limpieza de backend productivo: comentarios historicos, prefijos de logs y marcas internas removidas.
- Limpieza de tests backend: comentarios internos removidos sin tocar comportamiento.
- Limpieza de frontend: nombre de proyecto, comentarios de ticket y texto con encoding roto.
- Frontend lockfile sincronizado con `football-manager-ui`.

## Como levantar

Ver el documento del mapa mental:

`C:\Users\ichu_\Desktop\mapa mental de manager\v25d99-53-base-verde-y-limpieza-profunda.md`

Ese archivo tiene el camino de arranque, pruebas y notas de limpieza.

## Validacion reciente

Backend:

- `mvn -q -DskipTests test-compile` OK.
- Suite tactica/live focal OK:
  - `FormationInfererTest`
  - `FormationEffectivenessTest`
  - `PositionEffectivenessCalculatorTest`
  - `SubdivisionEffectivenessCalculatorTest`
  - `TeamRatingsCalculatorWithinZoneTest`
  - `TeamChemistryCalculatorTest`
  - `V24FormationParserTest`
  - `V24SubstitutionEngineTest`
  - `V24LiveSessionTest`

Frontend:

- `npm run build` OK.
- `npm test -- --watch=false --browsers=ChromeHeadless` OK: 915 tests success, 2 skipped.

## Deudas abiertas

- `npm audit` reporta vulnerabilidades en dependencias. No correr `npm audit fix` a ciegas; hay que hacerlo con upgrade controlado y pruebas.
- Queda deuda de documentacion historica en `docs/` y runbooks antiguos. No bloquea el MVP, pero conviene archivarla o resumirla.
- Algunos tests conservan nombres historicos de clase porque renombrarlos masivamente puede aportar poco y meter riesgo.
- El proximo paso funcional recomendado es mejorar el modelo de lesiones/stamina: cansancio durante el partido, impacto progresivo en atributos, severidad de lesion y riesgo al mantener jugadores tocados.

## Criterio para seguir

Antes de cambiar motor o UI:

1. Mantener backend y frontend verdes.
2. Probar el flujo en el harness con el mismo partido y varias formaciones.
3. Confirmar visualmente que el modal de DT modifica los datos que consume el motor.
4. Documentar cada hallazgo en el mapa mental para no repetir investigacion.
