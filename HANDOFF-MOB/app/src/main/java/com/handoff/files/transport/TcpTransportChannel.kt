package com.handoff.files.transport

import com.handoff.files.protocol.HandoffFramer
import com.handoff.files.protocol.HandoffPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket

class TcpTransportChannel(
    private val socket: Socket,
    socketTimeoutMs: Int = 20_000
) : ITransportChannel {

    init {
        socket.soTimeout = socketTimeoutMs
        socket.tcpNoDelay = true
    }

    private val inputStream = BufferedInputStream(socket.getInputStream())
    private val outputStream = BufferedOutputStream(socket.getOutputStream())

    override val isConnected: Boolean
        get() = socket.isConnected && !socket.isClosed && !socket.isInputShutdown && !socket.isOutputShutdown

    override val remoteAddress: String
        get() = socket.inetAddress?.hostAddress ?: "unknown"

    override suspend fun sendPacket(packet: HandoffPacket): Unit = withContext(Dispatchers.IO) {
        synchronized(outputStream) {
            HandoffFramer.writePacket(outputStream, packet)
        }
    }

    override suspend fun receivePacket(): HandoffPacket = withContext(Dispatchers.IO) {
        synchronized(inputStream) {
            HandoffFramer.readPacket(inputStream)
        }
    }

    override fun close() {
        try {
            inputStream.close()
        } catch (_: Exception) {}
        try {
            outputStream.close()
        } catch (_: Exception) {}
        try {
            socket.close()
        } catch (_: Exception) {}
    }
}
