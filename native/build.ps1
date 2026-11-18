param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Platform = 'android-35',
    [string]$ToolsVersion = '35.0.0',
    [ValidateSet('all','android','windows')][string]$Target = 'all'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$Jdk) {throw 'Set JAVA_HOME to a JDK 17+ with jpackage.'}
$env:JAVA_HOME = $Jdk
$javaBin = Join-Path $Jdk 'bin'
function Run([string]$exe, [array]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {throw "$exe failed ($LASTEXITCODE)"}
}
function New-BuildFolder([string]$path) {(New-Item -ItemType Directory -Path $path -Force).FullName}
$build = New-BuildFolder ('build/' + [guid]::NewGuid().ToString('N'))
$release = New-BuildFolder 'release'
$gson = (Resolve-Path 'lib/gson-2.10.1.jar').Path
$common = @(Get-ChildItem 'shared/rift/*.java' | ForEach-Object FullName)
$javac = Join-Path $javaBin 'javac.exe'
$jar = Join-Path $javaBin 'jar.exe'
if ($Target -ne 'android') {
    $jna = (Resolve-Path 'lib/jna-5.17.0.jar').Path
    $classes = New-BuildFolder "$build/win-classes"
    $inputDir = New-BuildFolder "$build/input"
    Run $javac (@('--release','8','-encoding','UTF-8','-cp',"$gson;$jna",'-d',$classes) + $common + @(Get-ChildItem 'windows/rift/*.java' | ForEach-Object FullName))
    $manifest = "$build/manifest.txt"
    [IO.File]::WriteAllText($manifest,"Manifest-Version: 1.0"+[Environment]::NewLine+"Main-Class: rift.Desktop"+[Environment]::NewLine+"Class-Path: gson-2.10.1.jar jna-5.17.0.jar"+[Environment]::NewLine+[Environment]::NewLine)
    Run $jar @('cfm',"$inputDir/RiftTimer.jar",$manifest,'-C',$classes,'.','-C',$PSScriptRoot,'art')
    Copy-Item -LiteralPath $gson,$jna -Destination $inputDir
    $speechDir = New-BuildFolder "$inputDir/speech"
    Copy-Item -LiteralPath 'voice-data/model' -Destination $speechDir -Recurse
    Copy-Item 'voice-data/windows/*.dll' -Destination $speechDir
    $win = New-BuildFolder "$build/windows"
    Run (Join-Path $javaBin 'jpackage.exe') @('--type','app-image','--name','RiftTimer','--app-version','2.2.3','--vendor','Rift Timer','--input',$inputDir,'--main-jar','RiftTimer.jar','--main-class','rift.Desktop','--add-modules','java.desktop,java.logging,java.prefs,jdk.unsupported,jdk.crypto.ec','--dest',$win,'--icon',(Resolve-Path 'art/RiftTimer.ico').Path)
    $winRelease = New-BuildFolder "$release/windows"
    Copy-Item -LiteralPath "$win/RiftTimer" -Destination $winRelease -Recurse -Force
    Run 'tar.exe' @('-a','-cf',"$release/RiftTimer-Windows-x64.zip",'-C',$win,'RiftTimer')
}
if ($Target -ne 'windows') {
    if (!$Sdk) {throw 'Set ANDROID_HOME to an Android SDK.'}
    $bt = Join-Path $Sdk "build-tools/$ToolsVersion"
    $android = Join-Path $Sdk "platforms/$Platform/android.jar"
    $classes = New-BuildFolder "$build/android-classes"
    $jna = (Resolve-Path 'lib/jna-android.jar').Path
    $sources = @(Get-ChildItem android/rift/*.java | ForEach-Object FullName)
    Run $javac (@('--release','8','-encoding','UTF-8','-cp',"$gson;$android;$jna",'-d',$classes) + $common + $sources)
    Run $jar @('cf',"$build/code.jar",'-C',$classes,'.')
    $dex = New-BuildFolder "$build/dex"
    Run "$bt/d8.bat" @('--release','--min-api','26','--lib',$android,'--output',$dex,"$build/code.jar",$gson,$jna)
    $assets = New-BuildFolder "$build/assets"
    Copy-Item 'art/*.png' $assets
    Copy-Item -LiteralPath 'voice-data/model' -Destination $assets -Recurse
    Run "$bt/aapt.exe" @('package','-f','-M','android/AndroidManifest.xml','-S','android/res','-A',$assets,'-I',$android,'-F',"$build/unsigned.apk")
    Run $jar @('uf',"$build/unsigned.apk",'-C',$dex,'classes.dex','-C','voice-data/android','lib')
    Run "$bt/zipalign.exe" @('-f','4',"$build/unsigned.apk","$build/aligned.apk")
    $sign = if ($env:RIFT_SIGN_DIR) {(Resolve-Path -LiteralPath $env:RIFT_SIGN_DIR).Path} else {New-BuildFolder '.signing'}
    $pass = Join-Path $sign 'password'
    $key = Join-Path $sign 'release.p12'
    if (!(Test-Path $pass)) {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {$rng.GetBytes($bytes)} finally {$rng.Dispose()}
        [IO.File]::WriteAllText($pass,[Convert]::ToBase64String($bytes))
    }
    $env:RIFT_SIGN_PASS = [IO.File]::ReadAllText($pass)
    try {
        if (!(Test-Path $key)) {
            Run "$javaBin/keytool.exe" @('-genkeypair','-storetype','PKCS12','-keystore',$key,'-alias','rift','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Rift Timer','-storepass:env','RIFT_SIGN_PASS')
        }
        Run "$bt/apksigner.bat" @('sign','--ks',$key,'--ks-key-alias','rift','--ks-pass','env:RIFT_SIGN_PASS','--v4-signing-enabled','false','--out',"$release/RiftTimer-Android.apk","$build/aligned.apk")
        Run "$bt/apksigner.bat" @('verify','--verbose',"$release/RiftTimer-Android.apk")
    } finally {Remove-Item Env:RIFT_SIGN_PASS -ErrorAction SilentlyContinue}
}

