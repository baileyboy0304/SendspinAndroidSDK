package com.mph070770.sendspinandroid.debug

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mph070770.sendspinandroid.ConnectionState
import com.mph070770.sendspinandroid.SendspinClient
import com.mph070770.sendspinandroid.discovery.AutoConnectManager
import com.mph070770.sendspinandroid.discovery.ServerInfo
import com.mph070770.sendspinandroid.discovery.ServerRepository
import kotlinx.coroutines.delay

/**
 * Full debug UI with auto-connect and all technical features
 */
@Composable
fun DebugPlayerScreen(
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

    // Manual connection inputs
    var manualUrl by remember { mutableStateOf("") }
    var manualClientId by remember { mutableStateOf("android-player-1") }
    var manualClientName by remember { mutableStateOf("Android Player") }

    // Local player volume (Android system volume)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var playerVolume by remember { mutableStateOf(getAndroidVolume(audioManager)) }
    var playerMuted by remember { mutableStateOf(false) }

    // Observe Android volume changes
    LaunchedEffect(Unit) {
        while (true) {
            val currentVolume = getAndroidVolume(audioManager)
            if (currentVolume != playerVolume) {
                playerVolume = currentVolume
            }
            delay(500)
        }
    }

    // Ducking state
    var isDucked by remember { mutableStateOf(false) }
    var duckMode by remember { mutableStateOf("local") }
    var originalLocalVolume by remember { mutableStateOf<Int?>(null) }
    var originalGroupVolume by remember { mutableStateOf<Int?>(null) }

    // Discovery section toggle
    var showDiscoverySection by remember { mutableStateOf(true) }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING
    val hasPlayerRole = streamState.playbackState.isNotEmpty()
    val hasController = controllerState != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Sendspin Player (Debug)", style = MaterialTheme.typography.titleLarge)

        // === Connection Status Card (NEW) ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isConnected -> MaterialTheme.colorScheme.surface
                    isConnecting -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                isConnected -> Icons.Default.CheckCircle
                                isConnecting -> Icons.Default.Sync
                                else -> Icons.Default.Cancel
                            },
                            contentDescription = null,
                            tint = when {
                                isConnected -> MaterialTheme.colorScheme.primary
                                isConnecting -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(32.dp)
                        )

                        Column {
                            Text(
                                text = when {
                                    isConnected -> "Connected"
                                    isConnecting -> "Connecting..."
                                    else -> "Disconnected"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            if (isConnected) {
                                Text(
                                    text = "Group: ${streamState.groupName.ifBlank { "Unknown" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }

                            Text(
                                text = if (isAutoMode) "Mode: Auto-connect" else "Mode: Manual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (isConnected) {
                        Button(
                            onClick = { autoConnectManager.disconnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Disconnect")
                        }
                    }
                }

                if (!isConnected && !isConnecting) {
                    Text(
                        text = if (isAutoMode) {
                            "Searching for servers... (${discoveredServers.size} found)"
                        } else {
                            "Select a server below to connect"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        HorizontalDivider()

        // === Discovery Section ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available Servers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { showDiscoverySection = !showDiscoverySection },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (showDiscoverySection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showDiscoverySection) "Collapse" else "Expand"
                        )
                    }
                }

                if (showDiscoverySection) {
                    // Discovered servers
                    if (discoveredServers.isNotEmpty()) {
                        Text(
                            text = "Discovered (${discoveredServers.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )

                        discoveredServers.forEach { server ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoConnectManager.connectManually(server)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = server.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Connect",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Searching for servers...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Recent servers
                    if (recentServers.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "Recent Servers",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )

                        recentServers.take(3).forEach { recent ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoConnectManager.connectManually(recent.toServerInfo())
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = recent.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${recent.address} • ${recent.formattedTime}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "Recent",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Manual Connection
        Text("Manual Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text("Server URL") },
            placeholder = { Text("ws://192.168.1.137:8927/sendspin") },
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
                    } catch (e: Exception) {
                        Log.e("DebugPlayerScreen", "Error parsing manual URL", e)
                    }
                }
            },
            enabled = manualUrl.isNotBlank() && !isConnected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect Manually")
        }

        HorizontalDivider()

        // Metadata display with album art
        metadata?.let { meta ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Album artwork
                    (meta.artworkBitmap as? android.graphics.Bitmap)?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Album Art",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } ?: run {
                        Surface(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "No artwork",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Track info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = meta.title ?: "Unknown Track",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        meta.artist?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        meta.album?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            meta.year?.let {
                                Text(
                                    text = it.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            meta.trackNumber?.let {
                                Text(
                                    text = "Track $it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Progress bar
                meta.progress?.let { progress ->
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Column(modifier = Modifier.padding(16.dp).padding(top = 0.dp)) {
                        TrackProgressBar(client, meta, bufferStats.clockOffsetUs)
                    }
                }

                // Playback state indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)
                ) {
                    if (meta.repeatMode != null && meta.repeatMode != "off") {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    when (meta.repeatMode) {
                                        "one" -> "Repeat One"
                                        "all" -> "Repeat All"
                                        else -> "Repeat"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (meta.repeatMode == "one") Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    if (meta.shuffleEnabled == true) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Shuffle", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    meta.progress?.let { progress ->
                        if (progress.speed != 1000) {
                            val speed = progress.speed / 1000.0
                            AssistChip(
                                onClick = { },
                                label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Transport controls - Always enabled to fix server state bug
        controllerState?.let { controller ->
            HorizontalDivider()

            Text("Group Controls", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { client.previous() },
                    enabled = true  // Always enabled
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }

                IconButton(
                    onClick = { client.play() },
                    enabled = true  // Always enabled
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }

                IconButton(
                    onClick = { client.pause() },
                    enabled = true  // Always enabled
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }

                IconButton(
                    onClick = { client.stop() },
                    enabled = true  // Always enabled
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }

                IconButton(
                    onClick = { client.next() },
                    enabled = true  // Always enabled
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }

            // Group volume control
            if (controller.supportedCommands.contains("volume")) {
                Text("Group Volume", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { client.setGroupMute(!controller.muted) },
                        enabled = controller.supportedCommands.contains("mute")
                    ) {
                        Icon(
                            if (controller.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (controller.muted) "Unmute" else "Mute"
                        )
                    }

                    Text("${controller.volume}", modifier = Modifier.width(40.dp))

                    Slider(
                        value = controller.volume.toFloat(),
                        onValueChange = { client.setGroupVolume(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Local Player Volume Control
        if (isConnected && hasPlayerRole) {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))  // Extra spacing

            Text("Local Player Volume", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        playerMuted = !playerMuted
                        if (playerMuted) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        }
                        client.setLocalMute(playerMuted)
                    }
                ) {
                    Icon(
                        if (playerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (playerMuted) "Unmute Player" else "Mute Player"
                    )
                }

                Text("$playerVolume", modifier = Modifier.width(40.dp))

                Slider(
                    value = playerVolume.toFloat(),
                    onValueChange = {
                        playerVolume = it.toInt()
                        setAndroidVolume(audioManager, playerVolume)
                        client.setLocalVolume(playerVolume)
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))  // Extra spacing after
        }

        // Ducking Controls
        if (isConnected && hasPlayerRole) {
            HorizontalDivider()

            Text("Voice Assistant Ducking", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isDucked) {
                    Button(
                        onClick = {
                            when (duckMode) {
                                "group" -> if (hasController) {
                                    controllerState?.let { controller ->
                                        if (controller.volume > 10) {
                                            originalGroupVolume = controller.volume
                                            client.setGroupVolume(10)
                                            isDucked = true
                                        }
                                    }
                                }
                                else -> {
                                    if (playerVolume > 10) {
                                        originalLocalVolume = playerVolume
                                        playerVolume = 10
                                        setAndroidVolume(audioManager, 10)
                                        isDucked = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Duck")
                    }
                } else {
                    Button(
                        onClick = {
                            isDucked = false
                            when (duckMode) {
                                "local" -> {
                                    originalLocalVolume?.let { vol ->
                                        playerVolume = vol
                                        setAndroidVolume(audioManager, vol)
                                    }
                                    originalLocalVolume = null
                                }
                                "group" -> {
                                    originalGroupVolume?.let { vol ->
                                        client.setGroupVolume(vol)
                                    }
                                    originalGroupVolume = null
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Unduck")
                    }
                }

                if (duckMode == "local" && !isDucked) {
                    Button(
                        onClick = { duckMode = "local" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Local")
                    }
                } else {
                    OutlinedButton(
                        onClick = { duckMode = "local" },
                        enabled = !isDucked,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Local")
                    }
                }

                if (duckMode == "group" && !isDucked && hasController) {
                    Button(
                        onClick = { duckMode = "group" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Group")
                    }
                } else {
                    OutlinedButton(
                        onClick = { duckMode = "group" },
                        enabled = !isDucked && hasController,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Group")
                    }
                }
            }

            Text(
                text = when {
                    isDucked && duckMode == "local" -> "Local player ducked to 10%"
                    isDucked && duckMode == "group" -> "Group ducked to 10%"
                    duckMode == "group" && hasController -> "Ready to duck group"
                    duckMode == "group" -> "Group control unavailable"
                    else -> "Ready to duck local player"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider()

        // Enhanced Debug Stats
        Text("Debug Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Stream: ${streamState.codec} ${streamState.sampleRate}Hz ${streamState.channels}ch")

                Text(
                    text = "Clock Synchronization (Kalman Filter)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("• Offset: ${bufferStats.clockOffsetUs}µs (${bufferStats.clockOffsetUs / 1000}ms)")
                Text("• Drift: ${String.format("%.3f", bufferStats.clockDriftPpm)} ppm")
                Text("• Round-trip: ~${bufferStats.roundTripUs}µs (${bufferStats.roundTripUs / 1000}ms)")

                Text(
                    text = "Buffer Status",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("• Queued: ${bufferStats.queuedChunks} chunks")
                Text("• Ahead: ~${bufferStats.bufferAheadMs}ms")
                Text("• Late drops: ${bufferStats.lateDrops}")

                // Synchronization status
                if (!bufferStats.isClockConverged) {
                    Text(
                        text = "• Synchronising >>>> ${bufferStats.clockMeasurements}/12 measurements (error: ${bufferStats.clockErrorUs/1000}ms)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "• Clock: Converged ✓ (error: ${bufferStats.clockErrorUs/1000}ms)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (isAutoMode) {
                    Text(
                        text = "Auto-Connect Status",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text("• Mode: Auto-connect enabled")
                    Text("• Discovered: ${discoveredServers.size} server${if (discoveredServers.size != 1) "s" else ""}")
                }
            }
        }

        // Playout offset control
        var playoutOffset by remember { mutableStateOf(client.getPlayoutOffset()) }
        Text("Playout offset: ${playoutOffset}ms (neg = earlier / catch up)")
        Slider(
            value = playoutOffset.toFloat(),
            onValueChange = {
                playoutOffset = it.toLong()
                client.setPlayoutOffset(it.toLong())
            },
            valueRange = -1000f..1000f,
            steps = 200,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
        Text(
            "Sendspin Android Player - Debug UI with Auto-Connect | Kalman Filter Sync",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun TrackProgressBar(
    client: SendspinClient,
    metadata: com.mph070770.sendspinandroid.Metadata,
    clockOffsetUs: Long
) {
    var currentProgress by remember { mutableStateOf(0L) }

    LaunchedEffect(metadata.progress, clockOffsetUs) {
        while (true) {
            val nowLocalUs = System.nanoTime() / 1000L
            val serverTimeUs = nowLocalUs + clockOffsetUs
            currentProgress = calculateTrackPosition(metadata, serverTimeUs) ?: 0L
            delay(200)
        }
    }

    Column {
        LinearProgressIndicator(
            progress = metadata.progress?.let { progress ->
                if (progress.duration > 0) {
                    (currentProgress.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            } ?: 0f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentProgress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            metadata.progress?.let { progress ->
                if (progress.duration > 0) {
                    Text(
                        text = formatDuration(progress.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun calculateTrackPosition(metadata: com.mph070770.sendspinandroid.Metadata, currentServerTimeUs: Long): Long? {
    val progress = metadata.progress ?: return null

    if (progress.speed == 0) return progress.position

    val elapsedUs = currentServerTimeUs - metadata.timestamp
    val elapsedMs = elapsedUs / 1000L
    val speedMultiplier = progress.speed / 1000.0
    val calculatedProgress = progress.position + (elapsedMs * speedMultiplier).toLong()

    return if (progress.duration != 0L) {
        calculatedProgress.coerceIn(0L, progress.duration)
    } else {
        calculatedProgress.coerceAtLeast(0L)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun getAndroidVolume(audioManager: AudioManager): Int {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return (currentVolume * 100 / maxVolume).coerceIn(0, 100)
}

private fun setAndroidVolume(audioManager: AudioManager, volumePercent: Int) {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val systemVolume = (volumePercent * maxVolume / 100)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVolume, 0)
}