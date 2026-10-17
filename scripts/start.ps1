#requires -Version 5.1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composePath = Join-Path $projectRoot 'compose.yaml'
$envPath = Join-Path $projectRoot '.env'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install and start Docker Desktop, then run this script again.' }
if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) { throw 'compose.yaml is missing from the project root.' }
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker is not ready. Start Docker Desktop and try again.' }

function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=')
}

if (-not (Test-Path -LiteralPath $envPath)) {
    $lines = @(
        'DATABASE_USERNAME=jobtrail'
        ('DATABASE_PASSWORD=' + (New-LocalSecret))
        'JOBTRAIL_USERNAME=owner'
        ('JOBTRAIL_PASSWORD=' + (New-LocalSecret))
        'JOBTRAIL_AUTH_ENABLED=false'
        'JDBC_DATABASE_URL=jdbc:postgresql://db:5432/jobtrail'
    )
    [IO.File]::WriteAllLines($envPath, $lines, (New-Object Text.UTF8Encoding $false))
    Write-Host 'Created private local settings in .env. Keep this file out of version control.'
} else {
    $settings = [IO.File]::ReadAllText($envPath)
    if ($settings -notmatch '(?m)^\s*DATABASE_PASSWORD\s*=\s*[^\r\n\s]+' -or
        $settings -notmatch '(?m)^\s*JOBTRAIL_PASSWORD\s*=\s*[^\r\n\s]+') {
        throw 'Existing .env is missing a database or app password. Complete it locally; existing database passwords are never changed automatically.'
    }
}

$compose = @('compose', '--project-directory', $projectRoot, '--env-file', $envPath, '-f', $composePath)
& docker @compose up --build --detach --wait --wait-timeout 180
if ($LASTEXITCODE -ne 0) {
    throw 'Startup failed. Inspect Docker Compose service logs; existing database data has not been reset.'
}
Write-Host 'JobTrail is ready at http://127.0.0.1:8080'
Write-Host 'Local access needs no password with the default settings. The app is published only on this computer.'
