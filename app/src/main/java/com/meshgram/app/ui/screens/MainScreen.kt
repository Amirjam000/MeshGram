package com.meshgram.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshgram.app.model.MessageType
import com.meshgram.app.model.Story
import com.meshgram.app.ui.theme.SuccessGreen
import com.meshgram.app.viewmodel.MainViewModel

enum class MainTab(val title: String) {
    CHATS("گفتگوها"),
    TRANSFER("انتقال مستقیم"),
    PROFILE("پروفایل")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(MainTab.CHATS) }
    var selectedStoryForViewer by remember { mutableStateOf<Story?>(null) }
    var showAddStoryDialog by remember { mutableStateOf(false) }
    var newStoryText by remember { mutableStateOf("") }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsStateWithLifecycle()
    val recordingAmplitudes by viewModel.audioRecordPlayer.recordingAmplitudes.collectAsStateWithLifecycle()
    val audioState by viewModel.audioRecordPlayer.playbackState.collectAsStateWithLifecycle()
    val transferProgress by viewModel.transferEngine.transferState.collectAsStateWithLifecycle()
    val userName by viewModel.localUserName.collectAsStateWithLifecycle()
    val userBio by viewModel.localUserBio.collectAsStateWithLifecycle()
    val isWalkieTalkieActive by viewModel.callManager.isWalkieTalkieActive.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مش‌گرام",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // نشانگر آنلاین و تعداد همتاهای شبکه
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (connectedPeers.isNotEmpty()) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (connectedPeers.isNotEmpty()) SuccessGreen else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${connectedPeers.size} همتا",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (connectedPeers.isNotEmpty()) SuccessGreen else Color.Gray
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // دکمه واکی‌تاکی سریع (PTT Walkie-Talkie)
                        IconButton(
                            onClick = {
                                if (isWalkieTalkieActive) {
                                    viewModel.callManager.stopWalkieTalkie()
                                } else {
                                    viewModel.callManager.startWalkieTalkie("192.168.49.255")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = "بیسیم واکی‌تاکی",
                                tint = if (isWalkieTalkieActive) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // دکمه تغییر تم
                        IconButton(onClick = { viewModel.toggleTheme() }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = "تم",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.CHATS,
                        onClick = { currentTab = MainTab.CHATS },
                        icon = { Icon(if (currentTab == MainTab.CHATS) Icons.Default.Chat else Icons.Outlined.Chat, contentDescription = null) },
                        label = { Text(MainTab.CHATS.title) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.TRANSFER,
                        onClick = { currentTab = MainTab.TRANSFER },
                        icon = { Icon(if (currentTab == MainTab.TRANSFER) Icons.Default.SwapHoriz else Icons.Outlined.SwapHoriz, contentDescription = null) },
                        label = { Text(MainTab.TRANSFER.title) }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.PROFILE,
                        onClick = { currentTab = MainTab.PROFILE },
                        icon = { Icon(if (currentTab == MainTab.PROFILE) Icons.Default.Person else Icons.Outlined.Person, contentDescription = null) },
                        label = { Text(MainTab.PROFILE.title) }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (currentTab) {
                    MainTab.CHATS -> {
                        ChatScreen(
                            messages = messages,
                            stories = stories,
                            isDarkTheme = isDarkTheme,
                            localUserId = viewModel.localUserId,
                            onSendMessage = { text, recip, type, payload ->
                                viewModel.sendMessage(text, recip, type, payload)
                            },
                            onRecordVoice = { viewModel.startVoiceRecording() },
                            onStopVoice = { viewModel.stopVoiceRecording() },
                            recordingAmplitudes = recordingAmplitudes,
                            isRecordingVoice = isRecordingVoice,
                            onPlayAudio = { path, id -> viewModel.playAudio(path, id) },
                            activeAudioId = audioState.activeMessageId,
                            audioSpeed = audioState.speed,
                            onCycleAudioSpeed = { viewModel.cycleAudioSpeed() },
                            onStoryClick = { story -> selectedStoryForViewer = story },
                            onAddStoryClick = { showAddStoryDialog = true },
                            onOpenApkPicker = { currentTab = MainTab.TRANSFER }
                        )
                    }
                    MainTab.TRANSFER -> {
                        TransferScreen(
                            installedApps = installedApps,
                            transferProgress = transferProgress,
                            onSendApp = { appInfo -> viewModel.sendInstalledApp(appInfo) },
                            onStartServer = { viewModel.startTransferServer() },
                            onGenerateQrCode = { viewModel.generatePairingQr() }
                        )
                    }
                    MainTab.PROFILE -> {
                        ProfileScreen(
                            userName = userName,
                            userBio = userBio,
                            keyFingerprint = viewModel.localKeyFingerprint,
                            isDarkTheme = isDarkTheme,
                            connectedPeersCount = connectedPeers.size,
                            onUpdateProfile = { name, bio -> viewModel.updateProfile(name, bio) },
                            onToggleTheme = { viewModel.toggleTheme() }
                        )
                    }
                }
            }
        }

        // نمایش تمام صفحه استوری در صورت کلیک
        selectedStoryForViewer?.let { story ->
            StoryViewerScreen(
                story = story,
                onClose = { selectedStoryForViewer = null },
                onLikeClick = { viewModel.likeStory(story.id) }
            )
        }

        // دیالوگ ارسال استوری جدید
        if (showAddStoryDialog) {
            AlertDialog(
                onDismissRequest = { showAddStoryDialog = false },
                title = { Text("ارسال استوری ۲۴ ساعته مش", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "این استوری به مدت ۲۴ ساعت برای تمام همتاهای متصل به شبکه مش قابل مشاهده خواهد بود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = newStoryText,
                            onValueChange = { newStoryText = it },
                            placeholder = { Text("متن استوری شما...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newStoryText.isNotBlank()) {
                                viewModel.addStory(newStoryText)
                                newStoryText = ""
                                showAddStoryDialog = false
                            }
                        }
                    ) {
                        Text("انتشار استوری")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddStoryDialog = false }) {
                        Text("لغو")
                    }
                }
            )
        }
    }
}
