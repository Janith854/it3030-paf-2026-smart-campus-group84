# This script loads .env and starts the backend
$PSScriptRoot = Get-Location

$EnvPath = ".env"
if (-not (Test-Path $EnvPath)) {
    $EnvPath = "backend\.env"
}

if (Test-Path $EnvPath) {
    Write-Host "Loading $EnvPath file..." -ForegroundColor Cyan
    Get-Content $EnvPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            if ($line -match '^([^=]+)=(.*)$') {
                $name = $Matches[1].Trim()
                $value = $Matches[2].Trim()
                [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
                Write-Host "Set $name" -ForegroundColor Gray
            }
        }
    }
} else {
    Write-Host ".env file not found. Using system environment variables." -ForegroundColor Yellow
}

# Run the setup and start script in the same session to ensure env vars are preserved
. "$PSScriptRoot\auto_setup_maven.ps1"
