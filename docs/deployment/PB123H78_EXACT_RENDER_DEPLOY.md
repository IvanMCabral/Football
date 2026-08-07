# PB1.2.3H7.8 — Exact Render deploy

## Estado

**BLOCKED — deploy exacto no ejecutado.**

El baseline local es verificable: `c709b831c344bd6f3cf83e49c6d68d72d47a8d9e` está
en la rama `feat/v25d99.20.3.1-runtime-fixes` y coincide con su upstream. El
backend compila con `mvn -q -DskipTests test-compile`.

La operación se detuvo antes de modificar Render porque no existe un canal de
control autenticado disponible en esta sesión. El diagnóstico de Chrome mostró
que el perfil `Profile 1` tiene la extensión instalada y habilitada, pero Chrome
no estaba ejecutándose; el manifest existe, mientras que la clave de registro
del native host no existe y el bridge devuelve `Browser is not available: chrome`.
La primera comprobación pública agotó el timeout mientras el servicio estaba
frío; un reintento posterior devolvió liveness 200 y readiness 200 con
`database=UP` y `redis=UP`. Esto no aporta la revisión live ni el conteo de
instancias requeridos para promover el commit exacto.

## Gates no ejecutados

- Render live revision, plan e instance count: no observables sin dashboard/API.
- Deploy de `c709b831...`: no ejecutado.
- Startup y revisión live: no certificables; el primer probe agotó timeout y el
  reintento posterior fue 200, pero no expuso revisión ni topología.
- Readiness/dependencias: tres lecturas consecutivas 200 con
  `database=UP` y `redis=UP`; no se pudo demostrar que correspondan al commit
  exacto.
- Upstash storage, DBSIZE, plan y región: no observables sin dashboard/API.

No se accedió ni modificó Render, Upstash, Neon, Firebase ni datos públicos.
