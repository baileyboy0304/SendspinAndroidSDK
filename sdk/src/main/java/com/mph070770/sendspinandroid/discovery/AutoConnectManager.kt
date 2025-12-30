package com.mph070770.sendspinandroid.discovery

import android.content.Context
import android.util.Log
import com.mph070770.sendspinandroid.ConnectionState
import com.mph070770.sendspinandroid.SendspinClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages auto-connection behavior:
 * - Auto-connects to first discovered server on app launch
 * - Continues discovery in background
 * - Enters manual mode when user explicitly disconnects
 *
 * Usage:
 * ```kotlin
 * val manager = AutoConnectManager(context, client)
 * manager.startAutoConnect()
 * 
 * // User wants to disconnect
 * manager.disconnect() // Enters manual mode
 * 
 * // User wants to connect to specific server
 * manager.connectManually(serverInfo)
 * ```
 */
class AutoConnectManager(
    private val context: Context,
    private val client: SendspinClient
) {
    companion object {
        private const val TAG = "AutoConnectManager"
        private const val DEFAULT_CLIENT_ID = "android-player-1"
        private const val DEFAULT_CLIENT_NAME = "Android Player"
    }

    private var discoveryManager: NsdDiscoveryManager? = null
    
    // Track if we're in auto-connect mode or manual mode
    private val _isAutoMode = MutableStateFlow(true)
    val isAutoMode: StateFlow<Boolean> = _isAutoMode.asStateFlow()
    
    // Track if we've attempted auto-connect to avoid multiple attempts
    private var hasAutoConnected = false
    
    // Track current connection attempt
    private var currentServerAddress: String? = null

    /**
     * Starts auto-connect process:
     * 1. Starts mDNS discovery
     * 2. Auto-connects to first discovered server
     * 3. Continues discovery in background
     */
    fun startAutoConnect() {
        Log.d(TAG, "Starting auto-connect mode")
        _isAutoMode.value = true
        hasAutoConnected = false
        
        startDiscovery()
    }

    /**
     * Stops auto-connect and enters manual mode.
     * User must explicitly choose servers from this point.
     */
    fun enterManualMode() {
        Log.d(TAG, "Entering manual mode")
        _isAutoMode.value = false
    }

    /**
     * User-initiated disconnect. Enters manual mode.
     */
    fun disconnect() {
        Log.d(TAG, "User disconnect - entering manual mode")
        _isAutoMode.value = false
        currentServerAddress = null
        client.disconnect()
    }

    /**
     * User-initiated connection to specific server.
     * Enters manual mode and connects.
     */
    fun connectManually(server: ServerInfo, clientId: String = DEFAULT_CLIENT_ID, clientName: String = DEFAULT_CLIENT_NAME) {
        Log.d(TAG, "Manual connect to: ${server.name} at ${server.address}")
        _isAutoMode.value = false
        currentServerAddress = server.address
        
        client.connect(server.wsUrl, clientId, clientName)
        ServerRepository.addToRecent(server)
    }

    /**
     * Starts discovery and sets up auto-connect listener.
     */
    private fun startDiscovery() {
        if (discoveryManager != null) {
            Log.d(TAG, "Discovery already running")
            return
        }

        discoveryManager = NsdDiscoveryManager(
            context = context,
            listener = object : NsdDiscoveryManager.DiscoveryListener {
                override fun onServerDiscovered(name: String, address: String, path: String) {
                    Log.i(TAG, "Discovered: $name at $address$path")
                    val server = ServerInfo(name, address, path)
                    ServerRepository.addDiscoveredServer(server)
                    
                    // Auto-connect to first server if in auto mode and haven't connected yet
                    if (_isAutoMode.value && !hasAutoConnected && currentServerAddress == null) {
                        Log.i(TAG, "Auto-connecting to first discovered server: $name")
                        hasAutoConnected = true
                        currentServerAddress = address
                        
                        client.connect(server.wsUrl, DEFAULT_CLIENT_ID, DEFAULT_CLIENT_NAME)
                        ServerRepository.addToRecent(server)
                    }
                }
                
                override fun onServerLost(name: String) {
                    Log.i(TAG, "Server lost: $name")
                    // Note: We don't auto-reconnect on server loss
                    // User can manually reconnect if desired
                }
                
                override fun onDiscoveryStarted() {
                    Log.d(TAG, "Discovery started")
                }
                
                override fun onDiscoveryStopped() {
                    Log.d(TAG, "Discovery stopped")
                }
                
                override fun onDiscoveryError(error: String) {
                    Log.e(TAG, "Discovery error: $error")
                }
            }
        )
        
        discoveryManager?.startDiscovery()
    }

    /**
     * Stops discovery (typically called when app is destroyed).
     */
    fun stopDiscovery() {
        Log.d(TAG, "Stopping discovery")
        discoveryManager?.stopDiscovery()
        discoveryManager = null
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        Log.d(TAG, "Cleanup")
        stopDiscovery()
        currentServerAddress = null
    }

    /**
     * Check if currently connected to a server.
     */
    fun isConnected(): Boolean {
        return client.isConnected()
    }

    /**
     * Get current server address if connected.
     */
    fun getCurrentServerAddress(): String? = currentServerAddress
}
