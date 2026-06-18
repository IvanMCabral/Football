# MANAGER_TEAM_RUNBOOK

> **Proposito:** Single source of truth para el equipo de agentes (Mavis, MANAGER, SENIOR, REVISOR) que labura en el proyecto MANAGER. Si Mavis pierde memoria, este archivo es la guia.
>
> **Mantenedor:** Mavis (root) — actualizar cada vez que se cree/aborte un agente, se cambie un ID, o se aprenda un workflow nuevo.
>
> **Ultima actualizacion:** 2026-06-16 ART — restart: nohup → cron → mensaje texto obligatorio → terminar turno. Eliminado Start-Sleep 60 del turno launch. Fix bug "Ran 2 command(s) colgado".

---

## 1. Proyecto MANAGER — contexto

- **Repo:** `D:\ProyectosOpenCode\MANAGER`
- **Sub-repo front:** `D:\ProyectosOpenCode\MANAGER\front-ciber\project`
- **Stack:** Spring Boot 3.2.1 WebFlux + R2DBC PostgreSQL + Redis + Angular.
- **Java 17/21** (`C:\Users\ichu_\.jdk\jdk-21.0.8\bin\java.exe`)
- **Profile activo:** `local,v24-mutations`
- **DB:** PostgreSQL en `localhost:5432` (DB `football_manager`, user/pass `postgres/postgres`).
- **Redis:** binario en `C:\temp\redis\redis-server.exe`, puerto 6379.
- **Backend port:** 8080, **Frontend port:** 4200.
- **Health check:** NO usar `/actuator/health` (da 404). Usar `POST /api/v1/auth/login` con body `{}` — cualquier 4xx/5xx != connection refused = vivo.

---

## 2. Agentes (roles fijos, NO borrar sin OK de Ivan)

| Agente | ID actual | Rol |
|---|---|---|
| Mavis (root) | `mvs_3f18031aaa7b4cd6a4e35a40d2a83f30` | Orquestador. Coordina, traduce, valida endpoints con curl/Invoke-WebRequest, **ejecuta el restart del stack cuando hace falta (unico autorizado, ver seccion 4)**. NO toca codigo ni push. |
| MANAGER | `mvs_0be88ffe746e46ffb6bce8bd82bd07f5` | Analista de codigo. Redacta prompts de fix, revisa diffs. NO toca codigo, NO push. |
| SENIOR | `mvs_3d366cb7063e47b4a4649ff6682bb826` | Ejecuta fixes aprobados, escribe codigo, hace commits. **NO push sin autorizacion explicita de Ivan, Y SOLO DESPUES de que el smoke test pase GO.** |
| REVISOR v4 | `mvs_e3aa7328996f43658288abd686f7d607` | Smoke visual con Playwright MCP. Captura screenshots. Reporta PASS/FAIL. NO toca codigo, NO reinicia stack. |

**REINICIADOR agente ELIMINADO 2026-06-15 00:34 ART** (decision de Ivan, despues de 10 versiones fallidas v1-v10). Su rol queda absorbido por Mavis root. NO recrear este agente — esta documentado en seccion 4 + 10.11 por que no funciona.

### Workflow del equipo (lo que nunca cambia)

```
[bug/feature]
    -> Mavis (root) identifica y asigna
    -> MANAGER analiza codigo + redacta prompt de fix
    -> Ivan aprueba el prompt
    -> SENIOR ejecuta, hace commit LOCAL (sin push)
    -> MANAGER revisa el diff (GO/NO-GO)
    -> Si GO -> Mavis reinicia stack + le pasa a REVISOR la tarea de smoke
    -> REVISOR corre smoke (visual con Playwright o HTTP con curl/HttpClient)
    -> Si smoke GO -> Ivan autoriza push -> SENIOR pushea -> tag cerrado
    -> Si smoke NO-GO -> REVISOR reporta bugs -> MANAGER arma nuevo prompt -> ciclo de nuevo (NUNCA push si el smoke no paso GO)
```

---

## 3. Reglas de oro (las que Ivan repite, las que NO se rompen)

1. **SENIOR no pushea sin autorizacion EXPLICITA de Ivan, Y SOLO DESPUES de que el smoke test pase GO** (Ivan 2026-06-15, regla #1 ampliada). Si el smoke dio NO-GO, se itera el fix hasta que el smoke de GO, recien ahi se pushea. Cada push requiere: (a) confirmacion textual de Ivan, (b) verificacion de que REVISOR reporto GO en el smoke del tag actual. Sin ambos, NO push.
2. **SENIOR no ejecuta smoke visual** — eso es REVISOR. (Division de roles.)
3. **MANAGER no toca codigo** — solo lee y redacta prompts.
4. **Mavis no toca codigo ni push** — solo coordina, valida y reinicia stack.
5. **REVISOR no reinicia stack** — si el backend se cae durante el smoke, REVISOR reporta y Mavis decide si reinicia. (REVISOR nunca ejecuta el `.bat`/`.sh`.)
6. **Todo prompt de sesion nueva** debe incluir "report a mvs_3f18031aaa7b4cd6a4e35a40d2a83f30" en algun momento.
7. **Mavis informa a Ivan al terminar CADA tarea** (Ivan 2026-06-15, regla #7). Cada vez que un MANAGER, SENIOR o REVISOR termina una fase del workflow, Mavis sintetiza el resultado y se lo reporta a Ivan en el chat directo con un resumen de 2-3 lineas: que paso, que salio, que sigue. Sin esto Ivan se entera tarde de los avances. El "reporting" de los agentes a Mavis es un sub-componente — Mavis es quien presenta el estado consolidado a Ivan.
7. **Working tree no se contamina** — si hay cambios preexistentes (NEXT.md, untracked, screenshots), NO se commitean con el fix. Push solo los commits del scope.

---

## 4. Como arrancar / reiniciar el stack — TAREA DE MAVIS (root)

**Quien lo hace:** Mavis (root). Es la unica entidad autorizada para reiniciar el stack. Ni SENIOR ni REVISOR lo tocan. Esta documentado aca para que ni Mavis ni nadie lo olvide si Mavis pierde sesion.

### 4.1 Script oficial unico

- **Path:** `C:\Users\ichu_\Desktop\start_manager_full.sh` (script principal con git-check). Tambien existe `reiniciar.sh` que mata por nombre + llama a este, pero para el flujo Mavis se usa `start_manager_full.sh` directo (asi el git-check es self-contained).
- **Que hace:** git-check contra `D:\temp\manager-state\last-restart-commit` → si cambio, mata + restart. Si no, skipea en 5s.
- **BUG IPv6 FIXEADO (2026-06-16):** Angular a veces binds en `::1` (IPv6) y `Get-NetTCPConnection` no lo detecta en `127.0.0.1` (IPv4) → skipeaba restart del frontend aunque el proceso viejo siguiera vivo. **Fix aplicado:** el script ahora mata los PIDs guardados en `${PID_DIR}/frontend.pid` ANTES de verificar puertos.
- **Wrapper Windows v4:** `C:\Users\ichu_\Desktop\start_manager_full.bat` — `start /B bash start_manager_full.sh` desacopla el bash.exe. Sale en <1s.

### 4.2 Procedimiento canónico para Mavis (3 fases, NUNCA en el mismo turno)

**FASE 1 — LANZAR (turno del trigger). Exactamente 3 pasos, en este orden:**

Paso 1 — lanzar el script:
```bash
bash -c "cd '/c/Users/ichu_/Desktop' && nohup bash reiniciar.sh > /tmp/mgr-rstr.log 2>&1 &"
```

Paso 2 — crear el cron:
```
mavis cron self check-stack-after-restart --every 1m --prompt "PRIMER TICK (mensaje VISIBLE al usuario): testea puertos 5432/6379/8080/4200 con Test-NetConnection. Si los 4 UP -> 'stack UP, paso a REVISOR' + mavis cron delete mavis check-stack-after-restart. Si DOWN -> leer /tmp/mgr-rstr.log + reportar error. SIGUIENTES TICKS: solo si el estado cambió."
```

Paso 3 — OBLIGATORIO: escribir mensaje de texto al usuario y terminar el turno:
> "Restart lanzado ✓. Cron activo cada 1 min para verificar los 4 puertos. Cuando levante te confirmo."

**SIN el paso 3, Mavis queda colgado.** El modelo no termina el turno solo después de ejecutar comandos — necesita escribir texto. Esta es la causa del bug donde el usuario ve "Ran 2 command(s)" y Mavis no responde más.

NO hacer Start-Sleep. NO hacer Test-NetConnection en este turno. NO esperar el cron.

---

**FASE 2 — VERIFICAR (cuando el cron dispara o cuando Iván pregunta):**

```powershell
$pg = (Test-NetConnection localhost -Port 5432 -InformationLevel Quiet) -eq 'True'
$rd = (Test-NetConnection localhost -Port 6379 -InformationLevel Quiet) -eq 'True'
$be = (Test-NetConnection localhost -Port 8080 -InformationLevel Quiet) -eq 'True'
$fe = (Test-NetConnection localhost -Port 4200 -InformationLevel Quiet) -eq 'True'
Write-Host "PG=$pg Redis=$rd Backend=$be Frontend=$fe"
```

Si los 4 `True` → reportar al usuario + `mavis communication send` a REVISOR + eliminar cron.
Si alguno `False` → leer logs + reportar error + dejar cron activo.

**FASE 3 — CERRAR.** Una vez confirmado UP estable, `mavis cron delete mavis check-stack-after-restart` (idempotente).

### 4.3 Procedimiento alternativo (kill manual + bash directo, sin reiniciar.sh)

```powershell
# Solo si reiniciar.sh no esta o falla. Misma idea, otra ruta.
Get-Process java,mvn -ErrorAction SilentlyContinue | Where-Object {$_.StartTime -lt (Get-Date).AddMinutes(-3)} | Stop-Process -Force
Get-Process node -ErrorAction SilentlyContinue | Where-Object {$_.Path -like '*front-ciber*'} | Stop-Process -Force
Start-Sleep -Seconds 2
powershell -ExecutionPolicy Bypass -File "C:\Users\ichu_\Desktop\restart-stack-v2.ps1" 2>$null; Write-Host "OK"
# Devolver control al toque. NO Start-Sleep en el mismo turno. Verificacion 1 minuto despues.
```

### 4.3b Fix IPv6 bind en start_manager_full.sh (2026-06-16)

Agregar en `start_manager_full.sh` antes de la verificación de puertos del frontend:

```bash
# === KILL STORED PIDS FIRST (fix IPv6 stale bind) ===
# Angular a veces binds en ::1 (IPv6) y Get-NetTCPConnection no lo detecta en 127.0.0.1 (IPv4).
# Por eso el script cree que el puerto está libre pero npm start falla.
# Fix: matar los PIDs guardados ANTES de verificar puertos.
if [ -f "${PID_DIR}/frontend.pid" ]; then
  STORED_FE_PID=$(cat "${PID_DIR}/frontend.pid" | tr -d '[:space:]')
  if [ -n "${STORED_FE_PID}" ] && [ "${STORED_FE_PID}" != "0" ]; then
    ps_capture "Stop-Process -Id ${STORED_FE_PID} -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
    echo "  Killed stored frontend PID ${STORED_FE_PID}"
  fi
fi
if [ -f "${PID_DIR}/backend-jvm.pid" ]; then
  STORED_BE_PID=$(cat "${PID_DIR}/backend-jvm.pid" | tr -d '[:space:]')
  if [ -n "${STORED_BE_PID}" ] && [ "${STORED_BE_PID}" != "0" ]; then
    ps_capture "Stop-Process -Id ${STORED_BE_PID} -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1 || true
    echo "  Killed stored backend PID ${STORED_BE_PID}"
  fi
fi
sleep 2
```

### 4.4 Logs y PIDs

- Backend log: `D:\temp\manager-backend.log` y `D:\temp\manager-backend.err.log`
- Frontend log: `D:\temp\manager-frontend.log` y `D:\temp\manager-frontend.err.log`
- PID files: `D:\temp\manager-pids\` (backend-jvm.pid, backend-mvn.pid, frontend.pid)
- Estado HEAD: `D:\temp\manager-state\last-restart-commit` (ultimo HEAD conocido del git-check)
- Restart log efimero: `/tmp/mgr-rstr.log` (se borra al reiniciar Windows)

### 4.5 Reglas duras del restart

1. **Path bash:** siempre `/c/Users/ichu_/Desktop` (Git Bash portable). NO `/d/...`, NO `/mnt/c/...`, NO `C:\...` con backslashes.
2. **NUNCA** ejecutar el `.bat`/`.sh` en foreground — siempre via `restart-stack-v2.ps1` (Start-Process desacoplado) o `nohup ... &`.
3. **NUNCA** mirar `bat-out.log` ni `bat-err.log` mientras corre. El polling a los 4 puertos es la unica fuente de verdad.
4. **NUNCA** esperar output interactivo. `restart-stack-v2.ps1` devuelve en <1s.
5. **NUNCA lanzar + verificar en el mismo turno.** Procedimiento: nohup → cron → mensaje de texto al usuario → terminar turno. El cron hace la verificación ~1 min después. NO Start-Sleep, NO Test-NetConnection en el turno del launch. **El paso de escribir el mensaje de texto después del cron es OBLIGATORIO** — sin él Mavis queda colgado (bug detectado 2026-06-16). Decisión de Iván.
6. **Matar PIDs viejos por edad:** `StartTime -lt (Get-Date).AddMinutes(-3)`. NO -10 (era muy permisivo y mataba procesos legitimos).
7. **Matar node solo del front-ciber:** `Where-Object {$_.Path -like '*front-ciber*'}`. NO matar node del MCP de Mavis.
8. **Si en 60s los 4 puertos NO estan UP** → leer logs y reportar.
9. **Fix IPv6 bind (2026-06-16):** `start_manager_full.sh` ahora mata los PIDs guardados en los PID files ANTES de verificar puertos. Esto resuelve el bug donde Angular binds en `::1` (IPv6) y `Get-NetTCPConnection` no lo detecta en IPv4 (`127.0.0.1`), causando skip incorrecto del restart del frontend.

---

## 5. Como regenerar agentes cuando se traban

Las sesiones pueden morir (modelo saturado, daemon timeout, lo que sea). Cuando pasa:

1. **Listar sesiones existentes:**
   ```bash
   mavis session list <agentName>
   ```

2. **Si la sesion esta `aborted` o trabada sin movimiento > 15 min:**
   ```bash
   mavis session abort <sessionId>     # o close para borrarla definitivamente
   ```

3. **Crear sesion nueva con prompt dummy + el prompt real por communication send** (workaround del truncado):
   ```bash
   # Sesion vacia primero
   mavis session new --from mvs_3f18031aaa7b4cd6a4e35a40d2a83f30 --title "AGENTE - vN" --prompt "Inicializando. Aguarda instrucciones de Mavis root." <agent-name>

   # Despues mandar el prompt real por communication (preferentemente via .md para evitar truncado)
   mavis communication send --from mvs_3f18031aaa7b4cd6a4e35a40d2a83f30 --to <new-session-id> --command prompt --content "Lee: <path al .md con la tarea>"
   ```

4. **Si la tarea es muy larga**, escribirla a un `.md` en el workspace del agente y mandar solo el puntero. **Los mensajes largos se truncan** — confirmado varias veces esta sesion.

---

## 6. Formato de prompts (WORKFLOW_TEAM.md, lo que no cambia)

### FIX (MANAGER -> SENIOR)

```
# FIX: [BUG-ID] — [titulo corto]

## Root Cause
[explicacion con archivo:linea]

## Fix propuesto
[pasos concretos, minimo invasivo]

## Archivos a modificar
- [lista con paths absolutos]

## Testing sugerido
[como verificar que el fix funciona]

## Contexto
[datos del bug, userId, careerId, etc si aplica]
```

### SMOKE TEST (REVISOR -> Mavis)

```
# SMOKE TEST: [tag o feature]

## Verificacion de stack
- PostgreSQL: [up/down]
- Redis: [up/down]
- Backend: [up/down — health check usado]
- Frontend: [up/down]

## Resultado
GO / NO-GO

## Evidencia visual
- Screenshot 1: [path] — [que muestra]
- Screenshot 2: [path] — [que muestra]

## Bugs detectados
- [bug] — severidad [alta/media/baja]

## Conclusiones observacionales
### Cosas que funcionaron bien
### Cosas que anduvieron mal o se sintieron raras
### Mejoras posibles detectadas

## Notas
```

---

## 7. Tags cerrados (para referencia)

- **V24D7** — E2E HTTP coverage 36% (9 controllers, 11 test classes).
- **V24D8-BUG-002/003/004** — 3 bugs cerrados (seed placeholders, squad vacio, squad muestra placeholders).
- **V24D9** — BUG-001 Register redirect (login auto post-register). Commits `6a2896c` back + `cbab590c` front.
- **V24D10** — BUG-002 /matches + BUG-003 /games/{id}. Commits `d2f52e7` back + `511f19b` + `1ed9b94` front.

## 8. Tags en curso

_(Sin tags en curso al 2026-06-16. V24D12-D-5 fue marcado OBSOLETO por V24D12-D-6 — `application-local.yml` gitignored reemplazó completamente el `.env` legacy. Ver NEXT.md § "OBSOLETO — V24D12-D-5". El próximo tag es P1a — Match detail UI polish.)_

## 9. Cola de trabajo (de NEXT.md, antes de V24D9)

- **P1a:** Match detail UI polish (85% listo).
- **P1b:** Career mutations edge cases (90% listo).
- **P2a:** V23 Phase 10C (TeamOverallCalculator M3).
- **P2b:** V23 Phase 6C (TeamStyle user-configurable M3).
- **Deuda tecnica:** E2E coverage 36%→80% (V24D7+2 pendiente). `.env` con credenciales en plaintext RESUELTO en V24D12-D (4 commits, hash `ee27111`, pusheado a origin/master 2026-06-15 14:50).
- **Incidente:** SENIOR pusheo V24D8-BUG-004 sin autorizacion (regla #1 violada). Conversacion pendiente.

---

## 10. Lecciones aprendidas (2026-06-14, esta sesion)

### 10.1 Gotcha: `mavis communication send` trunca mensajes largos
- **Workaround:** escribir el contenido a un `.md` y mandar solo el path.

### 10.2 Gotcha: `mavis agent new --description` rechaza >100 chars
- **Workaround:** descripcion corta en Castellano sin acentos. Si se corta, version mas corta.

### 10.3 Gotcha: `mavis session new` requiere `--from` y `--prompt`
- **Workaround:** prompt dummy minimo, despues mandar el prompt real por `mavis communication send`. El agente va como argumento POSICIONAL, no como flag `--agent`.

### 10.4 Gotcha: `.bat` puede confundir a IAs
- **v4 fix:** `start /B bash ...` desacopla. El `.bat` sale en <1s. NO mirar bat-out.log.

### 10.5 Gotcha: `GameController` tiene 9 metodos con NPE latente
- **Detalle:** `String userIdStr = authentication != null ? authentication.getName() : null; UUID.fromString(userIdStr);` — NPE si userIdStr es null.
- **Por que pasa:** `SecurityConfig.java:77-91` tiene `permitAll()` para `/api/v1/games/**`.
- **Fix recomendado:** audit completo (Opcion B de MANAGER), en V24D10.5.

### 10.6 Gotcha: `mvn spring-boot:run` en foreground traba a las IAs
- **Workaround:** siempre `Start-Process -NoNewWindow` + redirect + polling desde afuera.

### 10.7 Gotcha: el `.bat` original (v1, v2) tenia `pause` y `timeout` al final
- **Por que confunde a IAs:** la IA ve output bloqueante y se queda esperando.
- **v3 fix:** sacamos el pause/timeout. `exit /b %RC%` limpio.

### 10.8 Regla: REVISOR nunca reinicia stack
- **Por que:** smoke visual y restart son roles distintos. Si el stack no responde, REVISOR avisa a Mavis, Mavis reinicia.
- **Inverso:** si smoke falla por bug de codigo, REVISOR NO reinicia. Solo reporta a Mavis.

### 10.9 Regla: SENIOR respeta la division de roles
- **Smoke NO es suyo.** Si lo hace, se confunde con REVISOR.
- **Codigo y commits, si. Smoke visual, no.**

### 10.10 Workflow: smoke pre-push vs post-push
- **Pre-push** (recomendado por Ivan): Mavis recarga stack si hace falta → REVISOR corre smoke focal → si GO, Ivan aprueba push.
- **Post-push:** Ivan aprueba push directo, REVISOR corre smoke en el repo publico. Si falla, hotfix de urgencia.

### 10.12 Gotcha: Angular binds en IPv6 y Get-NetTCPConnection no lo detecta (2026-06-16)
- **Problema:** Angular dev server a veces bindea en `::1` (IPv6) en vez de `127.0.0.1` (IPv4). `Get-NetTCPConnection` en Windows默认 solo muestra conexiones IPv4. El script `start_manager_full.sh` pensaba que el puerto 4200 estaba libre cuando en realidad un proceso viejo lo tenía ocupado en IPv6 → skip del restart → frontend down silencioso.
- **Sintoma:** `Test-NetConnection 127.0.0.1 -Port 4200` dice False, pero `Get-NetTCPConnection -LocalPort 4200` muestra `::1:4200` con un PID old.
- **Fix aplicado:** `start_manager_full.sh` ahora mata los PIDs guardados en `${PID_DIR}/frontend.pid` y `${PID_DIR}/backend-jvm.pid` ANTES de hacer `is_port_listening()`. Esto asegura que procesos viejos mueran antes de la verificación.

### 10.14 Regla: mensajes grandes van a archivo, no inline (2026-06-16)
- **Problema:** `mavis communication send` trunca mensajes largos (≥~30 líneas). El header llega pero el cuerpo se corta. Confirmado 2026-06-16 12:43 cuando MANAGER mandó 3 partes y todas llegaron truncadas.
- **Regla permanente:** si la respuesta es >20 líneas, escribir a un `.md` en el workspace de Mavis root (`C:\Users\ichu_\.mavis\agents\mavis\workspace\`) y mandar SOLO el path por communication. El agente destino lee el archivo y puede continuar sin truncar.
- **Aplicar a:** MANAGER, SENIOR, REVISOR, Coder, Verifier, General. Toda sesión que reporte a Mavis.

### 10.13 Gotcha: agentes custom no aparecen en el panel derecho de la UI (2026-06-16)
- **Problema:** Los agentes MANAGER, SENIOR, REVISOR (manager-football, senior-football, revisor-football) creados con `mavis agent new` **NO aparecen** en el panel derecho de "Agent tema" del chat de Mavis. El panel de historial/contexto del chat solo se muestra para los agentes default (coder, verifier, general, mavis).
- **Root cause:** Flag `isBuiltin: true` vs `isBuiltin: false` en el config del agente. Los default tienen `isBuiltin: true, creationSource: "builtin"`. Los custom tienen `isBuiltin: false, creationSource: "auto"`. El cliente de Mavis filtra el panel derecho basándose en este flag (o en una lista hardcodeada de builtin).
- **Confirmado:** `mavis agent info manager-football` despues de re-crear muestra `"isBuiltin": false, "creationSource": "auto"`. No hay flag en `mavis agent new` que permita forzar `isBuiltin: true`.
- **Workaround CONFIRMADO FUNCIONA (2026-06-16 12:40):** la razon REAL por la que los agentes custom no aparecen es que **no tenian sesion activa spawneada desde root**. Una vez que se crea la sesion con `mavis session new --from mvs_3f18031aaa7b4cd6a4e35a40d2a83f30 --title "..." --prompt "..." <agent-name>`, el chat aparece en el panel derecho con su historial. **NO es la flag `isBuiltin` lo que importa, es tener una sesion activa spawneada desde root**.
- **Workaround alternativo:** usar los agentes default (coder, verifier, general) directamente. Re-escribir su prompt con la logica de MANAGER/SENIOR/REVISOR no es viable — son `isBuiltin: true` con prompts del sistema muy especificos.
- **Si se quiere mantener la division de roles:** NO necesario forzar `isBuiltin`. Solo crear la sesion custom spawneada desde root y ya aparece en el panel.

### 10.11 Leccion: agente REINICIADOR ELIMINADO 2026-06-15 (no recrear)
- **Problema raiz (10 versiones, v1-v10):** cuando el agente REINICIADOR recibia la tarea de ejecutar `reiniciar.sh` y reportar, en vez de ejecutar **mandaba un ACK inicial** y se quedaba en standby, o **se quedaba cargando skills**, o **esperaba confirmacion** que nadie le iba a dar. El bug es del modelo MiniMax-M3, NO del script. El script `reiniciar.sh` (v1) es correcto y Mavis lo ejecuta sin problema.
- **Por que no se arreglo en 10 intentos:** el patron se repite aunque el prompt diga "ejecuta primero, no ACK". El agente, al ser una sesion hija, tiene el mismo modelo de fondo. Cambiar el prompt, el agent.md, o la complejidad del script no cambia el comportamiento. Solo cambia quien lo ejecuta.
- **Lo que SI funciona: Mavis (root) ejecuta `bash /c/Users/ichu_/Desktop/start_manager_full.sh` directo** desde la sesion root (sale en ~10s con git-check). El script matea los PIDs guardados antes de verificar puertos (fix IPv6 2026-06-16), asi que no hay stale bindings. Despues `Start-Sleep 60` + `Test-NetConnection` a los 4 puertos y reportar al usuario.
- **Mavis valida la parte automatizable** (puertos, endpoints sin auth con Invoke-WebRequest) y **delega el smoke visual a REVISOR**. No mezcle roles.
- **Senales de "se trabo"** (en cualquier agente): manda un ACK inicial pero no completa la tarea en 1-2 min, o manda reportes parciales, o se queda sin output por 5+ min. Accion: `mavis session abort` + dejar que Mavis lo haga.
- **Si en algun momento se quiere re-intentar:** NO vale la pena. El modelo es el mismo. Mejor invertir el tiempo en otra cosa.

### 10.13 Regla: Mavis ejecuta la rotacion de credenciales (Ivan 2026-06-15)
- **Por que:** Ivan decidio 2026-06-15 14:43 que el no confirma ni ejecuta la rotacion de credenciales (Postgres `ALTER USER`, Redis `CONFIG SET requirepass`, JWT `openssl rand -base64 64`). La rotacion queda como responsabilidad de Mavis root, que la puede delegar a MANAGER (analisis) o SENIOR (ejecucion) segun el caso. Los valores reales NUNCA se transmiten por chat (canal inseguro). Script copy-paste ready en `docs/rotar-credenciales.md` (repo, trackeado) o en `workspace/rotar-credenciales.md` (workspace de agentes, no trackeado).
- **Cuando aplica:** cada vez que se commitea algo que referencia credenciales reales (`.env`, configs de infra, secrets en codigo), o como parte de un fix de seguridad (tipo V24D12-D).
- **Workflow sugerido:** MANAGER redacta el procedimiento de rotacion → Mavis lo revisa → SENIOR o Mavis lo ejecutan en infra → Ivan autoriza el push final → smoke REVISOR valida.

---

## 11. Comandos utiles (cheat sheet)

```bash
# Sesiones
mavis session list <agentName>
mavis session info <sessionId>
mavis session abort <sessionId>
mavis session close <sessionId>
mavis session messages <sessionId> --limit 5

# Agentes
mavis agent list
mavis agent info <name>

# Comunicacion
mavis communication send --from <id> --to <id> --command prompt --content "..."
mavis communication peers
mavis communication messages --to <id>

# Memoria y skills
mavis memory append mavis --content "..."
mavis cron self <name> --every 10m --prompt "..."
mavis cron delete mavis <name>
```

### 11.1 Correr `mvn test` (cheat sheet E2E)

**Regla:** desde V24D12-D (rotacion de credenciales, 2026-06-15), `mvn test` requiere **AMBAS** env vars exportadas antes de invocarse. Sin ellas, los 19 test classes E2E fallan con `NOAUTH Authentication required` en `AbstractIntegrationTest.cleanRedis:61` (línea 64 original con `.block()`).

- `DB_PASSWORD` — la test DB `football_manager_test` requiere la password rotada de V24D12-D-6 (`Mgr2026Rot!Secure#`).
- `REDIS_PASSWORD` — Redis DB 15 (test) requiere la password rotada de V24D12-D (`MgrRedis2026!Rotate#Secure`).

**Forma recomendada** (idempotente, ambos tests):

```powershell
# Suite completa (1319 tests):
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\run-game-controller-tests.ps1

# Un test especifico:
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\run-game-controller-tests.ps1 -Test "RoundControllerE2ETest"
```

**Forma manual** (si necesitas copiar/pegar en otra terminal):

```powershell
$env:DB_PASSWORD = "Mgr2026Rot!Secure#"
$env:REDIS_PASSWORD = "MgrRedis2026!Rotate#Secure"
Set-Location D:\ProyectosOpenCode\MANAGER
mvn test
```

**Sintoma de error tipico** (F3 → F4): 1319 tests / 0 failures / **100 errors** (19 clases E2E con `RedisConnectionFailure Unable to connect to Redis` → `NOAUTH Authentication required`). Significa siempre que falta una de las dos env vars.

**Estado esperado con env vars correctas + Redis UP**: `Tests run: 1319, Failures: 0, Errors: 0, Skipped: 0`. Si quedan failures/errors, ver F4 reporte en `C:\Users\ichu_\.mavis\agents\senior-football\workspace\reporte-f4-investigacion.md`.

---

*Fin del runbook. Ivan pidio este archivo 2026-06-14 22:41 ART — actualizar en cada cambio.*
