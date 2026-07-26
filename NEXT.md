# NEXT - siguiente paso

> Actualizado: 2026-07-26

## Estado de repos

- Backend: limpio y pusheado en `feat/v25d99.20.3.1-runtime-fixes`.
- Frontend: limpio y pusheado en `feat/v25d99.20.3.1-runtime-fixes`.

## Ultimos commits relevantes

Backend:

- `69486caa` - `clean backend configuration comments`
- `a2138d9a` - `clean backend runtime comments`
- `6aef2083` - `clean backend test comments`

Frontend:

- `ca59687` - `clean frontend project metadata`
- `4883f8f` - `sync frontend lockfile name`

## Pruebas recientes

- Backend compile: OK.
- Backend tactica/live focal: OK.
- Frontend build: OK.
- Frontend tests headless: OK, 915 success, 2 skipped.

## Siguiente trabajo recomendado

1. Revisar dependencias frontend con upgrade controlado por `npm audit`.
2. Archivar o resumir documentacion historica que ya no describe el estado real.
3. Empezar el modelo profesional de lesiones y stamina:
   - stamina baja minuto a minuto;
   - atributos efectivos bajan con cansancio;
   - lesion leve/moderada/grave afecta rendimiento;
   - mantener a un lesionado en cancha aumenta riesgo;
   - el modal de sustitucion debe explicar por que recomienda cada cambio.
4. Volver al harness para comparar mismo partido con:
   - distintas formaciones;
   - cambios durante el partido;
   - jugadores cansados;
   - jugadores lesionados;
   - movimientos por pixel.

## Regla operativa

Si se toca motor o modal, se prueba:

- build/compile;
- tests focales;
- harness con seed fija;
- una pasada visual en navegador.
