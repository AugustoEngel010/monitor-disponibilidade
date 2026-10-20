param([switch]$NoBuild, [string]$ProjectDirectory)

$ErrorActionPreference = 'Stop'
$project = 'mon02-smoke-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$root = Split-Path -Parent $PSScriptRoot
if ($ProjectDirectory) { $root = (Resolve-Path -LiteralPath $ProjectDirectory).Path }
$evidence = Join-Path $root '.validation'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$log = Join-Path $evidence ($project + '.log')
$savedPort = $env:APP_PORT
$savedAdminUsername = $env:ADMIN_USERNAME
$savedAdminPassword = $env:ADMIN_PASSWORD
$env:ADMIN_USERNAME = [guid]::NewGuid().ToString('N')
$env:ADMIN_PASSWORD = [guid]::NewGuid().ToString('N')
$initialAdminPassword = $env:ADMIN_PASSWORD
$rotatedAdminPassword = [guid]::NewGuid().ToString('N')
$env:APP_PORT = '0'
$notificationEnvironment = @{
    SMTP_UI_PORT='0'; SMTP_LOCAL_PORT='0'; SMTP_HOST='smtp'; SMTP_PORT='1025';
    SMTP_FROM='monitor@example.org'; SMTP_USERNAME=''; SMTP_PASSWORD='';
    SMTP_AUTH='false'; SMTP_STARTTLS='false'; SMTP_SSL='false';
    MONITOR_DEMO_ORIGIN='http://smtp:8025'; MONITOR_SCHEDULER_ENABLED='true';
    MONITOR_NOTIFICATIONS_ENABLED='true'; MONITOR_NOTIFICATIONS_POLL_MS='250'
}
$savedNotificationEnvironment = @{}
foreach ($key in $notificationEnvironment.Keys) {
    $savedNotificationEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    [Environment]::SetEnvironmentVariable($key, $notificationEnvironment[$key], 'Process')
}
Push-Location $root

function Invoke-Compose {
    param([string[]]$Arguments, [int]$ExpectedExit = 0)
    # Docker writes progress to stderr even on success; its exit code is the gate.
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & docker compose -p $project @Arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    $lines = @($output | ForEach-Object { $_.ToString() })
    Add-Content -Path $log -Value ($lines -join "`n") -Encoding UTF8
    if ($code -ne $ExpectedExit) { throw "docker compose action failed: expected exit $ExpectedExit, got $code" }
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
    $queryLines = Invoke-Compose @('exec', '-T', 'db', 'psql', '-U', $user, '-d', $database,
            '-v', 'ON_ERROR_STOP=1', '-Atc', $Sql)
    return ($queryLines -join "`n").Trim()
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

function Assert-AdministrativeAccess {
    param([string]$Password, [int]$ExpectedStatus, $Session)
    $address = (Invoke-Compose @('port', 'app', '8080') -join '').Trim()
    $headers = @{}
    if ($Password) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($env:ADMIN_USERNAME + ':' + $Password)
        $headers.Authorization = 'Basic ' + [Convert]::ToBase64String($bytes)
    }
    $request = @{ UseBasicParsing = $true; Uri = "http://$address/actuator/info"; Headers = $headers; TimeoutSec = 5 }
    if ($Session) { $request.WebSession = $Session }
    try { $status = (Invoke-WebRequest @request).StatusCode }
    catch {
        if (-not $_.Exception.Response) { throw 'Administrative HTTP request failed before receiving a response' }
        $status = [int]$_.Exception.Response.StatusCode
    }
    Assert-Equal $status $ExpectedStatus 'MON-03 administrative HTTP access'
}

function Assert-NoCredentialLeaks {
    $contents = Get-Content -Raw -LiteralPath $log
    foreach ($secret in @($env:ADMIN_USERNAME, $initialAdminPassword, $rotatedAdminPassword)) {
        if ($contents.Contains($secret)) { throw 'MON-03-AC05 credential found in application logs' }
    }
    foreach ($password in @($initialAdminPassword, $rotatedAdminPassword)) {
        $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:ADMIN_USERNAME + ':' + $password))
        if ($contents.Contains($encoded)) { throw 'MON-03-AC05 Basic credential found in application logs' }
    }
    Add-Content -Path $log -Value 'PASS MON-03-AC05 no administrative credential in logs' -Encoding UTF8
}

function Wait-NotificationQuery {
    param([string]$Sql, [string]$Expected, [string]$Criterion)
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $value = Query $Sql
        if ($value -eq $Expected) { Assert-Equal $value $Expected $Criterion; return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$Criterion did not reach its expected SQL state"
}

function Assert-Notifications {
    Invoke-Compose @('up', '-d', 'smtp') | Out-Host
    $smtpAddress = (Invoke-Compose @('port', 'smtp', '8025') -join '').Trim()
    $deadline = (Get-Date).AddSeconds(20)
    do {
        try { $null = Invoke-RestMethod -Uri "http://$smtpAddress/api/v1/messages" -TimeoutSec 2; break }
        catch { if ((Get-Date) -ge $deadline) { throw }; Start-Sleep -Milliseconds 200 }
    } while ($true)
    $address = (Invoke-Compose @('port', 'app', '8080') -join '').Trim()
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:ADMIN_USERNAME + ':' + $env:ADMIN_PASSWORD))
    $headers = @{ Authorization = "Basic $basic" }
    $csrf = Invoke-RestMethod -Uri "http://$address/api/admin/csrf" -Headers $headers -SessionVariable notificationSession
    $headers['X-CSRF-TOKEN'] = $csrf.token
    $body = @{
        name='MON08 Compose smoke'; url='http://smtp:8025/api/v1/messages'; intervalSeconds=10;
        timeoutSeconds=1; expectedStatus=200; failureThreshold=1; recoveryThreshold=1; alertRecipient='ops@example.org'
    } | ConvertTo-Json
    $monitor = Invoke-RestMethod -Uri "http://$address/api/admin/monitors" -Method Post -Headers $headers `
        -WebSession $notificationSession -ContentType 'application/json' -Body $body
    $id = $monitor.id
    Wait-NotificationQuery "SELECT confirmed_state FROM monitor.monitors WHERE id='$id'" 'UP' 'MON-08 HTTP initial UP'
    Invoke-Compose @('stop', 'smtp') | Out-Host
    Query "UPDATE monitor.monitors SET next_check_at=now() WHERE id='$id'" | Out-Null
    Wait-NotificationQuery "SELECT confirmed_state FROM monitor.monitors WHERE id='$id'" 'DOWN' 'MON-08-AC01 down'
    Wait-NotificationQuery "SELECT count(*) FROM monitor.notification_attempts WHERE outcome='FAILED'" '1' 'MON-08-AC04 SMTP unavailable'
    $intent = Query 'SELECT id FROM monitor.notification_intents'
    Invoke-Compose @('restart', 'app') | Out-Host
    Wait-Ready
    Assert-Equal (Query 'SELECT id FROM monitor.notification_intents') $intent 'MON-08-AC02 pending intent survives restart'
    Invoke-Compose @('start', 'smtp') | Out-Host
    Query "UPDATE monitor.monitors SET next_check_at=now() WHERE id='$id'" | Out-Null
    Wait-NotificationQuery "SELECT count(*) FROM monitor.notification_intents" '2' 'MON-08-AC01 recovery intent'
    Wait-NotificationQuery "SELECT count(*) FROM monitor.notification_intents WHERE status='ACCEPTED'" '2' 'MON-08-AC07 ordered acceptance'
    Assert-Equal (Query "SELECT count(*) FROM monitor.notification_attempts WHERE outcome='ACCEPTED'") '2' 'MON-08-AC05 durable accepted attempts'
    Assert-Equal (Query "SELECT count(*) FROM monitor.notification_attempts WHERE error_code='SMTP_FAILURE'") '1' 'MON-08-AC05 sanitized failure'
    $smtpAddress = (Invoke-Compose @('port', 'smtp', '8025') -join '').Trim()
    $messages = Invoke-RestMethod -Uri "http://$smtpAddress/api/v1/messages" -TimeoutSec 5
    Assert-Equal $messages.total 2 'MON-08 Mailpit captured messages'
    foreach ($summary in $messages.messages) {
        $message = Invoke-RestMethod -Uri "http://$smtpAddress/api/v1/message/$($summary.ID)" -TimeoutSec 5
        Assert-Equal $message.To[0].Address 'ops@example.org' 'MON-08-AC06 fictitious recipient'
        if (-not $message.Text.Contains('MON08 Compose smoke') -or -not $message.Text.Contains('Incidente:')) {
            throw 'MON-08-AC06 missing frozen payload'
        }
    }
    Add-Content -Path $log -Value 'PASS MON-08-AC06 frozen payload captured by local SMTP' -Encoding UTF8
}

try {
    Invoke-Compose @('config', '--quiet') | Out-Host
    if (-not $NoBuild) { Invoke-Compose @('build', 'app') | Out-Host }
    Invoke-Compose @('up', '-d', '--wait', 'db') | Out-Host
    foreach ($missing in @('ADMIN_USERNAME=', 'ADMIN_PASSWORD=')) {
        $output = Invoke-Compose -Arguments @('run', '--rm', '--no-deps', '-T', '-e', $missing, 'app') -ExpectedExit 1
        if (-not ($output -join "`n").Contains('Administrative credentials are missing or invalid')) {
            throw 'MON-03-AC04 startup did not report the required configuration error'
        }
    }
    Add-Content -Path $log -Value 'PASS MON-03-AC04 missing username/password prevent startup' -Encoding UTF8
    Invoke-Compose @('up', '-d', 'app') | Out-Host
    Wait-Ready
    Assert-AdministrativeAccess -ExpectedStatus 401
    Assert-AdministrativeAccess -Password $initialAdminPassword -ExpectedStatus 200
    $address = (Invoke-Compose @('port', 'app', '8080') -join '').Trim()
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:ADMIN_USERNAME + ':' + $initialAdminPassword))
    $null = Invoke-WebRequest -UseBasicParsing -Uri "http://$address/api/admin/csrf" -Headers @{Authorization = "Basic $basic"} -SessionVariable previousSession -TimeoutSec 5
    $env:ADMIN_PASSWORD = $rotatedAdminPassword
    Invoke-Compose @('up', '-d', '--force-recreate', 'app') | Out-Host
    Wait-Ready
    Assert-AdministrativeAccess -Password $initialAdminPassword -ExpectedStatus 401 -Session $previousSession
    Assert-AdministrativeAccess -Password $rotatedAdminPassword -ExpectedStatus 200
    Add-Content -Path $log -Value 'PASS MON-03-AC02 password rotation rejects previous credential and cookie' -Encoding UTF8
    Assert-Equal (Query 'SELECT count(*) FROM flyway_schema_history WHERE success') '8' 'MON-02-AC02 migration count'
    Assert-Equal (Query "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history") '1,2,3,4,5,6,7,8' 'MON-02-AC02 migration versions'
    $history = Query 'SELECT row_to_json(h) FROM flyway_schema_history h ORDER BY installed_rank'
    Assert-Notifications
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
    Invoke-Compose @('logs', '--no-color') | Out-Null
    Assert-NoCredentialLeaks
    Add-Content -Path $log -Value 'PASS Compose lifecycle' -Encoding UTF8
    Write-Host "PASS Compose lifecycle; evidence: $log"
} finally {
    # This unique project belongs exclusively to this run. Never delete development volumes.
    try { Invoke-Compose @('logs', '--no-color') | Out-Null } catch { Write-Warning $_ }
    try { Invoke-Compose @('down', '--volumes', '--remove-orphans') | Out-Host } catch { Write-Warning $_ }
    $env:APP_PORT = $savedPort
    $env:ADMIN_USERNAME = $savedAdminUsername
    $env:ADMIN_PASSWORD = $savedAdminPassword
    foreach ($key in $savedNotificationEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $savedNotificationEnvironment[$key], 'Process')
    }
    Pop-Location
}
