param([string]$SourceDirectory = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
$project = 'mon12-clean-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$copy = Join-Path $source ('.validation/' + $project)
New-Item -ItemType Directory -Force -Path $copy | Out-Null
# Include the requested uncommitted changes, but no ignored build output or local secrets.
$files = & git -C $source ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate checkout files' }
foreach ($file in $files) {
    if ($file -match '^(\.codex|\.agents)/') { continue }
    $destination = Join-Path $copy $file
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
    Copy-Item -LiteralPath (Join-Path $source $file) -Destination $destination
}
if (Test-Path (Join-Path $copy 'target')) { throw 'Clean copy contains generated target' }
$envPath = Join-Path $copy '.env'
$example = Get-Content -Raw -Encoding utf8 (Join-Path $copy '.env.example')
$example = $example.Replace('<choose-admin-username>', 'clean-operator').Replace('<choose-admin-password>', 'clean-local-only')
$example = $example.Replace('APP_PORT=8080', 'APP_PORT=0').Replace('DEMO_PORT=8081', 'DEMO_PORT=0')
$example = $example.Replace('SMTP_UI_PORT=8025', 'SMTP_UI_PORT=0').Replace('SMTP_LOCAL_PORT=1025', 'SMTP_LOCAL_PORT=0')
Set-Content -LiteralPath $envPath -Value $example -Encoding utf8
function Compose {
    param([string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & docker compose --project-directory $copy -p $project @Arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    $lines = @($output | ForEach-Object { $_.ToString() })
    $lines | Add-Content -Encoding utf8 (Join-Path $source '.validation/mon12-clean.log')
    if ($code -ne 0) { throw "Clean Compose failed: $($Arguments -join ' ')" }
    return $lines
}
try {
    Compose @('config','--quiet') | Out-Null
    Compose @('up','--build','-d') | Out-Null
    Compose @('wait','demo-seed') | Out-Null
    $logs = Compose @('logs','--no-log-prefix','demo-seed')
    $first = ($logs | Where-Object { $_.StartsWith('{') } | Select-Object -Last 1) | ConvertFrom-Json
    if ($first.seed -ne 'OK') { throw 'Initial seed did not complete' }
    $repeat = Compose @('run','--rm','--no-deps','demo-seed')
    $second = ($repeat | Where-Object { $_.StartsWith('{') } | Select-Object -Last 1) | ConvertFrom-Json
    if ($first.main -ne $second.main -or $first.secondary -ne $second.secondary) { throw 'Seed IDs changed' }
    $address = (Compose @('port','app','8080') | Select-Object -Last 1).Trim()
    $health = Invoke-RestMethod -Uri ('http://' + $address + '/actuator/health/readiness') -TimeoutSec 10
    if ($health.status -ne 'UP') { throw 'Readiness is not UP' }
    Compose @('ps','-a') | Out-Null
    Compose @('down') | Out-Null
    Compose @('up','-d') | Out-Null
    Compose @('wait','demo-seed') | Out-Null
    $persisted = Compose @('logs','--no-log-prefix','demo-seed')
    $third = ($persisted | Where-Object { $_.StartsWith('{') } | Select-Object -Last 1) | ConvertFrom-Json
    if ($first.main -ne $third.main -or $first.secondary -ne $third.secondary) { throw 'IDs lost after down/up' }
    $result = @{project=$project; source=$source; copy=$copy; generatedTargetAbsent=$true;
        seed=$first; repeat=$second; afterDownUp=$third; readiness=$health; verdict='PASS'}
    $result | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $source '.validation/mon12-clean.json')
    Write-Output 'PASS clean checkout build, four services, repeated seed and down/up persistence'
} finally {
    Compose @('down','--volumes','--remove-orphans') | Out-Null
}
