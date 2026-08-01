param(
    [int]$StartupTimeoutSeconds = 180,
    [switch]$SkipBuild
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

function Invoke-NativeProcess($file, [string[]]$arguments, $name, $timeoutSeconds = 60) {
    $stdout = Join-Path $work "$name.out.log"
    $stderr = Join-Path $work "$name.err.log"
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $file
    $startInfo.Arguments = ($arguments | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    if (-not $process.WaitForExit($timeoutSeconds * 1000)) {
        try { $process.Kill($true) } catch { $process.Kill() }
        throw "Command timed out: $name"
    }
    $process.StandardOutput.ReadToEnd() | Set-Content -LiteralPath $stdout -Encoding UTF8
    $process.StandardError.ReadToEnd() | Set-Content -LiteralPath $stderr -Encoding UTF8
    if ($process.ExitCode -ne 0) {
        $errorTail = if (Test-Path $stderr) { (Get-Content $stderr -Tail 20) -join "`n" } else { '' }
        throw "Command failed: $name exit=$($process.ExitCode) $errorTail"
    }
}

function Invoke-Json($method, $uri, $body = $null, $token = $null) {
    $headers = @{}
    if ($token) {
        $headers['Authorization'] = "Bearer $token"
    }
    $arguments = @{
        Method = $method
        Uri = $uri
        TimeoutSec = 15
        Headers = $headers
    }
    if ($null -ne $body) {
        $arguments.Body = ($body | ConvertTo-Json -Depth 8)
        $arguments.ContentType = 'application/json'
    }
    return Invoke-RestMethod @arguments
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$initdb = Require-Command 'initdb'
$postgres = Require-Command 'postgres'
$psql = Require-Command 'psql'
$redisServer = Require-Command 'redis-server'

if (-not $SkipBuild) {
    & mvn -q -DskipTests package
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
$appPort = Get-FreeTcpPort
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
$appOut = Join-Path $work 'app.stdout.log'
$appErr = Join-Path $work 'app.stderr.log'
$resultFile = Join-Path $work 'result.json'
$redisProcess = $null
$appProcess = $null
$pgProcess = $null
$shutdownMs = $null

try {
    'initdb' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $init = Start-Process `
        -FilePath $initdb `
        -ArgumentList @('-D', $pgData, '-U', $pgAdminUser, '--auth=trust', '--encoding=UTF8', '--locale=C', '--no-instructions') `
        -PassThru `
        -WindowStyle Hidden
    if (-not $init.WaitForExit(60000)) {
        Stop-Process -Id $init.Id -Force -ErrorAction SilentlyContinue
        throw 'initdb timed out.'
    }
    $init.Refresh()
    if ($null -ne $init.ExitCode -and $init.ExitCode -ne 0) {
        throw "initdb failed with exit=$($init.ExitCode)."
    }
    'postgres-start' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
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
    'createdb' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $env:PGCONNECT_TIMEOUT = '10'
    & $psql -w -h 127.0.0.1 -p $pgPort -U $pgAdminUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE ROLE $dbUser LOGIN PASSWORD '$dbPassword';" *> (Join-Path $work 'create-role.log')
    if ($LASTEXITCODE -ne 0) {
        throw 'create role failed.'
    }
    & $psql -w -h 127.0.0.1 -p $pgPort -U $pgAdminUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $dbName OWNER $dbUser;" *> (Join-Path $work 'createdb.log')
    if ($LASTEXITCODE -ne 0) {
        throw 'createdb failed.'
    }

    'redis-start' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
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

    $env:SPRING_PROFILES_ACTIVE = 'prod'
    $env:PORT = "$appPort"
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
    $env:JAVA_TOOL_OPTIONS = '-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=20 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=UTC'

    'app-start' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $appProcess = Start-Process `
        -FilePath 'java' `
        -ArgumentList @('-jar', $jar.FullName) `
        -RedirectStandardOutput $appOut `
        -RedirectStandardError $appErr `
        -PassThru `
        -WindowStyle Hidden

    $liveness = $false
    $readiness = $false
    'wait-health' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($appProcess.HasExited) {
            throw "Application exited early with $($appProcess.ExitCode)"
        }
        try {
            $live = Invoke-RestMethod -Uri "http://127.0.0.1:$appPort/api/v1/health/liveness" -TimeoutSec 3
            $liveness = $live.status -eq 'UP'
        } catch {
            $liveness = $false
        }
        try {
            $readyResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$appPort/api/v1/health/readiness" -TimeoutSec 3 -UseBasicParsing
            $readiness = $readyResponse.StatusCode -eq 200
        } catch {
            $readiness = $false
        }
        if ($liveness -and $readiness) {
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $liveness) {
        throw 'Liveness did not become UP.'
    }
    if (-not $readiness) {
        throw 'Readiness did not become UP.'
    }

    'auth' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $authStamp = $stamp.Substring(0, 12)
    $email = "pb12-$authStamp@example.test"
    $password = 'ProdSmokePassword123!'
    $register = Invoke-Json Post "http://127.0.0.1:$appPort/api/v1/auth/register" @{
        email = $email
        username = "pb12-$authStamp"
        password = $password
    }
    if (-not $register.accessToken) {
        throw 'Register did not return access token.'
    }
    $login = Invoke-Json Post "http://127.0.0.1:$appPort/api/v1/auth/login" @{
        email = $email
        password = $password
    }
    if (-not $login.accessToken) {
        throw 'Login did not return access token.'
    }
    $me = Invoke-Json Get "http://127.0.0.1:$appPort/api/v1/auth/me" $null $login.accessToken

    'career' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $idsQuery = "select l.id as league_id, t.id as team_id from leagues l join teams t on t.league_id = l.id order by l.name, t.name limit 1;"
    $idsOut = Join-Path $work 'ids.out.log'
    $idsErr = Join-Path $work 'ids.err.log'
    & $psql -w -h 127.0.0.1 -p $pgPort -U $dbUser -d $dbName -At -F ',' -c $idsQuery > $idsOut 2> $idsErr
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to read league/team ids.'
    }
    $ids = (Get-Content $idsOut -ErrorAction SilentlyContinue | Select-Object -First 1)
    $careerCreated = $false
    if ($ids -and $ids.Contains(',')) {
        $parts = $ids.Split(',')
        $leagueId = $parts[0]
        $teamId = $parts[1]
        Invoke-Json Post "http://127.0.0.1:$appPort/api/v1/games" @{
            name = 'PB1.2.1 smoke career'
            leagueId = $leagueId
            teamId = $teamId
            difficulty = 'NORMAL'
            gameSpeed = 'NORMAL'
            teamsPerDivision = 20
        } $login.accessToken | Out-Null
        $careerCreated = $true
    }

    'flyway-check' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $flywayOut = Join-Path $work 'flyway.out.log'
    $flywayErr = Join-Path $work 'flyway.err.log'
    & $psql -w -h 127.0.0.1 -p $pgPort -U $dbUser -d $dbName -At -c 'select count(*) from flyway_schema_history where success = true;' > $flywayOut 2> $flywayErr
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to read Flyway schema history.'
    }
    $flywayCount = (Get-Content $flywayOut | Select-Object -First 1)
    if ([int]$flywayCount -lt 1) {
        throw 'Flyway schema history has no successful migration.'
    }

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

    'shutdown' | Set-Content -LiteralPath (Join-Path $work 'step.txt') -Encoding UTF8
    $startedAt = Get-Date
    Stop-Process -Id $appProcess.Id -ErrorAction SilentlyContinue
    $appProcess.WaitForExit(30000) | Out-Null
    $shutdownMs = [int]((Get-Date) - $startedAt).TotalMilliseconds
    if (-not $appProcess.HasExited) {
        throw 'Application did not exit during shutdown drill.'
    }

    $result = [ordered]@{
        status = 'PASS'
        jar = $jar.Name
        jarBytes = $jar.Length
        port = $appPort
        serverAddress = '0.0.0.0'
        liveness = 200
        readiness = 200
        registered = $true
        login = $true
        userId = $me.id
        careerCreated = $careerCreated
        flywaySuccessfulMigrations = [int]$flywayCount
        localLogArtifacts = 0
        shutdownMs = $shutdownMs
        postgresTemp = $true
        redisTemp = $true
        workDir = $work
    }
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $resultFile -Encoding UTF8
    $result | ConvertTo-Json -Compress
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($redisProcess -and -not $redisProcess.HasExited) {
        Stop-Process -Id $redisProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($pgProcess -and -not $pgProcess.HasExited) {
        Stop-Process -Id $pgProcess.Id -Force -ErrorAction SilentlyContinue
    }
}
