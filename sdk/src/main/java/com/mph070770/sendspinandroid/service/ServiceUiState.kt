package com.mph070770.sendspinandroid.service

import android.graphics.Bitmap

/**
 * UI State for SendspinService - extracted from ServiceUiState
 */
internal data class ServiceUiState(
    val wsUrl: String = "ws://192.168.1.137:8927/sendspin",
    val clientId: String = "android-player-1",
    val clientName: String = "Android Player",
    val connected: Boolean = false,
    val status: String = "idle",
    val activeRoles: String = "",
    val playbackState: String = "",
    val groupName: String = "",
    val streamDesc: String = "",
    val offsetUs: Long = 0,
    val driftPpm: Double = 0.0,
    val rttUs: Long = 0,
    val queuedChunks: Int = 0,
    val bufferAheadMs: Long = 0,
    val lateDrops: Long = 0,
    val playoutOffsetMs: Long = -300,
    val isClockConverged: Boolean = false,
    val clockMeasurements: Int = 0,
    val clockErrorUs: Long = Long.MAX_VALUE,
    val hasController: Boolean = false,
    val groupVolume: Int = 100,
    val groupMuted: Boolean = false,
    val supportedCommands: Set<String> = emptySet(),
    val playerVolume: Int = 100,
    val playerMuted: Boolean = false,
    val playerVolumeFromServer: Boolean = false,
    val playerMutedFromServer: Boolean = false,
    val hasMetadata: Boolean = false,
    val metadataTimestamp: Long? = null,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val albumTitle: String? = null,
    val albumArtist: String? = null,
    val trackYear: Int? = null,
    val trackNumber: Int? = null,
    val artworkUrl: String? = null,
    val trackProgress: Long? = null,
    val trackDuration: Long? = null,
    val playbackSpeed: Int? = null,
    val repeatMode: String? = null,
    val shuffleEnabled: Boolean? = null,
    val hasArtwork: Boolean = false,
    val artworkBitmap: Bitmap? = null,
    val isDucked: Boolean = false,
    val duckMode: String = "local", // "local" or "group"
    val originalLocalVolume: Int? = null,
    val originalGroupVolume: Int? = null
)
