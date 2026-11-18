package com.bitchat.android.transport

import com.bitchat.android.mesh.MeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessageTransportImpl(
    private val meshService: MeshService
) : MessageTransport {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val publicMessages: StateFlow<List<BitchatMessage>> =
        AppStateStore.publicMessages

    override val privateMessages: StateFlow<Map<String, List<BitchatMessage>>> =
    AppStateStore.privateMessages

    override val activePeers: StateFlow<List<ActivePeer>> =
        combine(
            AppStateStore.peers,
            AppStateStore.directPeers
        ) { allPeerIds, directPeerIds ->
            val nicknames = meshService.getPeerNicknames()
            allPeerIds
                .filter { it != meshService.myPeerID }
                .map { peerId ->
                    ActivePeer(
                        peerId = peerId,
                        nickname = nicknames[peerId] ?: peerId.take(8),
                        isReachableViaBle = peerId in directPeerIds || meshService.hasEstablishedSession(peerId)
                    )
                }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    override val transportMode: StateFlow<TransportMode> =
        AppStateStore.peers
            .map { peers ->
                val activeCount = peers.count { it != meshService.myPeerID }
                if (activeCount > 0) TransportMode.BLE_MESH else TransportMode.OFFLINE
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TransportMode.OFFLINE
            )

    override fun sendPublicMessage(content: String, channel: String?) {
        meshService.sendMessage(
            content = content,
            mentions = emptyList(),
            channel = channel
        )
    }

    override fun sendPrivateMessage(
        content: String,
        recipientPeerId: String,
        recipientNickname: String
    ) {
        meshService.sendPrivateMessage(
            content = content,
            recipientPeerID = recipientPeerId,
            recipientNickname = recipientNickname,
            messageID = null
        )
    }

    override fun sendReadReceipt(messageId: String, recipientPeerId: String) {
        val myNickname = AppStateStore.nickname.value.ifBlank { "Anonymous" }
        meshService.sendReadReceipt(
            messageID = messageId,
            recipientPeerID = recipientPeerId,
            readerNickname = myNickname
        )
        AppStateStore.markPrivateMessageRead(messageId)
    }

    override fun triggerPanicWipe() {
        scope.launch {
            AppStateStore.panicClearPrivateConversations()
            meshService.clearAllEncryptionData()
            meshService.clearAllInternalData()
            AppStateStore.clear()
        }
    }
}