# PB1.2.3H2 — Remediación del inicio de partido

## Problema observado

La muestra H1 tenía **5.278 ms** desde la acción pública hasta `/live`. El
número mezclaba navegación Angular, espera previa al POST, respuesta HTTP y
apertura del SSE; no era una medición del motor aislado.

## Cambios aplicados

- Se agregó `MatchStartRequestTrace` con correlación por request ID y todas las
  etapas requeridas, sin exponer datos sensibles.
- El cliente marca T0, envía `X-Request-Id` y registra respuesta, ruta, stream y
  primer SSE.
- El header de correlación fue incorporado a CORS; antes de ese cambio el
  preflight público rechazaba el POST y dejaba la pantalla en “Iniciando
  partido…”.
- El catálogo de equipos dejó de bloquear el camino crítico: los nombres se
  hidratan luego desde los fixtures.
- Se eliminó la marca de click en el camino de auto-start que dejaba tiempos
  acumulados entre fechas.
- Se conservaron idempotencia, estado inicial consultable, reglas, fixtures y
  resultados del motor.

## Resultado

Las diez trazas warm muestran POST p50 **302 ms**, primer SSE p50 **305 ms** y
cero POST/stream duplicados en la campaña pública. La navegación completa hasta
`/live` sigue teniendo una cola externa mayor (p50 **4.050 ms** en la medición
de click de la pantalla de carrera); la diferencia está cuantificada como
pre-request Angular y no se atribuye al motor.

## Estado

La remediación queda **COMPLETED WITH ISSUES**: el cuello restante está medido,
no produce inconsistencias ni errores de estado, y queda aislado para una
iteración posterior de UX de navegación.
