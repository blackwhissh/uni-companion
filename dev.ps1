<#
.SYNOPSIS
  Start Postgres, identity-service, learning-service, and the Vite web app.

.PARAMETER Ai
  AI backend for learning-service: fake (default) or vertex.
  Only applied when learning-service is started by this run.

.PARAMETER TunnelCors
  Enable Spring profile "qa" + Vite --mode qa (Cloudflare tunnel CORS / same-origin proxy).
  Does not start the QA orchestrator — that lives in the sibling repo uni-companion-qa.

.EXAMPLE
  .\dev.ps1
  .\dev.ps1 -Ai vertex
  .\dev.ps1 -Ai vertex -TunnelCors
#>
[CmdletBinding()]
param(
    [ValidateSet('fake', 'vertex')]
    [string]$Ai = 'fake',

    [switch]$TunnelCors
)

$ErrorActionPreference = 'Stop'
$RepoRoot = $PSScriptRoot

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-PortListening([int]$Port) {
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Wait-PortListening {
    param(
        [Parameter(Mandatory)][int]$Port,
        [int]$TimeoutSec = 120,
        [string]$Label = "port $Port"
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port) { return $true }
        Start-Sleep -Seconds 2
    }
    Write-Host "  Timed out waiting for $Label." -ForegroundColor DarkYellow
    return $false
}

function Test-PostgresReady {
    $status = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' uni-companion-postgres 2>$null
    if ($status -eq 'healthy' -or $status -eq 'running') {
        return $true
    }
    return Test-PortListening -Port 5433
}

function Wait-PostgresHealthy {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $status = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' uni-companion-postgres 2>$null
        if ($status -eq 'healthy' -or $status -eq 'running') {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Postgres did not become healthy in time (last status: $status)."
}

function Start-ServiceWindow {
    param(
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][string]$Command,
        [hashtable]$EnvVars = @{}
    )
    $envLines = ($EnvVars.GetEnumerator() | ForEach-Object {
        if ($null -eq $_.Value) {
            "Remove-Item Env:$($_.Key) -ErrorAction SilentlyContinue"
        } else {
            "`$env:$($_.Key) = '$($_.Value)'"
        }
    }) -join "`n"
    $script = @"
`$Host.UI.RawUI.WindowTitle = '$Title'
Set-Location '$RepoRoot'
$envLines
$Command
"@
    Start-Process powershell.exe -ArgumentList @(
        '-NoExit',
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-Command', $script
    ) | Out-Null
}

$SpringQaSuffix = if ($TunnelCors) { ',qa' } else { '' }

Write-Step "AI profile: $Ai$(if ($TunnelCors) { ' + tunnel CORS (qa)' } else { '' })"
if ($Ai -eq 'vertex') {
    Write-Host "  Using Spring profiles local,vertex$SpringQaSuffix (real Gemini)."
    Write-Host "  Ensure: gcloud auth application-default login --project=uniconai"
    Write-Host "  This launcher clears GOOGLE_APPLICATION_CREDENTIALS for learning-service."
} else {
    Write-Host "  Using Spring profiles local,fake$SpringQaSuffix."
}
if ($TunnelCors) {
    Write-Host '  Tunnel CORS: Spring profile "qa" + Vite --mode qa.'
    Write-Host '  Orchestrator / Grok tunnels: use sibling repo uni-companion-qa (.\start-qa.ps1).'
}

Write-Step 'Postgres (port 5433)'
if (Test-PostgresReady) {
    Write-Host '  Already running — skip.' -ForegroundColor DarkYellow
} else {
    Write-Host '  Starting via docker compose...'
    Push-Location $RepoRoot
    try {
        docker compose up -d postgres
    } finally {
        Pop-Location
    }
    Wait-PostgresHealthy
    Write-Host '  Postgres is healthy on localhost:5433'
}

Write-Step 'identity-service (port 8081)'
if (Test-PortListening -Port 8081) {
    Write-Host '  Already listening on 8081 - skip.' -ForegroundColor DarkYellow
    if ($TunnelCors) {
        Write-Host '  Tip: restart with .\dev-stop.ps1 if this process was started without the qa profile.' -ForegroundColor DarkYellow
    }
} else {
    Write-Host '  Starting...'
    $identityEnv = @{
        SPRING_PROFILES_ACTIVE = "local$SpringQaSuffix"
    }
    $identityTitle = if ($TunnelCors) { 'uni-companion identity (qa)' } else { 'uni-companion identity' }
    Start-ServiceWindow -Title $identityTitle -Command 'mvn -pl services/identity-service spring-boot:run' -EnvVars $identityEnv
}

Write-Step "learning-service (port 8082, AI=$Ai)"
if (Test-PortListening -Port 8082) {
    Write-Host '  Already listening on 8082 - skip.' -ForegroundColor DarkYellow
    Write-Host "  Tip: AI/tunnel profiles are not applied to an existing process. Use .\dev-stop.ps1 then re-run to switch." -ForegroundColor DarkYellow
} else {
    Write-Host '  Starting...'
    $learningEnv = @{
        SPRING_PROFILES_ACTIVE = "local,$Ai$SpringQaSuffix"
    }
    if ($Ai -eq 'vertex') {
        $learningEnv['GOOGLE_APPLICATION_CREDENTIALS'] = $null
        $learningEnv['GOOGLE_CLOUD_PROJECT'] = 'uniconai'
        $learningEnv['GOOGLE_CLOUD_LOCATION'] = 'global'
    }
    $learningTitle = if ($TunnelCors) { "uni-companion learning ($Ai,qa)" } else { "uni-companion learning ($Ai)" }
    Start-ServiceWindow -Title $learningTitle -Command 'mvn -pl services/learning-service spring-boot:run' -EnvVars $learningEnv
}

Write-Step 'web / Vite (port 5173)'
if (Test-PortListening -Port 5173) {
    Write-Host '  Already listening on 5173 - skip.' -ForegroundColor DarkYellow
    if ($TunnelCors) {
        Write-Host '  Tip: restart with .\dev-stop.ps1 if Vite was not started with --mode qa.' -ForegroundColor DarkYellow
    }
} else {
    $webDir = Join-Path $RepoRoot 'apps\web'
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host '  node_modules missing - running npm install...'
        Push-Location $webDir
        try {
            npm install
        } finally {
            Pop-Location
        }
    }
    Write-Host '  Starting...'
    $webCmd = if ($TunnelCors) {
        'Set-Location apps\web; npm run dev:qa'
    } else {
        'Set-Location apps\web; npm run dev'
    }
    $webTitle = if ($TunnelCors) { 'uni-companion web (qa)' } else { 'uni-companion web' }
    Start-ServiceWindow -Title $webTitle -Command $webCmd
}

Write-Host ""
Write-Host 'Stack status:' -ForegroundColor Green
Write-Host "  Web               http://localhost:5173  $(if (Test-PortListening 5173) { '(up)' } else { '(starting)' })"
Write-Host "  identity-service  http://localhost:8081  $(if (Test-PortListening 8081) { '(up)' } else { '(starting)' })"
Write-Host "  learning-service  http://localhost:8082  $(if (Test-PortListening 8082) { '(up)' } else { '(starting)' })"
Write-Host "  AI profile        $Ai (applied only when learning starts)"
Write-Host ""
Write-Host 'Stop with:  .\dev-stop.ps1'
Write-Host 'Grok / MCP QA:  ..\uni-companion-qa\start-qa.ps1  (separate repo)'
