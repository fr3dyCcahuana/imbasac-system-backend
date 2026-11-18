[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][Guid]$DeviceId,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [switch]$GenerateRequest
)
$ErrorActionPreference = 'Stop'
# Candidate only. No remote attestation is produced or accepted by this script.
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
$repositoryPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if ($outputPath.Equals($repositoryPath, [StringComparison]::OrdinalIgnoreCase) -or
    $outputPath.StartsWith($repositoryPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use a private directory outside the repository.'
}
if (Test-Path -LiteralPath $outputPath) { throw 'The output directory already exists; nothing was changed.' }
$windowsIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($windowsIdentity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Abra Windows PowerShell como administrador con la misma cuenta de Windows que usara el navegador. Si se requieren credenciales de otra cuenta, contacte al administrador de IMBASAC.'
}
$tpm = TrustedPlatformModule\Get-Tpm
if ($tpm -is [string]) { throw "Windows no pudo consultar el TPM: $tpm" }
if ($tpm.TpmPresent -ne $true -or $tpm.TpmReady -ne $true) {
    throw 'Windows no confirmo que el TPM este presente y listo. Se requiere un TPM para generar la solicitud.'
}
$null = New-Item -ItemType Directory -Path $outputPath
$identity = $windowsIdentity.Name
$acl = Get-Acl -LiteralPath $outputPath
$acl.SetAccessRuleProtection($true, $false)
$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule('SYSTEM', 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
Set-Acl -LiteralPath $outputPath -AclObject $acl
$device = $DeviceId.ToString('D')
$inf = @"
[Version]
Signature = "`$Windows NT`$"
[NewRequest]
Subject = "CN=imbasac-device-$device"
RequestType = PKCS10
KeyAlgorithm = RSA
KeyLength = 3072
HashAlgorithm = SHA256
ProviderName = "Microsoft Platform Crypto Provider"
ProviderType = 0
KeySpec = 0
KeyUsage = 0x80
Exportable = FALSE
ExportableEncrypted = FALSE
MachineKeySet = FALSE
UseExistingKeySet = FALSE
[EnhancedKeyUsageExtension]
OID = 1.3.6.1.5.5.7.3.2
"@
$infPath = Join-Path $outputPath 'device-request.inf'
$requestPath = Join-Path $outputPath 'device-request.pem'
[IO.File]::WriteAllText($infPath, $inf, [Text.Encoding]::ASCII)
$report = [ordered]@{
    deviceId = $device; platform = 'WINDOWS_TPM_CANDIDATE'; tpmPresent = $tpm.TpmPresent;
    tpmReady = $tpm.TpmReady; provider = 'Microsoft Platform Crypto Provider';
    requestedRsaBits = 3072; hardwareAttestationVerified = $false; generated = $false;
    scope = 'CurrentUser: several ERP users can share the same OS profile; other OS profiles need homologation';
    browserCompatibilityVerified = $false
}
if ($GenerateRequest) {
    & certreq.exe -new $infPath $requestPath
    if ($LASTEXITCODE -ne 0) { throw 'TPM request failed. Do not switch to a software provider.' }
    $report.generated = $true
}
$report | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'review.json') -Encoding UTF8
Write-Output 'Candidate prepared. Only CSR/public report may leave this PC. Hardware approval remains pending.'
