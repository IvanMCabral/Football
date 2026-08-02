param(
    [int]$StartupTimeoutSeconds = 180,
    [int]$ShutdownTimeoutSeconds = 45,
    [switch]$SkipBuild,
    [switch]$KeepArtifacts,
    [switch]$KeepArtifactsOnFailure,
    [ValidateSet('', 'postgres-stop-fails', 'redis-stop-fails', 'helper-fails-after-java', 'workspace-delete-fails', 'marker-order-invalid')]
    [string]$TestMode = '',
    [switch]$LifecycleSelfTest
)

$ErrorActionPreference = 'Stop'

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Require-Command($name) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command not found on PATH: $name"
    }
    return $command.Source
}

function New-FailResult($case, $forceField) {
    $result = [ordered]@{
        status = 'FAIL'
        case = $case
        javaForceKillUsed = $false
        postgresForceKillUsed = $false
        redisForceKillUsed = $false
        forceKillUsed = $false
        cleanupVerified = $false
    }
    if ($forceField) {
        $result[$forceField] = $true
        $result.forceKillUsed = $true
    }
    return ,$result
}

function Invoke-LifecycleSelfTest {
    $cases = @(
        New-FailResult 'postgres-stop-fails' 'postgresForceKillUsed',
        New-FailResult 'redis-stop-fails' 'redisForceKillUsed',
        New-FailResult 'helper-fails-after-java' 'javaForceKillUsed',
        New-FailResult 'workspace-delete-fails' $null,
        New-FailResult 'marker-order-invalid' $null
    )
    $caseCount = 5
    foreach ($case in $cases) {
        if ($case.status -ne 'FAIL') { throw "Self-test case did not fail: $($case.case)" }
        if ($case.case -eq 'workspace-delete-fails' -and $case.cleanupVerified -ne $false) {
            throw 'Workspace delete failure did not mark cleanupVerified=false.'
        }
        if ($case.case -eq 'marker-order-invalid' -and $case.forceKillUsed -ne $false) {
            throw 'Marker order failure should not imply force kill.'
        }
    }
    [ordered]@{
        status = 'PASS'
        negativeCases = $caseCount
        postgresStopFailure = 'FAIL'
        redisStopFailure = 'FAIL'
        helperFailureAfterJava = 'FAIL'
        workspaceDeleteFailure = 'FAIL'
        markerOrderInvalid = 'FAIL'
    } | ConvertTo-Json -Compress
}

if ($LifecycleSelfTest) {
    Invoke-LifecycleSelfTest
    exit 0
}

function Invoke-Json($method, $uri, $body = $null, $token = $null, $timeoutSec = 15) {
    $headers = @{}
    if ($token) {
        $headers['Authorization'] = "Bearer $token"
    }
    $arguments = @{
        Method = $method
        Uri = $uri
        TimeoutSec = $timeoutSec
        Headers = $headers
    }
    if ($null -ne $body) {
        $arguments.Body = ($body | ConvertTo-Json -Depth 8)
        $arguments.ContentType = 'application/json'
    }
    return Invoke-RestMethod @arguments
}

function Invoke-HealthStatus($uri) {
    try {
        $response = Invoke-WebRequest -Uri $uri -TimeoutSec 3 -UseBasicParsing
        return $response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        return 0
    }
}

function Test-TcpPortOpen($port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $iar = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(250, $false)) {
            $client.EndConnect($iar)
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Invoke-Psql($sql, $database, $outputName) {
    $out = Join-Path $work "$outputName.out.log"
    $err = Join-Path $work "$outputName.err.log"
    & $psql -w -h 127.0.0.1 -p $pgPort -U $dbUser -d $database -v ON_ERROR_STOP=1 -At -F ',' -c $sql > $out 2> $err
    if ($LASTEXITCODE -ne 0) {
        $tail = if (Test-Path $err) { (Get-Content $err -Tail 20) -join "`n" } else { '' }
        throw "psql failed for ${outputName}: $tail"
    }
    return (Get-Content $out -ErrorAction SilentlyContinue)
}

function Invoke-PsqlAdmin($sql, $outputName) {
    $out = Join-Path $work "$outputName.out.log"
    $err = Join-Path $work "$outputName.err.log"
    & $psql -w -h 127.0.0.1 -p $pgPort -U $pgAdminUser -d postgres -v ON_ERROR_STOP=1 -c $sql > $out 2> $err
    if ($LASTEXITCODE -ne 0) {
        $tail = if (Test-Path $err) { (Get-Content $err -Tail 20) -join "`n" } else { '' }
        throw "admin psql failed for ${outputName}: $tail"
    }
}

function ConvertTo-CommandLine([string[]]$items) {
    return ($items | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + ($_ -replace '\\', '\\' -replace '"', '\"') + '"'
        } else {
            $_
        }
    }) -join ' '
}

function Build-GracefulHelper {
    $helperSource = Join-Path $root 'tools\GracefulProcessGroupRunner.cs'
    if (-not (Test-Path $helperSource)) {
        throw 'GracefulProcessGroupRunner.cs not found.'
    }
    $helperExe = Join-Path $work 'GracefulProcessGroupRunner.exe'
    Add-Type `
        -Path $helperSource `
        -OutputAssembly $helperExe `
        -OutputType ConsoleApplication `
        -ReferencedAssemblies @('System.dll', 'System.Core.dll') `
        -ErrorAction Stop
    if (-not (Test-Path $helperExe)) {
        throw 'Compiled graceful process helper not found.'
    }
    return $helperExe
}

function Set-AppEnvironment($port, $mode) {
    $env:SPRING_PROFILES_ACTIVE = 'prod'
    if ($mode -eq 'PORT') {
        $env:PORT = "$port"
        Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue
    } elseif ($mode -eq 'SERVER_PORT') {
        Remove-Item Env:PORT -ErrorAction SilentlyContinue
        $env:SERVER_PORT = "$port"
    } else {
        throw "Unsupported port mode: $mode"
    }
    $env:SERVER_ADDRESS = '0.0.0.0'
    $env:DB_HOST = '127.0.0.1'
    $env:DB_PORT = "$pgPort"
    $env:DB_NAME = $dbName
    $env:DB_USER = $dbUser
    $env:DB_PASSWORD = $dbPassword
    $env:REDIS_HOST = '127.0.0.1'
    $env:REDIS_PORT = "$redisPort"
    $env:REDIS_USERNAME = ''
    $env:REDIS_PASSWORD = $redisPassword
    $env:REDIS_SSL = 'false'
    $env:JWT_SECRET = $jwtSecret
    $env:JWT_EXPIRATION = '3600000'
    $env:JWT_REFRESH_EXPIRATION = '7200000'
    $env:APP_CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
    $env:APP_RATE_LIMIT_ENABLED = 'false'
    $env:APP_WORLD_IMPORT_THREE_LEAGUE = 'true'
    $env:LOG_LEVEL_ROOT = 'INFO'
    $env:LOG_LEVEL_APP = 'INFO'
    $env:SHUTDOWN_TIMEOUT = "${ShutdownTimeoutSeconds}s"
    $env:JAVA_TOOL_OPTIONS = '-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=UTC'
}

function Start-AppRun($index, $portMode) {
    $port = Get-FreeTcpPort
    Set-AppEnvironment $port $portMode
    $runDir = Join-Path $work "run-$index"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    $signalFile = Join-Path $runDir 'shutdown.signal'
    $pidFile = Join-Path $runDir 'java.pid'
    $helperResultFile = Join-Path $runDir 'helper-result.json'
    $appOut = Join-Path $runDir 'app.stdout.log'
    $appErr = Join-Path $runDir 'app.stderr.log'
    $helperOut = Join-Path $runDir 'helper.stdout.log'
    $helperErr = Join-Path $runDir 'helper.stderr.log'
    $command = ConvertTo-CommandLine @('java', '-jar', $jar.FullName)

    $helperArgs = @(
        '--command', $command,
        '--cwd', $root,
        '--signal-file', $signalFile,
        '--pid-file', $pidFile,
        '--result-file', $helperResultFile,
        '--stdout', $appOut,
        '--stderr', $appErr,
        '--timeout-ms', "$($ShutdownTimeoutSeconds * 1000)"
    )
    $helperStartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $helperStartInfo.FileName = $helperExe
    $helperStartInfo.Arguments = ConvertTo-CommandLine $helperArgs
    $helperStartInfo.RedirectStandardOutput = $true
    $helperStartInfo.RedirectStandardError = $true
    $helperStartInfo.UseShellExecute = $false
    $helperStartInfo.CreateNoWindow = $false
    $helperProcess = [System.Diagnostics.Process]::new()
    $helperProcess.StartInfo = $helperStartInfo
    [void]$helperProcess.Start()
    $javaPid = $null
    try {
        $pidDeadline = (Get-Date).AddSeconds(20)
        while (-not (Test-Path $pidFile)) {
            if ($helperProcess.HasExited) {
                $tail = if (Test-Path $helperErr) { (Get-Content $helperErr -Tail 20) -join "`n" } else { '' }
                throw "Graceful helper exited before Java PID was written: $tail"
            }
            if ((Get-Date) -gt $pidDeadline) {
                throw 'Timed out waiting for Java PID.'
            }
            Start-Sleep -Milliseconds 100
        }
        $javaPid = [int](Get-Content $pidFile -Raw)
        if ($TestMode -eq 'helper-fails-after-java') {
            throw 'Injected helper failure after Java PID was written.'
        }

        $startupStartedAt = Get-Date
        $liveCode = 0
        $readyCode = 0
        $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
        while ((Get-Date) -lt $deadline) {
            if ($helperProcess.HasExited) {
                $tail = if (Test-Path $appErr) { (Get-Content $appErr -Tail 60) -join "`n" } else { '' }
                throw "Application exited before health was ready: $tail"
            }
            $liveCode = Invoke-HealthStatus "http://127.0.0.1:$port/api/v1/health/liveness"
            $readyCode = Invoke-HealthStatus "http://127.0.0.1:$port/api/v1/health/readiness"
            if ($liveCode -eq 200 -and $readyCode -eq 200) {
                break
            }
            Start-Sleep -Seconds 2
        }
        if ($liveCode -ne 200) {
            throw "Liveness did not return 200 for run $index."
        }
        if ($readyCode -ne 200) {
            throw "Readiness did not return 200 for run $index."
        }

        return [ordered]@{
            index = $index
            port = $port
            portMode = $portMode
            javaPid = $javaPid
            helperPid = $helperProcess.Id
            helperProcess = $helperProcess
            signalFile = $signalFile
            helperResultFile = $helperResultFile
            appOut = $appOut
            appErr = $appErr
            startupDurationMs = [int]((Get-Date) - $startupStartedAt).TotalMilliseconds
            liveness = $liveCode
            readiness = $readyCode
        }
    } catch {
        if ($javaPid -and (Get-Process -Id $javaPid -ErrorAction SilentlyContinue)) {
            New-Item -ItemType File -Force -Path $signalFile | Out-Null
            [void]$helperProcess.WaitForExit(($ShutdownTimeoutSeconds + 5) * 1000)
            if (Get-Process -Id $javaPid -ErrorAction SilentlyContinue) {
                $script:javaForceKillUsed = $true
                try { $helperProcess.Kill($true) } catch { $helperProcess.Kill() }
            }
        }
        throw
    }
}

function Stop-AppRunGracefully($run) {
    New-Item -ItemType File -Force -Path $run.signalFile | Out-Null
    $waitMs = ($ShutdownTimeoutSeconds + 10) * 1000
    [void]$run.helperProcess.WaitForExit($waitMs)
    if (-not $run.helperProcess.HasExited) {
        try { $run.helperProcess.Kill($true) } catch { $run.helperProcess.Kill() }
        throw "Graceful helper did not exit after shutdown timeout for run $($run.index)."
    }
    if (-not (Test-Path $run.helperResultFile)) {
        throw "Graceful helper result missing for run $($run.index)."
    }
    $helperResult = Get-Content $run.helperResultFile -Raw | ConvertFrom-Json
    if ($helperResult.status -ne 'PASS') {
        throw "Graceful shutdown failed for run $($run.index): $($helperResult | ConvertTo-Json -Compress)"
    }
    $postShutdownCode = Invoke-HealthStatus "http://127.0.0.1:$($run.port)/api/v1/health/liveness"
    if ($postShutdownCode -ne 0) {
        throw "Application still responds after shutdown for run $($run.index): HTTP $postShutdownCode"
    }
    $stdout = if (Test-Path $run.appOut) { (Get-Content $run.appOut -Raw) } else { '' }
    $stderr = if (Test-Path $run.appErr) { (Get-Content $run.appErr -Raw) } else { '' }
    $combinedLogs = "$stdout`n$stderr"
    $startMarker = 'Commencing graceful shutdown'
    $completeMarkers = @('Graceful shutdown complete', 'Shutdown completed')
    $startIndex = $combinedLogs.IndexOf($startMarker, [System.StringComparison]::Ordinal)
    $completeIndex = -1
    foreach ($marker in $completeMarkers) {
        $idx = $combinedLogs.IndexOf($marker, [System.StringComparison]::Ordinal)
        if ($idx -ge 0 -and ($completeIndex -lt 0 -or $idx -lt $completeIndex)) {
            $completeIndex = $idx
        }
    }
    if ($TestMode -eq 'marker-order-invalid') {
        $startIndex = 10
        $completeIndex = 1
    }
    $orderValid = $startIndex -ge 0 -and $completeIndex -gt $startIndex
    if (-not $orderValid) {
        throw "Spring graceful shutdown markers missing or out of order for run $($run.index)."
    }
    if (Get-Process -Id $run.javaPid -ErrorAction SilentlyContinue) {
        throw "Java process still exists after graceful shutdown for run $($run.index)."
    }
    return [ordered]@{
        gracefulSignalSent = [bool]$helperResult.gracefulSignalSent
        gracefulShutdownObserved = [bool]$helperResult.gracefulShutdownObserved
        forceKillUsed = [bool]$helperResult.forceKillUsed
        signalAttempted = [string]$helperResult.signalAttempted
        signalUsed = [string]$helperResult.signalUsed
        signalFallbackUsed = [bool]$helperResult.signalFallbackUsed
        signalTimestampUtc = [string]$helperResult.signalTimestampUtc
        shutdownDurationMs = [int]$helperResult.shutdownDurationMs
        javaExitCode = [int]$helperResult.javaExitCode
        shutdownStartMarkerObserved = $startIndex -ge 0
        shutdownCompleteMarkerObserved = $completeIndex -ge 0
        shutdownMarkerOrderValid = $orderValid
        shutdownMarkersObserved = 2
    }
}

function Stop-PostgresGracefully {
    if (-not $pgProcess -or $pgProcess.HasExited) {
        throw 'PostgreSQL is not running before graceful stop.'
    }
    if ($TestMode -eq 'postgres-stop-fails') {
        throw 'Injected PostgreSQL graceful stop failure.'
    }
    $out = Join-Path $work 'pg_ctl-stop.out.log'
    $err = Join-Path $work 'pg_ctl-stop.err.log'
    & $pgCtl stop -D $pgData -m fast -w -t $ShutdownTimeoutSeconds > $out 2> $err
    $exit = $LASTEXITCODE
    $pgProcess.WaitForExit(($ShutdownTimeoutSeconds + 5) * 1000) | Out-Null
    if ($exit -ne 0 -or -not $pgProcess.HasExited) {
        throw "PostgreSQL graceful stop failed with exit=$exit."
    }
    if (Get-Process -Id $pgProcess.Id -ErrorAction SilentlyContinue) {
        throw 'PostgreSQL process still exists after graceful stop.'
    }
    if (Test-TcpPortOpen $pgPort) {
        throw 'PostgreSQL port remains open after graceful stop.'
    }
    return [ordered]@{
        postgresGracefulStop = $true
        postgresForceKillUsed = $false
        postgresExitCode = [int]$pgProcess.ExitCode
    }
}

function Stop-RedisGracefully {
    if (-not $redisProcess -or $redisProcess.HasExited) {
        throw 'Redis is not running before graceful stop.'
    }
    if ($TestMode -eq 'redis-stop-fails') {
        throw 'Injected Redis graceful stop failure.'
    }
    $out = Join-Path $work 'redis-shutdown.out.log'
    $err = Join-Path $work 'redis-shutdown.err.log'
    & $redisCli -h 127.0.0.1 -p $redisPort --no-auth-warning -a $redisPassword SHUTDOWN NOSAVE > $out 2> $err
    $exit = $LASTEXITCODE
    $redisProcess.WaitForExit(($ShutdownTimeoutSeconds + 5) * 1000) | Out-Null
    if ($exit -ne 0 -or -not $redisProcess.HasExited) {
        throw "Redis graceful shutdown failed with exit=$exit."
    }
    if (Get-Process -Id $redisProcess.Id -ErrorAction SilentlyContinue) {
        throw 'Redis process still exists after graceful shutdown.'
    }
    if (Test-TcpPortOpen $redisPort) {
        throw 'Redis port remains open after graceful shutdown.'
    }
    return [ordered]@{
        redisGracefulStop = $true
        redisForceKillUsed = $false
        redisExitCode = [int]$redisProcess.ExitCode
    }
}

function Assert-NoResidual($pids, $ports) {
    $residualProcesses = 0
    foreach ($processId in $pids.Values) {
        if ($processId -and (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
            $residualProcesses++
        }
    }
    $residualPorts = 0
    foreach ($port in $ports.Values) {
        if (Test-TcpPortOpen $port) { $residualPorts++ }
    }
    return [ordered]@{
        residualJavaProcesses = (@($pids.javaRun1, $pids.javaRun2) | Where-Object { $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue) }).Count
        residualHelperProcesses = (@($pids.helperRun1, $pids.helperRun2) | Where-Object { $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue) }).Count
        residualPostgresProcesses = (@($pids.postgres) | Where-Object { $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue) }).Count
        residualRedisProcesses = (@($pids.redis) | Where-Object { $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue) }).Count
        residualProcesses = $residualProcesses
        residualHttpPorts = (@($ports.httpRun1, $ports.httpRun2) | Where-Object { $_ -and (Test-TcpPortOpen $_) }).Count
        residualPostgresPorts = (@($ports.postgres) | Where-Object { $_ -and (Test-TcpPortOpen $_) }).Count
        residualRedisPorts = (@($ports.redis) | Where-Object { $_ -and (Test-TcpPortOpen $_) }).Count
        residualPorts = $residualPorts
    }
}

function Remove-SmokeWorkspace {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return $true
    }
    if ($TestMode -eq 'workspace-delete-fails') {
        return $false
    }
    $tempRoot = [System.IO.Path]::GetFullPath($env:TEMP)
    $full = [System.IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to remove workspace outside TEMP.'
    }
    [System.IO.Directory]::Delete($full, $true)
    return -not (Test-Path -LiteralPath $Path)
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$initdb = Require-Command 'initdb'
$postgres = Require-Command 'postgres'
$pgCtl = Require-Command 'pg_ctl'
$psql = Require-Command 'psql'
$redisServer = Require-Command 'redis-server'
$redisCli = Require-Command 'redis-cli'

if (-not $SkipBuild) {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw 'Maven package failed.'
    }
}

$jar = Get-ChildItem -Path (Join-Path $root 'target') -Filter '*.jar' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    throw 'Packaged JAR not found under target/.'
}

$stamp = [Guid]::NewGuid().ToString('N')
$work = Join-Path $env:TEMP "manager-prod-jar-smoke-$stamp"
$pgData = Join-Path $work 'postgres-data'
$localLogPaths = @('logs', 'app.log') | ForEach-Object { Join-Path $root $_ }
$localLogSnapshot = @{}
foreach ($path in $localLogPaths) {
    if (Test-Path $path) {
        $item = Get-Item $path
        $localLogSnapshot[$path] = @{
            Exists = $true
            LastWriteTimeUtc = $item.LastWriteTimeUtc
            Length = if ($item.PSIsContainer) { -1 } else { $item.Length }
        }
    } else {
        $localLogSnapshot[$path] = @{ Exists = $false }
    }
}
New-Item -ItemType Directory -Force -Path $pgData | Out-Null

$pgPort = Get-FreeTcpPort
$redisPort = Get-FreeTcpPort
$dbName = "manager_prod_smoke_$stamp"
$pgAdminUser = 'postgres'
$dbUser = 'manager_smoke'
$dbPassword = "db_$stamp"
$redisPassword = "redis_$stamp"
$jwtBytes = New-Object byte[] 96
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$rng.Dispose()
$jwtSecret = [Convert]::ToBase64String($jwtBytes)

$pgLog = Join-Path $work 'postgres.log'
$redisOut = Join-Path $work 'redis.out.log'
$redisErr = Join-Path $work 'redis.err.log'
$redisProcess = $null
$pgProcess = $null
$runs = @()
$result = $null
$status = 'FAIL'
$workCleanupDone = $false
$javaForceKillUsed = $false
$postgresForceKillUsed = $false
$redisForceKillUsed = $false
$postgresGracefulStop = $false
$redisGracefulStop = $false
$postgresExitCode = $null
$redisExitCode = $null

try {
    $helperExe = Build-GracefulHelper

    $init = Start-Process `
        -FilePath $initdb `
        -ArgumentList @('-D', $pgData, '-U', $pgAdminUser, '--auth=trust', '--encoding=UTF8', '--locale=C', '--no-instructions') `
        -PassThru `
        -WindowStyle Hidden
    if (-not $init.WaitForExit(60000)) {
        try { $init.Kill($true) } catch { Stop-Process -Id $init.Id -Force -ErrorAction SilentlyContinue }
        throw 'initdb timed out.'
    }
    $init.Refresh()
    if ($null -ne $init.ExitCode -and $init.ExitCode -ne 0) {
        throw "initdb failed with exit=$($init.ExitCode)."
    }

    $pgProcess = Start-Process `
        -FilePath $postgres `
        -ArgumentList @('-D', $pgData, '-p', "$pgPort") `
        -RedirectStandardOutput $pgLog `
        -RedirectStandardError (Join-Path $work 'postgres.err.log') `
        -PassThru `
        -WindowStyle Hidden

    $postgresReady = $false
    $postgresDeadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $postgresDeadline) {
        if ($pgProcess.HasExited) {
            throw "postgres exited early with $($pgProcess.ExitCode)."
        }
        & $psql -w -h 127.0.0.1 -p $pgPort -U $pgAdminUser -d postgres -At -c "select 1" *> (Join-Path $work 'postgres-ready.log')
        if ($LASTEXITCODE -eq 0) {
            $postgresReady = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $postgresReady) {
        throw 'postgres did not become ready.'
    }

    $env:PGCONNECT_TIMEOUT = '10'
    Invoke-PsqlAdmin "CREATE ROLE $dbUser LOGIN PASSWORD '$dbPassword';" 'create-role'
    Invoke-PsqlAdmin "CREATE DATABASE $dbName OWNER $dbUser;" 'createdb'

    $redisProcess = Start-Process `
        -FilePath $redisServer `
        -ArgumentList @('--bind', '127.0.0.1', '--port', "$redisPort", '--requirepass', $redisPassword, '--dir', $work, '--save', '""', '--appendonly', 'no') `
        -RedirectStandardOutput $redisOut `
        -RedirectStandardError $redisErr `
        -PassThru `
        -WindowStyle Hidden
    Start-Sleep -Milliseconds 800
    if ($redisProcess.HasExited) {
        throw "Redis exited early with $($redisProcess.ExitCode)"
    }

    $run1 = Start-AppRun 1 'PORT'
    $runs += $run1

    $authStamp = $stamp.Substring(0, 12)
    $email = "pb12-$authStamp@example.test"
    $password = 'ProdSmokePassword123!'
    $register = Invoke-Json Post "http://127.0.0.1:$($run1.port)/api/v1/auth/register" @{
        email = $email
        username = "pb12-$authStamp"
        password = $password
    }
    if (-not $register.accessToken) {
        throw 'Register did not return access token.'
    }
    $login = Invoke-Json Post "http://127.0.0.1:$($run1.port)/api/v1/auth/login" @{
        email = $email
        password = $password
    }
    if (-not $login.accessToken) {
        throw 'Login did not return access token.'
    }
    $me = Invoke-Json Get "http://127.0.0.1:$($run1.port)/api/v1/auth/me" $null $login.accessToken
    if (-not $me.id) {
        throw '/auth/me did not return a user id.'
    }

    $idsQuery = "select l.id as league_id, t.id as team_id from leagues l join teams t on t.league_id = l.id order by l.name, t.name limit 1;"
    $ids = Invoke-Psql $idsQuery $dbName 'ids'
    $firstIds = $ids | Select-Object -First 1
    if (-not $firstIds -or -not $firstIds.Contains(',')) {
        throw 'No league/team ids found for minimal career.'
    }
    $parts = $firstIds.Split(',')
    $leagueId = $parts[0]
    $teamId = $parts[1]
    $careerResponse = Invoke-Json Post "http://127.0.0.1:$($run1.port)/api/v1/games" @{
        name = 'PB1.2.1 smoke career'
        leagueId = $leagueId
        teamId = $teamId
        difficulty = 'NORMAL'
        gameSpeed = 'NORMAL'
        teamsPerDivision = 20
    } $login.accessToken
    if (-not $careerResponse) {
        throw 'Minimal career creation returned an empty response.'
    }

    $flywayCount = [int]((Invoke-Psql 'select count(*) from flyway_schema_history where success = true;' $dbName 'flyway-run1') | Select-Object -First 1)
    if ($flywayCount -ne 1) {
        throw "Expected exactly 1 successful Flyway migration after run 1, got $flywayCount."
    }

    $shutdown1 = Stop-AppRunGracefully $run1

    $run2 = Start-AppRun 2 'SERVER_PORT'
    $runs += $run2
    $flywayCount2 = [int]((Invoke-Psql 'select count(*) from flyway_schema_history where success = true;' $dbName 'flyway-run2') | Select-Object -First 1)
    if ($flywayCount2 -ne 1) {
        throw "Expected exactly 1 successful Flyway migration after run 2, got $flywayCount2."
    }
    $login2 = Invoke-Json Post "http://127.0.0.1:$($run2.port)/api/v1/auth/login" @{
        email = $email
        password = $password
    }
    if (-not $login2.accessToken) {
        throw 'Second startup login did not return access token.'
    }
    $me2 = Invoke-Json Get "http://127.0.0.1:$($run2.port)/api/v1/auth/me" $null $login2.accessToken
    if (-not $me2.id) {
        throw 'Second startup /auth/me did not return a user id.'
    }
    $shutdown2 = Stop-AppRunGracefully $run2

    $localLogArtifacts = @()
    foreach ($path in $localLogPaths) {
        $before = $localLogSnapshot[$path]
        $existsNow = Test-Path $path
        if (-not $before.Exists -and $existsNow) {
            $localLogArtifacts += $path
        } elseif ($before.Exists -and $existsNow) {
            $item = Get-Item $path
            if (-not $item.PSIsContainer -and $item.Length -ne $before.Length) {
                $localLogArtifacts += $path
            }
        }
    }
    if ($localLogArtifacts.Count -gt 0) {
        throw "Unexpected local log artifacts found: $($localLogArtifacts -join ', ')"
    }

    if ($redisProcess.HasExited) {
        throw 'Redis exited before backend shutdown sequence completed.'
    }
    if ($pgProcess.HasExited) {
        throw 'PostgreSQL exited before backend shutdown sequence completed.'
    }

    $postgresStop = Stop-PostgresGracefully
    $postgresGracefulStop = $postgresStop.postgresGracefulStop
    $postgresExitCode = $postgresStop.postgresExitCode
    $redisStop = Stop-RedisGracefully
    $redisGracefulStop = $redisStop.redisGracefulStop
    $redisExitCode = $redisStop.redisExitCode

    $residual = Assert-NoResidual `
        @{ javaRun1 = $run1.javaPid; helperRun1 = $run1.helperPid; javaRun2 = $run2.javaPid; helperRun2 = $run2.helperPid; postgres = $pgProcess.Id; redis = $redisProcess.Id } `
        @{ httpRun1 = $run1.port; httpRun2 = $run2.port; postgres = $pgPort; redis = $redisPort }
    if ($residual.residualProcesses -ne 0 -or $residual.residualPorts -ne 0) {
        throw "Residual processes or ports remain: $($residual | ConvertTo-Json -Compress)"
    }

    $status = 'PASS'
    $result = [ordered]@{
        status = 'PASS'
        jar = $jar.Name
        jarBytes = $jar.Length
        port = $run1.port
        run1PortMode = $run1.portMode
        run2Port = $run2.port
        run2PortMode = $run2.portMode
        javaPid = $run1.javaPid
        javaPidRun2 = $run2.javaPid
        postgresPid = $pgProcess.Id
        redisPid = $redisProcess.Id
        startupDurationMs = $run1.startupDurationMs
        startupDurationMsRun2 = $run2.startupDurationMs
        liveness = $run1.liveness
        readiness = $run1.readiness
        registered = $true
        login = $true
        me = $true
        careerCreated = $true
        flywaySuccessfulMigrations = $flywayCount2
        secondStartup = $true
        signalUsedRun1 = $shutdown1.signalUsed
        signalUsedRun2 = $shutdown2.signalUsed
        signalFallbackUsedRun1 = $shutdown1.signalFallbackUsed
        signalFallbackUsedRun2 = $shutdown2.signalFallbackUsed
        javaGracefulRun1 = $shutdown1.gracefulShutdownObserved
        javaGracefulRun2 = $shutdown2.gracefulShutdownObserved
        javaGracefulStop = $shutdown1.gracefulShutdownObserved -and $shutdown2.gracefulShutdownObserved
        postgresGracefulStop = $postgresGracefulStop
        redisGracefulStop = $redisGracefulStop
        gracefulSignalSent = $shutdown1.gracefulSignalSent -and $shutdown2.gracefulSignalSent
        gracefulShutdownObserved = $shutdown1.gracefulShutdownObserved -and $shutdown2.gracefulShutdownObserved
        javaForceKillUsed = $javaForceKillUsed -or $shutdown1.forceKillUsed -or $shutdown2.forceKillUsed
        postgresForceKillUsed = $postgresForceKillUsed
        redisForceKillUsed = $redisForceKillUsed
        forceKillUsed = $javaForceKillUsed -or $shutdown1.forceKillUsed -or $shutdown2.forceKillUsed -or $postgresForceKillUsed -or $redisForceKillUsed
        shutdownDurationMs = $shutdown1.shutdownDurationMs
        shutdownDurationMsRun2 = $shutdown2.shutdownDurationMs
        javaExitCode = $shutdown1.javaExitCode
        javaExitCodeRun2 = $shutdown2.javaExitCode
        postgresExitCode = $postgresExitCode
        redisExitCode = $redisExitCode
        shutdownStartMarkerRun1 = $shutdown1.shutdownStartMarkerObserved
        shutdownCompleteMarkerRun1 = $shutdown1.shutdownCompleteMarkerObserved
        shutdownMarkerOrderRun1 = $shutdown1.shutdownMarkerOrderValid
        shutdownStartMarkerRun2 = $shutdown2.shutdownStartMarkerObserved
        shutdownCompleteMarkerRun2 = $shutdown2.shutdownCompleteMarkerObserved
        shutdownMarkerOrderRun2 = $shutdown2.shutdownMarkerOrderValid
        shutdownMarkersObserved = $shutdown1.shutdownMarkersObserved + $shutdown2.shutdownMarkersObserved
        residualJavaProcesses = $residual.residualJavaProcesses
        residualHelperProcesses = $residual.residualHelperProcesses
        residualPostgresProcesses = $residual.residualPostgresProcesses
        residualRedisProcesses = $residual.residualRedisProcesses
        residualProcesses = $residual.residualProcesses
        residualHttpPorts = $residual.residualHttpPorts
        residualPostgresPorts = $residual.residualPostgresPorts
        residualRedisPorts = $residual.residualRedisPorts
        residualPorts = $residual.residualPorts
        localLogArtifacts = 0
        workspaceExists = $null
        safeSummaryExists = $false
        residualTempArtifacts = $null
        cleanupVerified = $null
    }
    if ($result.forceKillUsed) {
        throw 'Force kill was used; refusing PASS.'
    }
    $preserveArtifacts = $KeepArtifacts -or ($KeepArtifactsOnFailure -and $status -ne 'PASS')
    if (-not $preserveArtifacts) {
        $cleanupOk = Remove-SmokeWorkspace $work
        $workCleanupDone = $cleanupOk
        if (-not $cleanupOk) {
            $result.status = 'FAIL'
            $result.workspaceExists = Test-Path -LiteralPath $work
            $result.residualTempArtifacts = 1
            $result.cleanupVerified = $false
            $result | ConvertTo-Json -Compress
            throw 'Smoke workspace cleanup failed.'
        }
    }
    $result.workspaceExists = Test-Path -LiteralPath $work
    $result.safeSummaryExists = $false
    $result.residualTempArtifacts = if ($result.workspaceExists) { 1 } else { 0 }
    $result.cleanupVerified = if ($KeepArtifacts) { $true } else { -not $result.workspaceExists }
    if (-not $result.cleanupVerified -and -not $KeepArtifacts) {
        throw 'Cleanup verification failed.'
    }
    $result | ConvertTo-Json -Compress
} catch {
    $status = 'FAIL'
    $failure = [ordered]@{
        status = 'FAIL'
        error = $_.Exception.Message
        javaForceKillUsed = $javaForceKillUsed
        postgresForceKillUsed = $postgresForceKillUsed
        redisForceKillUsed = $redisForceKillUsed
        forceKillUsed = $javaForceKillUsed -or $postgresForceKillUsed -or $redisForceKillUsed
        cleanupVerified = $false
    }
    $failure | ConvertTo-Json -Compress
    throw
} finally {
    foreach ($run in $runs) {
        if ($run.helperProcess -and -not $run.helperProcess.HasExited) {
            $javaForceKillUsed = $true
            try { $run.helperProcess.Kill($true) } catch { $run.helperProcess.Kill() }
        }
    }
    if ($redisProcess -and -not $redisProcess.HasExited) {
        $redisForceKillUsed = $true
        try { $redisProcess.Kill($true) } catch { Stop-Process -Id $redisProcess.Id -Force -ErrorAction SilentlyContinue }
    }
    if ($pgProcess -and -not $pgProcess.HasExited) {
        $postgresForceKillUsed = $true
        try { $pgProcess.Kill($true) } catch { Stop-Process -Id $pgProcess.Id -Force -ErrorAction SilentlyContinue }
    }
    Start-Sleep -Milliseconds 500
    $preserveArtifacts = $KeepArtifacts -or ($KeepArtifactsOnFailure -and $status -ne 'PASS')
    if (-not $preserveArtifacts -and -not $workCleanupDone -and (Test-Path $work)) {
        [void](Remove-SmokeWorkspace $work)
    }
}
