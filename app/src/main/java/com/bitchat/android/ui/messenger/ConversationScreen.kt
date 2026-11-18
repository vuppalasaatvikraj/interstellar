package com.bitchat.android.ui.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Locale

data class SaturnMascotSticker(
    val id: String,
    val label: String,
    val tag: String,
    val bgColor: Color
)

private val SaturnStickerPack = listOf(
    SaturnMascotSticker("happy", "Happy Wave", "[sticker:saturn_happy]", SaturnYellow),
    SaturnMascotSticker("love", "Big Heart", "[sticker:saturn_love]", SaturnPeach),
    SaturnMascotSticker("excited", "Yay!", "[sticker:saturn_excited]", SaturnSkyBlue),
    SaturnMascotSticker("sleepy", "Sleepy Zzz", "[sticker:saturn_sleepy]", SaturnLavender),
    SaturnMascotSticker("cheer", "You've got this!", "[sticker:saturn_cheer]", SaturnSage),
    SaturnMascotSticker("shy", "Blushing", "[sticker:saturn_shy]", SaturnSurfaceSecondary),
    SaturnMascotSticker("laptop", "Busy", "[sticker:saturn_laptop]", SaturnSkyBlue),
    SaturnMascotSticker("reading", "Cozy", "[sticker:saturn_reading]", SaturnYellow)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    threadId: String,
    threadTitle: String,
    isGroup: Boolean,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onPickImage: () -> Unit = {},
    onToggleVoiceNote: () -> Unit = {}
) {
    val privateChats by chatViewModel.privateChats.collectAsState()
    val channelMessages by chatViewModel.channelMessages.collectAsState()
    val connectedPeers by chatViewModel.connectedPeers.collectAsState()
    val displayNames by AppStateStore.privateConversationDisplayNames.collectAsState()
    val myPeerId = chatViewModel.myPeerID

    val currentTitle = if (!isGroup) {
        displayNames[threadId] ?: threadTitle
    } else {
        threadTitle
    }

    val rawMessages = if (isGroup) {
        channelMessages[threadId].orEmpty()
    } else {
        privateChats[threadId].orEmpty()
    }
    val isOnline = !isGroup && (threadId in connectedPeers)

    // Local soft-deleted message IDs ("Delete for me") & revoked IDs ("Delete for everyone")
    val deletedForMeIds = remember(threadId) { mutableStateListOf<String>() }
    val deletedForEveryoneIds = remember(threadId) { mutableStateListOf<String>() }

    val visibleMessages = remember(rawMessages, deletedForMeIds.size, deletedForEveryoneIds.size) {
        rawMessages.filter { it.id !in deletedForMeIds }
    }

    var draftText by remember(threadId) {
        mutableStateOf(if (isGroup) "" else chatViewModel.conversationDraft(threadId))
    }
    var showStickerTray by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteMoonConfirm by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<BitchatMessage?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(threadId, isGroup) {
        if (isGroup) {
            chatViewModel.switchToChannel(threadId)
        } else {
            chatViewModel.startPrivateChat(threadId)
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    val handleExit = {
        if (!isGroup) {
            chatViewModel.setConversationDraft(threadId, draftText)
            chatViewModel.endPrivateChat()
        }
        onBack()
    }

    BackHandler { handleExit() }

    Scaffold(
        containerColor = SaturnPaper,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SaturnPaper),
                navigationIcon = {
                    IconButton(onClick = handleExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SaturnInk)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isGroup) SaturnSkyBlue else SaturnYellow)
                                .border(1.5.dp, SaturnInk, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentTitle.trim().take(1).uppercase().ifBlank { "M" },
                                fontWeight = FontWeight.ExtraBold,
                                color = SaturnInk
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentTitle,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = SaturnInk
                            )
                            Text(
                                text = when {
                                    isGroup -> "Group orbit"
                                    isOnline -> "Nearby now"
                                    else -> "Moon"
                                },
                                fontSize = 12.sp,
                                color = SaturnSecondaryText
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options", tint = SaturnInk)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = SaturnSurface
                        ) {
                            if (!isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Rename Moon", color = SaturnInk) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SaturnInk) },
                                    onClick = {
                                        menuExpanded = false
                                        showRenameDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete chat history", color = SaturnInk) },
                                leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = SaturnInk) },
                                onClick = {
                                    menuExpanded = false
                                    showClearHistoryConfirm = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isGroup) "Delete group" else "Delete Moon",
                                        color = SaturnError,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SaturnError) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteMoonConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Expandable 8-Pose Saturn Mascot Sticker Tray
                if (showStickerTray) {
                    Surface(
                        color = SaturnSurface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        border = BorderStroke(1.5.dp, SaturnInk),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Saturn Stickers",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = SaturnInk
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SaturnStickerPack) { sticker ->
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(sticker.bgColor)
                                            .border(1.5.dp, SaturnInk, RoundedCornerShape(16.dp))
                                            .clickable {
                                                chatViewModel.sendMessage(sticker.tag) { }
                                                showStickerTray = false
                                            }
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Swap with Image(painterResource(R.drawable.mascot_...), ...) once PNGs are added
                                        Text("★", fontSize = 22.sp, color = SaturnInk)
                                        Text(
                                            text = sticker.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SaturnInk,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Composer: [Sticker Button on LEFT] + ["Saturn it" Pill with Camera & Mic] + [Send]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left-side Saturn Mascot Sticker Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (showStickerTray) SaturnYellow else SaturnSurface)
                            .border(1.5.dp, SaturnInk, CircleShape)
                            .clickable { showStickerTray = !showStickerTray },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Saturn Stickers",
                            tint = SaturnInk,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = draftText,
                        onValueChange = {
                            draftText = it
                            if (!isGroup) chatViewModel.setConversationDraft(threadId, it)
                        },
                        placeholder = {
                            SaturnTypingText(
                                fullText = "Saturn it",
                                color = SaturnSecondaryText,
                                fontSize = 15.sp,
                                charDelayMs = 80L,
                                repeatLoop = true
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                IconButton(onClick = onPickImage, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Camera",
                                        tint = SaturnInk,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(onClick = onToggleVoiceNote, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Mic",
                                        tint = SaturnInk,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SaturnSurface,
                            unfocusedContainerColor = SaturnSurface,
                            focusedBorderColor = SaturnInk,
                            unfocusedBorderColor = SaturnInk,
                            focusedTextColor = SaturnInk,
                            unfocusedTextColor = SaturnInk,
                            cursorColor = SaturnInk
                        ),
                        maxLines = 4,
                        modifier = Modifier.weight(1f)
                    )

                    if (draftText.trim().isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SaturnPeach)
                                .border(1.5.dp, SaturnInk, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    val trimmed = draftText.trim()
                                    chatViewModel.sendMessage(trimmed) { accepted ->
                                        if (accepted) {
                                            draftText = ""
                                            if (!isGroup) chatViewModel.setConversationDraft(threadId, "")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SaturnInk)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // MagicUI Meteors + Brand Board Doodles in Saturn palette
            SaturnMeteorsAndDoodlesBackground(modifier = Modifier.fillMaxSize())

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(visibleMessages, key = { it.id }) { message ->
                    val isMine = message.senderPeerID == myPeerId
                    val isRevoked = message.id in deletedForEveryoneIds ||
                        message.content.startsWith("[revoked:")
                    SaturnBubble(
                        message = message,
                        isMine = isMine,
                        isRevoked = isRevoked,
                        showSender = isGroup && !isMine,
                        onLongClick = { selectedMessageForAction = message }
                    )
                }
            }
        }
    }

    // Long-Press Message Dialog: Delete for me / Delete for everyone / Cancel
    selectedMessageForAction?.let { targetMsg ->
        val isMine = targetMsg.senderPeerID == myPeerId
        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            containerColor = SaturnSurface,
            title = {
                Text("Message Options", fontWeight = FontWeight.ExtraBold, color = SaturnInk)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            deletedForMeIds.add(targetMsg.id)
                            selectedMessageForAction = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Delete for me",
                            color = SaturnInk,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (isMine) {
                        TextButton(
                            onClick = {
                                deletedForEveryoneIds.add(targetMsg.id)
                                chatViewModel.sendMessage("[revoked:${targetMsg.id}]") { }
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Delete for everyone",
                                color = SaturnError,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForAction = null }) {
                    Text("Cancel", color = SaturnSecondaryText, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Rename Moon Companion Dialog
    if (showRenameDialog) {
        var newMoonName by remember(currentTitle) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = SaturnSurface,
            title = { Text("Rename Moon", fontWeight = FontWeight.Bold, color = SaturnInk) },
            text = {
                OutlinedTextField(
                    value = newMoonName,
                    onValueChange = { newMoonName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = newMoonName.trim()
                        if (clean.isNotEmpty()) {
                            AppStateStore.setPrivateConversationDisplayName(threadId, clean)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = SaturnInk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = SaturnSecondaryText)
                }
            }
        )
    }

    // Clear Chat History Confirmation
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            containerColor = SaturnSurface,
            title = { Text("Delete chat history?", fontWeight = FontWeight.Bold, color = SaturnInk) },
            text = { Text("This clears all messages in this chat on your device.", color = SaturnSecondaryText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isGroup) AppStateStore.clearPrivateConversation(threadId)
                        showClearHistoryConfirm = false
                    }
                ) {
                    Text("Clear", color = SaturnError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = SaturnInk)
                }
            }
        )
    }

    // Delete Moon / Group Confirmation
    if (showDeleteMoonConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMoonConfirm = false },
            containerColor = SaturnSurface,
            title = {
                Text(
                    text = if (isGroup) "Delete group?" else "Delete Moon?",
                    fontWeight = FontWeight.Bold,
                    color = SaturnInk
                )
            },
            text = {
                Text(
                    text = "This removes $currentTitle and their chat from your home screen.",
                    color = SaturnSecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteMoonConfirm = false
                        if (isGroup) {
                            chatViewModel.leaveChannel(threadId)
                        } else {
                            AppStateStore.removePrivateConversation(threadId)
                        }
                        handleExit()
                    }
                ) {
                    Text("Delete", color = SaturnError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMoonConfirm = false }) {
                    Text("Cancel", color = SaturnInk)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaturnBubble(
    message: BitchatMessage,
    isMine: Boolean,
    isRevoked: Boolean,
    showSender: Boolean,
    onLongClick: () -> Unit
) {
    val stickerItem = remember(message.content) {
        SaturnStickerPack.find { it.tag == message.content.trim() }
    }

    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    val bubbleColor = when {
        isRevoked -> SaturnSurfaceSecondary
        stickerItem != null -> stickerItem.bgColor
        isMine -> SaturnPeach
        else -> SaturnSurface
    }
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.timestamp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 285.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .border(1.5.dp, SaturnInk, bubbleShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            if (showSender) {
                Text(
                    text = message.sender,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaturnSecondaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            when {
                isRevoked -> {
                    Text(
                        text = "This message was deleted",
                        color = SaturnSecondaryText,
                        fontSize = 14.sp
                    )
                }
                stickerItem != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        // Swap to Image(painterResource(R.drawable.mascot_...), ...) once PNGs are exported
                        Text("★", fontSize = 44.sp, color = SaturnInk)
                        Text(
                            text = stickerItem.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaturnInk
                        )
                    }
                }
                else -> {
                    Text(
                        text = message.content,
                        color = SaturnInk,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = timeText, fontSize = 10.sp, color = SaturnSecondaryText)
                if (isMine && !isRevoked) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val tickIcon = when (message.deliveryStatus) {
                        is DeliveryStatus.Read, is DeliveryStatus.Delivered -> Icons.Default.DoneAll
                        is DeliveryStatus.Sent -> Icons.Default.Done
                        else -> Icons.Default.Schedule
                    }
                    Icon(
                        imageVector = tickIcon,
                        contentDescription = null,
                        tint = SaturnInk,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}