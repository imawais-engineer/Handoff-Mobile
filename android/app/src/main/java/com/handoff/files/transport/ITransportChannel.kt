package com.handoff.files.transport

import com.handoff.files.protocol.HandoffPacket
import java.io.Closeable

interface ITransportChannel : Closeable {
    val isConnected: Boolean
    val remoteAddress: String

    suspend fun sendPacket(packet: HandoffPacket)
    suspend fun receivePacket(): HandoffPacket
}
