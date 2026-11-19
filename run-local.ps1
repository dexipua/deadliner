# Runs the bot locally: starts Postgres in Docker, loads .env, starts the app.
# Usage: .\run-local.ps1   (stop with Ctrl+C)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path .env)) { throw ".env not found - copy .env.example to .env and fill it in" }
Get-Content .env | Where-Object { $_ -match '^\s*[A-Z_]+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "env:$($name.Trim())" -Value $value.Trim()
}

$jdk = Join-Path $HOME '.local\java\jdk-21.0.13+9'
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }

docker compose up -d
.\mvnw.cmd -q spring-boot:run
