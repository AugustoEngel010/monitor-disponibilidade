param([switch]$NoBuild, [string]$ProjectDirectory)

$ErrorActionPreference = 'Stop'
$project = 'mon02-smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$root = Split-Path -Parent $PSScriptRoot
if ($ProjectDirectory) { $root = (Resolve-Path -LiteralPath $ProjectDirectory).Path }
$evidence = Join-Path $root '.validation'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$log = Join-Path $evidence ($project + '.log')
$savedPort = $env:APP_PORT
$env:APP_PORT = '0'
Push-Location $root

function Invoke-Compose {
    param([string[]]$Arguments)
    # Docker writes progress to stderr even on success; its exit code is the gate.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & docker compose -p $project @Arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    $lines = @($output | ForEach-Object { $_.ToString() })
    Add-Content -Path $log -Value ($lines -join "`n") -Encoding UTF8
    if ($code -ne 0) { throw "docker compose $($Arguments -join ' ') failed: exit $code`n$($lines -join "`n")" }
    return $lines
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Criterion)
    if ($Actual -ne $Expected) { throw "$Criterion expected [$Expected], got [$Actual]" }
    Add-Content -Path $log -Value "PASS $Criterion = $Actual" -Encoding UTF8
}

function Query {
    param([string]$Sql)
    # Read database name/user from the running container, including custom .env values.
    $user = (Invoke-Compose @('exec', '-T', 'db', 'printenv', 'POSTGRES_USER') -join '').Trim()
    $database = (Invoke-Compose @('exec', '-T', 'db', 'printenv', 'POSTGRES_DB') -join '').Trim()
    return (Invoke-Compose @('exec', '-T', 'db', 'psql', '-U', $user, '-d', $database,
            '-v', 'ON_ERROR_STOP=1', '-Atc', $Sql) -join "`n").Trim()
}

function Wait-Ready {
    $address = (Invoke-Compose @('port', 'app', '8080') -join '').Trim()
    $url = "http://$address/actuator/health/readiness"
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
            $body = $response.Content
            if ($body -is [byte[]]) { $body = [Text.Encoding]::UTF8.GetString($body) }
            $status = ($body | ConvertFrom-Json).status
            Assert-Equal $response.StatusCode 200 'MON-02-AC01 HTTP readiness'
            Assert-Equal $status 'UP' 'MON-02-AC01 readiness status'
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw 'MON-02-AC01 readiness did not become HTTP 200/UP in 120 seconds'
}

try {
    Invoke-Compose @('config', '--quiet') | Out-Host
    $start = @('up', '-d')
    if (-not $NoBuild) { $start += '--build' }
    Invoke-Compose $start | Out-Host
    Wait-Ready
    Assert-Equal (Query 'SELECT count(*) FROM flyway_schema_history WHERE success') '1' 'MON-02-AC02 migration count'
    Assert-Equal (Query 'SELECT version FROM flyway_schema_history') '1' 'MON-02-AC02 migration version'
    $history = Query 'SELECT row_to_json(h) FROM flyway_schema_history h ORDER BY installed_rank'
    Query 'CREATE TABLE monitor.mon02_sentinel (id integer PRIMARY KEY, value integer NOT NULL); INSERT INTO monitor.mon02_sentinel VALUES (1, 4242)' | Out-Host
    Invoke-Compose @('exec', '-T', 'app', 'sh', '-c',
            'test $(id -u) = 10001 && ! command -v mvn && ! command -v javac && test ! -e /var/run/docker.sock && test -f /app/monitor.jar') | Out-Host
    Add-Content -Path $log -Value 'PASS MON-02-AC07 UID=10001, JAR present, no Maven/javac/socket' -Encoding UTF8
    Invoke-Compose @('restart', 'db', 'app') | Out-Host
    Wait-Ready
    Assert-Equal (Query 'SELECT value FROM monitor.mon02_sentinel WHERE id = 1') '4242' 'MON-02-AC03 restart sentinel'
    Assert-Equal (Query 'SELECT row_to_json(h) FROM flyway_schema_history h ORDER BY installed_rank') $history 'MON-02-AC02 restart history'
    Invoke-Compose @('down') | Out-Host
    Invoke-Compose @('up', '-d') | Out-Host
    Wait-Ready
    Assert-Equal (Query 'SELECT value FROM monitor.mon02_sentinel WHERE id = 1') '4242' 'MON-02-AC03 down/up sentinel'
    Assert-Equal (Query 'SELECT row_to_json(h) FROM flyway_schema_history h ORDER BY installed_rank') $history 'MON-02-AC02 down/up history'
    Add-Content -Path $log -Value 'PASS Compose lifecycle' -Encoding UTF8
    Write-Host "PASS Compose lifecycle; evidence: $log"
} finally {
    # This unique project belongs exclusively to this run. Never delete development volumes.
    try { Invoke-Compose @('logs', '--no-color') | Out-Null } catch { Write-Warning $_ }
    try { Invoke-Compose @('down', '--volumes', '--remove-orphans') | Out-Host } catch { Write-Warning $_ }
    $env:APP_PORT = $savedPort
    Pop-Location
}
