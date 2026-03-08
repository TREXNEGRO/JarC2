Write-Host @"
╔═══════════════════════════════════════════════════════════╗
║         TrustInJava C2 - Quick Start Script              ║
╚═══════════════════════════════════════════════════════════╝
"@ -ForegroundColor Cyan

Write-Host "`n[*] Checking Python installation..." -ForegroundColor Yellow
$python = Get-Command python -ErrorAction SilentlyContinue

if (-not $python) {
    Write-Host "[!] Python not found! Please install Python 3.8 or higher." -ForegroundColor Red
    exit 1
}

$pythonVersion = & python --version 2>&1
Write-Host "[+] Found: $pythonVersion" -ForegroundColor Green

Write-Host "`n[*] Checking dependencies..." -ForegroundColor Yellow
$packages = @("Flask", "flask-socketio")
$missing = @()

foreach ($package in $packages) {
    $installed = & python -c "import $($package.ToLower().Replace('-', '_'))" 2>&1
    if ($LASTEXITCODE -ne 0) {
        $missing += $package
    }
}

if ($missing.Count -gt 0) {
    Write-Host "[!] Missing packages: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "[*] Installing dependencies..." -ForegroundColor Yellow
    & python -m pip install -r requirements.txt
}

Write-Host "[+] All dependencies installed" -ForegroundColor Green

Write-Host "`n[*] Starting C2 Server..." -ForegroundColor Cyan
Write-Host @"

    C2 Server Configuration:
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    - Agent Listener: 0.0.0.0:4444
    - Web Interface:  http://localhost:5000
    - WebSocket:      Real-time updates enabled
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    Quick Setup for Agents:
    1. Create config.ep with your server IP and port
    2. Run: java -jar RemoteAgent.jar
    3. Open web interface to control agents

    Press Ctrl+C to stop the server
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

"@ -ForegroundColor White

Start-Sleep -Seconds 2

& python c2_server.py
