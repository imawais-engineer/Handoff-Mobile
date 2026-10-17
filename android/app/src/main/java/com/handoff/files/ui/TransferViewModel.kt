package com.handoff.files.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handoff.files.domain.model.PairingInfo
import com.handoff.files.domain.model.TransferDirection
import com.handoff.files.domain.model.TransferFile
import com.handoff.files.domain.model.TransferHistoryItem
import com.handoff.files.domain.model.TransferProgress
import com.handoff.files.domain.model.TransferStatus
import com.handoff.files.protocol.HandoffPacket
import com.handoff.files.protocol.ProtocolConstants
import com.handoff.files.security.CryptoEngine
import com.handoff.files.security.RateLimiter
import com.handoff.files.security.SessionKeys
import com.handoff.files.transfer.FileStreamer
import com.handoff.files.transfer.StagingFileManager
import com.handoff.files.transport.NetworkResolver
import com.handoff.files.transport.TcpTransportChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCancellableCoroutine

import com.handoff.files.transport.WebTransferServer

data class WebTransferPrompt(
    val filename: String,
    val sizeBytes: Long,
    val mimeType: String
)

data class TransferUiState(
    val activeProgress: TransferProgress = TransferProgress(),
    val selectedFiles: List<TransferFile> = emptyList(),
    val historyItems: List<TransferHistoryItem> = emptyList(),
    val pairingInfo: PairingInfo? = null,
    val isReceiverListening: Boolean = false,
    val isWebTransferRunning: Boolean = false,
    val webTransferUrl: String? = null,
    val webTransferPrompt: WebTransferPrompt? = null,
    val localIp: String? = null,
    val incomingProposal: HandoffPacket.TransferProposal? = null,
    val errorMessage: String? = null
)

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val stagingManager = StagingFileManager(
        stagingDirectory = File(application.cacheDir, "staging"),
        destinationDirectory = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(application.filesDir, "received")
    )
    private val fileStreamer = FileStreamer(stagingManager)
    private val rateLimiter = RateLimiter()

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var activeTransferJob: Job? = null
    private var isCancellationRequested = false

    // Continuation for receiver approval dialog
    private var approvalContinuation: (suspendCancellableCoroutine<Boolean>.() -> Unit)? = null
    private var pendingApprovalContinuation: kotlin.coroutines.Continuation<Boolean>? = null

    init {
        refreshNetworkState()
    }

    fun refreshNetworkState() {
        val ip = NetworkResolver.getLocalIpAddress()
        _uiState.update { it.copy(localIp = ip) }
    }

    fun onFilesSelected(files: List<TransferFile>) {
        _uiState.update { it.copy(selectedFiles = files) }
    }

    fun startReceiveMode() {
        if (_uiState.value.isReceiverListening) return

        refreshNetworkState()
        val ip = _uiState.value.localIp ?: "127.0.0.1"

        serverJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(0) // Bind ephemeral port
                serverSocket = server
                val port = server.localPort

                val ephemeralToken = CryptoEngine.bytesToHex(CryptoEngine.generateRandomBytes(16))
                val sasPin = String.format("%06d", (100_000..999_999).random())
                val expiresAt = System.currentTimeMillis() + (ProtocolConstants.PAIRING_TICKET_TTL_SECONDS * 1000)

                val pairingInfo = PairingInfo(
                    hostIp = ip,
                    port = port,
                    ephemeralToken = ephemeralToken,
                    sasPinCode = sasPin,
                    expiresAtTimestamp = expiresAt
                )

                _uiState.update {
                    it.copy(
                        isReceiverListening = true,
                        pairingInfo = pairingInfo,
                        errorMessage = null
                    )
                }

                // Accept connection loop
                while (!server.isClosed) {
                    val socket = server.accept()
                    val remoteAddress = socket.inetAddress?.hostAddress ?: "unknown"

                    // Rate limit check
                    if (rateLimiter.isLockedOut(remoteAddress)) {
                        socket.close()
                        continue
                    }

                    handleIncomingConnection(socket, pairingInfo)
                }
            } catch (e: Exception) {
                if (serverSocket?.isClosed == false) {
                    _uiState.update { it.copy(errorMessage = "Receiver error: ${e.message}") }
                }
            } finally {
                _uiState.update { it.copy(isReceiverListening = false, pairingInfo = null) }
            }
        }
    }

    private suspend fun handleIncomingConnection(socket: Socket, pairingInfo: PairingInfo) {
        val channel = TcpTransportChannel(socket)
        val remoteIp = channel.remoteAddress
        val progressFlow = MutableStateFlow(TransferProgress())

        // Collect progress into UI
        val progressJob = viewModelScope.launch {
            progressFlow.collect { progress ->
                _uiState.update { it.copy(activeProgress = progress) }
            }
        }

        try {
            // 1. Perform Authenticated Key Exchange
            val sessionKeys = performServerHandshake(channel, pairingInfo, remoteIp)
            if (sessionKeys == null) {
                channel.close()
                return
            }

            // 2. Receive file stream with approval callback
            val publishedFile = fileStreamer.receiveFile(
                channel = channel,
                aesKey = sessionKeys.aesKey,
                onProposalReceived = { proposal ->
                    _uiState.update { it.copy(incomingProposal = proposal) }
                    suspendCancellableCoroutine { cont ->
                        pendingApprovalContinuation = cont
                    }
                },
                progressFlow = progressFlow,
                isCancelled = { isCancellationRequested }
            )

            if (publishedFile != null) {
                recordHistory(
                    filename = publishedFile.name,
                    sizeBytes = publishedFile.length(),
                    direction = TransferDirection.RECEIVE,
                    outcome = "COMPLETED",
                    durationMs = progressFlow.value.elapsedSeconds * 1000,
                    peer = remoteIp
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Receive failed: ${e.message}") }
        } finally {
            progressJob.cancel()
            channel.close()
            _uiState.update { it.copy(incomingProposal = null) }
        }
    }

    fun approveIncomingTransfer(approved: Boolean) {
        pendingApprovalContinuation?.resume(approved)
        pendingApprovalContinuation = null
        _uiState.update { it.copy(incomingProposal = null) }
    }

    fun stopReceiveMode() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        _uiState.update { it.copy(isReceiverListening = false, pairingInfo = null) }
    }

    private var webTransferServer: WebTransferServer? = null
    private var pendingWebApprovalContinuation: kotlin.coroutines.Continuation<Boolean>? = null

    fun startWebTransferMode() {
        if (_uiState.value.isWebTransferRunning) return
        refreshNetworkState()
        val ip = _uiState.value.localIp ?: "127.0.0.1"
        val port = 8080
        val token = CryptoEngine.bytesToHex(CryptoEngine.generateRandomBytes(16))
        val pin = String.format("%06d", (100_000..999_999).random())

        val server = WebTransferServer(
            stagingManager = stagingManager,
            onApprovalRequested = { fname, size, mime ->
                _uiState.update { it.copy(webTransferPrompt = WebTransferPrompt(fname, size, mime)) }
                suspendCancellableCoroutine { cont ->
                    pendingWebApprovalContinuation = cont
                }
            },
            onFileReceived = { file ->
                recordHistory(
                    filename = file.name,
                    sizeBytes = file.length(),
                    direction = TransferDirection.RECEIVE,
                    outcome = "COMPLETED",
                    durationMs = 0L,
                    peer = "Windows Web Browser"
                )
            }
        )

        val progressFlow = MutableStateFlow(TransferProgress())
        viewModelScope.launch {
            progressFlow.collect { progress ->
                _uiState.update { it.copy(activeProgress = progress) }
            }
        }

        server.start(port = port, pin = pin, token = token, progressFlow = progressFlow)
        webTransferServer = server

        val pairing = PairingInfo(
            hostIp = ip,
            port = port,
            ephemeralToken = token,
            sasPinCode = pin,
            expiresAtTimestamp = System.currentTimeMillis() + (ProtocolConstants.PAIRING_TICKET_TTL_SECONDS * 1000)
        )

        _uiState.update {
            it.copy(
                isWebTransferRunning = true,
                webTransferUrl = "http://$ip:$port",
                pairingInfo = pairing
            )
        }
    }

    fun approveWebTransfer(approved: Boolean) {
        pendingWebApprovalContinuation?.resume(approved)
        pendingWebApprovalContinuation = null
        _uiState.update { it.copy(webTransferPrompt = null) }
    }

    fun stopWebTransferMode() {
        webTransferServer?.stop()
        webTransferServer = null
        _uiState.update {
            it.copy(
                isWebTransferRunning = false,
                webTransferUrl = null,
                webTransferPrompt = null
            )
        }
    }

    fun startSend(host: String, port: Int, qrToken: String?, manualPin: String?) {
        isCancellationRequested = false
        val file = _uiState.value.selectedFiles.firstOrNull() ?: return

        activeTransferJob = viewModelScope.launch(Dispatchers.IO) {
            val progressFlow = MutableStateFlow(TransferProgress(status = TransferStatus.CONNECTING))
            val progressJob = viewModelScope.launch {
                progressFlow.collect { progress ->
                    _uiState.update { it.copy(activeProgress = progress) }
                }
            }

            var channel: TcpTransportChannel? = null
            try {
                val socket = Socket(host, port)
                channel = TcpTransportChannel(socket)

                // 1. Client Authenticated Key Exchange
                val sessionKeys = performClientHandshake(channel, qrToken, manualPin)
                if (sessionKeys == null) {
                    progressFlow.value = progressFlow.value.copy(
                        status = TransferStatus.FAILED,
                        errorMessage = "Pairing authentication failed"
                    )
                    return@launch
                }

                // 2. Stream file
                val success = fileStreamer.sendFile(
                    channel = channel,
                    transferId = UUID.randomUUID().toString(),
                    filename = file.name,
                    mimeType = file.mimeType,
                    fileSize = file.sizeBytes,
                    inputStreamProvider = {
                        // For demonstration and file provider URIs
                        if (file.uriString.startsWith("file://") || !file.uriString.startsWith("content://")) {
                            val path = file.uriString.removePrefix("file://")
                            FileInputStream(File(path))
                        } else {
                            val uri = android.net.Uri.parse(file.uriString)
                            getApplication<Application>().contentResolver.openInputStream(uri)
                                ?: throw IllegalStateException("Cannot open input stream for URI: $uri")
                        }
                    },
                    aesKey = sessionKeys.aesKey,
                    progressFlow = progressFlow,
                    isCancelled = { isCancellationRequested }
                )

                recordHistory(
                    filename = file.name,
                    sizeBytes = file.sizeBytes,
                    direction = TransferDirection.SEND,
                    outcome = if (success) "COMPLETED" else "FAILED",
                    durationMs = progressFlow.value.elapsedSeconds * 1000,
                    peer = host
                )

            } catch (e: Exception) {
                progressFlow.value = progressFlow.value.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = e.message ?: "Connection error"
                )
            } finally {
                channel?.close()
                progressJob.cancel()
            }
        }
    }

    private suspend fun performClientHandshake(
        channel: TcpTransportChannel,
        qrToken: String?,
        manualPin: String?
    ): SessionKeys? {
        val keyPair = CryptoEngine.generateEphemeralKeyPair()
        val clientNonce = CryptoEngine.generateNonce()

        // 1. Send Key Exchange
        channel.sendPacket(
            HandoffPacket.PairKeyExchange(
                ephemeralPublicKeyHex = CryptoEngine.bytesToHex(keyPair.public.encoded),
                clientNonceHex = CryptoEngine.bytesToHex(clientNonce),
                deviceName = android.os.Build.MODEL
            )
        )

        // 2. Receive Key Response
        val response = channel.receivePacket()
        if (response !is HandoffPacket.PairKeyResponse) return null

        val serverPubBytes = CryptoEngine.hexToBytes(response.ephemeralPublicKeyHex)
        val serverNonce = CryptoEngine.hexToBytes(response.serverNonceHex)

        val sharedSecret = CryptoEngine.computeSharedSecret(keyPair.private, serverPubBytes)
        val salt = clientNonce + serverNonce
        val sessionKeys = CryptoEngine.deriveSessionKeys(sharedSecret, clientNonce, serverNonce, salt)

        // 3. Verify SAS or QR Token
        val proof = CryptoEngine.bytesToHex(CryptoEngine.hmacSha256(sessionKeys.authKey, clientNonce))
        val qrProof = qrToken?.let {
            CryptoEngine.bytesToHex(CryptoEngine.hmacSha256(sessionKeys.authKey, it.toByteArray(Charsets.UTF_8)))
        }

        channel.sendPacket(HandoffPacket.PairVerify(sasProofHex = proof, qrTokenProofHex = qrProof))

        // 4. Receive Ack
        val ack = channel.receivePacket()
        return if (ack is HandoffPacket.PairAck && ack.isSuccess) sessionKeys else null
    }

    private suspend fun performServerHandshake(
        channel: TcpTransportChannel,
        pairingInfo: PairingInfo,
        remoteIp: String
    ): SessionKeys? {
        val req = channel.receivePacket()
        if (req !is HandoffPacket.PairKeyExchange) {
            rateLimiter.recordFailure(remoteIp)
            return null
        }

        val clientPubBytes = CryptoEngine.hexToBytes(req.ephemeralPublicKeyHex)
        val clientNonce = CryptoEngine.hexToBytes(req.clientNonceHex)

        val keyPair = CryptoEngine.generateEphemeralKeyPair()
        val serverNonce = CryptoEngine.generateNonce()

        val sharedSecret = CryptoEngine.computeSharedSecret(keyPair.private, clientPubBytes)
        val salt = clientNonce + serverNonce
        val sessionKeys = CryptoEngine.deriveSessionKeys(sharedSecret, clientNonce, serverNonce, salt)

        val sasCommitment = CryptoEngine.bytesToHex(
            CryptoEngine.hmacSha256(sessionKeys.authKey, sessionKeys.sasPinCode.toByteArray(Charsets.UTF_8))
        )

        channel.sendPacket(
            HandoffPacket.PairKeyResponse(
                ephemeralPublicKeyHex = CryptoEngine.bytesToHex(keyPair.public.encoded),
                serverNonceHex = CryptoEngine.bytesToHex(serverNonce),
                sasCommitmentHex = sasCommitment
            )
        )

        val verifyPacket = channel.receivePacket()
        if (verifyPacket !is HandoffPacket.PairVerify) {
            rateLimiter.recordFailure(remoteIp)
            channel.sendPacket(HandoffPacket.PairAck(status = "AUTH_FAILED", message = "Invalid verification packet"))
            return null
        }

        // Verify either QR token match or client SAS proof
        val expectedClientProof = CryptoEngine.bytesToHex(CryptoEngine.hmacSha256(sessionKeys.authKey, clientNonce))
        val sasProofMatches = verifyPacket.sasProofHex.equals(expectedClientProof, ignoreCase = true)

        val qrProofMatches = verifyPacket.qrTokenProofHex?.let { clientQrProof ->
            val expectedQrProof = CryptoEngine.bytesToHex(
                CryptoEngine.hmacSha256(sessionKeys.authKey, pairingInfo.ephemeralToken.toByteArray(Charsets.UTF_8))
            )
            clientQrProof.equals(expectedQrProof, ignoreCase = true)
        } ?: false

        val isAuthenticated = sasProofMatches || qrProofMatches

        if (!isAuthenticated) {
            val locked = rateLimiter.recordFailure(remoteIp)
            val status = if (locked) "RATE_LIMITED" else "AUTH_FAILED"
            channel.sendPacket(HandoffPacket.PairAck(status = status, message = "Pairing authentication failed"))
            return null
        }

        rateLimiter.recordSuccess(remoteIp)
        channel.sendPacket(HandoffPacket.PairAck(status = "OK"))
        return sessionKeys
    }

    fun cancelTransfer() {
        isCancellationRequested = true
        activeTransferJob?.cancel()
        _uiState.update {
            it.copy(
                activeProgress = it.activeProgress.copy(
                    status = TransferStatus.CANCELLED,
                    errorMessage = "Transfer cancelled"
                )
            )
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(historyItems = emptyList()) }
    }

    private fun recordHistory(
        filename: String,
        sizeBytes: Long,
        direction: TransferDirection,
        outcome: String,
        durationMs: Long,
        peer: String
    ) {
        val item = TransferHistoryItem(
            id = UUID.randomUUID().toString(),
            filename = filename,
            sizeBytes = sizeBytes,
            direction = direction,
            timestamp = System.currentTimeMillis(),
            outcome = outcome,
            durationMs = durationMs,
            peerDevice = peer
        )
        _uiState.update { it.copy(historyItems = listOf(item) + it.historyItems) }
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiveMode()
    }
}
