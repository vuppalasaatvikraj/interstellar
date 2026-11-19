package com.bitchat.android.ui.messenger

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class SaturnThreadItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isOnline: Boolean,
    val isGroup: Boolean
)

private enum class ActiveHomeSheet {
    NONE,
    PLUS_ACTIONS,
    ADD_MOON_BLUETOOTH,
    CREATE_GROUP,
    PROFILE_SETTINGS,
    ABOUT_SATURN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    chatViewModel: ChatViewModel,
    founderNames: String = "Saatvik Raj & Khayum Shaik",
    creatorDockLinks: List<SaturnDockLink> = listOf(
        SaturnDockLink(
            label = "Email Saatvik",
            url = "mailto:vuppalasaatvikraj@gmail.com",
            icon = Icons.Default.Email,
            pillColor = SaturnPeach
        ),
        SaturnDockLink(
            label = "Saatvik LinkedIn",
            url = "https://www.linkedin.com/in/saatvik-vuppala-269462430/",
            icon = Icons.Default.Work,
            pillColor = SaturnSkyBlue
        )
    ),
    onOpenChat: (id: String, title: String, isGroup: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val moonNamePrefs = remember(context) {
        context.getSharedPreferences("saturn_moon_names", Context.MODE_PRIVATE)
    }
    val themePrefs = remember(context) {
        context.getSharedPreferences("saturn_theme_prefs", Context.MODE_PRIVATE)
    }
    var isDarkTheme by remember {
        mutableStateOf(themePrefs.getBoolean("is_dark_theme", false))
    }

    val bgPaper = if (isDarkTheme) SaturnDarkBg else SaturnPaper
    val cardSurface = if (isDarkTheme) SaturnDarkSurface else SaturnSurface
    val cardSurfaceSecondary = if (isDarkTheme) SaturnDarkSurfaceSecondary else SaturnSurfaceSecondary
    val primaryInk = if (isDarkTheme) SaturnPaper else SaturnInk
    val secondaryInk = if (isDarkTheme) SaturnSand else SaturnSecondaryText
    val strokeBorder = if (isDarkTheme) SaturnSand else SaturnInk

    val conversations by chatViewModel.conversations.collectAsState()
    val privateChats by chatViewModel.privateChats.collectAsState()
    val joinedChannels by chatViewModel.joinedChannels.collectAsState()
    val channelMessages by chatViewModel.channelMessages.collectAsState()
    val unreadChannelCounts by chatViewModel.unreadChannelMessages.collectAsState()
    val peerNicknames by chatViewModel.peerNicknames.collectAsState()
    val connectedPeers by chatViewModel.connectedPeers.collectAsState()
    val myNickname by chatViewModel.nickname.collectAsState()
    val displayNames by AppStateStore.privateConversationDisplayNames.collectAsState()
    val unreadPrivateCounts by AppStateStore.unreadPrivateMessageCounts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeSheet by remember { mutableStateOf(ActiveHomeSheet.NONE) }
    var longPressedThread by remember { mutableStateOf<SaturnThreadItem?>(null) }
    var infoDialogThread by remember { mutableStateOf<SaturnThreadItem?>(null) }
    var myAvatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()?.let { myAvatarBitmap = it }
        }
    }

    val allThreads = remember(
        conversations,
        privateChats,
        joinedChannels,
        channelMessages,
        unreadChannelCounts,
        displayNames,
        unreadPrivateCounts,
        connectedPeers,
        searchQuery
    ) {
        val moons = conversations.map { summary ->
            val id = summary.conversationID
            val customName = moonNamePrefs.getString(id, null)?.takeIf { it.isNotBlank() }
            val name = customName ?: displayNames[id] ?: summary.displayName.ifBlank { id.take(8) }
            val draftPreview = summary.draft?.takeIf { it.isNotBlank() }?.let { "Draft: $it" }
            val lastMsg = privateChats[id]?.lastOrNull()
            val lastContent = lastMsg?.content?.takeIf { it.isNotBlank() }
            SaturnThreadItem(
                id = id,
                title = name,
                subtitle = draftPreview ?: lastContent ?: "Say hello to your Moon",
                timestamp = lastMsg?.timestamp?.time ?: 0L,
                unreadCount = unreadPrivateCounts[id] ?: 0,
                isPinned = summary.isPinned,
                isOnline = id in connectedPeers,
                isGroup = false
            )
        }

        val groups = joinedChannels.map { channel ->
            val msgs = channelMessages[channel].orEmpty()
            val lastMsg = msgs.lastOrNull()
            SaturnThreadItem(
                id = channel,
                title = channel.removePrefix("#"),
                subtitle = lastMsg?.content ?: "New group orbit",
                timestamp = lastMsg?.timestamp?.time ?: 0L,
                unreadCount = unreadChannelCounts[channel] ?: 0,
                isPinned = false,
                isOnline = false,
                isGroup = true
            )
        }

        (moons + groups)
            .filter { item ->
                searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.subtitle.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<SaturnThreadItem> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
    }

    Scaffold(
        containerColor = bgPaper,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SaturnPeach)
                    .border(2.dp, strokeBorder, RoundedCornerShape(18.dp))
                    .clickable { activeSheet = ActiveHomeSheet.PLUS_ACTIONS },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Moon or Create Group",
                    tint = SaturnInk,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SaturnMeteorsAndDoodlesBackground(
                modifier = Modifier.fillMaxSize(),
                isDarkTheme = isDarkTheme
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WavingMascotPlaceholder(size = 38.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Saturn",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryInk,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            isDarkTheme = !isDarkTheme
                            themePrefs.edit().putBoolean("is_dark_theme", isDarkTheme).apply()
                        }
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Light or Dark Mode",
                            tint = primaryInk
                        )
                    }
                    IconButton(onClick = { activeSheet = ActiveHomeSheet.ABOUT_SATURN }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "About Saturn",
                            tint = primaryInk
                        )
                    }
                    IconButton(onClick = { activeSheet = ActiveHomeSheet.PROFILE_SETTINGS }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings",
                            tint = primaryInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                SaturnSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search Moons & groups...",
                    isDarkTheme = isDarkTheme
                )

                val moonNames = remember(allThreads) {
                    allThreads.filter { !it.isGroup }.map { it.title }
                }
                if (moonNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your Moons",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryInk
                        )
                        SaturnAvatarCircles(
                            moonNames = moonNames,
                            extraCount = (moonNames.size - 5).coerceAtLeast(0),
                            avatarSize = 30.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (allThreads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WavingMascotPlaceholder(size = 86.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "It's a little quiet in orbit.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = primaryInk
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap + to add nearby Moons or start a group.",
                                fontSize = 14.sp,
                                color = secondaryInk
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(allThreads, key = { "${it.isGroup}_${it.id}" }) { item ->
                            MoonOrGroupRow(
                                item = item,
                                isDarkTheme = isDarkTheme,
                                onClick = { onOpenChat(item.id, item.title, item.isGroup) },
                                onLongClick = { longPressedThread = item }
                            )
                        }
                    }
                }
            }
        }
    }

    longPressedThread?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { longPressedThread = null },
            containerColor = cardSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = target.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = primaryInk
                )
                Text(
                    text = if (target.isGroup) "Group" else "Moon",
                    fontSize = 13.sp,
                    color = secondaryInk
                )
                HorizontalDivider(color = SaturnSand, modifier = Modifier.padding(vertical = 6.dp))

                SheetActionRow(
                    icon = Icons.Default.PushPin,
                    label = if (target.isPinned) "Unpin" else "Pin to top",
                    tint = primaryInk,
                    onClick = {
                        chatViewModel.toggleConversationPinned(target.id)
                        longPressedThread = null
                    }
                )
                SheetActionRow(
                    icon = Icons.Outlined.Info,
                    label = if (target.isGroup) "Group info" else "Moon info",
                    tint = primaryInk,
                    onClick = {
                        infoDialogThread = target
                        longPressedThread = null
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.DeleteOutline,
                    label = if (target.isGroup) "Delete group" else "Delete Moon",
                    tint = SaturnError,
                    onClick = {
                        if (target.isGroup) {
                            chatViewModel.leaveChannel(target.id)
                        } else {
                            scope.launch {
                                chatViewModel.deletePrivateConversation(target.id)
                            }
                        }
                        longPressedThread = null
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    infoDialogThread?.let { info ->
        AlertDialog(
            onDismissRequest = { infoDialogThread = null },
            containerColor = cardSurface,
            title = {
                Text(
                    text = if (info.isGroup) "Group Info" else "Moon Info",
                    fontWeight = FontWeight.Bold,
                    color = primaryInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${info.title}", fontWeight = FontWeight.SemiBold, color = primaryInk)
                    Text(
                        text = if (info.isGroup) "Group ID: ${info.id}" else "Moon ID: ${info.id.take(12)}",
                        fontSize = 13.sp,
                        color = secondaryInk
                    )
                    Text(
                        text = if (info.isOnline) "Status: Nearby right now" else "Status: Out of direct range",
                        fontSize = 13.sp,
                        color = secondaryInk
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDialogThread = null }) {
                    Text("Done", fontWeight = FontWeight.Bold, color = primaryInk)
                }
            }
        )
    }

    when (activeSheet) {
        ActiveHomeSheet.NONE -> Unit

        ActiveHomeSheet.PLUS_ACTIONS -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = cardSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Start something new", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = primaryInk)

                    SheetActionRow(
                        icon = Icons.Default.BluetoothSearching,
                        label = "Add Moon (Nearby Bluetooth)",
                        tint = primaryInk,
                        onClick = { activeSheet = ActiveHomeSheet.ADD_MOON_BLUETOOTH }
                    )
                    SheetActionRow(
                        icon = Icons.Default.GroupAdd,
                        label = "Create Group",
                        tint = primaryInk,
                        onClick = { activeSheet = ActiveHomeSheet.CREATE_GROUP }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ActiveHomeSheet.ADD_MOON_BLUETOOTH -> {
            val nearbyIds = (connectedPeers + peerNicknames.keys)
                .distinct()
                .filter { it != chatViewModel.myPeerID }

            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = cardSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Nearby Moons via Bluetooth", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = primaryInk)
                    Text(
                        text = "Keep Bluetooth on to spot people close by.",
                        fontSize = 14.sp,
                        color = secondaryInk
                    )

                    if (nearbyIds.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Looking for nearby Moons...", color = secondaryInk)
                        }
                    } else {
                        nearbyIds.forEach { peerId ->
                            val peerName = peerNicknames[peerId] ?: peerId.take(8)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(cardSurfaceSecondary)
                                    .border(1.5.dp, strokeBorder, RoundedCornerShape(18.dp))
                                    .clickable {
                                        activeSheet = ActiveHomeSheet.NONE
                                        onOpenChat(peerId, peerName, false)
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(SaturnYellow)
                                        .border(1.5.dp, SaturnInk, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(peerName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = SaturnInk)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peerName, fontWeight = FontWeight.Bold, color = primaryInk)
                                    Text("Tap to add Moon", fontSize = 12.sp, color = secondaryInk)
                                }
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = primaryInk)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ActiveHomeSheet.CREATE_GROUP -> {
            var groupName by remember { mutableStateOf("") }
            val availableMoons = remember(conversations, displayNames) {
                conversations.map { summary ->
                    val id = summary.conversationID
                    val customName = moonNamePrefs.getString(id, null)?.takeIf { it.isNotBlank() }
                    val name = customName ?: displayNames[id] ?: summary.displayName.ifBlank { id.take(8) }
                    id to name
                }
            }
            val selectedMoonIds = remember { mutableStateMapOf<String, Boolean>() }

            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = cardSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Create a Group", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = primaryInk)
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("Group name...", color = secondaryInk) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (availableMoons.isNotEmpty()) {
                        Text(
                            text = "Invite Moons to Orbit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryInk
                        )
                        availableMoons.forEach { (moonId, moonName) ->
                            val isChecked = selectedMoonIds[moonId] == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cardSurfaceSecondary)
                                    .border(1.5.dp, strokeBorder, RoundedCornerShape(16.dp))
                                    .clickable { selectedMoonIds[moonId] = !isChecked }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = moonName,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryInk,
                                    modifier = Modifier.weight(1f)
                                )
                                SaturnHandDrawnCheckbox(
                                    checked = isChecked,
                                    onCheckedChange = { selectedMoonIds[moonId] = it }
                                )
                            }
                        }
                    }

                    SaturnRippleButton(
                        text = "Create Group",
                        enabled = groupName.trim().isNotEmpty(),
                        onClick = {
                            val cleanName = groupName.trim().removePrefix("#")
                            if (cleanName.isNotEmpty()) {
                                val channelTag = "#$cleanName"
                                chatViewModel.joinChannel(channelTag)
                                activeSheet = ActiveHomeSheet.NONE
                                onOpenChat(channelTag, cleanName, true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ActiveHomeSheet.PROFILE_SETTINGS -> {
            var editedName by remember(myNickname) { mutableStateOf(myNickname) }
            val uniqueUsername = remember(myNickname, chatViewModel.myPeerID) {
                val base = myNickname.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "moon" }
                "$base#${chatViewModel.myPeerID.take(4)}"
            }

            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = cardSurface
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SaturnAnimatedGridBackground(modifier = Modifier.matchParentSize())

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Your Profile", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = primaryInk)

                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(SaturnYellow)
                                .border(2.dp, strokeBorder, CircleShape)
                                .clickable { avatarPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            val avatar = myAvatarBitmap
                            if (avatar != null) {
                                Image(
                                    bitmap = avatar,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = editedName.trim().take(1).uppercase().ifBlank { "S" },
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SaturnInk
                                )
                            }
                        }
                        Text("Tap circle to upload photo", fontSize = 12.sp, color = secondaryInk)

                        if (editedName.isNotBlank()) {
                            SaturnTypingText(
                                fullText = editedName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryInk
                            )
                        }

                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Surface(
                            color = cardSurfaceSecondary,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, SaturnSand),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Your Unique Username", fontSize = 12.sp, color = secondaryInk)
                                    SaturnTypingText(
                                        fullText = uniqueUsername,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = primaryInk,
                                        charDelayMs = 45L
                                    )
                                }
                                Icon(Icons.Default.Verified, contentDescription = null, tint = primaryInk)
                            }
                        }

                        Surface(
                            color = cardSurfaceSecondary,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, SaturnSand),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = primaryInk
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryInk)
                                    Text(
                                        text = if (isDarkTheme) "Dark Mode (Ink)" else "Light Mode (Paper)",
                                        fontSize = 12.sp,
                                        color = secondaryInk
                                    )
                                }
                                Switch(
                                    checked = isDarkTheme,
                                    onCheckedChange = { checked ->
                                        isDarkTheme = checked
                                        themePrefs.edit().putBoolean("is_dark_theme", checked).apply()
                                    }
                                )
                            }
                        }

                        SaturnRippleButton(
                            text = "Save Profile",
                            onClick = {
                                if (editedName.trim().isNotEmpty()) {
                                    chatViewModel.setNickname(editedName.trim())
                                }
                                activeSheet = ActiveHomeSheet.NONE
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        ActiveHomeSheet.ABOUT_SATURN -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = bgPaper
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SaturnAnimatedGridBackground(modifier = Modifier.matchParentSize())

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SaturnPlanetLogoMark(size = 64.dp)
                            WavingMascotPlaceholder(size = 76.dp)
                        }

                        Text(
                            text = "Saturn",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryInk
                        )

                        SaturnTypingText(
                            fullText = "A little more human.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryInk
                        )

                        Surface(
                            color = cardSurface,
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(2.dp, strokeBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Our Goal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = primaryInk
                                )
                                Text(
                                    text = "Saturn brings people closer together for simple, private, and meaningful conversations—wherever you are.",
                                    fontSize = 14.sp,
                                    color = primaryInk,
                                    lineHeight = 20.sp
                                )
                                HorizontalDivider(color = SaturnSand)
                                Text(
                                    text = "Founders",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = secondaryInk
                                )
                                Text(
                                    text = founderNames,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = primaryInk
                                )
                            }
                        }

                        Text(
                            text = "Connect with the founders",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryInk
                        )
                        SaturnCreatorDock(
                            links = creatorDockLinks,
                            onOpenUrl = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )

                        SaturnRippleButton(
                            text = "Invite Moons",
                            icon = Icons.Default.Share,
                            containerColor = SaturnYellow,
                            onClick = { shareInviteMoons(context) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SaturnSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search Moons & groups...",
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val isActive = isFocused || query.isNotEmpty()

    val barSurface = if (isDarkTheme) SaturnDarkSurface else SaturnSurface
    val barBorder = if (isActive) Color(0xFF15A986) else if (isDarkTheme) SaturnSand else SaturnInk
    val barText = if (isDarkTheme) SaturnPaper else SaturnInk
    val barHint = if (isDarkTheme) SaturnSand else SaturnSecondaryText

    val iconRotation by animateFloatAsState(
        targetValue = if (isActive) 80f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "searchIconRotation"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "searchIconScale"
    )
    val clearScale by animateFloatAsState(
        targetValue = if (query.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "clearBtnScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(100.dp),
        color = barSurface,
        border = BorderStroke(
            width = 1.5.dp,
            color = barBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = iconRotation
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .clickable(enabled = isActive) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF15A986),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = -80f }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = barText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = barText,
                    fontSize = 15.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = barHint
                        )
                    }
                    innerTextField()
                }
            )

            if (clearScale > 0.05f) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = clearScale
                            scaleY = clearScale
                            alpha = clearScale
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = barText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SaturnHandDrawnCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val uncheckedShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 12.dp, bottomEnd = 5.dp, bottomStart = 11.dp
    )
    val checkedShape = RoundedCornerShape(
        topStart = 12.dp, topEnd = 4.dp, bottomEnd = 11.dp, bottomStart = 5.dp
    )

    val scale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
        label = "checkboxBounce"
    )
    val rotation by animateFloatAsState(
        targetValue = if (checked) -2f else 0f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "checkboxTilt"
    )
    val checkDrawProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "checkmarkSplash"
    )

    val shape = if (checked) checkedShape else uncheckedShape
    val fillColor = if (checked) SaturnPeach else Color(0xFFFDFCF0)

    Box(
        modifier = modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .offset(x = 4.dp, y = 4.dp)
                .background(SaturnInk, shape)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(fillColor, shape)
                .border(width = 3.dp, color = SaturnInk, shape = shape),
            contentAlignment = Alignment.Center
        ) {
            if (checkDrawProgress > 0f) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.18f, h * 0.52f)
                        lineTo(w * 0.42f, h * 0.76f)
                        lineTo(w * 0.84f, h * 0.24f)
                    }
                    drawPath(
                        path = path,
                        color = SaturnInk.copy(alpha = checkDrawProgress),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoonOrGroupRow(
    item: SaturnThreadItem,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val rowBg = if (isDarkTheme) SaturnDarkSurface else SaturnSurface
    val rowBorder = if (isDarkTheme) SaturnSand else SaturnInk
    val titleColor = if (isDarkTheme) SaturnPaper else SaturnInk
    val subColor = if (isDarkTheme) SaturnSand else SaturnSecondaryText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(rowBg)
            .border(1.5.dp, rowBorder, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (item.isGroup) SaturnSkyBlue else SaturnYellow)
                .border(1.5.dp, SaturnInk, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.isGroup) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = SaturnInk)
            } else {
                Text(
                    text = item.title.take(1).uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = SaturnInk
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.isOnline && !item.isGroup) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(SaturnSage)
                            .border(1.dp, SaturnInk, CircleShape)
                    )
                }
                if (item.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = subColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                fontSize = 14.sp,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            if (item.timestamp > 0L) {
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                    fontSize = 11.sp,
                    color = subColor
                )
            }
            if (item.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(SaturnPeach)
                        .border(1.dp, SaturnInk, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.unreadCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaturnInk
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = SaturnInk,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = tint)
    }
}

private fun shareInviteMoons(context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join me on Saturn")
        putExtra(
            Intent.EXTRA_TEXT,
            "Come orbit with me on Saturn! Download the app so we can message directly."
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Invite Moons"))
}

@Composable
private fun WavingMascotPlaceholder(size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension * 0.38f
        drawCircle(SaturnYellow, radius = r, center = Offset(cx, cy))
        drawCircle(SaturnInk, radius = r, center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        drawCircle(SaturnInk, radius = 2.5.dp.toPx(), center = Offset(cx - r * 0.32f, cy - r * 0.1f))
        drawCircle(SaturnInk, radius = 2.5.dp.toPx(), center = Offset(cx + r * 0.32f, cy - r * 0.1f))
    }
}

@Composable
private fun SaturnPlanetLogoMark(size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension * 0.28f
        drawCircle(SaturnYellow, radius = r, center = Offset(cx, cy))
        drawCircle(SaturnInk, radius = r, center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        drawLine(
            color = SaturnPeach,
            start = Offset(cx - r * 1.6f, cy + r * 0.4f),
            end = Offset(cx + r * 1.6f, cy - r * 0.4f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}