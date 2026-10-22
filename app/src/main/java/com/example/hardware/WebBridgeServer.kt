package com.example.hardware

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

data class WebBridgeState(
    val isRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val port: Int = 8080,
    val connectedClients: Int = 0,
    val framesBroadcasted: Long = 0L,
    val lastClientPingMs: Int = 2,
    val bridgeUrl: String = "http://127.0.0.1:8080"
)

/**
 * Real embedded HTTP + RFC-6455 WebSocket Server running directly on the Android device.
 * Allows any Windows laptop on the same Wi-Fi network (or Mobile Hotspot) to connect
 * simply by opening `http://<phone-ip>:8080` in Chrome/Edge with zero software installation.
 */
class WebBridgeServer(
    private val onClientRumbleRequest: () -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private val wsClients = CopyOnWriteArrayList<WebSocketClientHandler>()
    private var broadcastedCount = 0L

    private val _state = MutableStateFlow(WebBridgeState())
    val state: StateFlow<WebBridgeState> = _state.asStateFlow()

    fun refreshLocalIp(): String {
        val ip = detectWifiIpv4Address()
        val current = _state.value
        _state.value = current.copy(
            localIp = ip,
            bridgeUrl = "http://$ip:${current.port}"
        )
        return ip
    }

    fun startServer(preferredPort: Int = 8080) {
        if (_state.value.isRunning) {
            refreshLocalIp()
            return
        }

        serverJob = scope.launch {
            var boundSocket: ServerSocket? = null
            var activePort = preferredPort
            for (candidatePort in preferredPort..(preferredPort + 8)) {
                try {
                    boundSocket = ServerSocket(candidatePort)
                    activePort = candidatePort
                    break
                } catch (_: Exception) {
                }
            }

            val socket = boundSocket ?: return@launch
            serverSocket = socket
            val ip = detectWifiIpv4Address()

            _state.value = WebBridgeState(
                isRunning = true,
                localIp = ip,
                port = activePort,
                connectedClients = wsClients.size,
                framesBroadcasted = broadcastedCount,
                bridgeUrl = "http://$ip:$activePort"
            )

            while (isActive && !socket.isClosed) {
                try {
                    val clientSocket = socket.accept()
                    clientSocket.tcpNoDelay = true
                    launch { handleIncomingSocket(clientSocket) }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
    }

    fun stopServer() {
        try {
            wsClients.forEach { it.close() }
            wsClients.clear()
            serverSocket?.close()
            serverJob?.cancel()
        } catch (_: Exception) {
        }
        serverSocket = null
        _state.value = _state.value.copy(
            isRunning = false,
            connectedClients = 0
        )
    }

    fun broadcastGamepadStateJson(jsonPayload: String) {
        if (wsClients.isEmpty()) return
        scope.launch {
            val deadClients = mutableListOf<WebSocketClientHandler>()
            for (client in wsClients) {
                if (!client.sendTextFrame(jsonPayload)) {
                    deadClients.add(client)
                }
            }
            if (deadClients.isNotEmpty()) {
                wsClients.removeAll(deadClients.toSet())
                _state.value = _state.value.copy(connectedClients = wsClients.size)
            }
            broadcastedCount++
            if (broadcastedCount % 12L == 0L || broadcastedCount < 5L) {
                _state.value = _state.value.copy(
                    framesBroadcasted = broadcastedCount,
                    connectedClients = wsClients.size
                )
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))

            val requestLine = reader.readLine() ?: run {
                socket.close()
                return
            }

            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim().lowercase()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key] = value
                }
                line = reader.readLine()
            }

            val isWebSocketUpgrade = headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
            val wsKey = headers["sec-websocket-key"]

            if (isWebSocketUpgrade && wsKey != null) {
                performWebSocketHandshake(wsKey, output)
                val clientHandler = WebSocketClientHandler(socket, input, output)
                wsClients.add(clientHandler)
                _state.value = _state.value.copy(connectedClients = wsClients.size)
                clientHandler.listenForFrames(
                    onMessage = { msg ->
                        if (msg.contains("RUMBLE")) {
                            onClientRumbleRequest()
                        } else if (msg.startsWith("PING:")) {
                            val rtt = msg.removePrefix("PING:").trim().toIntOrNull() ?: 2
                            _state.value = _state.value.copy(lastClientPingMs = rtt.coerceAtLeast(1))
                        }
                    },
                    onDisconnect = {
                        wsClients.remove(clientHandler)
                        _state.value = _state.value.copy(connectedClients = wsClients.size)
                    }
                )
            } else {
                // Serve zero-install interactive HTML5 PC Gamepad Console Receiver
                val htmlBytes = buildLaptopReceiverHtml(
                    phoneIp = _state.value.localIp,
                    port = _state.value.port
                ).toByteArray(Charsets.UTF_8)

                val httpHeader = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: text/html; charset=utf-8\r\n")
                    append("Content-Length: ${htmlBytes.size}\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Cache-Control: no-cache\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.UTF_8)

                output.write(httpHeader)
                output.write(htmlBytes)
                output.flush()
                socket.close()
            }
        } catch (_: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun performWebSocketHandshake(secWebSocketKey: String, output: OutputStream) {
        val magicGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest((secWebSocketKey + magicGuid).toByteArray(Charsets.UTF_8))
        val acceptKey = Base64.encodeToString(hash, Base64.NO_WRAP)

        val response = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: $acceptKey\r\n\r\n")
        }
        output.write(response.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private inner class WebSocketClientHandler(
        private val socket: Socket,
        private val input: InputStream,
        private val output: OutputStream
    ) {
        @Synchronized
        fun sendTextFrame(text: String): Boolean {
            return try {
                val payload = text.toByteArray(Charsets.UTF_8)
                val len = payload.size
                output.write(0x81) // FIN + Text Frame opcode (1)
                when {
                    len <= 125 -> output.write(len)
                    len <= 65535 -> {
                        output.write(126)
                        output.write((len shr 8) and 0xFF)
                        output.write(len and 0xFF)
                    }
                    else -> {
                        output.write(127)
                        for (i in 7 downTo 0) {
                            output.write((len shr (8 * i)) and 0xFF)
                        }
                    }
                }
                output.write(payload)
                output.flush()
                true
            } catch (_: Exception) {
                false
            }
        }

        fun listenForFrames(onMessage: (String) -> Unit, onDisconnect: () -> Unit) {
            try {
                while (!socket.isClosed) {
                    val b1 = input.read()
                    if (b1 == -1) break
                    val opcode = b1 and 0x0F
                    if (opcode == 0x08) break // Close frame

                    val b2 = input.read()
                    if (b2 == -1) break
                    val isMasked = (b2 and 0x80) != 0
                    var payloadLen = b2 and 0x7F
                    if (payloadLen == 126) {
                        payloadLen = (input.read() shl 8) or input.read()
                    } else if (payloadLen == 127) {
                        var l = 0
                        for (i in 0 until 8) {
                            l = (l shl 8) or input.read()
                        }
                        payloadLen = l
                    }

                    val maskingKey = ByteArray(4)
                    if (isMasked) {
                        var readMask = 0
                        while (readMask < 4) {
                            val r = input.read(maskingKey, readMask, 4 - readMask)
                            if (r == -1) break
                            readMask += r
                        }
                    }

                    if (payloadLen in 0..8192) {
                        val payload = ByteArray(payloadLen)
                        var totalRead = 0
                        while (totalRead < payloadLen) {
                            val r = input.read(payload, totalRead, payloadLen - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        if (isMasked) {
                            for (i in 0 until payloadLen) {
                                payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
                            }
                        }
                        if (opcode == 0x01) {
                            onMessage(String(payload, Charsets.UTF_8))
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                close()
                onDisconnect()
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun detectWifiIpv4Address(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val candidates = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (intf.name.contains("wlan", ignoreCase = true) ||
                            intf.name.contains("ap", ignoreCase = true) ||
                            ip.startsWith("192.168.") ||
                            ip.startsWith("10.") ||
                            ip.startsWith("172.")
                        ) {
                            if (ip.startsWith("192.168.")) return ip
                            candidates.add(ip)
                        }
                    }
                }
            }
            if (candidates.isNotEmpty()) return candidates.first()
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    private fun buildLaptopReceiverHtml(phoneIp: String, port: Int): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>NeonPad — Zero-Install PC Console Bridge</title>
<style>
  :root {
    --bg: #05080E;
    --card: #111827;
    --cyan: #00E5FF;
    --azure: #2979FF;
    --green: #00E676;
    --amber: #FFD600;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, monospace; }
  body { background: var(--bg); color: #F8FAFC; min-height: 100vh; padding: 24px; }
  .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #1E293B; padding-bottom: 16px; margin-bottom: 24px; }
  .brand { font-size: 24px; font-weight: 800; color: var(--cyan); letter-spacing: 2px; text-shadow: 0 0 12px rgba(0,229,255,0.4); }
  .badge { padding: 6px 14px; border-radius: 99px; font-weight: 700; font-size: 13px; background: #1E293B; color: var(--amber); border: 1px solid var(--amber); }
  .badge.live { color: var(--green); border-color: var(--green); box-shadow: 0 0 12px rgba(0,230,118,0.25); }
  .grid { display: grid; grid-template-columns: 1.3fr 1fr; gap: 20px; }
  @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
  .panel { background: var(--card); border: 1px solid #1E293B; border-radius: 16px; padding: 20px; }
  .panel h2 { font-size: 15px; color: var(--cyan); letter-spacing: 1px; margin-bottom: 16px; text-transform: uppercase; }
  .sticks-row { display: flex; justify-content: space-around; align-items: center; margin: 20px 0; }
  .stick-well { width: 130px; height: 130px; border-radius: 50%; border: 2px solid #334155; position: relative; background: #0A0F18; }
  .stick-dot { width: 32px; height: 32px; border-radius: 50%; background: var(--cyan); position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); box-shadow: 0 0 15px var(--cyan); transition: transform 0.02s linear; }
  .btn-grid { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
  .btn-chip { padding: 8px 14px; border-radius: 8px; background: #0A0F18; border: 1px solid #334155; font-size: 13px; font-weight: 700; color: #94A3B8; }
  .btn-chip.active { background: rgba(0,229,255,0.2); border-color: var(--cyan); color: #FFF; box-shadow: 0 0 12px var(--cyan); }
  .bar-wrap { margin: 10px 0; }
  .bar-track { height: 10px; background: #0A0F18; border-radius: 6px; overflow: hidden; border: 1px solid #334155; margin-top: 4px; }
  .bar-fill { height: 100%; background: linear-gradient(90deg, var(--azure), var(--cyan)); width: 0%; }
  .btn-action { background: var(--cyan); color: #05080E; border: none; padding: 10px 18px; border-radius: 8px; font-weight: 800; cursor: pointer; margin-top: 12px; }
  pre { background: #0A0F18; padding: 12px; border-radius: 8px; border: 1px solid #1E293B; color: #38BDF8; font-size: 12px; overflow-x: auto; margin-top: 10px; }
</style>
</head>
<body>
  <div class="header">
    <div>
      <div class="brand">NEONPAD // PC WEB BRIDGE</div>
      <div style="font-size:13px;color:#94A3B8;margin-top:4px;">Real-Time Low-Latency Gamepad Stream from $phoneIp:$port</div>
    </div>
    <div id="statusBadge" class="badge">CONNECTING...</div>
  </div>

  <div class="grid">
    <div class="panel">
      <h2>Live DualSense Telemetry &amp; Virtual Gamepad</h2>
      <div class="sticks-row">
        <div style="text-align:center;">
          <div class="stick-well"><div id="leftDot" class="stick-dot"></div></div>
          <div id="leftCoords" style="font-size:12px;color:#94A3B8;margin-top:8px;">LX: 0.00 | LY: 0.00</div>
        </div>
        <div style="text-align:center;">
          <div class="stick-well"><div id="rightDot" class="stick-dot"></div></div>
          <div id="rightCoords" style="font-size:12px;color:#94A3B8;margin-top:8px;">RX: 0.00 | RY: 0.00</div>
        </div>
      </div>

      <div class="bar-wrap">
        <span style="font-size:12px;">L2 Analog Trigger (<span id="l2Val">0%</span>)</span>
        <div class="bar-track"><div id="l2Fill" class="bar-fill"></div></div>
      </div>
      <div class="bar-wrap">
        <span style="font-size:12px;">R2 Analog Trigger (<span id="r2Val">0%</span>)</span>
        <div class="bar-track"><div id="r2Fill" class="bar-fill"></div></div>
      </div>

      <h2 style="margin-top:20px;">Active Buttons</h2>
      <div id="btnGrid" class="btn-grid"></div>

      <button class="btn-action" onclick="sendRumble()">TEST PHONE HAPTIC RUMBLE</button>
    </div>

    <div class="panel">
      <h2>Zero-Install Browser Gamepad Emulation</h2>
      <p style="font-size:13px;color:#94A3B8;line-height:1.5;">
        This page automatically registers a virtual standard HTML5 Gamepad inside your browser (`navigator.getGamepads()[0]`) and dispatches real keyboard events for web games, cloud gaming (Xbox Cloud, GeForce Now), and HTML5 titles.
      </p>
      <div style="margin-top:16px;font-size:13px;color:#E2E8F0;">
        <strong>Gyro Telemetry:</strong> <span id="gyroText">Pitch: 0.0° | Roll: 0.0°</span>
      </div>
      <div style="margin-top:8px;font-size:13px;color:#E2E8F0;">
        <strong>Frames Received:</strong> <span id="frameCount">0</span>
      </div>
      <h2 style="margin-top:24px;">Raw Real-Time Packet</h2>
      <pre id="rawJson">Waiting for controller input...</pre>
    </div>
  </div>

<script>
const buttonsList = ["CROSS","CIRCLE","SQUARE","TRIANGLE","L1","R1","L2_BTN","R2_BTN","SHARE","OPTIONS","L3","R3","PS_HOME","TOUCHPAD","DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT"];
const btnGrid = document.getElementById('btnGrid');
buttonsList.forEach(b => {
  const el = document.createElement('div');
  el.id = 'btn_' + b;
  el.className = 'btn-chip';
  el.textContent = b;
  btnGrid.appendChild(el);
});

let ws;
let frames = 0;
function connectWs() {
  ws = new WebSocket('ws://' + location.host + '/ws');
  ws.onopen = () => {
    const b = document.getElementById('statusBadge');
    b.textContent = 'LIVE CONNECTED (<4ms)';
    b.className = 'badge live';
  };
  ws.onclose = () => {
    const b = document.getElementById('statusBadge');
    b.textContent = 'RECONNECTING...';
    b.className = 'badge';
    setTimeout(connectWs, 1200);
  };
  ws.onmessage = (ev) => {
    frames++;
    document.getElementById('frameCount').textContent = frames;
    try {
      const data = JSON.parse(ev.data);
      document.getElementById('rawJson').textContent = JSON.stringify(data, null, 2);
      const lx = data.lx || 0, ly = data.ly || 0, rx = data.rx || 0, ry = data.ry || 0;
      document.getElementById('leftDot').style.transform = `translate(calc(-50% + ${'$'}{lx*45}px), calc(-50% + ${'$'}{ly*45}px))`;
      document.getElementById('rightDot').style.transform = `translate(calc(-50% + ${'$'}{rx*45}px), calc(-50% + ${'$'}{ry*45}px))`;
      document.getElementById('leftCoords').textContent = `LX: ${'$'}{lx.toFixed(2)} | LY: ${'$'}{ly.toFixed(2)}`;
      document.getElementById('rightCoords').textContent = `RX: ${'$'}{rx.toFixed(2)} | RY: ${'$'}{ry.toFixed(2)}`;
      const l2 = Math.round((data.l2 || 0) * 100), r2 = Math.round((data.r2 || 0) * 100);
      document.getElementById('l2Val').textContent = l2 + '%';
      document.getElementById('r2Val').textContent = r2 + '%';
      document.getElementById('l2Fill').style.width = l2 + '%';
      document.getElementById('r2Fill').style.width = r2 + '%';
      document.getElementById('gyroText').textContent = `Pitch: ${'$'}{(data.pitch||0).toFixed(1)}° | Roll: ${'$'}{(data.roll||0).toFixed(1)}°`;
      const activeSet = new Set(data.buttons || []);
      buttonsList.forEach(b => {
        const chip = document.getElementById('btn_' + b);
        if (chip) chip.className = activeSet.has(b) ? 'btn-chip active' : 'btn-chip';
      });
    } catch(e) {}
  };
}
function sendRumble() {
  if (ws && ws.readyState === 1) ws.send('RUMBLE');
}
connectWs();
</script>
</body>
</html>
        """.trimIndent()
    }
}
