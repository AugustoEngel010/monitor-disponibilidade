param(
    [string]$ProjectName = 'monitor-portfolio',
    [switch]$NoBuild,
    [string]$ProjectDirectory = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference = 'Stop'
if ($ProjectName -notmatch '^[a-z0-9][a-z0-9-]+$') { throw 'Invalid Compose project name' }
$fixtures = @{
    ADMIN_USERNAME='portfolio-operator'; ADMIN_PASSWORD='portfolio-local-only';
    APP_PORT='18080'; SMTP_UI_PORT='18025'; SMTP_LOCAL_PORT='11025'; DEMO_PORT='18081';
    DB_NAME='monitor'; DB_USER='monitor'; DB_PASSWORD='portfolio-db-local';
    JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=75.0';
    MONITOR_DEMO_ORIGIN='http://demo:8080'; MONITOR_SCHEDULER_ENABLED='true';
    MONITOR_CHECK_CONCURRENCY='4'; MONITOR_SCHEDULER_POLL_MS='250';
    MONITOR_NOTIFICATIONS_ENABLED='true'; MONITOR_NOTIFICATIONS_POLL_MS='250';
    SMTP_HOST='smtp'; SMTP_PORT='1025'; SMTP_FROM='monitor@example.org';
    SMTP_USERNAME=''; SMTP_PASSWORD=''; SMTP_AUTH='false'; SMTP_STARTTLS='false'; SMTP_SSL='false';
    SMTP_CONNECT_TIMEOUT_MS='5000'; SMTP_READ_TIMEOUT_MS='3000'; SMTP_WRITE_TIMEOUT_MS='5000';
    MONITOR_RETENTION_ENABLED='true'; MONITOR_RETENTION_DAYS='30';
    MONITOR_RETENTION_BATCH_SIZE='1000'; MONITOR_RETENTION_POLL_MS='3600000'
}
$names = @($fixtures.Keys)
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
function Compose {
    param([string[]]$Arguments)
    & docker compose --project-directory $ProjectDirectory -p $ProjectName @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Compose failed: $($Arguments -join ' ')" }
}
function Phase([string]$Name) {
    Compose -Arguments @('run','--rm','--no-deps','demo-tools',$Name)
}
# Create the bind mount as the host user before Docker can create it as root on Linux.
$reportPath = Join-Path $ProjectDirectory '.validation/demo'
New-Item -ItemType Directory -Force -Path $reportPath | Out-Null
try {
    # Disposable fixtures for an isolated local project; never an external SMTP server.
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $fixtures[$name], 'Process') }
    Compose -Arguments @('config','--quiet')
    $start = @('up','-d')
    if (!$NoBuild) { $start += '--build' }
    Compose -Arguments $start
    # Seed must finish before any runner performs another seed (URLs are not globally unique).
    Compose -Arguments @('wait','demo-seed')
    if (!$NoBuild) { Compose -Arguments @('build','demo-tools') }
    Compose -Arguments @('run','--rm','--no-deps','demo-seed','node','--test','server.test.mjs','seed.test.mjs')
    Phase 'normal'
    Compose -Arguments @('stop','smtp')
    Phase 'pending'
    Compose -Arguments @('restart','app')
    Phase 'restart'
    Compose -Arguments @('start','smtp')
    Phase 'retry'
    Phase 'slow'
    $env:MONITOR_SCHEDULER_ENABLED = 'false'
    Compose -Arguments @('up','-d','--no-deps','--force-recreate','app')
    Phase 'stale'
    $env:MONITOR_SCHEDULER_ENABLED = 'true'
    Compose -Arguments @('up','-d','--no-deps','--force-recreate','app')
    Phase 'finish'
    Write-Output 'PASS MON-12: painel http://localhost:18080; Mailpit http://localhost:18025; evidencias .validation/demo'
} finally {
    # Restore services after failures too. Keep the database and reports for inspection.
    try {
        $env:MONITOR_SCHEDULER_ENABLED = 'true'
        Compose -Arguments @('start','smtp')
        Compose -Arguments @('up','-d','--no-deps','app')
        & docker compose --project-directory $ProjectDirectory -p $ProjectName logs --no-color app db smtp demo |
            Set-Content -Encoding utf8 (Join-Path $reportPath 'compose.log')
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
}
