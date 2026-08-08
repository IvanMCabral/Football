# PB1.2.3H7.9B — Warm public N=10 performance

Se ejecutaron 10 muestras independientes por endpoint, con runtime warm,
readiness 200, DB UP y Redis UP. Son tiempos de red API medidos desde Node;
no representan handler físico de navegador.

| Endpoint | N | p50 ms | p95 ms | Resultado |
|---|---:|---:|---:|---|
| dashboard/user-stats | 10 | 1163.0 | 1952.2 | P2, supera objetivo |
| career/status (squad) | 10 | 206.7 | 245.0 | PASS |
| career/lineup/current | 10 | 207.1 | 909.8 | PASS p50; p95 bajo observación |
| career/fixtures/round/1 | 10 | 244.2 | 314.2 | PASS |
| career/standings | 10 | 204.0 | 282.9 | PASS |
| career/lineup/auto-select | 10 | 2718.6 | 3183.3 | P2, supera objetivo |
| career/lineup/confirm | 10 | 2538.7 | 2590.5 | P2, supera objetivo |

Todas las respuestas fueron HTTP 200. No se inventaron campos
handler→POST, response→live ni handler→first-SSE: la instrumentación física
del navegador no estaba disponible. Las 12 rondas de las temporadas aportan
SSE y finalización de minuto 90, pero no sustituyen un trace de click a pixel.
