package com.maik205.shoumeiplayer.remote

import android.os.Build
import android.util.Log
import com.maik205.shoumeiplayer.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded WebSocket server on the Android TV allowing companion devices to connect,
 * pair via ECDH + AES-GCM, control navigation and playback, and receive Now Playing state.
 */
class RemoteServer(
    private val scope: CoroutineScope,
    private val coordinator: RemoteCommandCoordinator,
    private val sessionStore: SessionStore,
    private val pairedStore: PairedRemoteStore = PairedRemoteStore(),
    private val preferredPort: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 8097
        private const val TAG = "RemoteServer"
        private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val activeClients = ConcurrentHashMap.newKeySet<ClientConnection>()

    @Volatile
    var boundPort: Int = 0
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false && serverJob?.isActive == true

    fun start() {
        if (isRunning) return

        pairedStore.purgeExpired()

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(preferredPort).also {
                    it.reuseAddress = true
                }
                serverSocket = ss
                boundPort = ss.localPort
                Log.i(TAG, "Shoumei Remote Server listening on port $boundPort")

                while (isActive && !ss.isClosed) {
                    try {
                        val socket = ss.accept()
                        launch(Dispatchers.IO) {
                            handleNewConnection(socket)
                        }
                    } catch (e: SocketException) {
                        if (!ss.isClosed) Log.w(TAG, "Accept error: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server on port $preferredPort", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        activeClients.forEach { it.close() }
        activeClients.clear()
        serverJob?.cancel()
        serverJob = null
        boundPort = 0
    }

    private suspend fun handleNewConnection(socket: Socket) {
        val client = ClientConnection(socket, coordinator, sessionStore)
        activeClients.add(client)
        try {
            client.run()
        } finally {
            activeClients.remove(client)
            client.close()
        }
    }

    private inner class ClientConnection(
        private val socket: Socket,
        private val coordinator: RemoteCommandCoordinator,
        private val sessionStore: SessionStore,
    ) {
        private val clientId = "${socket.inetAddress?.hostAddress ?: "unknown"}:${socket.port}"
        private var clientDeviceName: String = "Mobile Device"
        private val input = BufferedInputStream(socket.getInputStream())
        private val output = BufferedOutputStream(socket.getOutputStream())
        private var isWebSocket = false
        private var authenticated = false
        private var derivedAesKey: ByteArray? = null
        private var activePairingToken: String? = null
        private var activeExpiresAtMs: Long? = null

        suspend fun run() = withContext(Dispatchers.IO) {
            // Perform HTTP Upgrade if client is WebSocket
            if (!performHandshake()) {
                socket.close()
                return@withContext
            }

            // Start pushing Now Playing updates to this client
            val stateJob = launch {
                coordinator.nowPlaying.collectLatest { state ->
                    val msg = RemoteMessage.NowPlaying(state)
                    sendJson(msg)
                }
            }

            val inputFocusJob = launch {
                coordinator.inputFocusState.collectLatest { focusState ->
                    if (authenticated) {
                        val msg = RemoteMessage.InputFocusState(
                            focused = focusState.isFocused,
                            text = focusState.text,
                            hint = focusState.fieldHint,
                        )
                        sendJson(msg)
                    }
                }
            }

            val sessionJob = launch {
                sessionStore.session.collectLatest {
                    val key = derivedAesKey
                    val token = activePairingToken
                    val expires = activeExpiresAtMs
                    if (authenticated && key != null && token != null && expires != null) {
                        sendEncryptedCredentials(key, token, expires)
                    }
                }
            }

            try {
                while (isActive && !socket.isClosed) {
                    val text = readFrame() ?: break
                    handleClientMessage(text)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client connection ended: ${e.message}")
            } finally {
                stateJob.cancel()
                sessionJob.cancel()
                inputFocusJob.cancel()
                if (authenticated) {
                    coordinator.onClientDisconnected(clientId)
                }
            }
        }

        private fun performHandshake(): Boolean {
            val lines = mutableListOf<String>()
            val lineReader = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1) return false
                val c = b.toChar()
                if (c == '\n') {
                    val line = lineReader.toString().trim()
                    if (line.isEmpty()) break
                    lines.add(line)
                    lineReader.clear()
                } else if (c != '\r') {
                    lineReader.append(c)
                }
            }

            val webSocketKey = lines.firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()

            if (webSocketKey != null) {
                isWebSocket = true
                val acceptHash = MessageDigest.getInstance("SHA-1")
                    .digest((webSocketKey + WEBSOCKET_GUID).toByteArray(Charsets.UTF_8))
                val accept = Base64.getEncoder().encodeToString(acceptHash)

                val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n"
                output.write(response.toByteArray(Charsets.UTF_8))
                output.flush()
                return true
            }

            // Plain TCP line-delimited client is also accepted
            isWebSocket = false
            return true
        }

        private suspend fun handleClientMessage(json: String) {
            try {
                val message = RemoteJson.decodeFromString<RemoteMessage>(json)
                when (message) {
                    is RemoteMessage.HandshakeInit -> handleHandshakeInit(message)
                    is RemoteMessage.PairConfirm -> handlePairConfirm(message)
                    is RemoteMessage.KeyCommand -> {
                        if (authenticated) coordinator.dispatchKey(message.key)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.TextInput -> {
                        if (authenticated) coordinator.dispatchTextInput(message.text)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.SetText -> {
                        if (authenticated) coordinator.setText(message.text)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.PlaybackCommand -> {
                        if (authenticated) coordinator.handlePlaybackCommand(message.action, message.positionMs, message.deltaMs)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.PlayItem -> {
                        if (authenticated) coordinator.requestPlayItem(message)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.OpenItem -> {
                        if (authenticated) coordinator.requestOpenItem(message)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.OpenLibrary -> {
                        if (authenticated) coordinator.requestOpenLibrary(message)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.SelectTrack -> {
                        if (authenticated) coordinator.selectTrack(message.trackId, message.type)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.SetQuality -> {
                        if (authenticated) coordinator.setQuality(message.quality)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    is RemoteMessage.SetVolume -> {
                        if (authenticated) coordinator.setSystemVolume(message.volume)
                        else sendJson(RemoteMessage.StatusAck(success = false, message = "Unauthenticated"))
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse client message: $json", e)
            }
        }

        private suspend fun handleHandshakeInit(init: RemoteMessage.HandshakeInit) {
            try {
                clientDeviceName = init.clientDeviceName.ifBlank { "Mobile Device" }
                val clientPublicBytes = Base64.getDecoder().decode(init.clientPublicKeyBase64)
                val clientPublicKey = RemoteCrypto.decodePublicKey(clientPublicBytes)

                val serverKeyPair = RemoteCrypto.generateKeyPair()
                val sharedSecret = RemoteCrypto.deriveSharedSecret(serverKeyPair, clientPublicKey)

                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val sessionKeys = RemoteCrypto.deriveKeysAndPin(sharedSecret, salt)
                this.derivedAesKey = sessionKeys.aesKey

                // Check for persisted handshake / pairing token
                val clientToken = init.pairingToken
                if (!clientToken.isNullOrBlank()) {
                    val existing = pairedStore.findClient(clientToken)
                    val now = System.currentTimeMillis()
                    if (existing != null && now >= existing.expiresAtMs) {
                        // Handshake has expired after 7 days!
                        Log.i(TAG, "Pairing token expired for client $clientId (${existing.deviceName}). Re-pairing required.")
                        pairedStore.removeClient(clientToken)
                        val activePin = coordinator.getOrCreatePairingPin()
                        coordinator.showPairingCard()
                        val challenge = RemoteMessage.HandshakeChallenge(
                            serverPublicKeyBase64 = Base64.getEncoder().encodeToString(serverKeyPair.public.encoded),
                            saltBase64 = Base64.getEncoder().encodeToString(salt),
                            pin = activePin,
                            pairingValid = false,
                            pairingExpired = true,
                        )
                        sendJson(challenge)
                        return
                    } else if (existing != null) {
                        // Valid unexpired handshake! Auto-authenticate without PIN
                        Log.i(TAG, "Reconnecting with valid pairing token for client $clientId (${existing.deviceName})")
                        authenticated = true
                        activePairingToken = existing.pairingToken
                        activeExpiresAtMs = existing.expiresAtMs
                        coordinator.onClientConnected(
                            id = clientId,
                            deviceName = clientDeviceName.ifBlank { existing.deviceName },
                            ipAddress = socket.inetAddress?.hostAddress ?: "Unknown",
                        )
                        val challenge = RemoteMessage.HandshakeChallenge(
                            serverPublicKeyBase64 = Base64.getEncoder().encodeToString(serverKeyPair.public.encoded),
                            saltBase64 = Base64.getEncoder().encodeToString(salt),
                            pin = "",
                            pairingValid = true,
                            pairingExpired = false,
                        )
                        sendJson(challenge)
                        sendEncryptedCredentials(sessionKeys.aesKey, existing.pairingToken, existing.expiresAtMs)
                        return
                    } else {
                        // Token unknown or previously revoked
                        Log.i(TAG, "Unknown pairing token for client $clientId. Re-pairing required.")
                        val activePin = coordinator.getOrCreatePairingPin()
                        coordinator.showPairingCard()
                        val challenge = RemoteMessage.HandshakeChallenge(
                            serverPublicKeyBase64 = Base64.getEncoder().encodeToString(serverKeyPair.public.encoded),
                            saltBase64 = Base64.getEncoder().encodeToString(salt),
                            pin = activePin,
                            pairingValid = false,
                            pairingExpired = true,
                        )
                        sendJson(challenge)
                        return
                    }
                }

                // First-time pairing (or pairing with QR code PIN)
                val activePin = if (!init.pairingPin.isNullOrBlank()) {
                    coordinator.pairingPin.value ?: sessionKeys.pin
                } else {
                    sessionKeys.pin
                }
                coordinator.setPairingPin(activePin)
                val isQrMatch = !init.pairingPin.isNullOrBlank() && init.pairingPin == activePin

                val challenge = RemoteMessage.HandshakeChallenge(
                    serverPublicKeyBase64 = Base64.getEncoder().encodeToString(serverKeyPair.public.encoded),
                    saltBase64 = Base64.getEncoder().encodeToString(salt),
                    pin = activePin,
                    pairingValid = false,
                    pairingExpired = false,
                )
                sendJson(challenge)

                if (isQrMatch) {
                    authenticated = true
                    coordinator.onClientConnected(
                        id = clientId,
                        deviceName = clientDeviceName,
                        ipAddress = socket.inetAddress?.hostAddress ?: "Unknown",
                    )
                    val pairing = pairedStore.createPairing(clientId, clientDeviceName)
                    activePairingToken = pairing.pairingToken
                    activeExpiresAtMs = pairing.expiresAtMs
                    sendEncryptedCredentials(sessionKeys.aesKey, pairing.pairingToken, pairing.expiresAtMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in handshake init", e)
            }
        }

        private suspend fun handlePairConfirm(confirm: RemoteMessage.PairConfirm) {
            val aesKey = derivedAesKey ?: return
            val activePin = coordinator.pairingPin.value

            if (activePin == null || confirm.pin != activePin) {
                sendJson(RemoteMessage.StatusAck(success = false, message = "Invalid PIN"))
                return
            }

            authenticated = true
            coordinator.onClientConnected(
                id = clientId,
                deviceName = clientDeviceName,
                ipAddress = socket.inetAddress?.hostAddress ?: "Unknown",
            )
            val pairing = pairedStore.createPairing(clientId, clientDeviceName)
            activePairingToken = pairing.pairingToken
            activeExpiresAtMs = pairing.expiresAtMs
            sendEncryptedCredentials(aesKey, pairing.pairingToken, pairing.expiresAtMs)
        }

        private suspend fun sendEncryptedCredentials(
            aesKey: ByteArray,
            pairingToken: String,
            expiresAtMs: Long,
        ) {
            val session = sessionStore.session.firstOrNull()
            val serverUrl = sessionStore.serverUrl.firstOrNull().orEmpty()

            val credentialsPayload = RemoteSessionData(
                tvName = "Shoumei TV (${Build.MODEL})",
                serverUrl = serverUrl,
                accessToken = session?.accessToken.orEmpty(),
                userId = session?.userId.orEmpty(),
                userName = session?.userName.orEmpty(),
                pairingToken = pairingToken,
                expiresAtMs = expiresAtMs,
            )
            val plainBytes = RemoteJson.encodeToString(RemoteSessionData.serializer(), credentialsPayload)
                .toByteArray(Charsets.UTF_8)
            val encrypted = RemoteCrypto.encrypt(plainBytes, aesKey)

            sendJson(
                RemoteMessage.EncryptedCredentials(
                    ivBase64 = encrypted.ivBase64(),
                    cipherTextBase64 = encrypted.cipherTextBase64(),
                )
            )
        }

        private fun sendJson(message: RemoteMessage) {
            try {
                val json = RemoteJson.encodeToString(RemoteMessage.serializer(), message)
                if (isWebSocket) {
                    sendWebSocketText(json)
                } else {
                    output.write((json + "\n").toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error sending to client: ${e.message}")
            }
        }

        private fun sendWebSocketText(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            output.write(0x81) // FIN + Text frame
            when {
                bytes.size <= 125 -> {
                    output.write(bytes.size)
                }
                bytes.size <= 65535 -> {
                    output.write(126)
                    output.write((bytes.size shr 8) and 0xFF)
                    output.write(bytes.size and 0xFF)
                }
                else -> {
                    output.write(127)
                    for (i in 7 downTo 0) {
                        output.write(((bytes.size.toLong() shr (i * 8)) and 0xFF).toInt())
                    }
                }
            }
            output.write(bytes)
            output.flush()
        }

        private fun readFrame(): String? {
            if (!isWebSocket) {
                // Line-delimited JSON
                val sb = StringBuilder()
                while (true) {
                    val b = input.read()
                    if (b == -1) return if (sb.isNotEmpty()) sb.toString() else null
                    val c = b.toChar()
                    if (c == '\n') return sb.toString().trim()
                    if (c != '\r') sb.append(c)
                }
            }

            // WebSocket Frame Reader
            val b1 = input.read()
            if (b1 == -1) return null
            val opcode = b1 and 0x0F
            if (opcode == 0x08) return null // Close frame

            val b2 = input.read()
            if (b2 == -1) return null
            val isMasked = (b2 and 0x80) != 0
            var payloadLen = (b2 and 0x7F).toLong()

            if (payloadLen == 126L) {
                val byte1 = input.read()
                val byte2 = input.read()
                if (byte1 == -1 || byte2 == -1) return null
                payloadLen = ((byte1 and 0xFF) shl 8 or (byte2 and 0xFF)).toLong()
            } else if (payloadLen == 127L) {
                var len = 0L
                for (i in 0 until 8) {
                    val b = input.read()
                    if (b == -1) return null
                    len = (len shl 8) or (b and 0xFF).toLong()
                }
                payloadLen = len
            }

            val mask = ByteArray(4)
            if (isMasked) {
                if (input.read(mask) != 4) return null
            }

            val buffer = ByteArray(payloadLen.toInt())
            var bytesRead = 0
            while (bytesRead < payloadLen) {
                val read = input.read(buffer, bytesRead, (payloadLen - bytesRead).toInt())
                if (read == -1) return null
                bytesRead += read
            }

            if (isMasked) {
                for (i in buffer.indices) {
                    buffer[i] = (buffer[i].toInt() xor mask[i % 4].toInt()).toByte()
                }
            }

            return String(buffer, Charsets.UTF_8)
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
