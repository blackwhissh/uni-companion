<#
.SYNOPSIS
  Start Postgres, identity-service, learning-service, and the Vite web app.
  Skips any piece that is already listening / healthy.

.PARAMETER Ai
  AI backend for learning-service: fake (default) or vertex.
  Only applied when learning-service is started by this run.

.EXAMPLE
  .\dev.ps1
  .\dev.ps1 -Ai vertex
#>
[CmdletBinding()]
param(
    [ValidateSet('fake', 'vertex')]
    [string]$Ai = 'fake'
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

function Test-PostgresReady {
    $status = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' uni-companion-postgres 2>$null
    if ($status -eq 'healthy' -or $status -eq 'running') {
        return $true
    }
    return Test-PortListening -Port 5433
}

function Start-ServiceWindow {
    param(
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][string]$Command,
        [hashtable]$EnvVars = @{}
    )

    $envLines = foreach ($key in $EnvVars.Keys) {
        $value = $EnvVars[$key]
        if ($null -eq $value) {
            "Remove-Item Env:$key -ErrorAction SilentlyContinue"
        } else {
            "`$env:$key = '$value'"
        }
    }

    $script = @"
`$Host.UI.RawUI.WindowTitle = '$Title'
Set-Location '$RepoRoot'
$($envLines -join "`n")
Write-Host '[$Title] starting...' -ForegroundColor Green
$Command
"@

    Start-Process -FilePath 'powershell.exe' -ArgumentList @(
        '-NoExit',
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-Command', $script
    ) | Out-Null
}

Write-Step "AI profile: $Ai"
if ($Ai -eq 'vertex') {
    Write-Host "  Using Spring profiles local,vertex (real Gemini)."
    Write-Host "  Ensure: gcloud auth application-default login --project=uniconai"
    Write-Host "  This launcher clears GOOGLE_APPLICATION_CREDENTIALS for learning-service."
} else {
    Write-Host "  Using Spring profiles local,fake (no Vertex calls)."
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
    Write-Host '  Already listening on 8081 — skip.' -ForegroundColor DarkYellow
} else {
    Write-Host '  Starting...'
    Start-ServiceWindow -Title 'uni-companion identity' -Command 'mvn -pl services/identity-service spring-boot:run'
}

Write-Step "learning-service (port 8082, AI=$Ai)"
if (Test-PortListening -Port 8082) {
    Write-Host '  Already listening on 8082 — skip.' -ForegroundColor DarkYellow
    Write-Host "  Tip: AI profile ($Ai) is not applied to an existing process. Use .\dev-stop.ps1 then re-run to switch." -ForegroundColor DarkYellow
} else {
    Write-Host '  Starting...'
    $learningEnv = @{
        SPRING_PROFILES_ACTIVE = "local,$Ai"
    }
    if ($Ai -eq 'vertex') {
        $learningEnv['GOOGLE_APPLICATION_CREDENTIALS'] = $null
        $learningEnv['GOOGLE_CLOUD_PROJECT'] = 'uniconai'
        $learningEnv['GOOGLE_CLOUD_LOCATION'] = 'global'
    }
    Start-ServiceWindow -Title "uni-companion learning ($Ai)" -Command 'mvn -pl services/learning-service spring-boot:run' -EnvVars $learningEnv
}

Write-Step 'web / Vite (port 5173)'
if (Test-PortListening -Port 5173) {
    Write-Host '  Already listening on 5173 — skip.' -ForegroundColor DarkYellow
} else {
    $webDir = Join-Path $RepoRoot 'apps\web'
    if (-not (Test-Path (Join-Path $webDir 'node_modules'))) {
        Write-Host '  node_modules missing — running npm install...'
        Push-Location $webDir
        try {
            npm install
        } finally {
            Pop-Location
        }
    }
    Write-Host '  Starting...'
    Start-ServiceWindow -Title 'uni-companion web' -Command 'Set-Location apps\web; npm run dev'
}

Write-Host ""
Write-Host 'Stack status:' -ForegroundColor Green
Write-Host "  Web               http://localhost:5173  $(if (Test-PortListening 5173) { '(up)' } else { '(starting)' })"
Write-Host "  identity-service  http://localhost:8081  $(if (Test-PortListening 8081) { '(up)' } else { '(starting)' })"
Write-Host "  learning-service  http://localhost:8082  $(if (Test-PortListening 8082) { '(up)' } else { '(starting)' })"
Write-Host "  AI profile        $Ai (applied only when learning starts)"
Write-Host ""
Write-Host 'Stop everything with:  .\dev-stop.ps1'
