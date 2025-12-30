package com.mph070770.sendspinandroid.service
import com.mph070770.sendspinandroid.client.SendspinPcmClient

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.Activity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class SendspinService : Service() {
    private val tag = "SendspinService"
    private val binder = LocalBinder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var client: SendspinPcmClient? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _uiState = MutableStateFlow(ServiceUiState())
    val uiState: StateFlow<ServiceUiState> = _uiState

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sendspin_playback"

        fun startService(context: Context, wsUrl: String, clientId: String, clientName: String) {
            Log.d("SendspinService", "===== startService() COMPANION METHOD CALLED =====")
            Log.d("SendspinService", "Context: ${context.javaClass.simpleName}")
            Log.d("SendspinService", "URL: $wsUrl")
            Log.d("SendspinService", "ClientId: $clientId")
            Log.d("SendspinService", "ClientName: $clientName")

            val intent = Intent(context, SendspinService::class.java).apply {
                putExtra("wsUrl", wsUrl)
                putExtra("clientId", clientId)
                putExtra("clientName", clientName)
            }
            Log.d("SendspinService", "Intent created with extras")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("SendspinService", "Starting foreground service (Android O+)")
                context.startForegroundService(intent)
            } else {
                Log.d("SendspinService", "Starting service (pre-Android O)")
                context.startService(intent)
            }
            Log.d("SendspinService", "===== startService() COMPLETED =====")
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, SendspinService::class.java))
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): SendspinService = this@SendspinService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "===== SERVICE onCreate() CALLED =====")
        createNotificationChannel()

        // Acquire wake lock to keep CPU running during playback
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SendspinService::WakeLock"
        ).apply {
            setReferenceCounted(false)
        }
        Log.i(tag, "Service onCreate completed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "===== SERVICE onStartCommand() CALLED =====")
        Log.i(tag, "Intent: $intent")
        Log.i(tag, "Flags: $flags, StartId: $startId")

        // Start foreground immediately
        Log.d(tag, "Starting foreground with notification")
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(tag, "Foreground started")

        intent?.let {
            Log.d(tag, "Intent extras: ${it.extras}")
            val wsUrl = it.getStringExtra("wsUrl")
            val clientId = it.getStringExtra("clientId")
            val clientName = it.getStringExtra("clientName")

            Log.d(tag, "Extracted: wsUrl=$wsUrl, clientId=$clientId, clientName=$clientName")

            if (wsUrl == null || clientId == null || clientName == null) {
                Log.e(tag, "Missing required intent extras!")
                return START_STICKY
            }

            // Disconnect existing connection before creating new one
            if (client != null) {
                Log.i(tag, "Disconnecting existing client before reconnecting")
                disconnect()
            }

            Log.d(tag, "Calling connect() with params")
            connect(wsUrl, clientId, clientName)
        } ?: run {
            Log.w(tag, "onStartCommand called with null intent!")
        }

        Log.i(tag, "===== onStartCommand() COMPLETED =====")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(tag, "===== SERVICE onBind() CALLED =====")
        return binder
    }

    override fun onDestroy() {
        Log.i(tag, "Service destroyed")
        disconnect()

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sendspin Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sendspin music playback"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val state = _uiState.value

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(state.trackTitle ?: "Sendspin Player")
            .setContentText(state.trackArtist ?: "Not playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Add album art if available
        state.artworkBitmap?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        // Add media controls
        if (state.hasController) {
            // Previous
            if (state.supportedCommands.contains("previous")) {
                val prevIntent = createMediaActionIntent("previous")
                builder.addAction(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    prevIntent
                )
            }

            // Play/Pause
            if (state.playbackState == "playing" && state.supportedCommands.contains("pause")) {
                val pauseIntent = createMediaActionIntent("pause")
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    pauseIntent
                )
            } else if (state.supportedCommands.contains("play")) {
                val playIntent = createMediaActionIntent("play")
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Play",
                    playIntent
                )
            }

            // Next
            if (state.supportedCommands.contains("next")) {
                val nextIntent = createMediaActionIntent("next")
                builder.addAction(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextIntent
                )
            }
        }

        // Use media style
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun createMediaActionIntent(action: String): PendingIntent {
        val intent = Intent(this, SendspinService::class.java).apply {
            putExtra("media_action", action)
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    fun connect(wsUrl: String, clientId: String, clientName: String) {
        Log.d(tag, "===== SERVICE CONNECT CALLED =====")
        Log.d(tag, "connect() params: wsUrl=$wsUrl, clientId=$clientId, clientName=$clientName")

        // Don't create a new connection if we're already connected to the same server
        if (client != null &&
            _uiState.value.wsUrl == wsUrl &&
            _uiState.value.clientId == clientId &&
            _uiState.value.connected) {
            Log.i(tag, "Already connected to this server, ignoring duplicate connect request")
            return
        }

        Log.d(tag, "Calling disconnect() to clean up")
        disconnect()

        // Acquire wake lock when connecting
        Log.d(tag, "Acquiring wake lock")
        wakeLock?.acquire()

        Log.d(tag, "Setting UI state to connecting (connected=false)")
        _uiState.value = _uiState.value.copy(
            wsUrl = wsUrl,
            clientId = clientId,
            clientName = clientName,
            status = "connecting...",
            connected = false  // Don't set true until WebSocket actually connects!
        )

        Log.d(tag, "Creating SendspinPcmClient")
        client = SendspinPcmClient(
            wsUrl = wsUrl,
            clientId = clientId,
            clientName = clientName,
            context = this,
            onUiUpdate = { patch ->
                // Removed verbose logging - was spamming
                _uiState.value = patch(_uiState.value)
                updateNotification()
            }
        ).also {
            it.setPlayoutOffsetMs(_uiState.value.playoutOffsetMs)
        }

        Log.d(tag, "Launching connect coroutine")
        scope.launch {
            try {
                Log.d(tag, "Calling client.connect()...")
                client?.connect()
                Log.d(tag, "client.connect() completed")
            } catch (e: Exception) {
                Log.e(tag, "Exception in client.connect()", e)
            }
        }
        Log.d(tag, "===== SERVICE CONNECT COMPLETED =====")
    }

    fun disconnect() {
        client?.close("user_disconnect")
        client = null

        // Release wake lock when disconnecting
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        _uiState.value = _uiState.value.copy(connected = false, status = "disconnected")
        updateNotification()
    }

    fun setPlayoutOffsetMs(ms: Long) {
        val clamped = ms.coerceIn(-1000L, 1000L)
        _uiState.value = _uiState.value.copy(playoutOffsetMs = clamped)
        client?.setPlayoutOffsetMs(clamped)
    }

    // Controller commands
    fun sendPlay() = client?.sendControllerCommand("play")
    fun sendPause() = client?.sendControllerCommand("pause")
    fun sendStop() = client?.sendControllerCommand("stop")
    fun sendNext() = client?.sendControllerCommand("next")
    fun sendPrevious() = client?.sendControllerCommand("previous")

    fun setGroupVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        client?.sendControllerCommand("volume", volume = clamped)
    }

    fun setGroupMute(muted: Boolean) {
        client?.sendControllerCommand("mute", mute = muted)
    }

    // Player (local device) volume controls
    fun setPlayerVolume(volume: Int) {
        client?.setPlayerVolume(volume)
    }

    fun setPlayerMute(muted: Boolean) {
        client?.setPlayerMute(muted)
    }

    fun clearPlayerVolumeFlag() {
        _uiState.value = _uiState.value.copy(playerVolumeFromServer = false)
    }

    fun clearPlayerMutedFlag() {
        _uiState.value = _uiState.value.copy(playerMutedFromServer = false)
    }
}