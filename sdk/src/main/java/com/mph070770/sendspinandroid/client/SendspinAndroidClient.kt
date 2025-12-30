package com.mph070770.sendspinandroid.client
import com.mph070770.sendspinandroid.service.SendspinService
import com.mph070770.sendspinandroid.service.ServiceUiState

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.mph070770.sendspinandroid.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Android implementation of SendspinClient.
 * Bridges the public API to the internal Service implementation.
 */
internal class SendspinAndroidClient(
    private val context: Context
) : SendspinClient {

    companion object {
        private const val TAG = "SendspinAndroidClient"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var service: SendspinService? = null
    private var serviceBound = false

    // State flows - exposed publicly
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState())
    override val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _metadata = MutableStateFlow<Metadata?>(null)
    override val metadata: StateFlow<Metadata?> = _metadata.asStateFlow()

    private val _bufferStats = MutableStateFlow(BufferStats())
    override val bufferStats: StateFlow<BufferStats> = _bufferStats.asStateFlow()

    private val _controllerState = MutableStateFlow<ControllerState?>(null)
    override val controllerState: StateFlow<ControllerState?> = _controllerState.asStateFlow()

    // Listeners for event-based callbacks
    private val listeners = mutableListOf<SendspinClientListener>()

    // State collection job - needs to be cancelled on disconnect
    private var stateCollectionJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected: Service bound successfully")
            val localBinder = binder as? SendspinService.LocalBinder
            service = localBinder?.getService()
            serviceBound = true
            Log.d(TAG, "onServiceConnected: service=${service != null}, serviceBound=$serviceBound")

            // Observe service state and convert to SDK models
            service?.let { svc ->
                Log.d(TAG, "onServiceConnected: Starting state collection")
                stateCollectionJob?.cancel() // Cancel any existing job
                stateCollectionJob = scope.launch {
                    svc.uiState.collect { serviceState ->
                        // Only log significant state changes, not every update
                        if (serviceState.connected != _connectionState.value.let { it == ConnectionState.CONNECTED }) {
                            Log.i(TAG, "Connection state changed: connected=${serviceState.connected}, status=${serviceState.status}")
                        }
                        updateFromServiceState(serviceState)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "onServiceDisconnected: Service disconnected unexpectedly")
            stateCollectionJob?.cancel()
            stateCollectionJob = null
            service = null
            serviceBound = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    init {
        Log.d(TAG, "SendspinAndroidClient: Initializing, binding to service")
        // Bind to service
        val intent = Intent(context, SendspinService::class.java)
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "SendspinAndroidClient: bindService result=$bound")
    }

    override fun connect(url: String, clientId: String, clientName: String) {
        Log.d(TAG, "connect() called: url=$url, clientId=$clientId, clientName=$clientName")
        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "connect(): State set to CONNECTING, starting service")

        // Rebind to service if we unbound during disconnect
        if (!serviceBound) {
            Log.d(TAG, "connect(): Not bound, rebinding to service")
            val intent = Intent(context, SendspinService::class.java)
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "connect(): Rebind result=$bound")
        }

        SendspinService.startService(context, url, clientId, clientName)
        Log.d(TAG, "connect(): Service start requested")
    }

    override fun disconnect() {
        Log.d(TAG, "disconnect() called")

        // Cancel state collection FIRST to stop processing updates
        stateCollectionJob?.cancel()
        stateCollectionJob = null
        Log.d(TAG, "disconnect(): State collection cancelled")

        // Clear all state flows immediately so UI updates
        _connectionState.value = ConnectionState.DISCONNECTED
        _streamState.value = StreamState(
            codec = "",
            sampleRate = 0,
            channels = 0,
            bitDepth = 0,
            isPlaying = false,
            playbackState = "",
            groupName = ""
        )
        _metadata.value = null
        _bufferStats.value = BufferStats()
        _controllerState.value = null
        Log.d(TAG, "disconnect(): All state flows cleared")

        // Tell service to disconnect (closes WebSocket, stops audio, etc.)
        service?.disconnect()
        Log.d(TAG, "disconnect(): Told service to disconnect")

        // Unbind from service BEFORE stopping it (critical!)
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
                service = null
                Log.d(TAG, "disconnect(): Unbound from service")
            } catch (e: Exception) {
                Log.e(TAG, "disconnect(): Error unbinding service", e)
            }
        }

        // Now stop the service
        SendspinService.stopService(context)
        Log.d(TAG, "disconnect(): Service stop requested")
    }

    override fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }


    // Playback controls
    override fun play() { service?.sendPlay() }
    override fun pause() { service?.sendPause() }
    override fun stop() { service?.sendStop() }
    override fun next() { service?.sendNext() }
    override fun previous() { service?.sendPrevious() }

    // Volume controls
    override fun setGroupVolume(volume: Int) { service?.setGroupVolume(volume) }
    override fun setGroupMute(muted: Boolean) { service?.setGroupMute(muted) }
    override fun setLocalVolume(volume: Int) { service?.setPlayerVolume(volume) }
    override fun setLocalMute(muted: Boolean) { service?.setPlayerMute(muted) }

    // Settings
    override fun setPlayoutOffset(milliseconds: Long) {
        service?.setPlayoutOffsetMs(milliseconds)
    }

    override fun getPlayoutOffset(): Long {
        // Get from current state
        return runBlocking {
            service?.uiState?.first()?.playoutOffsetMs ?: -300L
        }
    }

    // Listeners
    override fun addListener(listener: SendspinClientListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: SendspinClientListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    override fun release() {
        Log.d(TAG, "release() called - cleaning up resources")

        // Cancel state collection
        stateCollectionJob?.cancel()
        stateCollectionJob = null

        // Cancel scope
        scope.cancel()

        // Unbind from service if still bound
        if (serviceBound) {
            try {
                context.unbindService(serviceConnection)
                serviceBound = false
                service = null
                Log.d(TAG, "release(): Unbound from service")
            } catch (e: Exception) {
                Log.e(TAG, "release(): Error unbinding service", e)
            }
        }

        Log.d(TAG, "release() completed")
    }

    /**
     * Convert service UiState to SDK state models
     */
    private fun updateFromServiceState(state: ServiceUiState) {
        // Handle server-commanded volume changes
        if (state.playerVolumeFromServer) {
            // Server commanded a volume change - update Android system volume
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val systemVolume = (state.playerVolume * maxVolume / 100)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, systemVolume, 0)
            Log.i(TAG, "Server commanded volume change to ${state.playerVolume}%")

            // Clear the flag in service
            service?.clearPlayerVolumeFlag()
        }

        // Handle server-commanded mute changes
        if (state.playerMutedFromServer) {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            if (state.playerMuted) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
            }
            Log.i(TAG, "Server commanded mute change to ${state.playerMuted}")

            // Clear the flag in service
            service?.clearPlayerMutedFlag()
        }

        // Connection state
        val newConnectionState = when {
            state.connected -> ConnectionState.CONNECTED
            state.status.contains("connecting") -> ConnectionState.CONNECTING
            state.status.contains("error") -> ConnectionState.ERROR
            else -> ConnectionState.DISCONNECTED
        }
        if (_connectionState.value != newConnectionState) {
            _connectionState.value = newConnectionState
            notifyListeners { it.onStateChanged(newConnectionState) }
        }

        // Stream state
        val streamParts = state.streamDesc.split(" ")
        val newStreamState = StreamState(
            codec = streamParts.getOrNull(0) ?: "",
            sampleRate = streamParts.getOrNull(1)?.replace("Hz", "")?.toIntOrNull() ?: 0,
            channels = streamParts.getOrNull(2)?.replace("ch", "")?.toIntOrNull() ?: 0,
            bitDepth = streamParts.getOrNull(3)?.replace("bit", "")?.toIntOrNull() ?: 0,
            isPlaying = state.playbackState == "playing",
            playbackState = state.playbackState,
            groupName = state.groupName
        )
        if (_streamState.value != newStreamState) {
            _streamState.value = newStreamState
            notifyListeners { it.onStreamStateChanged(newStreamState) }
        }

        // Metadata
        val newMetadata = if (state.hasMetadata && state.metadataTimestamp != null) {
            Metadata(
                timestamp = state.metadataTimestamp!!,
                title = state.trackTitle,
                artist = state.trackArtist,
                album = state.albumTitle,
                albumArtist = state.albumArtist,
                year = state.trackYear,
                trackNumber = state.trackNumber,
                artworkUrl = state.artworkUrl,
                artworkBitmap = state.artworkBitmap,
                progress = state.trackProgress?.let { prog ->
                    TrackProgress(
                        position = prog,
                        duration = state.trackDuration ?: 0,
                        speed = state.playbackSpeed ?: 1000
                    )
                },
                repeatMode = state.repeatMode,
                shuffleEnabled = state.shuffleEnabled
            )
        } else null

        if (_metadata.value != newMetadata) {
            _metadata.value = newMetadata
            notifyListeners { it.onMetadataChanged(newMetadata) }
        }

        // Buffer stats - THIS IS THE FIX!
        val newBufferStats = BufferStats(
            queuedChunks = state.queuedChunks,
            bufferAheadMs = state.bufferAheadMs,
            lateDrops = state.lateDrops,
            clockOffsetUs = state.offsetUs,
            clockDriftPpm = state.driftPpm,
            roundTripUs = state.rttUs,
            isClockConverged = state.isClockConverged,      // FIXED: Added
            clockMeasurements = state.clockMeasurements,    // FIXED: Added
            clockErrorUs = state.clockErrorUs               // FIXED: Added
        )
        if (_bufferStats.value != newBufferStats) {
            _bufferStats.value = newBufferStats
            notifyListeners { it.onBufferStatsChanged(newBufferStats) }
        }

        // Controller state
        val newControllerState = if (state.hasController) {
            ControllerState(
                volume = state.groupVolume,
                muted = state.groupMuted,
                supportedCommands = state.supportedCommands
            )
        } else null

        if (_controllerState.value != newControllerState) {
            _controllerState.value = newControllerState
            notifyListeners { it.onControllerStateChanged(newControllerState) }
        }
    }

    private fun notifyListeners(block: (SendspinClientListener) -> Unit) {
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    block(listener)
                } catch (e: Exception) {
                    // Don't let listener exceptions crash the SDK
                }
            }
        }
    }
}