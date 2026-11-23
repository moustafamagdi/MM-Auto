[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$KeyStore,
    [string]$Alias,
    [string]$UnsignedApk = (Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release-unsigned.apk'),
    [string]$SdkPath,
    [string]$OutputApk = (Join-Path $PSScriptRoot '..\dist\MM-Auto.apk')
)
$ErrorActionPreference = 'Stop'
$expectedSigner = '6d9d30d8f427a6cfc0241831d1e324e684fe2aefda72925c89ef872bf3a5ab65'
if (!$SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (!$SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (!$SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$tools = Join-Path $SdkPath 'build-tools\36.0.0'
$align = Join-Path $tools 'zipalign.exe'
$sign = Join-Path $tools 'apksigner.bat'
foreach ($required in @($KeyStore, $UnsignedApk, $align, $sign)) {
    if (!(Test-Path -LiteralPath $required)) { throw "File not found: $required" }
}
if (Test-Path -LiteralPath $OutputApk) { throw "Output already exists: $OutputApk" }
$outputPath = [IO.Path]::GetFullPath($OutputApk)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
$aligned = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName() + '.apk')
try {
    & $align -p 4 $UnsignedApk $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    $signArgs = @('sign', '--ks', $KeyStore, '--out', $outputPath)
    if ($Alias) { $signArgs += @('--ks-key-alias', $Alias) }
    $signArgs += $aligned
    # apksigner asks for the keystore password locally; never place it in source or command history.
    & $sign @signArgs
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
    $report = & $sign verify --verbose --print-certs $outputPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $report | Write-Output
    $fingerprints = @($report | Select-String -Pattern 'certificate SHA-256 digest: ([a-fA-F0-9]{64})' | ForEach-Object { $_.Matches[0].Groups[1].Value.ToLowerInvariant() })
    if ($fingerprints.Count -ne 1 -or $fingerprints[0] -ne $expectedSigner) {
        throw 'Wrong signing certificate. Use the original MM signing keystore.'
    }
    & $align -c -p 4 $outputPath
    if ($LASTEXITCODE -ne 0) { throw 'Signed APK alignment verification failed.' }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath).Hash.ToLowerInvariant()
    "$hash  $([IO.Path]::GetFileName($outputPath))" | Set-Content -Encoding ascii -LiteralPath ($outputPath + '.sha256')
    Write-Output "Ready: $outputPath"
} catch {
    if (Test-Path -LiteralPath $outputPath) { Remove-Item -LiteralPath $outputPath }
    throw
} finally {
    if (Test-Path -LiteralPath $aligned) { Remove-Item -LiteralPath $aligned }
}
