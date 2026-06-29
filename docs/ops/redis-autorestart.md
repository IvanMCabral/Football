# Redis Auto-Restart — Setup & Scheduled Task

**Date:** 2026-06-29
**Author:** SENIOR-football (C42 cleanup)
**Status:** Script (`restart-redis.ps1`) is written and committed. Scheduled Task install is the next step — Mavis (root) handles that.

---

## 1. Why this exists

`MANAGER` (the football manager game) uses **Redis** as the primary
store for the live career session state. If Redis goes down:

- The backend cannot load `CareerSave` from Redis.
- All `career/...` and `match-engine/...` endpoints fail with
  `RedisConnectionFailureException`.
- The 127 E2E backend tests that require a live Redis start erroring
  (this is the baseline that was documented in §5 of the C42 task
  brief — "Los errores de Redis no conectado son baseline, no regresión").

Before C42 the only way to bring Redis back was to SSH into the box
and run `redis-server` by hand. The V25D77-C42 cleanup introduces a
**single PowerShell script** that detects the state and restarts Redis
idempotently, plus this doc to wire it into a Windows Scheduled Task
that watches the process every minute.

---

## 2. Files in this change

| File | Purpose |
|------|---------|
| `restart-redis.ps1` (repo root) | The restart script. Idempotent, single-purpose, no service install. |
| `docs/ops/redis-autorestart.md` | This file — install + test instructions for the Scheduled Task. |

The script is **not** a Windows service installer. It is a plain
PowerShell that any user (or a Scheduled Task running as the same
user that owns `C:\temp\redis\`) can execute.

---

## 3. Script usage

```powershell
# Default — restart only if DOWN. Exit 0 if already UP, 1 if restart failed.
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1

# Always restart, even if UP (use for "cycle" or scheduled downtime).
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1 -Force

# Status only — exit 0 if UP, 1 if DOWN. No state change.
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1 -Status
```

The script logs to stdout with a `[ISO-8601 timestamp]` prefix, so the
output is parseable if you pipe it to a file:

```powershell
.\restart-redis.ps1 *>> C:\temp\redis\restart.log
```

### What it does

1. **Probes** `127.0.0.1:6379` via `System.Net.Sockets.TcpClient` with
   a 1.5s timeout. If a `redis-cli.exe` is present at `C:\temp\redis\`,
   it also sends a `PING` and asserts the response contains `PONG`.
2. If Redis is **UP** and `-Force` is not set, prints "already UP" and
   exits 0.
3. If Redis is **DOWN** (or `-Force` was set), stops any existing
   `redis-server` process gracefully (up to 5s for the port to
   release) and starts a new instance with:
   - Binary: `C:\temp\redis\redis-server.exe`
   - Config: `C:\temp\redis\redis.windows.conf`
   - Working dir: `C:\temp\redis\`
   - stdout/stderr → `C:\temp\redis\redis.log` (append)
4. After start, polls for up to 10s waiting for the PING to succeed.
5. Returns exit 0 on success, exit 1 on any failure (binary missing,
   port stuck, timeout, etc.).

### Configuration knobs (edit the script if your install is elsewhere)

```powershell
$RedisRoot       = 'C:\temp\redis'
$RedisExe        = Join-Path $RedisRoot 'redis-server.exe'
$RedisConfig     = Join-Path $RedisRoot 'redis.windows.conf'
$RedisLog        = Join-Path $RedisRoot 'redis.log'
$RedisHost       = '127.0.0.1'
$RedisPort       = 6379
$ProbeTimeoutMs  = 1500
```

---

## 4. Manual test (smoke)

Before installing the Scheduled Task, verify the script does what it says:

```powershell
# 1. Check status — should be UP.
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1 -Status
# Expect: "UP   redis on 127.0.0.1:6379", exit 0.

# 2. Kill Redis (carefully — this drops any in-flight career session).
Stop-Process -Name redis-server -Force
Start-Sleep -Seconds 1

# 3. Run the restart script. Should start Redis.
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1
# Expect: "Starting Redis: ...", then "Redis UP (pid ...)", exit 0.

# 4. Probe again.
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1 -Status
# Expect: "UP   redis on 127.0.0.1:6379", exit 0.

# 5. Idempotency check — running it again is a no-op.
powershell -ExecutionPolicy Bypass -File D:\ProyectosOpenCode\MANAGER\restart-redis.ps1
# Expect: "Redis already UP on 127.0.0.1:6379. Nothing to do.", exit 0.
```

If any of these fail, check `C:\temp\redis\redis.log` and
`C:\temp\redis\redis.err` for the Redis-side error (e.g. port stuck
in TIME_WAIT, config syntax error, etc.).

---

## 5. Scheduled Task install (Mavis handles this)

The task spec — one minute poll, only runs the script (no force), uses
the same user that owns `C:\temp\redis\` so it can write the log:

```powershell
# Run as Administrator in an elevated PowerShell.
$Action  = New-ScheduledTaskAction `
    -Execute 'powershell.exe' `
    -Argument '-NoProfile -ExecutionPolicy Bypass -File "D:\ProyectosOpenCode\MANAGER\restart-redis.ps1"'

# Run every 1 minute, indefinitely. Task starts as soon as it's registered.
$Trigger = New-ScheduledTaskTrigger `
    -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Minutes 1) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

$Settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 2)

# Run as the same user that owns C:\temp\redis\ — replace with the
# real account. SYSTEM will not have write permission to the log.
$Principal = New-ScheduledTaskPrincipal `
    -UserId 'ichu_' `
    -LogonType Interactive `
    -RunLevel Highest

Register-ScheduledTask `
    -TaskName 'MANAGER-Redis-AutoRestart' `
    -Action $Action `
    -Trigger $Trigger `
    -Settings $Settings `
    -Principal $Principal `
    -Description 'V25D77-C42: restart C:\temp\redis\redis-server.exe if it goes down. Polled every 1 min.'
```

After registering, verify the task is alive:

```powershell
Get-ScheduledTask -TaskName 'MANAGER-Redis-AutoRestart' | Format-List *
Get-ScheduledTaskInfo   -TaskName 'MANAGER-Redis-AutoRestart' | Format-List *
```

And the first execution should be within 60 seconds. Check the
**Task Scheduler → Task Status → Last Run Result** column (should be
`0x0` for the no-op UP case).

### Disable / remove

```powershell
Unregister-ScheduledTask -TaskName 'MANAGER-Redis-AutoRestart' -Confirm:$false
```

---

## 6. Caveats & known limits

- **No password support.** The script assumes Redis has no
  `requirepass` (matches the current `C:\temp\redis\redis.windows.conf`).
  If you add a password later, the `PING` will fail and the script
  will loop-restart Redis every minute. Either (a) add `-a <pass>`
  to the `redis-cli` PING line and a `Test-Path` check for a
  password file, or (b) add `-no-auth-warning` / a `requirepass` env
  var. **TODO if/when we add Redis auth.**
- **Single-host only.** The script targets `127.0.0.1:6379`. If Redis
  ever moves to a different host/port, edit the constants at the top.
- **Graceful stop is best-effort.** We send SIGTERM-equivalent
  (`Stop-Process -Force`) and wait up to 5s for the port to release.
  If the port stays bound (Windows TIME_WAIT, antivirus, etc.) the
  script still tries to start a new instance which may fail. The
  scheduled task will retry on the next 1-min tick.
- **No career session preservation.** Stopping Redis drops whatever
  is in the in-memory DB. Since the backend flushes to Redis
  synchronously on every state change (see `CareerSessionService`),
  the only data at risk is whatever was mid-write when the process
  died. Acceptable for an outage-recovery tool; not acceptable for
  routine maintenance — for that, drain traffic first.
- **Not a service.** Windows Service Control Manager (SCM) is a more
  robust path (auto-restart on crash, no Scheduled Task overhead,
  visible in `services.msc`). Installing a real service requires
  `redis-server --service-install` and is out of scope for C42
  (it modifies the box globally, not just the project dir). The
  Scheduled Task approach is intentionally lightweight and
  reversible.

---

## 7. References

- `restart-redis.ps1` (this repo) — the script
- `docs/rotar-credenciales.md` (this repo) — Redis password rotation
  procedure (currently uses no password; if you add one, update
  this doc + the script's PING line)
- C42 cleanup brief: `C:\Users\ichu_\.mavis\agents\senior-football\workspace\C42-task-v2.md` §6
