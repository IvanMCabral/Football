# PB1.2.3D — Tactical editor certification

## Alcance

Se evaluó la edición táctica pública desde el modal de formación, con arrastre por píxeles, cambio de formación y persistencia observable.

## Resultado observado

- Apertura del modal en la aplicación pública: aprobada en escritorio.
- Arrastre de un jugador: evidencia guardada antes/después en `C:\Users\ichu_\AppData\Local\Temp\pb123c-public-before-drag.jpg`, `pb123c-public-after-drag.jpg` y `pb123c-public-post-fix-drag.jpg`.
- Cambio de formación: observado con `4-4-2`.
- Starter → banco con validación de 10/11: el rechazo es visible y evita una alineación inválida.

## Pendientes de certificación

- swap de dos titulares y persistencia después de recargar;
- sustituto → XI con confirmación de rol y posición;
- confirmación de lineup y recuperación tras logout/login;
- layout y arrastre en laptop y móvil;
- consistencia de todos los invariantes después de una temporada completa.

## Veredicto

`INCOMPLETE — NOT CERTIFIED`. La base visual funciona, pero el alcance PB1.2.3D exige el recorrido completo de reemplazos, recarga y dispositivos.
