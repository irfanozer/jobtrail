#requires -Version 5.1
<#
Imports an explicitly selected private JSON profile through the local API.
The payload version must equal the saved profile version. -ExpectedVersion explicitly selects a new expected version.
When local authentication is enabled, pass -Credential (Get-Credential); passwords are never printed.
#>
[CmdletBinding()]
param(
    [string]$ProfilePath = (Join-Path $PSScriptRoot '../private/profile-import.json'),
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [Nullable[long]]$ExpectedVersion,
    [System.Management.Automation.PSCredential]$Credential
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
try { $baseUri = [Uri]$BaseUrl } catch { throw 'BaseUrl must be a local HTTP address.' }
if (-not $baseUri.IsAbsoluteUri -or $baseUri.Scheme -notin @('http', 'https') -or
    $baseUri.Host -notin @('127.0.0.1', 'localhost', '[::1]', '::1') -or
    $baseUri.AbsolutePath -ne '/' -or $baseUri.Query -or $baseUri.Fragment -or $baseUri.UserInfo) {
    throw 'This script sends private data only to a loopback HTTP address, such as http://127.0.0.1:8080.'
}
if (-not (Test-Path -LiteralPath $ProfilePath -PathType Leaf)) { throw 'Private profile file not found. Specify -ProfilePath or prepare private/profile-import.json.' }
try { $payload = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $ProfilePath).Path) | ConvertFrom-Json } catch { throw 'The profile file is not valid JSON.' }
if ($null -eq $payload -or $null -eq $payload.PSObject.Properties['version']) { throw 'The profile payload must include its expected version.' }
if ($PSBoundParameters.ContainsKey('ExpectedVersion')) {
    if ($null -eq $ExpectedVersion -or $ExpectedVersion -lt 0) { throw 'ExpectedVersion must be zero or greater.' }
    $payload.version = $ExpectedVersion
}

Add-Type -AssemblyName System.Net.Http
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$handler.CookieContainer = [Net.CookieContainer]::new()
$client = [Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(30)
$base = $BaseUrl.TrimEnd('/')
function Send-LocalRequest([string]$Method, [string]$Endpoint, [Net.Http.HttpContent]$Content) {
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), [Uri]($base + $Endpoint))
    if ($null -ne $Content) { $request.Content = $Content }
    $cookie = $handler.CookieContainer.GetCookies($baseUri)['XSRF-TOKEN']
    if ($Method -ne 'GET' -and $null -ne $cookie) { $request.Headers.Add('X-XSRF-TOKEN', $cookie.Value) }
    try { return $client.SendAsync($request).GetAwaiter().GetResult() } finally { $request.Dispose() }
}
try {
    if ($Credential) {
        try {
            $loginPage = Send-LocalRequest 'GET' '/login' $null
            try {
                if (-not $loginPage.IsSuccessStatusCode -or $null -eq $handler.CookieContainer.GetCookies($baseUri)['XSRF-TOKEN']) {
                    throw 'Login token missing.'
                }
            } finally { $loginPage.Dispose() }
            $fields = [Collections.Generic.Dictionary[string,string]]::new()
            $fields.Add('username', $Credential.UserName)
            $fields.Add('password', $Credential.GetNetworkCredential().Password)
            $login = Send-LocalRequest 'POST' '/login' ([Net.Http.FormUrlEncodedContent]::new($fields))
            $fields.Clear()
            try {
                if ([int]$login.StatusCode -ne 302 -or $login.Headers.Location.OriginalString -notin @('/', $base + '/')) {
                    throw 'Credentials were not accepted.'
                }
            } finally { $login.Dispose() }
        } catch { throw 'Login failed. Check credentials and whether local authentication is enabled.' }
    }
    try {
        $response = Send-LocalRequest 'GET' '/api/profile' $null
        try {
            if (-not $response.IsSuccessStatusCode) { throw 'Profile request was rejected.' }
            $current = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        } finally { $response.Dispose() }
    } catch { throw 'Unable to read the local profile. Start the app; if authentication is enabled, supply -Credential (Get-Credential).' }
    if ($payload.version -ne $current.version) {
        throw ('Profile version mismatch. File expects ' + $payload.version + ', server has ' + $current.version + '. Review existing data before explicitly supplying -ExpectedVersion ' + $current.version + '.')
    }
    $body = $payload | ConvertTo-Json -Depth 20 -Compress
    try {
        $content = [Net.Http.StringContent]::new($body, [Text.Encoding]::UTF8, 'application/json')
        $response = Send-LocalRequest 'PUT' '/api/profile' $content
        try {
            if (-not $response.IsSuccessStatusCode) { throw 'Profile update was rejected.' }
            $result = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        } finally { $response.Dispose() }
    } catch { throw 'Profile import failed. The server may have rejected invalid data or a concurrent edit. Reload the current profile before retrying.' }
    Write-Host ('Private profile imported. Version ' + $result.version + '; ' + @($result.skills).Count + ' skills; ' + @($result.careerFacts).Count + ' career facts.')
} finally { $client.Dispose(); $handler.Dispose() }
