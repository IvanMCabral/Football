param(
    [int]$StartupTimeoutSeconds = 180,
    [int]$ShutdownTimeoutSeconds = 45,
    [switch]$SkipBuild,
    [switch]$KeepArtifacts,
    [switch]$KeepArtifactsOnFailure
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
    $shutdownMarkers = @(
        'Commencing graceful shutdown',
        'Graceful shutdown complete',
        'Shutdown completed'
    )
    $markerCount = 0
    foreach ($marker in $shutdownMarkers) {
        if ($combinedLogs.Contains($marker)) {
            $markerCount++
        }
    }
    if ($markerCount -lt 1) {
        throw "No Spring graceful shutdown marker found for run $($run.index)."
    }
    return [ordered]@{
        gracefulSignalSent = [bool]$helperResult.gracefulSignalSent
        gracefulShutdownObserved = [bool]$helperResult.gracefulShutdownObserved
        forceKillUsed = [bool]$helperResult.forceKillUsed
        shutdownDurationMs = [int]$helperResult.shutdownDurationMs
        javaExitCode = [int]$helperResult.javaExitCode
        shutdownMarkersObserved = $markerCount
    }
}

function Assert-NoResidual($pids, $ports) {
    $residualProcesses = 0
    foreach ($processId in $pids) {
        if ($processId -and (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
            $residualProcesses++
        }
    }
    $residualPorts = 0
    foreach ($port in $ports) {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $iar = $client.BeginConnect('127.0.0.1', $port, $null, $null)
            if ($iar.AsyncWaitHandle.WaitOne(250, $false)) {
                $client.EndConnect($iar)
                $residualPorts++
            }
        } catch {
        } finally {
            $client.Close()
        }
    }
    return [ordered]@{
        residualProcesses = $residualProcesses
        residualPorts = $residualPorts
    }
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$initdb = Require-Command 'initdb'
$postgres = Require-Command 'postgres'
$psql = Require-Command 'psql'
$redisServer = Require-Command 'redis-server'

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
$safeSummary = Join-Path $env:TEMP "manager-prod-jar-smoke-$stamp-summary.json"
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

    $residualBeforeDependencyStop = Assert-NoResidual @($run1.javaPid, $run1.helperPid, $run2.javaPid, $run2.helperPid) @($run1.port, $run2.port)
    if ($residualBeforeDependencyStop.residualProcesses -ne 0 -or $residualBeforeDependencyStop.residualPorts -ne 0) {
        throw "Residual Java/helper processes or app ports remain: $($residualBeforeDependencyStop | ConvertTo-Json -Compress)"
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
        gracefulSignalSent = $shutdown1.gracefulSignalSent -and $shutdown2.gracefulSignalSent
        gracefulShutdownObserved = $shutdown1.gracefulShutdownObserved -and $shutdown2.gracefulShutdownObserved
        forceKillUsed = $shutdown1.forceKillUsed -or $shutdown2.forceKillUsed
        shutdownDurationMs = $shutdown1.shutdownDurationMs
        shutdownDurationMsRun2 = $shutdown2.shutdownDurationMs
        javaExitCode = $shutdown1.javaExitCode
        javaExitCodeRun2 = $shutdown2.javaExitCode
        shutdownMarkersObserved = $shutdown1.shutdownMarkersObserved + $shutdown2.shutdownMarkersObserved
        residualProcesses = $residualBeforeDependencyStop.residualProcesses
        residualPorts = $residualBeforeDependencyStop.residualPorts
        localLogArtifacts = 0
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $safeSummary -Encoding UTF8
    $result | ConvertTo-Json -Compress
} catch {
    $status = 'FAIL'
    $failure = [ordered]@{
        status = 'FAIL'
        error = $_.Exception.Message
        workDir = $work
    }
    $failure | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $safeSummary -Encoding UTF8
    throw
} finally {
    foreach ($run in $runs) {
        if ($run.helperProcess -and -not $run.helperProcess.HasExited) {
            try { $run.helperProcess.Kill($true) } catch { $run.helperProcess.Kill() }
        }
    }
    if ($redisProcess -and -not $redisProcess.HasExited) {
        try { $redisProcess.Kill($true) } catch { Stop-Process -Id $redisProcess.Id -Force -ErrorAction SilentlyContinue }
    }
    if ($pgProcess -and -not $pgProcess.HasExited) {
        try { $pgProcess.Kill($true) } catch { Stop-Process -Id $pgProcess.Id -Force -ErrorAction SilentlyContinue }
    }
    Start-Sleep -Milliseconds 500
    $preserveArtifacts = $KeepArtifacts -or ($KeepArtifactsOnFailure -and $status -ne 'PASS')
    if (-not $preserveArtifacts -and (Test-Path $work)) {
        Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
    }
}
