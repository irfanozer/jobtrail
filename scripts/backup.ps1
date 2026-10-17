#requires -Version 5.1
<#
Creates a custom-format PostgreSQL backup of the database currently configured for the app.
Existing backup files are never overwritten. The database service must be running.
#>
[CmdletBinding()]
param([string]$OutputPath)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composePath = Join-Path $projectRoot 'compose.yaml'
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw 'Run scripts/start.ps1 first to create local settings.' }
$compose = @('compose', '--project-directory', $projectRoot, '--env-file', $envPath, '-f', $composePath)
$stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$token = [Guid]::NewGuid().ToString('N')
if ([string]::IsNullOrWhiteSpace($OutputPath)) { $OutputPath = Join-Path $projectRoot ('backups/jobtrail-' + $stamp + '-' + $token.Substring(0, 8) + '.dump') }
$outputFile = [IO.Path]::GetFullPath($OutputPath)
if ((Test-Path -LiteralPath $outputFile) -or (Test-Path -LiteralPath ($outputFile + '.sha256'))) { throw 'The backup destination already exists. Choose a new filename.' }
$outputDirectory = [IO.Path]::GetDirectoryName($outputFile)
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$configText = (& docker @compose config --format json 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve local Compose settings. Check .env and Docker Compose.' }
try { $config = $configText | ConvertFrom-Json } catch { throw 'Unable to read effective Compose configuration.' }
$jdbcUrl = [string]$config.services.app.environment.JDBC_DATABASE_URL
if ($jdbcUrl -notmatch '^jdbc:postgresql://db:5432/([a-z][a-z0-9_]{0,62})$') {
    throw 'Backup supports the local db service only. The app database URL must use jdbc:postgresql://db:5432/<database>.'
}
$databaseName = $Matches[1]
$containerPath = '/tmp/jobtrail-backup-' + $token + '.dump'
try {
    $diagnostic = (& docker @compose exec -T db sh -c 'umask 077; pg_dump --username "$POSTGRES_USER" --dbname "$1" --format custom --no-owner --no-privileges --file "$2"' sh $databaseName $containerPath 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Database backup failed. Confirm the db service is healthy and the configured database exists.' }
    $diagnostic = (& docker @compose cp ('db:' + $containerPath) $outputFile 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to copy the backup to the requested destination.' }
    if (-not (Test-Path -LiteralPath $outputFile -PathType Leaf) -or (Get-Item -LiteralPath $outputFile).Length -eq 0) {
        throw 'The backup output is empty or missing.'
    }
    $hash = (Get-FileHash -LiteralPath $outputFile -Algorithm SHA256).Hash
    [IO.File]::WriteAllText(($outputFile + '.sha256'), ($hash + '  ' + [IO.Path]::GetFileName($outputFile) + [Environment]::NewLine), (New-Object Text.UTF8Encoding $false))
    Write-Host ('Backup saved: ' + $outputFile)
    Write-Host ('Database: ' + $databaseName + '. SHA-256 checksum saved beside the backup.')
} finally {
    $cleanup = (& docker @compose exec -T db rm -f $containerPath 2>&1 | Out-String)
}
