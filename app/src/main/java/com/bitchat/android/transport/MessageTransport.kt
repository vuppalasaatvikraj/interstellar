package com.bitchat.android.transport

import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.flow.StateFlow

enum class TransportMode {
    OFFLINE,
    BLE_MESH,
    NOSTR_ONLINE,
    HYBRID
}

data class ActivePeer(
    val peerId: String,
    val nickname: String,
    val isReachableViaBle: Boolean,
    val nostrPubkey: String? = null
)

interface MessageTransport {
    val publicMessages: StateFlow<List<BitchatMessage>>
    val privateMessages: StateFlow<Map<String, List<BitchatMessage>>>
    val activePeers: StateFlow<List<ActivePeer>>
    val transportMode: StateFlow<TransportMode>

    fun sendPublicMessage(content: String, channel: String? = null)
    fun sendPrivateMessage(content: String, recipientPeerId: String, recipientNickname: String)
    fun sendReadReceipt(messageId: String, recipientPeerId: String)
    fun triggerPanicWipe()
}