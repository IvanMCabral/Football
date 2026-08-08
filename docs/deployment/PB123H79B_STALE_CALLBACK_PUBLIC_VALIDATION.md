# PB1.2.3H7.9B — Public reset and stale-state validation

La validación se realizó sólo con endpoints públicos usados por la UI.

1. C1 se creó y `/match-engine/rounds/start` respondió 201 con roundId
   `ec468091-39b2-42ff-a427-0c23a95fec5b`.
2. Se ejecutó `DELETE /career/reset`, que respondió 204; el status quedó
   `NO_CAREER`.
3. C2 se creó para el mismo owner y recibió el roundId
   `09354aac-9aea-42c8-b13c-7fd2bf92eb1e`.
4. Los roundId fueron distintos. El SSE de C2 mostró minutos 0, 1 y 2,
   `IN_PROGRESS`, sin datos identificables de C1.
5. Owner B conservó carrera `PRE_MATCH` y status HTTP 200 mientras Owner A
   era reseteado.

No se inspeccionaron generaciones internas, claves Redis ni endpoints de
debug. La conclusión es observable: no se detectó reaparición de estado C1 en
C2. No se afirma una garantía distribuida adicional.
