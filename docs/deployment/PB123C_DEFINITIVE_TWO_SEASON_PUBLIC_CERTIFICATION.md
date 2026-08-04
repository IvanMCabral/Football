# PB1.2.3C — Certificación pública definitiva de dos temporadas

Fecha de ejecución: 2026-08-04
Rama: `feat/v25d99.20.3.1-runtime-fixes`

## Resultado

**REJECTED**

La remediación corrigió y publicó el frontend y permitió completar el flujo técnico de una cuenta nueva hasta un partido finalizado. No se certificaron dos temporadas públicas consecutivas ni se completaron los drills de cold start/restart exigidos. No se usa evidencia histórica como sustituto.

## Revisiones y URLs

- Backend/root: `6579a698` (`Optimize first world load for new accounts`).
- Frontend: `dfed971`, que contiene `96ea9ba` y la corrección posterior del reset público del drag.
- Frontend público: <https://manager-4f952.web.app>.
- Backend público: <https://manager-staging-api.onrender.com>.
- Firebase CLI: deploy completo de 52 archivos, versión finalizada y release publicada.
- Índice público comprobado con HTTP 200; SHA-256 final observado el 2026-08-04T13:46: `0a743ede203d6327ebf045b1fc93f9f286eee8f0d0a3de410c8db0d7b954f357`.

## Cuenta nueva y flujo jugable

Se utilizó una identidad efímera `example.invalid`, sin registrar credenciales en este documento.

| Paso | Resultado | Tiempo observado |
|---|---:|---:|
| Registro con email, username y password | 200 | no persistido como dato personal |
| `/auth/me` | 200 | incluido en la validación |
| Carga de tres ligas | 200 | 13.074 ms |
| Creación de carrera | 201 | respuesta sin cuerpo |
| Estado de carrera | 200 | temporada 1, fecha 1, 38 fechas |
| Plantel | 200, 24 jugadores | 12.347 ms |
| Auto-select con `{formation:"4-4-2"}` | 200, 7.993 bytes | 6.000 ms aprox. |
| Fixture fecha 1 | 200, 10 partidos | 2.000 ms aprox. |
| Inicio de ronda | 200, estado `RUNNING` | 4.000 ms aprox. |
| Estado de partido | 200 | minuto 63, 2-0, 5 s después del inicio |
| Estado final | 200 | minuto 90, `FINISHED`, 3-0, 24 eventos |

El registro sin `username` fue probado por separado y devolvió 500. Los logs de Render identificaron la causa exacta: la columna `users.username` es `NOT NULL`. El formulario Angular sí exige y envía username; el contrato público debe documentar esta obligatoriedad o devolver un 4xx controlado, no 500.

## Temporadas

### Temporada 1

- Carrera nueva: sí.
- Fecha 1: auto-select y partido completado.
- Fecha 2: preparada e iniciada durante la medición de restart.
- Cierre de temporada: no ejecutado.
- Posición final, transición y estadísticas históricas: no certificadas.

### Temporada 2

- Transición real: no ejecutada.
- Fechas requeridas: no ejecutadas.
- Cierre de temporada: no ejecutado.

Por lo tanto, el gate de **dos temporadas nuevas, consecutivas y completas** queda abierto.

## Gates no cumplidos

- Dos temporadas públicas nuevas completas: **FAIL**.
- Transición de temporada y conservación histórica: **FAIL**.
- Cold start real con métricas separadas: **FAIL**.
- Restart de Render durante un partido con recuperación clasificada: **FAIL**.
- Instrumentación Redis/DB con p50/p95 por operación: **FAIL**.
- Registro sin username: **WARNING/P1**, actualmente expone 500 ante contrato incompleto.

## Adenda de redeploy

La corrección visual se desplegó nuevamente desde `dfed971` antes de cerrar esta certificación, pero no altera el resultado: siguen faltando dos temporadas públicas nuevas y consecutivas.

## Conclusión

La cuenta nueva es jugable cuando usa el contrato completo del frontend y el primer partido público finalizó correctamente. Esto no alcanza el criterio de cierre de PB1.2.3C. El resultado correcto para esta ejecución es **REJECTED**.
