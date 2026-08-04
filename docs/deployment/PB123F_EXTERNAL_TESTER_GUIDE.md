# PB1.2.3F — Guía del piloto público

## URLs

- Frontend: https://manager-4f952.web.app
- Backend: https://manager-staging-api.onrender.com

## Recorrido mínimo

1. Registrarse con una cuenta nueva.
2. Iniciar sesión y abrir la carrera.
3. Elegir país, división y club.
4. Confirmar la alineación desde `Squad`.
5. Abrir el partido en vivo y observar el minuto a minuto.
6. Si aparece una lesión, revisar la recomendación, sustituir o descartar y reanudar.
7. Abrir el resumen de fecha, resultados y tabla.
8. Volver al equipo, refrescar y cerrar sesión.
9. Iniciar sesión nuevamente y comprobar que la carrera, plantilla, fixture y tabla siguen disponibles.

## Perfiles

- **Novato:** sigue el recorrido mínimo sin cambiar tácticas.
- **Táctico:** abre el editor visual, cambia formación, mueve una ficha unos píxeles y verifica química/roles antes de guardar.
- **Móvil:** repite el recorrido con 390 × 844 CSS px; no debe haber scroll horizontal ni controles inaccesibles.

## Tiempos

Clasificar cada espera visible como inmediata (<1 s), aceptable (1–3 s), visible (3–8 s), lenta (8–20 s) o crítica (>20 s). Un despertar de Render puede producir una espera visible; no repetir clics mientras aparece el indicador de carga.

## Recuperación

Ante una pantalla de carga, esperar la recuperación del backend. Si una sesión se pierde, volver a iniciar sesión. Refrescar durante una carga o usar atrás/adelante no debe duplicar una fecha, una alineación ni un guardado.

## Reporte seguro

Compartir únicamente URL, endpoint, estado HTTP, duración, request ID sanitizado y descripción del resultado. Nunca compartir contraseñas, tokens, cookies, correos, payloads completos ni claves de Redis.
