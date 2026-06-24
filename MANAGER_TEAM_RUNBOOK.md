# MANAGER_TEAM_RUNBOOK

> **Proposito:** Single source of truth para el equipo de agentes (Mavis, MANAGER, SENIOR, REVISOR) que labura en el proyecto MANAGER. Si Mavis pierde memoria, este archivo es la guia.
>
> **Mantenedor:** Mavis (root) — actualizar cada vez que se cree/aborte un agente, se cambie un ID, o se aprenda un workflow nuevo.
>
> **Ultima actualizacion:** 2026-06-20 ART — agregado §4.7 "Limpieza de sesiones y crons staled". Cierre definitivo (close, irreversible) de sesiones que compres solo archivan.

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

- **Path:** `C:\Users\ichu_\Desktop\start_manager_full.sh` (script principal con git-check). Script único — antes existía `reiniciar.sh` como wrapper pero fue borrado el 2026-06-19 por redundante.
- **Que hace:** git-check contra `D:\temp\manager-state\last-restart-commit` → si cambio, mata + restart. Si no, skipea en 5s.
- **BUG IPv6 FIXEADO (2026-06-16):** Angular a veces binds en `::1` (IPv6) y `Get-NetTCPConnection` no lo detecta en `127.0.0.1` (IPv4) → skipeaba restart del frontend aunque el proceso viejo siguiera vivo. **Fix aplicado:** el script ahora mata los PIDs guardados en `${PID_DIR}/frontend.pid` ANTES de verificar puertos.
- **Wrapper Windows v4:** `C:\Users\ichu_\Desktop\start_manager_full.bat` — `start /B bash start_manager_full.sh` desacopla el bash.exe. Sale en <1s.

### 4.2 Procedimiento canónico para Mavis (PowerShell nativo, fire-and-forget con Start-Job)

**Descubrimiento crítico (2026-06-19):** el tool de terminal de Mavis es PowerShell, no bash. Además, el tool bash de OpenCode rastrea cualquier proceso hijo directo del shell que invoca y no retorna hasta que termine. Esto descartó:
- Sintaxis bash (`nohup`, `&`, `< /dev/null`, `disown`) — rompe el parser de PowerShell.
- `Start-Process` con o sin `-PassThru` — el tool bash queda "Running" durante los 60-90s del script.
- `cmd /c "start /B bash ..."` — desacopla bash.exe del cmd.exe pero el tool bash igual espera.

**Único mecanismo que desacopla realmente: PowerShell `Start-Job`**, que crea un BackgroundJob sin relación padre-hijo rastreable por el tool bash. Retorna inmediato.

**Comando obligatorio:**

```powershell
Write-Host "Lanzando restart..."
Start-Job -ScriptBlock { bash.exe /c/Users/ichu_/Desktop/start_manager_full.sh > /tmp/mgr-rstr.log 2>&1 }
Write-Host "Restart lanzado via PowerShell Start-Job. Job corriendo en background, tool bash retornó inmediato. Esperando tu próxima."
```

**PROHIBIDO:** sintaxis bash de backgrounding (`nohup`, `&`, `< /dev/null`, `disown`) en el tool de terminal de Mavis. `Start-Process` también está descartado por el tracking del tool bash.

**Procedimiento completo (incluye cron poll 1min para auto-verificar UP):**
1. Ejecutar el bloque PowerShell de arriba (Start-Job + Write-Host).
2. En el mismo turno, crear el cron poll de 1 minuto:
   ```bash
   mavis cron self check-stack-after-restart --every 1m --prompt "PRIMER TICK (mensaje VISIBLE al usuario, NO mavis-progress): testea puertos 5432/6379/8080/4200 con Test-NetConnection (4200 tambien ::1 por IPv6). Si los 4 UP -> reportar 'stack UP, listo para REVISOR' al usuario + mavis cron delete mavis check-stack-after-restart. Si DOWN -> leer /tmp/mgr-rstr.log + reportar. SIGUIENTES TICKS: gate-discipline (skip silencioso)."
   ```
3. Mensaje corto al usuario: "Restart disparado vía Start-Job. Cron activo cada 1 min para confirmar UP."
4. Cerrar el turno. Sin polling adicional en este turno.
5. Verificación de puertos la hace el cron (cuando dispare) o cuando Iván pregunte.

**Validado en producción:** 2026-06-19, Mavis lo ejecutó con éxito y el tool bash retornó inmediato.

---

**VERIFICAR (cuando Iván pregunta, en un turno posterior):**

```powershell
Test-NetConnection -ComputerName localhost -Port 5432 -InformationLevel Quiet
Test-NetConnection -ComputerName localhost -Port 6379 -InformationLevel Quiet
Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet
Test-NetConnection -ComputerName "::1" -Port 4200 -InformationLevel Quiet
```

Si los 4 `True` → reportar "stack UP" al usuario. Recién después pasar la tarea a REVISOR (si aplica).
Si alguno `False` → leer `/tmp/mgr-rstr.log` + reportar error.

### 4.3 Procedimiento alternativo (kill manual + bash directo)

```powershell
# Solo si start_manager_full.sh falla o se quiere mas control.
Get-Process java,mvn -ErrorAction SilentlyContinue | Where-Object {$_.StartTime -lt (Get-Date).AddMinutes(-3)} | Stop-Process -Force
Get-Process node -ErrorAction SilentlyContinue | Where-Object {$_.Path -like '*front-ciber*'} | Stop-Process -Force
Start-Sleep -Seconds 2
# Despues usar el mismo comando PowerShell del §4.2 (Start-Process nativo)
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
2. **NUNCA** ejecutar el `.bat`/`.sh` en foreground — siempre via PowerShell `Start-Job` (ver §4.2). NO usar `Start-Process`, NO `cmd /c start /B`, NO `nohup ... &` (todas quedan "Running" en el tool bash de OpenCode durante los 60-90s del script).
3. **NUNCA** mirar `bat-out.log` ni `bat-err.log` mientras corre. El polling a los 4 puertos es la unica fuente de verdad.
4. **NUNCA** esperar output interactivo. `Start-Job` retorna inmediato sin esperar al script.
5. **SIEMPRE crear el cron poll 1min** después del Start-Job (ver §4.2) — Iván prefiere este flujo para auto-verificar UP sin tener que esperar a preguntarle.
6. **Matar PIDs viejos por edad:** `StartTime -lt (Get-Date).AddMinutes(-3)`. NO -10 (era muy permisivo y mataba procesos legitimos).
7. **Matar node solo del front-ciber:** `Where-Object {$_.Path -like '*front-ciber*'}`. NO matar node del MCP de Mavis.
8. **Si en 60s los 4 puertos NO estan UP** → leer `/tmp/mgr-rstr.log` y reportar.
9. **Fix IPv6 bind (2026-06-16):** `start_manager_full.sh` ahora mata los PIDs guardados en los PID files ANTES de verificar puertos. Esto resuelve el bug donde Angular binds en `::1` (IPv6) y `Get-NetTCPConnection` no lo detecta en IPv4 (`127.0.0.1`), causando skip incorrecto del restart del frontend.

### 4.6 Como avanzar después del UP

Una vez que el cron poll confirma `stack UP` (o Iván pregunta y Mavis valida los 4 puertos), el flujo continúa así:

1. **Mavis reporta "stack UP, listo para REVISOR"** a Iván en el chat.
2. **Si hay sprint activo esperando smoke** → Mavis spawn REVISOR con scope del smoke (ruta al scope `.md` en workspace de REVISOR + career/matchId si aplica).
3. **REVISOR ejecuta el smoke** y reporta GO/NO-GO.
4. **Si REVISOR GO:**
   - Mavis reporta a Iván con TL;DR + lista de commits pendientes de push + paths de evidencia.
   - **Espera OK literal de Iván** antes de pedir push a SENIOR (regla SENIOR #1: Iván OK + smoke GO = condición necesaria para push).
   - Mavis spawn SENIOR con task de push (`git push origin <branch>` + tags).
   - SENIOR empuja + reporta con `evidencia-push-<tag>.md`.
5. **Si REVISOR NO-GO:**
   - Mavis reporta a Iván con causa raíz + fix sugerido.
   - Mavis devuelve sprint al agente correspondiente:
     - Si bug del front → MANAGER re-evalúa scope, escribe nuevo prompt SENIOR-grade, ciclo repite.
     - Si bug del back → idem.
     - Si bug de infra (ej. Redis auth, script start_manager_full.sh) → Mavis arregla directamente y vuelve a restart.
6. **Si no hay sprint activo** → Mavis pregunta a Iván qué sigue.

**Reglas duras:**
- NUNCA push sin Iván OK literal (regla SENIOR #1).
- NUNCA skip del smoke REVISOR — es la verificación en vivo que valida el wire UI y network.
- NUNCA delegar el restart a REVISOR/SENIOR/MANAGER — el restart lo hace Mavis root siempre (§4.2).

### 4.7 Limpieza de sesiones y crons staled (2026-06-20)

Las sesiones se acumulan: agentes que terminaron y quedaron idle, agentes que se trabaron sin reportar, crons que ya cumplieron su watchdog. **Limpieza periódica es tarea de Mavis root**, no de los agentes. Iván la pidió explícita cuando ve la lista llena.

#### Comandos clave

```powershell
# Ver crons activos
mavis cron list mavis

# Borrar UN cron
mavis cron delete mavis <cron-name>

# Archivar UNA sesion (reversible, queda oculta de la lista activa)
mavis session compress <sessionId>

# Borrar DEFINITIVO (irreversible — usar cuando Iván dice "sigo viendolas")
mavis session close <sessionId>
```

#### Diferencia crítica: `compress` vs `close`

| Comando | Qué hace | Iván la ve? | Reversible? |
|---|---|---|---|
| `compress` | Archiva — oculta de la lista activa | **SÍ** (sigue visible) | Sí (`mavis session messages <id>` la recupera) |
| `close` | Borra definitivamente | NO | NO — irrecuperable |

**Regla:** Si Iván dice "sigo viendolas", usar `close`. `compress` no alcanza.

#### Procedimiento de limpieza (cuando Iván lo pide)

```powershell
# 1. Listar sesiones activas de cada agente (3 agentes: manager/senior/revisor)
mavis session list manager-football
mavis session list senior-football
mavis session list revisor-football

# 2. Para cada sesion staled/finished que NO este laburando:
mavis session close <sessionId>     # irreversible — desaparece

# 3. Borrar crons viejos (despues de que su watchdog termino)
mavis cron list mavis                # ver
mavis cron delete mavis <cron-name>  # borrar

# 4. Verificar limpieza
mavis cron list mavis                # count debe ser 0 si no hay async pendiente
```

#### Cuándo limpiar

- Cuando Iván dice "borra los staled" / "sigo viendolas" / "limpia".
- Al cerrar un sprint (Mavis revisa sesiones staled antes de empezar el próximo).
- Cuando la lista de `mavis session list` muestra >10 sesiones, muchas con status `finished` hace días.

#### Anti-patrón aprendido (2026-06-20)

`compress` archiva pero la sesión sigue apareciendo en la lista visible de Iván (UI muestra sesiones archivadas con tilde). Iván se enoja porque "sigo viendolas". **Para que desaparezca de verdad, `close` (irreversible).**

NO confundir: cuando una sesión **acaba de terminar su trabajo útil** y todavía la queremos consultar, usar `compress`. Cuando **ya no la necesitamos nunca más**, usar `close`.

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
- **Problema raiz (10 versiones, v1-v10):** cuando el agente REINICIADOR recibia la tarea de ejecutar el script de restart y reportar, en vez de ejecutar **mandaba un ACK inicial** y se quedaba en standby, o **se quedaba cargando skills**, o **esperaba confirmacion** que nadie le iba a dar. El bug es del modelo MiniMax-M3, NO del script. El script `start_manager_full.sh` es correcto y Mavis lo ejecuta sin problema.
- **Por que no se arreglo en 10 intentos:** el patron se repite aunque el prompt diga "ejecuta primero, no ACK". El agente, al ser una sesion hija, tiene el mismo modelo de fondo. Cambiar el prompt, el agent.md, o la complejidad del script no cambia el comportamiento. Solo cambia quien lo ejecuta.
- **Lo que SI funciona: Mavis (root) ejecuta el comando PowerShell `Start-Process`** del §4.2 desde la sesion root. El script matea los PIDs guardados antes de verificar puertos (fix IPv6 2026-06-16), asi que no hay stale bindings. Verificación de puertos se hace después solo si Iván pregunta.
- **Mavis valida la parte automatizable** (puertos, endpoints sin auth con Invoke-WebRequest) y **delega el smoke visual a REVISOR**. No mezcle roles.
- **Senales de "se trabo"** (en cualquier agente): manda un ACK inicial pero no completa la tarea en 1-2 min, o manda reportes parciales, o se queda sin output por 5+ min. Accion: `mavis session abort` + dejar que Mavis lo haga.
- **Si en algun momento se quiere re-intentar:** NO vale la pena. El modelo es el mismo. Mejor invertir el tiempo en otra cosa.

### 10.15 Gotcha: Browser MCP memory leak despues de 25-30 min de polling SSE continuo (2026-06-18)
- **Problema:** El Browser MCP (Chromium-1208 via `mavis browser tool` o Playwright directo) acumula memoria RAM durante sesiones largas de polling SSE. Despues de ~25-30 min de un smoke que hace `page.waitForSelector` o polling periodico contra `/api/v1/match-engine/round/{roundId}/live`, el browser se cuelga o devuelve timeouts.
- **Sintoma:** `Browser MCP tool call timeout` o respuestas vacias del browser. RAM del proceso `chrome.exe` supera 1.5 GB.
- **Confirmado:** No es bug del proyecto (backend SSE funciona correcto via `curl`). Es el MCP el que pierde el event loop o se queda sin memoria.
- **Workaround (V24D15-CLEANUP):** durante smokes de REVISOR que polleen SSE por mas de 20 min, **refrescar el browser MCP cada 20-25 min** cerrando y reabriendo el tab, o usar `--max-duration 25m` en el scope del smoke. Si el smoke es < 20 min (la mayoria), no es necesario.
- **Aplicar a:** REVISOR (smokes visuales). Manager/Senior no usan Browser MCP directamente.
- **TODO futuro (out of scope V24D15-CLEANUP):** investigar si es bug conocido de Playwright 1.49 o del MCP wrapper de mavis. De momento, workaround operativo.
- **Por que:** Ivan decidio 2026-06-15 14:43 que el no confirma ni ejecuta la rotacion de credenciales (Postgres `ALTER USER`, Redis `CONFIG SET requirepass`, JWT `openssl rand -base64 64`). La rotacion queda como responsabilidad de Mavis root, que la puede delegar a MANAGER (analisis) o SENIOR (ejecucion) segun el caso. Los valores reales NUNCA se transmiten por chat (canal inseguro). Script copy-paste ready en `docs/rotar-credenciales.md` (repo, trackeado) o en `workspace/rotar-credenciales.md` (workspace de agentes, no trackeado).
- **Cuando aplica:** cada vez que se commitea algo que referencia credenciales reales (`.env`, configs de infra, secrets en codigo), o como parte de un fix de seguridad (tipo V24D12-D).
- **Workflow sugerido:** MANAGER redacta el procedimiento de rotacion → Mavis lo revisa → SENIOR o Mavis lo ejecutan en infra → Ivan autoriza el push final → smoke REVISOR valida.

---

## 10b. V24D20-TESTHARNESS — harness de smoke comparativo (2026-06-20)

**Para quién:** REVISOR (corre smokes Bloque A/B) + Iván (verifica empíricamente si formación/random afecta resultado).

**Qué es:** 5 endpoints REST en `/api/v1/test-harness/career/*` (gated a `@Profile({"dev","local","test"})`). Permiten a un test crear carrera custom, sobreescribir fixtures, resetear lesiones, cambiar formación, y dumpear state — sin tocar el motor V24.

### Endpoints (todos con JWT auth)

```bash
# 1) Crear carrera custom (wipe + start con teamsPerDivision configurable)
curl -X POST http://localhost:8080/api/v1/test-harness/career/create-custom \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"leagueId":"la-liga-1","teamId":"real-madrid","difficulty":"EASY","gameSpeed":"NORMAL","teamsPerDivision":2}'

# 2) Reemplazar fixtures (mismo rival × N rondas = Bloque B)
curl -X POST http://localhost:8080/api/v1/test-harness/career/replace-fixtures \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '[{"homeTeamId":"<userTeamId>","awayTeamId":"<rivalId>","round":1},{"homeTeamId":"<userTeamId>","awayTeamId":"<rivalId>","round":2}]'

# 3) Resetear lesiones/suspensiones (squad sano pre-smoke)
curl -X POST http://localhost:8080/api/v1/test-harness/career/reset-injuries \
  -H "Authorization: Bearer $JWT"

# 4) Cambiar formación (CRÍTICO: persiste en BOTH SessionTeam.formation AND teamStarting11Formation map)
curl -X POST http://localhost:8080/api/v1/test-harness/career/set-formation \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"formation":"3-5-2"}'

# 5) Snapshot del estado actual (para diff pre/post smoke)
curl -X GET http://localhost:8080/api/v1/test-harness/career/snapshot \
  -H "Authorization: Bearer $JWT"
```

### Flujo Bloque A (mismo rival × 4 formaciones → ¿varía resultado?)

```bash
# 1. Custom con 2 teams (RM vs BAR), división chica para iterar rápido
curl -X POST .../create-custom -d '{...,"teamsPerDivision":2}'

# 2. Por cada formación (4-3-3, 4-4-2, 3-5-2, 5-3-2):
for FORM in 4-3-3 4-4-2 3-5-2 5-3-2; do
  curl -X POST .../set-formation -d "{\"formation\":\"$FORM\"}"
  curl -X POST .../replace-fixtures -d '[{"homeTeamId":"<RM>","awayTeamId":"<BAR>","round":1}]'
  # Disparar simulación via endpoints normales (RoundController, MatchEngineController)
  # Capturar resultado, comparar con las otras 3 formaciones
done
```

### Flujo Bloque B (misma formación × 3 corridas mismo rival → ¿random cambia?)

```bash
# Mismo set-formation
curl -X POST .../set-formation -d '{"formation":"4-3-3"}'

# Mismo rival pero 3 fixtures distintos (rounds 1, 2, 3) — cada uno = corrida fresh
curl -X POST .../replace-fixtures -d '[
  {"homeTeamId":"<RM>","awayTeamId":"<BAR>","round":1},
  {"homeTeamId":"<RM>","awayTeamId":"<BAR>","round":2},
  {"homeTeamId":"<RM>","awayTeamId":"<BAR>","round":3}
]'

# Disparar las 3 rondas, capturar resultados
# Si seed=42 está activo (application-test.yml), resultados son reproducibles
```

### Determinismo (seed)

`application-test.yml` setea `app.simulation.random-seed: 42` para reproducibilidad. Sin este flag, default `0` = random real (no determinístico — útil para Bloque B si queremos ver varianza).

Para forzar seed runtime: editar `application-local.yml` y reiniciar el back. NO hay endpoint runtime para cambiar seed (decisión out-of-scope).

### Out-of-scope (NO se hace)

- Modificar V24MatchEngine (sagrado).
- UI nueva (es API-only, REVISOR usa curl/HTTP).
- Persistir datos de test en prod (todo gateado por `@Profile`).
- Endpoint runtime para cambiar seed (sprint siguiente si REVISOR lo pide).

### Tests automatizados (cubren el harness, NO la simulación)

- `TestHarnessUseCaseImplTest` — 9 unit tests (Mockito). Incluye regression guard para BUG_FORMATION_PERSIST_IGNORED (sprint 1.7).
- `TestHarnessControllerE2ETest` — 8 E2E tests (Spring + WebTestClient). Cubre HTTP wiring, auth, profile gating, response shape.

### Lo que REVISOR corre (smoke real, no automatizado)

El flow completo (createCustom + setFormation + replaceFixtures + simulate 4 rondas + comparar resultados) NO está automatizado — es responsabilidad de REVISOR correrlo via HTTP contra el back con profile `dev` activo, y reportar resultados a Iván.

---

## 10c. V24D20-SANDBOX-V2-MVP — 4 bug fixes + endpoint replay (2026-06-20)

**Para quién:** REVISOR (corre smokes que necesitan formación propagada, A3-404 trace, xG no outlier) + Iván (verifica empíricamente los 4 bugs del sprint).

**Qué es:** sobre el harness 10b, este sprint fixeó 4 bugs que bloqueaban el smoke comparativo y agregó 1 endpoint replay para experimentación "rápida" (re-simular un partido YA JUGADO con nueva formación/seed sin crear fixture nuevo).

### Bugs fixeados

| Bug | Root cause | Fix | Regresion guard |
|---|---|---|---|
| **#1 FORMATION_NOT_PROPAGATED** | `TestHarnessUseCaseImpl` save() no invalidaba `CareerSessionService.careerCache` — el engine veía la formación VIEJA | Inyectar `careerSessionService.invalidateCache(career.getUserId())` después de cada `careerRepository.save()` en executeReplaceFixtures / executeResetInjuries / executeSetFormation | 4 unit tests nuevos en `TestHarnessUseCaseImplTest` (red→green) |
| **#2 TOTALROUNDS_NOT_PERSISTED** | `setTotalRounds` era la 2da llamada en `executeReplaceFixtures` — vulnerable a side-effects futuros | Reorder: `setTotalRounds` es la ÚLTIMA escritura (post-initializeStandings) | 1 unit test nuevo `replaceFixtures_totalRoundsEqualsFixtureCount` |
| **#3 V24_DETAIL_404_A3** | Sin traces; hipótesis = careerId/matchId mismatch en algún path | Trace logs en save/find/callsite (RoundController + V24DetailedMatchRedisAdapter) | 1 unit test nuevo `threeMatchesInSameCareer_allFindable` (3 fixtures, all findable) |
| **#4 XG_GOALS_DIVERGENCE_A1** | Threshold `xg/0.60` permite 100% conversion para xG≥0.60; outliers posibles con shots de high-xG | Instrumentation: `static AtomicInteger goalAdditions` + warn en divergence (counter vs GOAL events) + warn en outlier 5x | 1 unit test nuevo `noMatchHasGoalsGreaterThan5xXg` (10 matches, no outlier) |

**CLEANUP TODO (sprint futuro):** eliminar el counter `goalAdditions` y los warns de divergence/outlier cuando BUG #4 se confirme fixed. Tag: `V24D20-SANDBOX-V2-MVP-CLEANUP`.

### Endpoint nuevo

```bash
# POST /api/v1/test-harness/career/match/{matchId}/replay
# Body opcional: { "seed": 12345 }  // null = System.currentTimeMillis() (no reproducible)
# Response 200: el MatchFixture actualizado con el nuevo resultado
curl -X POST http://localhost:8080/api/v1/test-harness/career/match/match-001/replay \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"seed":99999}'

# Sin body → seed = System.currentTimeMillis() (cambia entre corridas)
curl -X POST http://localhost:8080/api/v1/test-harness/career/match/match-001/replay \
  -H "Authorization: Bearer $JWT"
```

El replay:
1. Resetea el fixture a PENDING (era COMPLETED).
2. Re-simula via `V24DetailedMatchEngine` con el seed (caller-provided o auto).
3. Persiste el nuevo resultado + actualiza standings.
4. Limpia el V24 detail viejo en Redis (best-effort).
5. Save + invalidar cache (mismo pattern que los otros endpoints).

**Limitaciones MVP (conocidas, documentadas):**
- Standings double-count: el resultado original ya estaba en standings; replay aplica el nuevo encima. Para state limpio, REVISOR debe correr `replace-fixtures` antes de replay.
- V24 detail delete es best-effort (errores se loggean, no se tiran).

### Flujo "replay con formación cambiada" (REVISOR what-if)

```bash
# 1. Create-custom + replace-fixtures con 1 partido
curl -X POST .../create-custom -d '{...,"teamsPerDivision":2}'
curl -X POST .../replace-fixtures -d '[{"matchId":"match-X","homeTeamId":"<H>","awayTeamId":"<A>","round":1}]'

# 2. Snapshot pre-replay (capturar resultado original)
curl -X GET .../snapshot > pre.json

# 3. Set formation + replay
curl -X POST .../set-formation -d '{"formation":"3-5-2"}'
curl -X POST .../match/match-X/replay -d '{"seed":99999}'

# 4. Snapshot post-replay (diff vs pre.json)
curl -X GET .../snapshot > post.json
diff pre.json post.json
```

### Tests automatizados

- `TestHarnessUseCaseImplTest` — 18 unit tests (Mockito + real V24 engine). 4 nuevos BUG #1 + 1 nuevo BUG #2 + 4 nuevos replayMatch.
- `V24DetailedMatchRedisAdapterTest` — 17 tests. 1 nuevo BUG #3 regression guard.
- `V24DetailedMatchEngineDeterminismTest` — 2 tests. 1 nuevo BUG #4 outlier check.
- `TestHarnessControllerE2ETest` — 8 E2E tests (HTTP wiring intacto).

### Profile gating

Todos los endpoints siguen gated a `@Profile({"dev","local","test"})`. En `prod` retornan 404 (sin guard explícito, default Spring para unmapped path).

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

## 12. agent.md de los agentes custom (2026-06-18)

**Contexto:** los agentes custom (manager-football, senior-football, revisor-football) son `isBuiltin: false, creationSource: "auto"`. Su system prompt real NO viene del package, hay que escribirlo en `C:\Users\ichu_\.mavis\agents\<agente>\agent.md`. Si el archivo está vacío o no existe, el agente arranca sin instrucciones de dominio y puede inventar scope o fallar tareas.

**Regla:** todos los agentes custom DEBEN tener `agent.md` con:
- **Rol** (1 línea)
- **NO hace** (lista de cosas que NO debe hacer)
- **Proyecto** (paths absolutos, runbook, tags)
- **Jerarquía** (a quién reporta, de quién recibe, a quién manda)
- **Workflow** de la tarea típica
- **Reglas duras** (lo que NO se rompe)
- **Gotchas** del proyecto
- **Comandos útiles**

**Archivos actuales (2026-06-18):**
- `C:\Users\ichu_\.mavis\agents\manager-football\agent.md` — rol: analista, redacta prompts, revisa diffs. NO toca código, NO push.
- `C:\Users\ichu_\.mavis\agents\senior-football\agent.md` — rol: ejecutor, escribe código, hace commits locales. NO push sin OK Iván + REVISOR GO.
- `C:\Users\ichu_\.mavis\agents\revisor-football\agent.md` — rol: smoke tester visual con Playwright MCP. NO toca código, NO reinicia stack.

**Por qué importa (lección F5.3 2026-06-18):** MANAGER arrancó sin `agent.md`, inventó scope (Trade-off-001 de un ticket de cola viejo), no respetó la decisión de Iván (Lectura A = revertir pre-F5.2 + BUG-015). Tuve que cerrar 2 sesiones y armar el system prompt yo mismo desde Mavis. Después de aplicar agent.md, las próximas sesiones de MANAGER ya tendrán el rol claro.

**Si se agrega un agente custom nuevo:** escribir `agent.md` ANTES de spawnear la primera sesión. Sin system prompt, falla.

**PERSONA.md** (opcional): personalidad/voz del agente. Si se quiere, escribir a `C:\Users\ichu_\.mavis\agents\<agente>\PERSONA.md`. Si no, el agente habla "neutro" (sin estilo).

**Validar que está aplicado:** el `mavis agent info <agente>` muestra el stub built-in siempre ("# Role / This file defines your system prompt..."). Eso es el placeholder, NO el contenido real. El contenido real se lee del `agent.md` al instanciar la sesión. Para verificar que el archivo está bien, leerlo directamente.

---

*Fin del runbook. Ivan pidio este archivo 2026-06-14 22:41 ART — actualizar en cada cambio.*
