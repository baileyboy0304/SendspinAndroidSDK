package com.mph070770.sendspinandroid

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * Factory object for creating SendspinClient instances.
 */
object Sendspin {
    /**
     * Create a new SendspinClient instance.
     * 
     * @param context Android application context
     * @return A new SendspinClient instance
     */
    fun createClient(context: Context): SendspinClient {
        // Initialize ServerRepository for the app
        com.mph070770.sendspinandroid.discovery.ServerRepository.initialize(context)
        
        // Return implementation
        return com.mph070770.sendspinandroid.client.SendspinAndroidClient(context)
    }
}

// Public API data classes
data class ConnectionState(val value: String) {
    companion object {
        val DISCONNECTED = ConnectionState("DISCONNECTED")
        val CONNECTING = ConnectionState("CONNECTING")
        val CONNECTED = ConnectionState("CONNECTED")
        val ERROR = ConnectionState("ERROR")
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectionState) return false
        return value == other.value
    }
    
    override fun hashCode(): Int = value.hashCode()
}

data class StreamState(
    val codec: String = "",
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitDepth: Int = 0,
    val isPlaying: Boolean = false,
    val playbackState: String = "",
    val groupName: String = ""
)

data class TrackProgress(
    val position: Long,
    val duration: Long,
    val speed: Int
)

data class Metadata(
    val timestamp: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val artworkUrl: String? = null,
    val artworkBitmap: Bitmap? = null,
    val progress: TrackProgress? = null,
    val repeatMode: String? = null,
    val shuffleEnabled: Boolean? = null
)

data class BufferStats(
    val queuedChunks: Int = 0,
    val bufferAheadMs: Long = 0,
    val lateDrops: Long = 0,
    val clockOffsetUs: Long = 0,
    val clockDriftPpm: Double = 0.0,
    val roundTripUs: Long = 0,
    val isClockConverged: Boolean = false,
    val clockMeasurements: Int = 0,
    val clockErrorUs: Long = Long.MAX_VALUE
)

data class ControllerState(
    val volume: Int = 100,
    val muted: Boolean = false,
    val supportedCommands: Set<String> = emptySet()
)

interface SendspinClientListener {
    fun onStateChanged(state: ConnectionState) {}
    fun onStreamStateChanged(state: StreamState) {}
    fun onMetadataChanged(metadata: Metadata?) {}
    fun onBufferStatsChanged(stats: BufferStats) {}
    fun onControllerStateChanged(state: ControllerState?) {}
}

interface SendspinClient {
    val connectionState: StateFlow<ConnectionState>
    val streamState: StateFlow<StreamState>
    val metadata: StateFlow<Metadata?>
    val bufferStats: StateFlow<BufferStats>
    val controllerState: StateFlow<ControllerState?>
    
    fun connect(url: String, clientId: String = "android-player", clientName: String = "Android Player")
    fun disconnect()
    fun isConnected(): Boolean
    
    fun play()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    
    fun setGroupVolume(volume: Int)
    fun setGroupMute(muted: Boolean)
    fun setLocalVolume(volume: Int)
    fun setLocalMute(muted: Boolean)
    
    fun setPlayoutOffset(milliseconds: Long)
    fun getPlayoutOffset(): Long
    
    fun addListener(listener: SendspinClientListener)
    fun removeListener(listener: SendspinClientListener)
    fun release()
    
    companion object {
        fun create(context: Context): SendspinClient {
            com.mph070770.sendspinandroid.discovery.ServerRepository.initialize(context)
            return com.mph070770.sendspinandroid.client.SendspinAndroidClient(context)
        }
    }
}
