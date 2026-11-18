# Builds OneKeyUninstaller.apk without Gradle (aapt2 + javac + d8 + apksigner).
#
# Requirements:
#   - JDK 17 on PATH (or JAVA_HOME set).
#   - Android SDK with build-tools;34.0.0 and platforms;android-34.
# Point the build at your SDK by setting ANDROID_SDK (or ANDROID_HOME / ANDROID_SDK_ROOT),
# e.g.  $env:ANDROID_SDK = 'C:\Android\sdk'  before running this script.
$ErrorActionPreference = 'Stop'
$proj = $PSScriptRoot

$sdk = $env:ANDROID_SDK; if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = 'C:\Users\kmn18\AndroidBuild\sdk' }  # fallback: this machine's SDK
if (-not (Test-Path $sdk)) { throw "Android SDK not found. Set `$env:ANDROID_SDK to your SDK path." }

if (-not $env:JAVA_HOME) {
    $localJdk = Get-ChildItem 'C:\Users\kmn18\AndroidBuild\jdk' -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }

$bt = "$sdk\build-tools\34.0.0"
$jar = "$sdk\platforms\android-34\android.jar"
$out = Join-Path $env:TEMP 'onekey-out'
# Signing key lives outside the repo; override with $env:ONEKEY_KEYSTORE if you like.
$ks = $env:ONEKEY_KEYSTORE; if (-not $ks) { $ks = 'C:\Users\kmn18\AndroidBuild\onekey-release.jks' }

function Run($exe, $argList) {
    # Lower ErrorActionPreference around the native call so a tool writing to stderr
    # (e.g. javac's "uses a deprecated API" note) is not turned into a terminating
    # error by PowerShell 5.1. Only a non-zero exit code is treated as failure.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & $exe @argList } finally { $ErrorActionPreference = $prev }
    if ($LASTEXITCODE -ne 0) { throw "$exe failed with exit code $LASTEXITCODE" }
}

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex", "$out\test" | Out-Null

Write-Host '== resources'
Run "$bt\aapt2.exe" @('compile', '--dir', "$proj\res", '-o', "$out\res.zip")
Run "$bt\aapt2.exe" @('link', '-o', "$out\base.apk", '-I', $jar, '--manifest', "$proj\AndroidManifest.xml",
    '--java', "$out\gen", '--min-sdk-version', '26', '--target-sdk-version', '34',
    '--version-code', '5', '--version-name', '1.4', "$out\res.zip")

Write-Host '== compile'
# Shizuku API 13.1.5 (classes.jar from the official AARs, vendored under libs/)
$libs = @('api', 'provider', 'shared', 'aidl' | ForEach-Object { "$proj\libs\$_\classes.jar" })
$src = @(Get-ChildItem -Recurse "$proj\src", "$out\gen" -Filter *.java | ForEach-Object { $_.FullName })
Run 'javac' (@('-nowarn', '-Xlint:-options', '-source', '8', '-target', '8', '-encoding', 'UTF-8',
    '-bootclasspath', "$jar;$bt\core-lambda-stubs.jar", '-cp', ($libs -join ';'), '-d', "$out\classes") + $src)

Write-Host '== unit test (encryption and backup format)'
Run 'javac' @('-nowarn', '-encoding', 'UTF-8', '-cp', "$out\classes", '-d', "$out\test", "$proj\test\CryptoTest.java")
Run 'java' @('-cp', "$out\classes;$out\test", 'CryptoTest')

Write-Host '== dex'
$classes = @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object { $_.FullName })
Run 'java' (@('-cp', "$bt\lib\d8.jar", 'com.android.tools.r8.D8', '--release', '--min-api', '26',
    '--lib', $jar, '--output', "$out\dex") + $classes + $libs)

Write-Host '== package'
Copy-Item "$out\base.apk" "$out\unaligned.apk"
Push-Location "$out\dex"
Run 'jar' @('uf', "$out\unaligned.apk", 'classes.dex')
Pop-Location
Run "$bt\zipalign.exe" @('-f', '-p', '4', "$out\unaligned.apk", "$out\aligned.apk")

Write-Host '== sign'
if (-not (Test-Path $ks)) {
    Run 'keytool' @('-genkeypair', '-keystore', $ks, '-storepass', 'onekey-store', '-keypass', 'onekey-store',
        '-alias', 'onekey', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
        '-dname', 'CN=OneKey Uninstaller')
}
Run 'java' @('-jar', "$bt\lib\apksigner.jar", 'sign', '--ks', $ks, '--ks-pass', 'pass:onekey-store',
    '--out', "$proj\OneKeyUninstaller.apk", "$out\aligned.apk")

Write-Host '== verify'
Run 'java' @('-jar', "$bt\lib\apksigner.jar", 'verify', '--verbose', '--min-sdk-version', '26', "$proj\OneKeyUninstaller.apk")
Run "$bt\zipalign.exe" @('-c', '-p', '4', "$proj\OneKeyUninstaller.apk")
Run "$bt\aapt2.exe" @('dump', 'badging', "$proj\OneKeyUninstaller.apk")
Remove-Item "$proj\OneKeyUninstaller.apk.idsig" -ErrorAction SilentlyContinue
Write-Host "DONE: $proj\OneKeyUninstaller.apk"
