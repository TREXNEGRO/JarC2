Write-Host "[*] Building TrustInJava Agent..." -ForegroundColor Cyan

New-Item -ItemType Directory -Path "build\classes" -Force | Out-Null
New-Item -ItemType Directory -Path "build\libs" -Force | Out-Null
New-Item -ItemType Directory -Path "dist" -Force | Out-Null

Write-Host "[*] Downloading dependencies..." -ForegroundColor Yellow
$commonsNetUrl = "https://repo1.maven.org/maven2/commons-net/commons-net/3.9.0/commons-net-3.9.0.jar"
$commonsNetJar = "build\libs\commons-net-3.9.0.jar"

if (!(Test-Path $commonsNetJar)) {
    Invoke-WebRequest -Uri $commonsNetUrl -OutFile $commonsNetJar
    Write-Host "[+] Downloaded commons-net" -ForegroundColor Green
}

Write-Host "[*] Compiling Java sources..." -ForegroundColor Yellow
$sourceFiles = Get-ChildItem -Path "src\main\java" -Filter "*.java" -Recurse
$compileCommand = "javac -source 11 -target 11 -cp `"build\libs\*`" -d build\classes $($sourceFiles.FullName -join ' ')"
Invoke-Expression $compileCommand

if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Compilation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "[+] Compilation successful" -ForegroundColor Green

Write-Host "[*] Extracting dependencies..." -ForegroundColor Yellow
Push-Location "build\classes"
jar xf "..\libs\commons-net-3.9.0.jar"
Remove-Item "META-INF" -Recurse -Force -ErrorAction SilentlyContinue
Pop-Location

Write-Host "[*] Creating manifest..." -ForegroundColor Yellow
$manifest = @"
Manifest-Version: 1.0
Main-Class: com.trustinjava.agent.RemoteAgent
Agent-Class: com.trustinjava.agent.RemoteAgent
Can-Redefine-Classes: true
Can-Retransform-Classes: true

"@

$manifest | Out-File -FilePath "build\MANIFEST.MF" -Encoding ASCII

Write-Host "[*] Creating JAR file..." -ForegroundColor Yellow
Push-Location "build\classes"
jar cfm "..\..\dist\RemoteAgent.jar" "..\MANIFEST.MF" .
Pop-Location

if (Test-Path "dist\RemoteAgent.jar") {
    $jarSize = (Get-Item "dist\RemoteAgent.jar").Length / 1KB
    Write-Host "[+] JAR created successfully: dist\RemoteAgent.jar ($([math]::Round($jarSize, 2)) KB)" -ForegroundColor Green
    Write-Host "`n[*] Build complete!" -ForegroundColor Cyan
} else {
    Write-Host "[!] JAR creation failed!" -ForegroundColor Red
    exit 1
}
