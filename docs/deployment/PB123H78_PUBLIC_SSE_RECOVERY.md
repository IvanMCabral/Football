# PB1.2.3H7.8 — Public SSE recovery

## Resultado

**NOT RUN — public service unavailable for certification.**

No se abrió un stream SSE, no se inició un round y no se midieron primeros o
segundos eventos, monotonicidad, hard reload ni recuperación. Es incorrecto
presentar estos puntos como aprobados.

La URL pública del backend no respondió dentro del timeout en los probes de
liveness y readiness. Sin una respuesta HTTP no es posible distinguir una
caída de servicio de una revisión no desplegada, ni validar Content-Type,
duplicación de streams o tiempo al primer `data:`.
