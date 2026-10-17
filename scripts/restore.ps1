#requires -Version 5.1
<#
Restores into a NEW PostgreSQL recovery database. It never drops, cleans, or overwrites an existing database.
-Activate updates ignored .env to use the recovered database and recreates only the app service.
The old database and a copy of the old .env remain available. Without -Activate, the running app is unchanged.
Example: ./scripts/restore.ps1 -BackupPath ./backups/jobtrail-example.dump -Activate
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BackupPath,
    [string]$DatabaseName = ('jobtrail_recovery_' + [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)),
    [switch]$Activate
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $projectRoot '.env'
$composePath = Join-Path $projectRoot 'compose.yaml'
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw 'Run scripts/start.ps1 first to create local settings and start PostgreSQL.' }
if ($DatabaseName -notmatch '^[a-z][a-z0-9_]{0,62}$' -or $DatabaseName -in @('jobtrail', 'postgres', 'template0', 'template1')) {
    throw 'Use a new recovery database name: lowercase letters, digits and underscores, at most 63 characters. Reserved/default databases are not restore targets.'
}
$backupFile = (Resolve-Path -LiteralPath $BackupPath).Path
if (-not (Test-Path -LiteralPath $backupFile -PathType Leaf)) { throw 'BackupPath must point to a backup file.' }
if ((Get-Item -LiteralPath $backupFile).Length -eq 0) { throw 'The selected backup is empty.' }
$checksumFile = $backupFile + '.sha256'
if (Test-Path -LiteralPath $checksumFile -PathType Leaf) {
    $expectedHash = ([IO.File]::ReadAllText($checksumFile).Trim() -split '\s+')[0]
    $actualHash = (Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash
    if ($expectedHash -notmatch '^[a-fA-F0-9]{64}$' -or $actualHash -ne $expectedHash) { throw 'Backup checksum verification failed. No database was changed.' }
}
$jdbcUrl = 'jdbc:postgresql://db:5432/' + $DatabaseName
if ($Activate -and -not [string]::IsNullOrEmpty($env:JDBC_DATABASE_URL) -and $env:JDBC_DATABASE_URL -ne $jdbcUrl) {
    throw 'JDBC_DATABASE_URL is set in this terminal and would override .env. Remove that terminal override before using -Activate.'
}
$compose = @('compose', '--project-directory', $projectRoot, '--env-file', $envPath, '-f', $composePath)
$containerPath = '/tmp/jobtrail-restore-' + [Guid]::NewGuid().ToString('N') + '.dump'
try {
    $diagnostic = (& docker @compose cp $backupFile ('db:' + $containerPath) 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stage the backup. Confirm that the db service is running.' }
    $diagnostic = (& docker @compose exec -T db pg_restore --list $containerPath 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'The file is not a readable PostgreSQL custom-format backup.' }
    $diagnostic = (& docker @compose exec -T db sh -c 'createdb --username "$POSTGRES_USER" --owner "$POSTGRES_USER" --template template0 "$1"' sh $DatabaseName 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Unable to create the recovery database. It may already exist; existing databases are never overwritten.' }
    $diagnostic = (& docker @compose exec -T db sh -c 'pg_restore --username "$POSTGRES_USER" --dbname "$1" --single-transaction --exit-on-error --no-owner --no-privileges "$2"' sh $DatabaseName $containerPath 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw ('Restore failed and its transaction was rolled back. The new recovery database remains: ' + $DatabaseName + '. The app database is unchanged.')
    }
} finally {
    $cleanup = (& docker @compose exec -T db rm -f $containerPath 2>&1 | Out-String)
}
Write-Host ('Backup restored to new database: ' + $DatabaseName)
if (-not $Activate) {
    Write-Host 'The running app still uses its previous database.'
    Write-Host ('To use this recovery database, set JDBC_DATABASE_URL=' + $jdbcUrl + ' in .env and run scripts/start.ps1.')
    return
}

$environmentBackup = $envPath + '.before-restore-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
Copy-Item -LiteralPath $envPath -Destination $environmentBackup
$lines = [IO.File]::ReadAllLines($envPath)
$updatedLines = @()
$replaced = $false
foreach ($line in $lines) {
    if ($line -match '^\s*(?:export\s+)?JDBC_DATABASE_URL\s*=') {
        if (-not $replaced) { $updatedLines += ('JDBC_DATABASE_URL=' + $jdbcUrl); $replaced = $true }
    } else { $updatedLines += $line }
}
if (-not $replaced) { $updatedLines += ('JDBC_DATABASE_URL=' + $jdbcUrl) }
[IO.File]::WriteAllLines($envPath, $updatedLines, (New-Object Text.UTF8Encoding $false))
& docker @compose up --detach --no-deps --force-recreate --wait --wait-timeout 120 app
if ($LASTEXITCODE -ne 0) {
    throw ('The recovery database is intact, but app activation failed. Previous settings are saved in ' + [IO.Path]::GetFileName($environmentBackup) + '. Inspect local service logs.')
}
Write-Host 'Recovery database activated at http://127.0.0.1:8080. The previous database has been preserved.'
Write-Host ('Previous settings saved in ' + [IO.Path]::GetFileName($environmentBackup) + '.')
