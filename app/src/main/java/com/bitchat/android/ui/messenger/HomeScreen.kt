package com.bitchat.android.ui.messenger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    founderName: String = "Founder Name",
    creatorDockLinks: List<SaturnDockLink> = listOf(
        SaturnDockLink("Email", "mailto:hello@saturn.app", Icons.Default.Email, SaturnPeach),
        SaturnDockLink("GitHub", "https://github.com", Icons.Default.Code, SaturnYellow),
        SaturnDockLink("Social", "https://x.com", Icons.Default.AlternateEmail, SaturnSkyBlue),
        SaturnDockLink("Videos", "https://youtube.com", Icons.Default.PlayCircleOutline, SaturnSage)
    ),
    onOpenChat: (id: String, title: String, isGroup: Boolean) -> Unit
) {
    val context = LocalContext.current
    val conversations by chatViewModel.conversations.collectAsState()
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
    var myAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            myAvatarUri = uri
        }
    }

    val allThreads = remember(
        conversations,
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
            val name = displayNames[id] ?: summary.displayName.ifBlank { id.take(8) }
            SaturnThreadItem(
                id = id,
                title = name,
                subtitle = summary.draft?.takeIf { it.isNotBlank() }?.let { "Draft: $it" }
                    ?: summary.lastMessagePreview.ifBlank { "Say hello to your Moon" },
                timestamp = summary.lastMessageTimestamp,
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
        containerColor = SaturnPaper,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SaturnPeach)
                    .border(2.dp, SaturnInk, RoundedCornerShape(18.dp))
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
            // MagicUI Meteors + Brand Board Doodles in Saturn palette
            SaturnMeteorsAndDoodlesBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Top Header: Waving Mascot + "Saturn" + (i) About + 3-dots Settings
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
                        color = SaturnInk,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { activeSheet = ActiveHomeSheet.ABOUT_SATURN }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "About Saturn",
                            tint = SaturnInk
                        )
                    }
                    IconButton(onClick = { activeSheet = ActiveHomeSheet.PROFILE_SETTINGS }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings",
                            tint = SaturnInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar for Moons & Groups
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search Moons & groups...",
                            color = SaturnSecondaryText,
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SaturnInk)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = SaturnInk)
                            }
                        }
                    },
                    singleLine = true,
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Overlapping AvatarCircles Strip for Active Moons
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
                            color = SaturnSecondaryText
                        )
                        SaturnAvatarCircles(
                            moonNames = moonNames,
                            extraCount = (moonNames.size - 5).coerceAtLeast(0),
                            avatarSize = 30.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Moons & Groups Feed
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
                                color = SaturnInk
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap + to add nearby Moons or start a group.",
                                fontSize = 14.sp,
                                color = SaturnSecondaryText
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
                                onClick = { onOpenChat(item.id, item.title, item.isGroup) },
                                onLongClick = { longPressedThread = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Long-Press Action Menu (Pin, Clear Chat, Delete, Moon/Group Info)
    longPressedThread?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { longPressedThread = null },
            containerColor = SaturnSurface
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
                    color = SaturnInk
                )
                Text(
                    text = if (target.isGroup) "Group" else "Moon",
                    fontSize = 13.sp,
                    color = SaturnSecondaryText
                )
                HorizontalDivider(color = SaturnSand, modifier = Modifier.padding(vertical = 6.dp))

                SheetActionRow(
                    icon = Icons.Default.PushPin,
                    label = if (target.isPinned) "Unpin" else "Pin to top",
                    onClick = {
                        chatViewModel.togglePinConversation(target.id)
                        longPressedThread = null
                    }
                )
                SheetActionRow(
                    icon = Icons.Outlined.Info,
                    label = if (target.isGroup) "Group info" else "Moon info",
                    onClick = {
                        infoDialogThread = target
                        longPressedThread = null
                    }
                )
                SheetActionRow(
                    icon = Icons.Default.CleaningServices,
                    label = "Clear chat",
                    onClick = {
                        if (!target.isGroup) {
                            AppStateStore.clearPrivateConversation(target.id)
                        }
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
                            AppStateStore.removePrivateConversation(target.id)
                        }
                        longPressedThread = null
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Moon Info / Group Info Dialog
    infoDialogThread?.let { info ->
        AlertDialog(
            onDismissRequest = { infoDialogThread = null },
            containerColor = SaturnSurface,
            title = {
                Text(
                    text = if (info.isGroup) "Group Info" else "Moon Info",
                    fontWeight = FontWeight.Bold,
                    color = SaturnInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${info.title}", fontWeight = FontWeight.SemiBold, color = SaturnInk)
                    Text(
                        text = if (info.isGroup) "Group ID: ${info.id}" else "Moon ID: ${info.id.take(12)}",
                        fontSize = 13.sp,
                        color = SaturnSecondaryText
                    )
                    Text(
                        text = if (info.isOnline) "Status: Nearby right now" else "Status: Out of direct range",
                        fontSize = 13.sp,
                        color = SaturnSecondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDialogThread = null }) {
                    Text("Done", fontWeight = FontWeight.Bold, color = SaturnInk)
                }
            }
        )
    }

    // Bottom Sheets for '+', Settings, and About Saturn
    when (activeSheet) {
        ActiveHomeSheet.NONE -> Unit

        ActiveHomeSheet.PLUS_ACTIONS -> {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = SaturnSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Start something new", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = SaturnInk)

                    SheetActionRow(
                        icon = Icons.Default.BluetoothSearching,
                        label = "Add Moon (Nearby Bluetooth)",
                        onClick = { activeSheet = ActiveHomeSheet.ADD_MOON_BLUETOOTH }
                    )
                    SheetActionRow(
                        icon = Icons.Default.GroupAdd,
                        label = "Create Group",
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
                containerColor = SaturnSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Nearby Moons via Bluetooth", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = SaturnInk)
                    Text(
                        text = "Keep Bluetooth on to spot people close by.",
                        fontSize = 14.sp,
                        color = SaturnSecondaryText
                    )

                    if (nearbyIds.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Looking for nearby Moons...", color = SaturnSecondaryText)
                        }
                    } else {
                        nearbyIds.forEach { peerId ->
                            val peerName = peerNicknames[peerId] ?: peerId.take(8)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SaturnSurfaceSecondary)
                                    .border(1.5.dp, SaturnInk, RoundedCornerShape(18.dp))
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
                                    Text(peerName, fontWeight = FontWeight.Bold, color = SaturnInk)
                                    Text("Tap to add Moon", fontSize = 12.sp, color = SaturnSecondaryText)
                                }
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = SaturnInk)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        ActiveHomeSheet.CREATE_GROUP -> {
            var groupName by remember { mutableStateOf("") }
            ModalBottomSheet(
                onDismissRequest = { activeSheet = ActiveHomeSheet.NONE },
                containerColor = SaturnSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Create a Group", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = SaturnInk)
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("Group name...", color = SaturnSecondaryText) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                containerColor = SaturnSurface
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
                        Text("Your Profile", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = SaturnInk)

                        // Default: First letter of nickname in uppercase; Tap to upload photo
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(SaturnYellow)
                                .border(2.dp, SaturnInk, CircleShape)
                                .clickable { avatarPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (myAvatarUri != null) {
                                AsyncImage(
                                    model = myAvatarUri,
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
                        Text("Tap circle to upload photo", fontSize = 12.sp, color = SaturnSecondaryText)

                        // Live TypingAnimation preview of Profile Name
                        if (editedName.isNotBlank()) {
                            SaturnTypingText(
                                fullText = editedName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SaturnInk
                            )
                        }

                        // Editable Display Name
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Unique Read-Only Username with TypingAnimation
                        Surface(
                            color = SaturnSurfaceSecondary,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, SaturnSand),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Your Unique Username", fontSize = 12.sp, color = SaturnSecondaryText)
                                    SaturnTypingText(
                                        fullText = uniqueUsername,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = SaturnInk,
                                        charDelayMs = 45L
                                    )
                                }
                                Icon(Icons.Default.Verified, contentDescription = null, tint = SaturnInk)
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
                containerColor = SaturnPaper
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
                            color = SaturnInk
                        )

                        SaturnTypingText(
                            fullText = "A little more human.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaturnSecondaryText
                        )

                        Surface(
                            color = SaturnSurface,
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(2.dp, SaturnInk),
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
                                    color = SaturnInk
                                )
                                Text(
                                    text = "Saturn brings people closer together for simple, private, and meaningful conversations—wherever you are.",
                                    fontSize = 14.sp,
                                    color = SaturnInk,
                                    lineHeight = 20.sp
                                )
                                HorizontalDivider(color = SaturnSand)
                                Text(
                                    text = "Created by $founderName",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = SaturnSecondaryText
                                )
                            }
                        }

                        // MagicUI Dock for Users to Reach You
                        Text(
                            text = "Say hi to the creator",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaturnSecondaryText
                        )
                        SaturnCreatorDock(
                            links = creatorDockLinks,
                            onOpenUrl = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )

                        // Invite Moons via Android Quick Share / System Share
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoonOrGroupRow(
    item: SaturnThreadItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SaturnSurface)
            .border(1.5.dp, SaturnInk, RoundedCornerShape(20.dp))
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
                    color = SaturnInk,
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
                        tint = SaturnSecondaryText,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                fontSize = 14.sp,
                color = SaturnSecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            if (item.timestamp > 0L) {
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                    fontSize = 11.sp,
                    color = SaturnSecondaryText
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
    // Swap to Image(painterResource(R.drawable.mascot_happy), ...) once exported PNG is placed in res/drawable/
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
    // Swap to Image(painterResource(R.drawable.saturn_planet_logo), ...) once exported PNG is placed in res/drawable/
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