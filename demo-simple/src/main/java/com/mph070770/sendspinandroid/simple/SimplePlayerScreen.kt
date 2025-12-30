package com.mph070770.sendspinandroid.simple

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.mph070770.sendspinandroid.ConnectionState
import com.mph070770.sendspinandroid.SendspinClient
import com.mph070770.sendspinandroid.discovery.AutoConnectManager
import com.mph070770.sendspinandroid.discovery.ServerInfo
import com.mph070770.sendspinandroid.discovery.ServerRepository
import kotlinx.coroutines.delay

@Composable
fun SimplePlayerScreen(
    modifier: Modifier = Modifier
) {
    // Create the client
    val context = LocalContext.current
    val client = remember { SendspinClient.create(context) }

    // Initialize ServerRepository
    LaunchedEffect(Unit) {
        ServerRepository.initialize(context)
    }

    // === Auto-connect manager ===
    val autoConnectManager = remember { AutoConnectManager(context, client) }
    val isAutoMode by autoConnectManager.isAutoMode.collectAsState()

    // Start auto-connect on first launch
    LaunchedEffect(Unit) {
        autoConnectManager.startAutoConnect()
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            autoConnectManager.cleanup()
        }
    }

    // Observe discovered servers from repository
    val discoveredServers by ServerRepository.discoveredServers.collectAsState()
    val recentServers by ServerRepository.recentServers.collectAsState()

    // Observe all state from client
    val connectionState by client.connectionState.collectAsState()
    val streamState by client.streamState.collectAsState()
    val metadata by client.metadata.collectAsState()
    val bufferStats by client.bufferStats.collectAsState()
    val controllerState by client.controllerState.collectAsState()

    // Derived states
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING
    val artworkBitmap = metadata?.artworkBitmap as? Bitmap

    // Settings dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Manual connection inputs (for when user wants to enter custom server)
    var manualUrl by remember { mutableStateOf("") }
    var manualClientId by remember { mutableStateOf("android-player-1") }
    var manualClientName by remember { mutableStateOf("Android Player") }

    // Extract dominant color from artwork
    val dominantColor = remember(artworkBitmap) {
        artworkBitmap?.let { extractDominantColor(it) } ?: Color(0xFF6366F1)
    }

    // Create animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "background_animation")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_offset"
    )

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Animated background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.8f),
                            dominantColor.darken(0.4f),
                            dominantColor.darken(0.7f),
                            Color.Black
                        ),
                        startY = animatedOffset * 200f,
                        endY = 1500f + animatedOffset * 200f
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with settings button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection status indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isConnected -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Connected",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            if (isAutoMode) {
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        isConnecting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = "Disconnected",
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Settings button
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Album artwork - LARGE with gradient fade, but use weight to limit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)  // Take available space but don't force it
                    .heightIn(min = 200.dp, max = 500.dp)  // Reasonable bounds
            ) {
                if (artworkBitmap != null) {
                    Image(
                        bitmap = artworkBitmap.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop  // Fill and crop like original
                    )

                    // Gradient fade at bottom - RESTORED
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        dominantColor.darken(0.6f).copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )
                } else {
                    // No artwork placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "No artwork",
                                modifier = Modifier.size(120.dp),
                                tint = Color.White.copy(alpha = 0.3f)
                            )

                            if (!isConnected && !isConnecting && isAutoMode) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.White.copy(alpha = 0.7f),
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        text = "Searching for servers...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Found ${discoveredServers.size} server${if (discoveredServers.size != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Metadata and controls section - SCROLLABLE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)  // Take remaining space
                    .verticalScroll(rememberScrollState())
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                dominantColor.darken(0.6f).copy(alpha = 0.95f),
                                dominantColor.darken(0.7f),
                                Color.Black
                            )
                        )
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 32.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Track metadata
                Text(
                    text = metadata?.title ?: if (isConnected) "No track playing" else if (isConnecting) "Connecting..." else "Not Connected",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = metadata?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                metadata?.album?.let { album ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Progress bar with live updates
                metadata?.progress?.let { progress ->
                    val currentMetadata = metadata
                    if (currentMetadata == null) return@let

                    var currentProgress by remember { mutableLongStateOf(progress.position) }

                    LaunchedEffect(currentMetadata.timestamp) {
                        while (true) {
                            val nowLocalUs = System.nanoTime() / 1000L
                            val serverTimeUs = nowLocalUs + bufferStats.clockOffsetUs
                            currentProgress = calculateTrackPosition(currentMetadata, serverTimeUs) ?: progress.position
                            delay(200)
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = if (progress.duration > 0) {
                                (currentProgress.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            onValueChange = { /* TODO: Seek */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.9f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(currentProgress),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            Text(
                                text = formatDuration(progress.duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* TODO */ },
                        enabled = false,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Previous - Always enabled to fix server state bug
                    IconButton(
                        onClick = { client.previous() },
                        enabled = true,  // Always enabled
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause - Large circular button
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        IconButton(
                            onClick = {
                                if (streamState.isPlaying) {
                                    client.pause()
                                } else {
                                    client.play()
                                }
                            }
                        ) {
                            Icon(
                                if (streamState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (streamState.isPlaying) "Pause" else "Play",
                                tint = dominantColor.darken(0.2f),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Next - Always enabled to fix server state bug
                    IconButton(
                        onClick = { client.next() },
                        enabled = true,  // Always enabled
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { /* TODO */ },
                        enabled = false,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Volume controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)  // Increased from 12dp
                ) {
                    // Local player volume
                    if (isConnected) {
                        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
                        var playerVolume by remember { mutableIntStateOf(50) }

                        LaunchedEffect(Unit) {
                            while (true) {
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                playerVolume = (currentVolume * 100 / maxVolume).coerceIn(0, 100)
                                delay(500)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Player Volume",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.VolumeDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )

                                Slider(
                                    value = playerVolume.toFloat(),
                                    onValueChange = { newVolume ->
                                        playerVolume = newVolume.toInt()
                                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val systemVolume = (newVolume.toInt() * maxVolume / 100)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
                                        client.setLocalVolume(newVolume.toInt())
                                    },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White.copy(alpha = 0.8f),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                    )
                                )

                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Group volume - WITH DIVIDER for extra separation
                    controllerState?.let { controller ->
                        if (controller.supportedCommands.contains("volume")) {
                            // Extra visual separator
                            if (isConnected) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Group Volume",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { client.setGroupMute(!controller.muted) },
                                        enabled = controller.supportedCommands.contains("mute"),
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            if (controller.muted) Icons.Default.VolumeOff else Icons.Default.VolumeDown,
                                            contentDescription = if (controller.muted) "Unmute" else "Volume Down",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Slider(
                                        value = controller.volume.toFloat(),
                                        onValueChange = { client.setGroupVolume(it.toInt()) },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.White.copy(alpha = 0.8f),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                        )
                                    )

                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Text("Connection Settings")
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .heightIn(max = 500.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Current connection status
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isConnected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (isConnected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when {
                                            isConnected -> "Connected"
                                            isConnecting -> "Connecting..."
                                            else -> "Disconnected"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isConnected) {
                                    Text(
                                        text = "Group: ${streamState.groupName.ifBlank { "Unknown" }}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Mode: ${if (isAutoMode) "Auto-connect" else "Manual"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isConnected) {
                                    Button(
                                        onClick = {
                                            autoConnectManager.disconnect()
                                            showSettingsDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Disconnect")
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Discovered servers
                        if (discoveredServers.isNotEmpty()) {
                            Text(
                                text = "Discovered Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            discoveredServers.forEach { server ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            autoConnectManager.connectManually(server)
                                            showSettingsDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = server.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = server.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "Connect",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                        }

                        // Recent servers
                        if (recentServers.isNotEmpty()) {
                            Text(
                                text = "Recent Servers",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            recentServers.take(3).forEach { recent ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            autoConnectManager.connectManually(recent.toServerInfo())
                                            showSettingsDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = recent.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${recent.address} • ${recent.formattedTime}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = "Recent",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                        }

                        // Manual entry
                        Text(
                            text = "Manual Connection",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = manualUrl,
                            onValueChange = { manualUrl = it },
                            label = { Text("Server URL") },
                            placeholder = { Text("ws://192.168.1.100:8927/sendspin") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualClientId,
                                onValueChange = { manualClientId = it },
                                label = { Text("Client ID") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = manualClientName,
                                onValueChange = { manualClientName = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                if (manualUrl.isNotBlank()) {
                                    try {
                                        val address = manualUrl.trim()
                                            .removePrefix("ws://")
                                            .removePrefix("wss://")
                                            .substringBefore("/")
                                        val path = "/" + manualUrl.trim()
                                            .substringAfter("/", "sendspin")

                                        val server = ServerInfo(manualClientName.trim(), address, path)
                                        autoConnectManager.connectManually(
                                            server,
                                            manualClientId.trim(),
                                            manualClientName.trim()
                                        )
                                        showSettingsDialog = false
                                    } catch (e: Exception) {
                                        Log.e("SimplePlayerScreen", "Error parsing manual URL", e)
                                    }
                                }
                            },
                            enabled = manualUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Manually")
                        }

                        // Sync stats (if connected)
                        if (isConnected) {
                            HorizontalDivider()

                            Text(
                                text = "Sync Statistics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Clock Offset:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${bufferStats.clockOffsetUs / 1000}ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Drift:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format("%.3f ppm", bufferStats.clockDriftPpm),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Buffer:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${bufferStats.queuedChunks} chunks (${bufferStats.bufferAheadMs}ms)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Playout offset control
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(
                                text = "Playout Offset",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            val playoutOffset = client.getPlayoutOffset()
                            var localPlayoutOffset by remember { mutableStateOf(playoutOffset) }

                            Text(
                                text = "${localPlayoutOffset}ms",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Negative = earlier playback (catch up)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = localPlayoutOffset.toFloat(),
                                onValueChange = {
                                    localPlayoutOffset = it.toLong()
                                    client.setPlayoutOffset(localPlayoutOffset)
                                },
                                valueRange = -1000f..1000f,
                                steps = 200,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

private fun calculateTrackPosition(metadata: com.mph070770.sendspinandroid.Metadata, currentServerTimeUs: Long): Long? {
    val progress = metadata.progress ?: return null
    val timestamp = metadata.timestamp ?: return null

    if (progress.speed == 0) return progress.position

    val elapsedUs = currentServerTimeUs - timestamp
    val elapsedMs = elapsedUs / 1000L
    val speedMultiplier = progress.speed / 1000.0
    val calculatedProgress = progress.position + (elapsedMs * speedMultiplier).toLong()

    return if (progress.duration != 0L) {
        calculatedProgress.coerceIn(0L, progress.duration)
    } else {
        calculatedProgress.coerceAtLeast(0L)
    }
}

private fun extractDominantColor(bitmap: Bitmap): Color {
    return try {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch

        if (swatch != null) {
            Color(swatch.rgb)
        } else {
            Color(0xFF6366F1)
        }
    } catch (e: Exception) {
        Color(0xFF6366F1)
    }
}

private fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}