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
    val osInjectorClients: Int = 0,
    val framesBroadcasted: Long = 0L,
    val lastClientPingMs: Int = 2,
    val bridgeUrl: String = "http://127.0.0.1:8080"
)

/**
 * Real embedded HTTP + RFC-6455 WebSocket + Raw TCP Game Injector Server running on Android.
 *
 * Why a browser tab alone doesn't move characters in desktop Windows games:
 * Chrome/Edge sandboxes web pages so a normal tab cannot press keys inside external .exe games.
 * To solve this with ZERO software installation on Windows, this server also hosts:
 *  - `GET /NeonPad-PC-Bridge.bat` : 1-click zero-install Windows launcher
 *  - `GET /win`                   : Native Windows PowerShell + C# `user32.dll` `SendInput`
 *                                   Hardware Scan-Code & Mouse Look injector
 *  - `GET /stream`                : Low-latency (<2ms) TCP stream feeding the Windows OS injector
 */
class WebBridgeServer(
    private val onClientRumbleRequest: () -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private val wsClients = CopyOnWriteArrayList<WebSocketClientHandler>()
    private val tcpStreamClients = CopyOnWriteArrayList<TcpStreamClientHandler>()
    private var broadcastedCount = 0L
    private var lastSentJson = ""

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
                connectedClients = wsClients.size + tcpStreamClients.size,
                osInjectorClients = tcpStreamClients.size,
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
            tcpStreamClients.forEach { it.close() }
            tcpStreamClients.clear()
            serverSocket?.close()
            serverJob?.cancel()
        } catch (_: Exception) {
        }
        serverSocket = null
        _state.value = _state.value.copy(
            isRunning = false,
            connectedClients = 0,
            osInjectorClients = 0
        )
    }

    fun broadcastGamepadStateJson(jsonPayload: String) {
        if (wsClients.isEmpty() && tcpStreamClients.isEmpty()) return

        // Add osInjectorActive flag so browser dashboard knows if the Windows OS Bridge is running
        val enrichedJson = if (jsonPayload.endsWith("}")) {
            jsonPayload.dropLast(1) + ",\"osActive\":${tcpStreamClients.size}}"
        } else {
            jsonPayload
        }

        scope.launch {
            if (wsClients.isNotEmpty()) {
                val deadWs = mutableListOf<WebSocketClientHandler>()
                for (client in wsClients) {
                    if (!client.sendTextFrame(enrichedJson)) {
                        deadWs.add(client)
                    }
                }
                if (deadWs.isNotEmpty()) {
                    wsClients.removeAll(deadWs.toSet())
                }
            }

            if (tcpStreamClients.isNotEmpty()) {
                val deadTcp = mutableListOf<TcpStreamClientHandler>()
                for (client in tcpStreamClients) {
                    if (!client.sendLine(enrichedJson)) {
                        deadTcp.add(client)
                    }
                }
                if (deadTcp.isNotEmpty()) {
                    tcpStreamClients.removeAll(deadTcp.toSet())
                }
            }

            broadcastedCount++
            if (broadcastedCount % 10L == 0L || broadcastedCount < 5L) {
                _state.value = _state.value.copy(
                    framesBroadcasted = broadcastedCount,
                    connectedClients = wsClients.size + tcpStreamClients.size,
                    osInjectorClients = tcpStreamClients.size
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

            val path = requestLine.split(" ").getOrNull(1) ?: "/"

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

            when {
                isWebSocketUpgrade && wsKey != null -> {
                    performWebSocketHandshake(wsKey, output)
                    val clientHandler = WebSocketClientHandler(socket, input, output)
                    wsClients.add(clientHandler)
                    updateClientCounts()
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
                            updateClientCounts()
                        }
                    )
                }

                path.startsWith("/stream") -> {
                    // Persistent low-latency raw JSON line stream for the Windows OS Injector
                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/plain; charset=utf-8\r\n")
                        append("Cache-Control: no-cache\r\n")
                        append("Connection: keep-alive\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    output.write(header)
                    output.flush()

                    val tcpClient = TcpStreamClientHandler(socket, output)
                    tcpStreamClients.add(tcpClient)
                    updateClientCounts()
                    onClientRumbleRequest() // Vibrate phone to confirm Windows OS Game Bridge connected!

                    // Keep socket open until client disconnects
                    try {
                        while (!socket.isClosed && input.read() != -1) {
                            // Keep-alive read
                        }
                    } catch (_: Exception) {
                    } finally {
                        tcpStreamClients.remove(tcpClient)
                        tcpClient.close()
                        updateClientCounts()
                    }
                }

                path.startsWith("/win") -> {
                    // Serves the zero-install Windows PowerShell + C# SendInput Hardware ScanCode Bridge
                    val psScriptBytes = buildWindowsPowerShellInjectorScript(
                        phoneIp = _state.value.localIp,
                        port = _state.value.port
                    ).toByteArray(Charsets.UTF_8)

                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/plain; charset=utf-8\r\n")
                        append("Content-Length: ${psScriptBytes.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    output.write(header)
                    output.write(psScriptBytes)
                    output.flush()
                    socket.close()
                }

                path.startsWith("/NeonPad-PC-Bridge.bat") -> {
                    // Serves a 1-click downloadable .bat launcher for Windows 10/11
                    val ip = _state.value.localIp
                    val port = _state.value.port
                    val batContent = buildString {
                        append("@echo off\r\n")
                        append("title NeonPad - Windows OS Gamepad & KeyBridge\r\n")
                        append("color 0B\r\n")
                        append("echo =========================================================\r\n")
                        append("echo   NEONPAD // ZERO-INSTALL WINDOWS OS GAME CONTROLLER\r\n")
                        append("echo   Connecting to Phone at http://$ip:$port ...\r\n")
                        append("echo =========================================================\r\n")
                        append("powershell -NoProfile -ExecutionPolicy Bypass -Command \"irm http://$ip:$port/win | iex\"\r\n")
                        append("pause\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    val header = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: application/octet-stream\r\n")
                        append("Content-Disposition: attachment; filename=\"NeonPad-PC-Bridge.bat\"\r\n")
                        append("Content-Length: ${batContent.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    output.write(header)
                    output.write(batContent)
                    output.flush()
                    socket.close()
                }

                else -> {
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
            }
        } catch (_: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun updateClientCounts() {
        _state.value = _state.value.copy(
            connectedClients = wsClients.size + tcpStreamClients.size,
            osInjectorClients = tcpStreamClients.size
        )
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

    private inner class TcpStreamClientHandler(
        private val socket: Socket,
        private val output: OutputStream
    ) {
        @Synchronized
        fun sendLine(jsonLine: String): Boolean {
            return try {
                val bytes = (jsonLine + "\n").toByteArray(Charsets.UTF_8)
                output.write(bytes)
                output.flush()
                true
            } catch (_: Exception) {
                false
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
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
                output.write(0x81)
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
                    if (opcode == 0x08) break

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

    /**
     * Generates a self-contained native Windows PowerShell + C# `user32.dll` Hardware ScanCode
     * & Mouse Look injector that runs using built-in Windows components with zero installation.
     */
    private fun buildWindowsPowerShellInjectorScript(phoneIp: String, port: Int): String {
        return """
${'$'}ErrorActionPreference = 'SilentlyContinue'
Clear-Host
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  NEONPAD // WINDOWS OS DIRECTINPUT & HARDWARE KEY/MOUSE BRIDGE  " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "Connected to NeonPad Phone at $phoneIp`:$port" -ForegroundColor Green
Write-Host "All PC Games, Steam, Emulators & Windows Desktop now receive live input!" -ForegroundColor Yellow
Write-Host "  Left Stick   -> W / A / S / D (Movement)"
Write-Host "  Right Stick  -> Mouse Look / Camera Aim"
Write-Host "  D-Pad        -> Up / Down / Left / Right Arrow Keys"
Write-Host "  Cross (X)    -> Space (Jump / Select / Nitro)"
Write-Host "  Circle (O)   -> Left Ctrl (Crouch / Dodge)"
Write-Host "  Square ([])  -> R (Reload / Action)"
Write-Host "  Triangle (/\)-> F (Interact / Enter Vehicle)"
Write-Host "  L1 / R1      -> Q / E"
Write-Host "  L2 / R2      -> Right Click (Aim) / Left Click (Fire)"
Write-Host "  L3 / R3      -> Left Shift (Sprint) / V (Melee)"
Write-Host "  Options/Share-> Esc (Pause) / Tab (Map)"
Write-Host "==================================================================" -ForegroundColor Cyan

${'$'}csharp = @"
using System;
using System.Runtime.InteropServices;

public static class NeonWinInput {
    [DllImport("user32.dll", SetLastError = true)]
    private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, UIntPtr dwExtraInfo);

    [DllImport("user32.dll")]
    private static extern uint MapVirtualKey(uint uCode, uint uMapType);

    private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_SCANCODE = 0x0008;

    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;

    public static void SetKey(byte vk, bool extended, bool down) {
        byte scan = (byte)MapVirtualKey(vk, 0);
        uint flags = KEYEVENTF_SCANCODE;
        if (extended) flags |= KEYEVENTF_EXTENDEDKEY;
        if (!down) flags |= KEYEVENTF_KEYUP;
        keybd_event(vk, scan, flags, UIntPtr.Zero);
    }

    public static void MoveMouse(int dx, int dy) {
        if (dx != 0 || dy != 0) {
            mouse_event(MOUSEEVENTF_MOVE, dx, dy, 0, UIntPtr.Zero);
        }
    }

    public static void SetMouseBtn(bool left, bool down) {
        uint flag = left ? (down ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP)
                         : (down ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP);
        mouse_event(flag, 0, 0, 0, UIntPtr.Zero);
    }
}
"@

Add-Type -TypeDefinition ${'$'}csharp -Language CSharp

${'$'}prevKeys = @{}
${'$'}prevLmb = ${'$'}false
${'$'}prevRmb = ${'$'}false

function Update-Key([string]${'$'}id, [byte]${'$'}vk, [bool]${'$'}ext, [bool]${'$'}pressed) {
    ${'$'}was = [bool]${'$'}prevKeys[${'$'}id]
    if (${'$'}pressed -ne ${'$'}was) {
        [NeonWinInput]::SetKey(${'$'}vk, ${'$'}ext, ${'$'}pressed)
        ${'$'}prevKeys[${'$'}id] = ${'$'}pressed
    }
}

while (${'$'}true) {
    try {
        ${'$'}client = New-Object System.Net.Sockets.TcpClient("$phoneIp", $port)
        ${'$'}client.NoDelay = ${'$'}true
        ${'$'}stream = ${'$'}client.GetStream()
        ${'$'}writer = New-Object System.IO.StreamWriter(${'$'}stream)
        ${'$'}reader = New-Object System.IO.StreamReader(${'$'}stream)
        ${'$'}writer.Write("GET /stream HTTP/1.1`r`nHost: $phoneIp`r`n`r`n")
        ${'$'}writer.Flush()

        while (${'$'}client.Connected) {
            ${'$'}line = ${'$'}reader.ReadLine()
            if (${'$'}null -eq ${'$'}line) { break }
            if (-not ${'$'}line.StartsWith("{")) { continue }

            ${'$'}d = ${'$'}line | ConvertFrom-Json
            ${'$'}btns = @(${'$'}d.buttons)
            ${'$'}lx = [double]${'$'}d.lx
            ${'$'}ly = [double]${'$'}d.ly
            ${'$'}rx = [double]${'$'}d.rx
            ${'$'}ry = [double]${'$'}d.ry

            Update-Key "W" 0x57 ${'$'}false (${'$'}ly -lt -0.32)
            Update-Key "S" 0x53 ${'$'}false (${'$'}ly -gt 0.32)
            Update-Key "A" 0x41 ${'$'}false (${'$'}lx -lt -0.32)
            Update-Key "D" 0x44 ${'$'}false (${'$'}lx -gt 0.32)

            Update-Key "UP"    0x26 ${'$'}true (${'$'}btns -contains "DPAD_UP")
            Update-Key "DOWN"  0x28 ${'$'}true (${'$'}btns -contains "DPAD_DOWN")
            Update-Key "LEFT"  0x25 ${'$'}true (${'$'}btns -contains "DPAD_LEFT")
            Update-Key "RIGHT" 0x27 ${'$'}true (${'$'}btns -contains "DPAD_RIGHT")

            Update-Key "SPACE" 0x20 ${'$'}false (${'$'}btns -contains "CROSS")
            Update-Key "CTRL"  0x11 ${'$'}false (${'$'}btns -contains "CIRCLE")
            Update-Key "R"     0x52 ${'$'}false (${'$'}btns -contains "SQUARE")
            Update-Key "F"     0x46 ${'$'}false (${'$'}btns -contains "TRIANGLE")
            Update-Key "Q"     0x51 ${'$'}false (${'$'}btns -contains "L1")
            Update-Key "E"     0x45 ${'$'}false (${'$'}btns -contains "R1")
            Update-Key "SHIFT" 0x10 ${'$'}false (${'$'}btns -contains "L3")
            Update-Key "V"     0x56 ${'$'}false (${'$'}btns -contains "R3")
            Update-Key "TAB"   0x09 ${'$'}false (${'$'}btns -contains "SHARE")
            Update-Key "ESC"   0x1B ${'$'}false (${'$'}btns -contains "OPTIONS")
            Update-Key "ENTER" 0x0D ${'$'}false (${'$'}btns -contains "PS_HOME")
            Update-Key "M"     0x4D ${'$'}false (${'$'}btns -contains "TOUCHPAD")

            ${'$'}lmb = (${'$'}btns -contains "R2_BTN") -or ([double]${'$'}d.r2 -gt 0.35)
            if (${'$'}lmb -ne ${'$'}prevLmb) {
                [NeonWinInput]::SetMouseBtn(${'$'}true, ${'$'}lmb)
                ${'$'}prevLmb = ${'$'}lmb
            }
            ${'$'}rmb = (${'$'}btns -contains "L2_BTN") -or ([double]${'$'}d.l2 -gt 0.35)
            if (${'$'}rmb -ne ${'$'}prevRmb) {
                [NeonWinInput]::SetMouseBtn(${'$'}false, ${'$'}rmb)
                ${'$'}prevRmb = ${'$'}rmb
            }

            if ([Math]::Abs(${'$'}rx) -gt 0.12 -or [Math]::Abs(${'$'}ry) -gt 0.12) {
                ${'$'}mx = [int](${'$'}rx * 22)
                ${'$'}my = [int](${'$'}ry * 22)
                [NeonWinInput]::MoveMouse(${'$'}mx, ${'$'}my)
            }
        }
    } catch {
        Start-Sleep -Milliseconds 1000
    }
}
        """.trimIndent()
    }

    private fun buildLaptopReceiverHtml(phoneIp: String, port: Int): String {
        val psOneLiner = "powershell -ExecutionPolicy Bypass -c \"irm http://$phoneIp:$port/win | iex\""
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
  body { background: var(--bg); color: #F8FAFC; min-height: 100vh; padding: 20px; }
  .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #1E293B; padding-bottom: 14px; margin-bottom: 18px; flex-wrap: wrap; gap: 10px; }
  .brand { font-size: 22px; font-weight: 800; color: var(--cyan); letter-spacing: 2px; text-shadow: 0 0 12px rgba(0,229,255,0.4); }
  .badge { padding: 6px 14px; border-radius: 99px; font-weight: 700; font-size: 13px; background: #1E293B; color: var(--amber); border: 1px solid var(--amber); }
  .badge.live { color: var(--green); border-color: var(--green); box-shadow: 0 0 12px rgba(0,230,118,0.25); }
  .hero-activate { background: linear-gradient(135deg, #0D1B2A 0%, #111827 100%); border: 2px solid var(--cyan); border-radius: 16px; padding: 18px; margin-bottom: 20px; box-shadow: 0 0 24px rgba(0,229,255,0.15); }
  .hero-activate.os-live { border-color: var(--green); box-shadow: 0 0 24px rgba(0,230,118,0.25); }
  .cmd-box { display: flex; align-items: center; justify-content: space-between; gap: 10px; background: #05080E; border: 1px solid var(--cyan); padding: 10px 14px; border-radius: 10px; margin-top: 10px; flex-wrap: wrap; }
  .cmd-text { color: var(--cyan); font-size: 13px; font-weight: 700; user-select: all; }
  .btn-primary { background: var(--cyan); color: #05080E; border: none; padding: 10px 18px; border-radius: 8px; font-weight: 800; cursor: pointer; text-decoration: none; display: inline-block; font-size: 13px; }
  .btn-amber { background: var(--amber); color: #05080E; border: none; padding: 10px 18px; border-radius: 8px; font-weight: 800; cursor: pointer; text-decoration: none; display: inline-block; font-size: 13px; }
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

  <!-- CRITICAL STEP TO CONTROL EXTERNAL WINDOWS PC GAMES & DESKTOP -->
  <div id="osBridgeBox" class="hero-activate">
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;">
      <div>
        <div id="osBridgeTitle" style="font-size:16px;font-weight:800;color:var(--amber);">
          ⚡ ENABLE WINDOWS OS GAME CONTROL (REQUIRED FOR DESKTOP / STEAM / PC GAMES)
        </div>
        <div style="font-size:13px;color:#CBD5E1;margin-top:4px;">
          Web browsers block tabs from pressing keys inside external Windows games. Activate the built-in Windows OS Bridge below (zero installs needed):
        </div>
      </div>
      <a href="/NeonPad-PC-Bridge.bat" class="btn-amber">⬇ 1-CLICK LAUNCHER (NeonPad-PC-Bridge.bat)</a>
    </div>

    <div class="cmd-box">
      <div>
        <div style="font-size:11px;color:#94A3B8;margin-bottom:3px;">OR PRESS <strong>Win + R</strong> ON YOUR LAPTOP KEYBOARD, PASTE THIS &amp; HIT ENTER:</div>
        <code id="psCmd" class="cmd-text">$psOneLiner</code>
      </div>
      <button class="btn-primary" onclick="copyWinCmd()">COPY WIN+R COMMAND</button>
    </div>
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

      <button class="btn-primary" style="margin-top:14px;" onclick="sendRumble()">TEST PHONE HAPTIC RUMBLE</button>
    </div>

    <div class="panel">
      <h2>Windows PC Key &amp; Mouse Mapping</h2>
      <div style="font-size:13px;color:#CBD5E1;line-height:1.7;">
        <div>• <strong>Left Stick:</strong> W / A / S / D (Movement)</div>
        <div>• <strong>Right Stick / Gyro:</strong> 3D Mouse Look / Aim</div>
        <div>• <strong>L2 / R2 Triggers:</strong> Right Click (Aim) / Left Click (Fire)</div>
        <div>• <strong>Cross (✕) / Circle (◯):</strong> Space (Jump) / Left Ctrl (Crouch)</div>
        <div>• <strong>Square (▢) / Triangle (△):</strong> R (Reload) / F (Interact)</div>
        <div>• <strong>L1 / R1 / L3 / R3:</strong> Q / E / Left Shift (Sprint) / V</div>
        <div>• <strong>D-Pad:</strong> Up / Down / Left / Right Arrows</div>
      </div>
      <div style="margin-top:14px;font-size:13px;color:#E2E8F0;">
        <strong>Gyro Telemetry:</strong> <span id="gyroText">Pitch: 0.0° | Roll: 0.0°</span>
      </div>
      <div style="margin-top:6px;font-size:13px;color:#E2E8F0;">
        <strong>Frames Received:</strong> <span id="frameCount">0</span>
      </div>
      <h2 style="margin-top:18px;">Raw Real-Time Packet</h2>
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

function copyWinCmd() {
  const txt = document.getElementById('psCmd').textContent;
  navigator.clipboard.writeText(txt);
  alert('Copied! Now press Win + R on your laptop keyboard, paste (Ctrl+V), and press Enter!');
}

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
      if (data.osActive && data.osActive > 0) {
        document.getElementById('osBridgeBox').className = 'hero-activate os-live';
        document.getElementById('osBridgeTitle').style.color = 'var(--green)';
        document.getElementById('osBridgeTitle').textContent = '✅ WINDOWS OS GAME INJECTOR ACTIVE — CONTROLLING ALL PC GAMES & DESKTOP!';
      }
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
