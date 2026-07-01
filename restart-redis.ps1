# V25D77-C42 Redis auto-restart helper
#
# Detects if Redis is reachable on 127.0.0.1:6379 and starts the local
# `redis-server.exe` if it is not. Idempotent: a no-op when Redis is already UP.
# Safe to run from a Windows Scheduled Task, a startup script, or by hand.
#
# Layout discovered during C42 cleanup (2026-06-29):
#   - Binary : C:\temp\redis\redis-server.exe
#   - Config : C:\temp\redis\redis.windows.conf   (binds 127.0.0.1, port 6379, no auth)
#   - Working: C:\temp\redis\
#   - Log    : C:\temp\redis\redis.log            (Redis appends here)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\restart-redis.ps1            # only restarts if DOWN
#   powershell -ExecutionPolicy Bypass -File .\restart-redis.ps1 -Force     # always restarts
#   powershell -ExecutionPolicy Bypass -File .\restart-redis.ps1 -Status    # print status + exit 0/1
#
# Exit codes:
#   0  Redis was already UP (or was successfully started).
#   1  Redis is DOWN and the script could not start it (binary missing, port stuck, etc.).
#
# The script is intentionally single-purpose and dependency-free. No external
# modules, no service install/uninstall, no registry tweaks. If you need a
# "managed" service see docs/ops/redis-autorestart.md (Scheduled Task path).

[CmdletBinding()]
param(
    [switch]$Force,    # always restart, even if Redis is currently UP
    [switch]$Status    # print status + exit 0 (UP) or 1 (DOWN). Do not start.
)

$ErrorActionPreference = 'Stop'

# ----- Configuration (edit if your install lives elsewhere) ------------------

$RedisRoot       = 'C:\temp\redis'
$RedisExe        = Join-Path $RedisRoot 'redis-server.exe'
# V25D77-C55.2.5 fix: use redis-min.conf (TIENE requirepass MgrRedis2026!Rotate#Secure)
# backend application-local.properties spring.data.redis.password=MgrRedis2026!Rotate#Secure
$RedisConfig     = Join-Path $RedisRoot 'redis-min.conf'
$RedisHost       = '127.0.0.1'
$RedisPort       = 6379
$ProbeTimeoutMs  = 1500

# ----- Helpers ---------------------------------------------------------------

function Test-RedisUp {
    param([string]$RedisHostAddr, [int]$Port, [int]$TimeoutMs)
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($RedisHostAddr, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if (-not $ok) { $client.Close(); return $false }
        $client.EndConnect($iar)
        # Try a PING via the binary if available, otherwise the TCP open is enough
        $redisCli = Join-Path $RedisRoot 'redis-cli.exe'
        if (Test-Path $redisCli) {
            $stdout = & $redisCli -h $RedisHostAddr -p $Port PING 2>&1
            $client.Close()
            return ($LASTEXITCODE -eq 0 -and ($stdout -join '') -match 'PONG')
        }
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Start-Redis {
    if (-not (Test-Path $RedisExe)) {
        Write-Warning "Redis binary not found at $RedisExe. Aborting."
        return $false
    }
    if (-not (Test-Path $RedisConfig)) {
        Write-Warning "Redis config not found at $RedisConfig. Aborting."
        return $false
    }
    Write-Host "[$(Get-Date -Format 'o')] Starting Redis: $RedisExe $RedisConfig"
    try {
        # PowerShell's Start-Process disallows RedirectStandardOutput and
        # RedirectStandardError pointing at the same file. We let Redis
        # inherit the parent's stdio instead — its config controls where
        # the daemon actually writes (see C:\temp\redis\redis.windows.conf
        # for the `logfile` directive, if any). If you need to tee to a
        # single file, route via a wrapper .ps1 that uses `*>&1`.
        $proc = Start-Process -FilePath $RedisExe `
                              -ArgumentList "`"$RedisConfig`"" `
                              -WorkingDirectory $RedisRoot `
                              -WindowStyle Hidden `
                              -PassThru
        # Give Redis a moment to bind the port
        $deadline = (Get-Date).AddSeconds(10)
        while ((Get-Date) -lt $deadline) {
            if (Test-RedisUp -RedisHostAddr $RedisHost -Port $RedisPort -TimeoutMs $ProbeTimeoutMs) {
                Write-Host "[$(Get-Date -Format 'o')] Redis UP (pid $($proc.Id))."
                return $true
            }
            Start-Sleep -Milliseconds 500
        }
        Write-Warning "Redis process started (pid $($proc.Id)) but did not respond to PING within 10s. Check $RedisLog."
        return $false
    } catch {
        Write-Warning "Failed to start Redis: $_"
        return $false
    }
}

function Stop-RedisGracefully {
    $pids = Get-Process -Name 'redis-server' -ErrorAction SilentlyContinue
    if ($null -eq $pids -or $pids.Count -eq 0) {
        Write-Host "[$(Get-Date -Format 'o')] No redis-server process to stop."
        return $true
    }
    foreach ($p in $pids) {
        Write-Host "[$(Get-Date -Format 'o')] Stopping redis-server (pid $($p.Id))..."
        try { Stop-Process -Id $p.Id -Force -ErrorAction Stop } catch {
            Write-Warning "Could not stop pid $($p.Id): $_"
            return $false
        }
    }
    # Give the port a moment to release
    $deadline = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-RedisUp -RedisHostAddr $RedisHost -Port $RedisPort -TimeoutMs $ProbeTimeoutMs)) {
            return $true
        }
        Start-Sleep -Milliseconds 250
    }
    Write-Warning "Port $RedisPort still bound after 5s. Continuing anyway."
    return $true
}

# ----- Main ------------------------------------------------------------------

$up = Test-RedisUp -RedisHostAddr $RedisHost -Port $RedisPort -TimeoutMs $ProbeTimeoutMs

if ($Status) {
    if ($up) {
        Write-Host "UP   redis on ${RedisHost}:${RedisPort}"
        exit 0
    } else {
        Write-Host "DOWN redis on ${RedisHost}:${RedisPort}"
        exit 1
    }
}

if ($up -and -not $Force) {
    Write-Host "[$(Get-Date -Format 'o')] Redis already UP on ${RedisHost}:${RedisPort}. Nothing to do."
    exit 0
}

if ($Force -and $up) {
    Write-Host "[$(Get-Date -Format 'o')] -Force: restarting Redis even though it is UP."
    if (-not (Stop-RedisGracefully)) { exit 1 }
}

if (-not (Start-Redis)) { exit 1 }
exit 0
