$ErrorActionPreference = 'Stop'

$expectedGoogleClientId = '129979310679-1jvva61ptka1qulph59takl8d4g1urb4.apps.googleusercontent.com'

if ($env:GOOGLE_CLIENT_ID -and $env:GOOGLE_CLIENT_ID -ne $expectedGoogleClientId) {
    throw 'GOOGLE_CLIENT_ID does not match the DEV mobile serverClientId.'
}

$env:GOOGLE_CLIENT_ID = $expectedGoogleClientId
$env:SPRING_PROFILES_ACTIVE = 'dev'

Write-Host 'Starting Controle Ja API DEV with Google audience configured.'
& "$PSScriptRoot\..\mvnw.cmd" spring-boot:run
