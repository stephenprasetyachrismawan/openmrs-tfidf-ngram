[CmdletBinding()]
param(
    [int]$Port = 8081
)

$ErrorActionPreference = 'Stop'
$baseUrl = "http://localhost:$Port"
$checks = @(
    @{ Name = 'Gateway'; Url = "$baseUrl/" },
    @{ Name = 'O3 SPA'; Url = "$baseUrl/openmrs/spa/home" },
    @{ Name = 'REST session'; Url = "$baseUrl/openmrs/ws/rest/v1/session" }
)

foreach ($check in $checks) {
    try {
        $response = Invoke-WebRequest -Uri $check.Url -UseBasicParsing -TimeoutSec 30
        Write-Output ("{0}: HTTP {1}" -f $check.Name, $response.StatusCode)
    }
    catch {
        $response = $_.Exception.Response
        if ($null -ne $response) {
            $status = [int]$response.StatusCode
            if ($status -in 401, 302) {
                Write-Output ("{0}: HTTP {1} (expected unauthenticated/redirect response)" -f $check.Name, $status)
            }
            else {
                throw ("{0}: unexpected HTTP {1}" -f $check.Name, $status)
            }
        }
        else {
            throw ("{0}: request failed: {1}" -f $check.Name, $_.Exception.Message)
        }
    }
}
