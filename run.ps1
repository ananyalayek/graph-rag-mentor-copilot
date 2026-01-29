param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly
)

$ErrorActionPreference = "Stop"

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        if ($_ -match '^\s*#' -or $_.Trim() -eq '') { return }
        if ($_ -match '^(\w+)=(.*)$') {
            $name = $matches[1]
            $value = $matches[2].Trim()
            if ($value.StartsWith('"') -and $value.EndsWith('"')) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            Set-Item -Path "env:$name" -Value $value
        }
    }
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot
Import-DotEnv -Path (Join-Path $repoRoot ".env")

if (-not $FrontendOnly) {
    Write-Host "Building backend..."
    mvn -q -DskipTests package
    Write-Host "Starting backend on http://localhost:8080 ..."
    Start-Process -FilePath "mvn" -ArgumentList "-q", "spring-boot:run" -WorkingDirectory $repoRoot
}

if (-not $BackendOnly) {
    Write-Host "Starting frontend on http://localhost:8501 ..."
    streamlit run Chat.py
}
