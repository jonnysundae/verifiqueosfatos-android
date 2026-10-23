$ErrorActionPreference = "Stop"

$sdkDir = "$env:LOCALAPPDATA\Android\Sdk"
$buildTools = "$sdkDir\build-tools\34.0.0"
$androidJar = "$sdkDir\platforms\android-34\android.jar"
$jdkBin = "$sdkDir\jdk\bin"

$aapt2 = "$buildTools\aapt2.exe"
$d8 = "$buildTools\d8.bat"
$zipalign = "$buildTools\zipalign.exe"
$apksigner = "$buildTools\apksigner.bat"
$javac = "$jdkBin\javac.exe"
$keytool = "$jdkBin\keytool.exe"

$env:JAVA_HOME = "$sdkDir\jdk"
$env:PATH = "$jdkBin;$env:PATH"

$root = $PSScriptRoot
$mainDir = "$root\app\src\main"
$outDir = "$root\build"

if (Test-Path $outDir) {
    Remove-Item $outDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path "$outDir\compiled_res", "$outDir\gen", "$outDir\classes", "$outDir\dex" | Out-Null

Write-Host "1. Compilando recursos (aapt2 compile)..."
& $aapt2 compile --dir "$mainDir\res" -o "$outDir\compiled_res\res.zip"

Write-Host "2. Linkando recursos e gerando R.java (aapt2 link)..."
& $aapt2 link `
    -I $androidJar `
    --manifest "$mainDir\AndroidManifest.xml" `
    --java "$outDir\gen" `
    -o "$outDir\unaligned.apk" `
    "$outDir\compiled_res\res.zip" `
    --auto-add-overlay

Write-Host "3. Compilando codigo Java (javac)..."
$javaFiles = Get-ChildItem -Path "$mainDir\java", "$outDir\gen" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
& $javac -source 1.8 -target 1.8 -encoding UTF-8 -bootclasspath $androidJar -d "$outDir\classes" $javaFiles

Write-Host "4. Convertendo bytecode para DEX (d8)..."
$classFiles = Get-ChildItem -Path "$outDir\classes" -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
& $d8 --lib $androidJar --output "$outDir\dex" $classFiles

Write-Host "5. Empacotando classes.dex no APK..."
& "$jdkBin\jar.exe" -uf "$outDir\unaligned.apk" -C "$outDir\dex" classes.dex

Write-Host "6. Alinhando APK (zipalign)..."
& $zipalign -f -p 4 "$outDir\unaligned.apk" "$outDir\aligned.apk"

$keystore = "$root\debug.keystore"
if (-not (Test-Path $keystore)) {
    Write-Host "Gerando chave de assinatura Android..."
    & $keytool -genkeypair -v -keystore $keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=VerifiqueOsFatos,OU=Dev,O=Community,L=BR,S=SP,C=BR"
}

$finalApk = "$root\verifiqueosfatos.apk"
Write-Host "7. Assinando APK (apksigner)..."
& $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $finalApk "$outDir\aligned.apk"

Write-Host "CONCLUIDO COM SUCESSO! APK gerado em: $finalApk"
Get-Item $finalApk | Select-Object Name, Length, FullName
