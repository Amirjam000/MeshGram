package com.meshgram.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshgram.app.model.*
import com.meshgram.app.ui.components.*
import com.meshgram.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<Message>,
    stories: List<Story>,
    isDarkTheme: Boolean,
    localUserId: String,
    onSendMessage: (String, String?, MessageType, Any?) -> Unit,
    onRecordVoice: () -> Unit,
    onStopVoice: () -> Pair<Any?, List<Int>>,
    recordingAmplitudes: List<Int>,
    isRecordingVoice: Boolean,
    onPlayAudio: (String, String) -> Unit,
    activeAudioId: String?,
    audioSpeed: Float,
    onCycleAudioSpeed: () -> Unit,
    onStoryClick: (Story) -> Unit,
    onAddStoryClick: () -> Unit,
    onOpenApkPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFolder by remember { mutableStateOf(ChatFolder.ALL) }
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var selectedSelfDestructSec by remember { mutableStateOf(0) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var activeMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var doubleTappedMessageId by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // فیلتر کردن پیام‌ها بر اساس پوشه انتخابی
    val filteredMessages = remember(messages, selectedFolder) {
        when (selectedFolder) {
            ChatFolder.ALL -> messages
            ChatFolder.PUBLIC_MESH -> messages.filter { it.recipientId == null }
            ChatFolder.DIRECT_E2EE -> messages.filter { it.recipientId != null && it.recipientId != "saved" }
            ChatFolder.SAVED_MESSAGES -> messages.filter { it.recipientId == "saved" }
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                // تب‌های دسته‌بندی پوشه‌های گفتگو (مانند تلگرام)
                ScrollableTabRow(
                    selectedTabIndex = selectedFolder.ordinal,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedFolder.ordinal]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    ChatFolder.values().forEach { folder ->
                        Tab(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            text = {
                                Text(
                                    text = folder.title,
                                    fontWeight = if (selectedFolder == folder) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                // نوار استوری‌های ۲۴ ساعته همتاها (مانند اینستاگرام)
                if (selectedFolder == ChatFolder.ALL || selectedFolder == ChatFolder.PUBLIC_MESH) {
                    StoryBar(
                        stories = stories,
                        isDarkTheme = isDarkTheme,
                        onMyStoryClick = onAddStoryClick,
                        onStoryClick = onStoryClick
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
                // بنر ریپلای یا نقل قول
                replyingToMessage?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "پاسخ به ${reply.senderName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = reply.text.ifEmpty { "پیام چندرسانه‌ای" },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "لغو", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // نوار ورود متن و دکمه‌های پیام صوتی و تصویری
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "پیوست",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // فیلد متن پیام
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("پیام امن در شبکه مش...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val currentRecipient = when (selectedFolder) {
                                    ChatFolder.SAVED_MESSAGES -> "saved"
                                    ChatFolder.DIRECT_E2EE -> "direct_peer"
                                    else -> null
                                }
                                onSendMessage(
                                    inputText,
                                    currentRecipient,
                                    MessageType.TEXT,
                                    null
                                )
                                inputText = ""
                                replyingToMessage = null
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "ارسال",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        // دکمه ضبط ویس با قابلیت کشیدن
                        IconButton(
                            onClick = {
                                if (isRecordingVoice) {
                                    val (file, samples) = onStopVoice()
                                    if (file != null) {
                                        onSendMessage(
                                            "پیام صوتی",
                                            null,
                                            MessageType.VOICE,
                                            VoicePayload(waveforms = samples, durationSec = 3, filePath = file.toString())
                                        )
                                    }
                                } else {
                                    onRecordVoice()
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isRecordingVoice) HeartRed else MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "ویس",
                                tint = if (isRecordingVoice) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    val isSelf = msg.senderId == localUserId

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        MessageBubble(
                            message = msg,
                            isSelf = isSelf,
                            isDarkTheme = isDarkTheme,
                            activeAudioId = activeAudioId,
                            audioSpeed = audioSpeed,
                            onPlayAudio = { path -> onPlayAudio(path, msg.id) },
                            onCycleSpeed = onCycleAudioSpeed,
                            onDoubleTap = {
                                doubleTappedMessageId = msg.id
                                val count = msg.reactions.getOrDefault("❤️", 0)
                                msg.reactions["❤️"] = count + 1
                            },
                            onLongClick = { activeMessageForMenu = msg }
                        )
                    }
                }
            }

            // انیمیشن پرواز قلب با دابل تپ (مانند اینستاگرام)
            FloatingHeartAnimation(
                trigger = doubleTappedMessageId != null,
                onAnimationEnd = { doubleTappedMessageId = null }
            )

            // منوی پیوست‌ها (استخراج APK، تایمر خودتخریبی و...)
            if (showAttachmentMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showAttachmentMenu = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "اشتراک و پیوست‌های هوشمند",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            AttachmentOption(
                                icon = Icons.Default.Apps,
                                title = "برنامه‌های من (APK)",
                                color = Color(0xFF10B981),
                                onClick = {
                                    showAttachmentMenu = false
                                    onOpenApkPicker()
                                }
                            )

                            AttachmentOption(
                                icon = Icons.Default.Timer,
                                title = "پیام محوشونده",
                                color = Color(0xFFF59E0B),
                                onClick = {
                                    selectedSelfDestructSec = if (selectedSelfDestructSec == 0) 10 else 0
                                    showAttachmentMenu = false
                                }
                            )

                            AttachmentOption(
                                icon = Icons.Default.Videocam,
                                title = "ویدیو نوت گرد",
                                color = Color(0xFF3B82F6),
                                onClick = {
                                    showAttachmentMenu = false
                                    onSendMessage("ویدیو نوت", null, MessageType.ROUND_VIDEO, VideoNotePayload(durationSec = 5))
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // منوی بازشوی روی پیام (کپی، ریپلای، فوروارد، حذف)
            activeMessageForMenu?.let { msg ->
                ModalBottomSheet(onDismissRequest = { activeMessageForMenu = null }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "عملیات روی پیام",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        ListItem(
                            headlineContent = { Text("پاسخ دادن (Reply)") },
                            leadingContent = { Icon(Icons.Default.Reply, null) },
                            modifier = Modifier.clickable {
                                replyingToMessage = msg
                                activeMessageForMenu = null
                            }
                        )

                        ListItem(
                            headlineContent = { Text("کپی کردن متن") },
                            leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(msg.text))
                                activeMessageForMenu = null
                            }
                        )

                        ListItem(
                            headlineContent = { Text("فوروارد بدون نام") },
                            leadingContent = { Icon(Icons.Default.Forward, null) },
                            modifier = Modifier.clickable {
                                onSendMessage(msg.text, null, msg.type, null)
                                activeMessageForMenu = null
                            }
                        )

                        ListItem(
                            headlineContent = { Text("افزودن به ذخیره‌شده‌ها") },
                            leadingContent = { Icon(Icons.Default.BookmarkBorder, null) },
                            modifier = Modifier.clickable {
                                onSendMessage(msg.text, "saved", msg.type, null)
                                activeMessageForMenu = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isSelf: Boolean,
    isDarkTheme: Boolean,
    activeAudioId: String?,
    audioSpeed: Float,
    onPlayAudio: (String) -> Unit,
    onCycleSpeed: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongClick: () -> Unit
) {
    val bubbleColor = if (isSelf) {
        if (isDarkTheme) NightBubbleSelf else DayBubbleSelf
    } else {
        if (isDarkTheme) NightBubblePeer else DayBubblePeer
    }

    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isSelf) 16.dp else 4.dp,
            bottomEnd = if (isSelf) 4.dp else 16.dp
        ),
        color = bubbleColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .widthIn(max = 300.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // نام فرستنده در چت عمومی
            if (!isSelf) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // فوروارد شده
            message.forwardFrom?.let { originalAuthor ->
                Text(
                    text = "فوروارد از $originalAuthor",
                    fontSize = 10.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ریپلای نقل قول شده
            message.replyToText?.let { quotedText ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = message.replyToSender ?: "پیام",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = quotedText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // نمایش بر اساس نوع پیام
            when (message.type) {
                MessageType.TEXT -> {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                MessageType.VOICE -> {
                    val isPlaying = activeAudioId == message.id
                    WaveformPlayer(
                        isPlaying = isPlaying,
                        waveforms = message.voiceData?.waveforms ?: emptyList(),
                        currentPosMs = 0,
                        totalDurationMs = (message.voiceData?.durationSec ?: 0) * 1000,
                        playbackSpeed = audioSpeed,
                        isSelf = isSelf,
                        onPlayPauseClick = {
                            message.voiceData?.filePath?.let { onPlayAudio(it) }
                        },
                        onSpeedClick = onCycleSpeed
                    )
                }
                MessageType.ROUND_VIDEO -> {
                    RoundVideoNoteBubble(
                        durationSec = message.videoNoteData?.durationSec ?: 5,
                        isSelf = isSelf,
                        onPlayClick = {}
                    )
                }
                MessageType.APK -> {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = "APK",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.mediaPayload?.fileName ?: "برنامه اندروید",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(message.mediaPayload?.fileSize ?: 0L) / (1024 * 1024)} مگابایت",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Text(text = message.text, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // زمان، تیک تحویل و ری‌اکشن‌ها
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // نمایش ری‌اکشن‌های ثبت‌شده
                if (message.reactions.isNotEmpty()) {
                    message.reactions.forEach { (emoji, count) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "$emoji $count",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // آیکون ساعت یا تیک‌های تحویل
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSelf) {
                    Spacer(modifier = Modifier.width(3.dp))
                    when (message.status) {
                        MessageStatus.PENDING -> Icon(Icons.Default.Schedule, "Pending", tint = Color.Gray, modifier = Modifier.size(12.dp))
                        MessageStatus.SENT -> Icon(Icons.Default.Check, "Sent", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, "Delivered", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        MessageStatus.SEEN -> Icon(Icons.Default.DoneAll, "Seen", tint = SeenCyan, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // نمایش لیست افراد بیننده پیام در چت عمومی (Seen by)
            if (message.readByList.isNotEmpty()) {
                Text(
                    text = "دیده شده توسط: ${message.readByList.joinToString("، ")}",
                    fontSize = 9.sp,
                    color = SeenCyan,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
