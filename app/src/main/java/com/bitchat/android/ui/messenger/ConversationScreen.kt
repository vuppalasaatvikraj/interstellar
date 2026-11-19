package com.bitchat.android.ui.messenger

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val moonNamePrefs = remember(context) {
        context.getSharedPreferences("saturn_moon_names", Context.MODE_PRIVATE)
    }
    val themePrefs = remember(context) {
        context.getSharedPreferences("saturn_theme_prefs", Context.MODE_PRIVATE)
    }
    val isDarkTheme = remember {
        themePrefs.getBoolean("is_dark_theme", false)
    }

    val bgPaper = if (isDarkTheme) SaturnDarkBg else SaturnPaper
    val cardSurface = if (isDarkTheme) SaturnDarkSurface else SaturnSurface
    val primaryInk = if (isDarkTheme) SaturnPaper else SaturnInk
    val secondaryInk = if (isDarkTheme) SaturnSand else SaturnSecondaryText
    val strokeBorder = if (isDarkTheme) SaturnSand else SaturnInk

    val privateChats by chatViewModel.privateChats.collectAsState()
    val channelMessages by chatViewModel.channelMessages.collectAsState()
    val connectedPeers by chatViewModel.connectedPeers.collectAsState()
    val displayNames by AppStateStore.privateConversationDisplayNames.collectAsState()
    val myPeerId = chatViewModel.myPeerID

    var customMoonName by remember(threadId) {
        mutableStateOf(moonNamePrefs.getString(threadId, null)?.takeIf { it.isNotBlank() })
    }

    val currentTitle = if (!isGroup) {
        customMoonName ?: displayNames[threadId] ?: threadTitle
    } else {
        threadTitle
    }

    val rawMessages = if (isGroup) {
        channelMessages[threadId].orEmpty()
    } else {
        privateChats[threadId].orEmpty()
    }
    val isOnline = !isGroup && (threadId in connectedPeers)

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
        containerColor = bgPaper,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgPaper),
                navigationIcon = {
                    IconButton(onClick = handleExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryInk)
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
                                color = primaryInk
                            )
                            Text(
                                text = when {
                                    isGroup -> "Group orbit"
                                    isOnline -> "Nearby now"
                                    else -> "Moon"
                                },
                                fontSize = 12.sp,
                                color = secondaryInk
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options", tint = primaryInk)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = cardSurface
                        ) {
                            if (!isGroup) {
                                DropdownMenuItem(
                                    text = { Text("Rename Moon", color = primaryInk) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = primaryInk) },
                                    onClick = {
                                        menuExpanded = false
                                        showRenameDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete chat history", color = primaryInk) },
                                leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = primaryInk) },
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
                if (showStickerTray) {
                    Surface(
                        color = cardSurface,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        border = BorderStroke(1.5.dp, strokeBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Saturn Stickers",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = primaryInk
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (showStickerTray) SaturnYellow else cardSurface)
                            .border(1.5.dp, strokeBorder, CircleShape)
                            .clickable { showStickerTray = !showStickerTray },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Saturn Stickers",
                            tint = if (showStickerTray) SaturnInk else primaryInk,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    SaturnMessageBox(
                        text = draftText,
                        onTextChange = {
                            draftText = it
                            if (!isGroup) chatViewModel.setConversationDraft(threadId, it)
                        },
                        onSend = {
                            val trimmed = draftText.trim()
                            chatViewModel.sendMessage(trimmed) { accepted ->
                                if (accepted) {
                                    draftText = ""
                                    if (!isGroup) chatViewModel.setConversationDraft(threadId, "")
                                }
                            }
                        },
                        onAttachClick = onPickImage,
                        onMicClick = onToggleVoiceNote,
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SaturnMeteorsAndDoodlesBackground(
                modifier = Modifier.fillMaxSize(),
                isDarkTheme = isDarkTheme
            )

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
                        isDarkTheme = isDarkTheme,
                        onLongClick = { selectedMessageForAction = message }
                    )
                }
            }
        }
    }

    selectedMessageForAction?.let { targetMsg ->
        val isMine = targetMsg.senderPeerID == myPeerId
        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            containerColor = cardSurface,
            title = {
                Text("Message Options", fontWeight = FontWeight.ExtraBold, color = primaryInk)
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
                            color = primaryInk,
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
                    Text("Cancel", color = secondaryInk, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showRenameDialog) {
        var newMoonName by remember(currentTitle) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = cardSurface,
            title = { Text("Rename Moon", fontWeight = FontWeight.Bold, color = primaryInk) },
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
                            moonNamePrefs.edit().putString(threadId, clean).apply()
                            customMoonName = clean
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = primaryInk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = secondaryInk)
                }
            }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            containerColor = cardSurface,
            title = { Text("Delete chat history?", fontWeight = FontWeight.Bold, color = primaryInk) },
            text = { Text("This clears all messages in this chat on your device.", color = secondaryInk) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isGroup) {
                            scope.launch { chatViewModel.deletePrivateConversation(threadId) }
                        }
                        showClearHistoryConfirm = false
                    }
                ) {
                    Text("Clear", color = SaturnError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = primaryInk)
                }
            }
        )
    }

    if (showDeleteMoonConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMoonConfirm = false },
            containerColor = cardSurface,
            title = {
                Text(
                    text = if (isGroup) "Delete group?" else "Delete Moon?",
                    fontWeight = FontWeight.Bold,
                    color = primaryInk
                )
            },
            text = {
                Text(
                    text = "This removes $currentTitle and their chat from your home screen.",
                    color = secondaryInk
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteMoonConfirm = false
                        moonNamePrefs.edit().remove(threadId).apply()
                        if (isGroup) {
                            chatViewModel.leaveChannel(threadId)
                        } else {
                            scope.launch { chatViewModel.deletePrivateConversation(threadId) }
                        }
                        handleExit()
                    }
                ) {
                    Text("Delete", color = SaturnError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMoonConfirm = false }) {
                    Text("Cancel", color = primaryInk)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaturnMessageBox(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onMicClick: () -> Unit,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()
    var showTooltip by remember { mutableStateOf(false) }

    val boxSurface = if (isDarkTheme) SaturnDarkSurface else SaturnSurface
    val boxBorder = if (isDarkTheme) SaturnSand else SaturnInk
    val boxText = if (isDarkTheme) SaturnPaper else SaturnInk
    val boxHint = if (isDarkTheme) SaturnSand else SaturnSecondaryText

    val sendBg by animateColorAsState(
        targetValue = if (hasText) SaturnPeach else Color.Transparent,
        animationSpec = tween(250),
        label = "sendButtonBg"
    )

    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = showTooltip,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut()
        ) {
            Surface(
                color = SaturnInk,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "Add an image",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(100.dp),
            color = boxSurface,
            border = BorderStroke(
                width = 1.5.dp,
                color = boxBorder
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, boxBorder, CircleShape)
                        .combinedClickable(
                            onClick = onAttachClick,
                            onLongClick = { showTooltip = !showTooltip }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add an image",
                        tint = boxText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = true,
                    cursorBrush = SolidColor(boxText),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = boxText,
                        fontSize = 15.sp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            SaturnTypingText(
                                fullText = "Saturn it...",
                                color = boxHint,
                                fontSize = 15.sp,
                                charDelayMs = 80L,
                                repeatLoop = true
                            )
                        }
                        innerTextField()
                    }
                )

                if (!hasText) {
                    IconButton(onClick = onMicClick, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice note",
                            tint = boxText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(sendBg)
                        .then(
                            if (hasText) Modifier.border(1.5.dp, SaturnInk, CircleShape)
                            else Modifier
                        )
                        .clickable(enabled = hasText) { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (hasText) SaturnInk else boxHint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaturnBubble(
    message: BitchatMessage,
    isMine: Boolean,
    isRevoked: Boolean,
    showSender: Boolean,
    isDarkTheme: Boolean,
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
        isRevoked -> if (isDarkTheme) SaturnDarkSurfaceSecondary else SaturnSurfaceSecondary
        stickerItem != null -> stickerItem.bgColor
        isMine -> SaturnPeach
        else -> if (isDarkTheme) SaturnDarkSurface else SaturnSurface
    }
    val bubbleBorder = if (isDarkTheme) SaturnSand else SaturnInk
    val bubbleTextColor = when {
        isMine || stickerItem != null -> SaturnInk
        isDarkTheme -> SaturnPaper
        else -> SaturnInk
    }
    val bubbleSubColor = when {
        isMine || stickerItem != null -> SaturnSecondaryText
        isDarkTheme -> SaturnSand
        else -> SaturnSecondaryText
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
                .border(1.5.dp, bubbleBorder, bubbleShape)
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
                    color = bubbleSubColor
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            when {
                isRevoked -> {
                    Text(
                        text = "This message was deleted",
                        color = bubbleSubColor,
                        fontSize = 14.sp
                    )
                }
                stickerItem != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
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
                        color = bubbleTextColor,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = timeText, fontSize = 10.sp, color = bubbleSubColor)
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