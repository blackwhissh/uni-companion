<#
.SYNOPSIS
  Stop local Uni Companion processes started via dev.ps1 (ports + Postgres container).
#>
[CmdletBinding()]
param(
    [switch]$KeepPostgres
)

$ErrorActionPreference = 'Continue'
$RepoRoot = $PSScriptRoot

function Stop-PortListeners([int[]]$Ports) {
    foreach ($port in $Ports) {
        $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $conns) {
            $procId = $conn.OwningProcess
            if ($procId -and $procId -ne 0) {
                $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
                $name = if ($proc) { $proc.ProcessName } else { 'unknown' }
                Write-Host "Stopping PID $procId ($name) on port $port"
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

Write-Host '==> Stopping identity / learning / Vite (8081, 8082, 5173)' -ForegroundColor Cyan
Stop-PortListeners -Ports @(8081, 8082, 5173)

# Maven may leave a child Java process; also close titled helper windows if still empty shells.
Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match 'uni-companion (identity|learning|web)' } |
    ForEach-Object {
        Write-Host "Stopping helper shell PID $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

if (-not $KeepPostgres) {
    Write-Host '==> Stopping Postgres container' -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        docker compose stop postgres | Out-Null
    } finally {
        Pop-Location
    }
} else {
    Write-Host 'Keeping Postgres running (-KeepPostgres).'
}

Write-Host 'Done.' -ForegroundColor Green
