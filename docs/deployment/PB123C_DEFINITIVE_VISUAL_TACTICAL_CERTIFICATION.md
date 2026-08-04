# PB1.2.3C — Certificación visual táctica definitiva

Fecha: 2026-08-04
Sitio probado: <https://manager-4f952.web.app/squad>
Navegador: Chrome real con sesión existente

## Resultado

**PARTIAL — NO CERTIFICADO PARA CIERRE**

La corrección de drag source está desplegada y el modal público se abrió con cancha, banco, formación, química, zonas y 11/11. Se realizó un arrastre real mediante mouse y el motor visual actualizó la lectura táctica. No se completaron todos los escenarios obligatorios ni las tres resoluciones de viewport.

## Evidencia visual

Capturas locales generadas durante la sesión:

- Antes del drag: `C:\Users\ichu_\AppData\Local\Temp\pb123c-public-before-drag.jpg`.
- Después del drag: `C:\Users\ichu_\AppData\Local\Temp\pb123c-public-after-drag.jpg`.
- Formación 4-4-2: `C:\Users\ichu_\AppData\Local\Temp\pb123c-public-formation-442.jpg`.

La captura posterior al drag mostró el cambio observable `Francisco Facello: ATT → DEF`, coordenadas `50.0/12.0 → 83.1/82.7`, química `66/99 → 65/99`, eficiencia `92% → 90%`, ATT `121% → 110%` y DEF `70% → 74%`. También apareció la traza de movimiento y cobertura por canal.

## Matriz de escenarios

| Escenario | Resultado | Evidencia |
|---|---|---|
| Abrir lineup público | PASS | Modal `Editor de Formación`, cancha y banco visibles |
| Arrastrar titular A a slot B | PASS | Drag real; cambio ATT → DEF y métricas distintas |
| Intercambiar dos titulares | NO CERTIFICADO | El intento no produjo cambio observable en esta sesión |
| Titular al banco | NO CERTIFICADO | El intento no produjo cambio observable en esta sesión |
| Suplente al once | NO CERTIFICADO | El intento no produjo cambio observable en esta sesión |
| Cambiar formación | PASS | 4-3-3 → 4-4-2, slots 11/11 y métricas actualizadas |
| Confirmar y persistir | NO CERTIFICADO | No se confirmó una nueva carrera con posterior verificación API |
| Recargar | NO CERTIFICADO | No se completó dentro de esta sesión visual |
| Logout/login | NO CERTIFICADO | No se completó dentro de esta sesión visual |
| Desktop | PASS | Captura Chrome visible, modal usable |
| Laptop 1366×768 | NO CERTIFICADO | No se obtuvo viewport real separado |
| Móvil 390×844 | NO CERTIFICADO | No se obtuvo viewport real separado |

## Observación de consistencia

Después de mover manualmente un jugador y cambiar a 4-4-2, la posición manual se conservó y se observó proximidad/solapamiento entre dos fichas en la zona baja derecha. Esto es coherente con conservar coordenadas manuales, pero requiere una prueba específica de normalización/reset antes de declararlo apto para todos los dispositivos.

## Consola y bundle

- El bundle público responde 200.
- El agregado de scripts públicos contiene la URL Render correcta.
- No contiene `localhost`.
- No contiene `_pickupPositionInElement` ni `_dragRef` privados.
- No se obtuvo una captura completa de consola limpia para todos los escenarios.

## Conclusión

El drag individual y el cambio de formación funcionan visualmente en desktop y modifican los números tácticos. La certificación táctica completa queda **REJECTED/PARTIAL** hasta probar swap, banco, confirmación, reload/login y los dos viewports restantes.
