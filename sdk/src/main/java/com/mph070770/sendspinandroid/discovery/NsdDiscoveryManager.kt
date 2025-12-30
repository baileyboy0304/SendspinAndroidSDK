package com.mph070770.sendspinandroid.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Manages mDNS service discovery using Android's native NsdManager.
 *
 * Discovers SendSpin servers advertising via mDNS with the service type
 * "_sendspin-server._tcp" as specified in the SendSpin protocol.
 *
 * Why NsdManager:
 * - Native Android implementation with proper network stack integration
 * - Handles WiFi multicast lock automatically
 * - Respects Android network permissions and restrictions
 * - More reliable than third-party mDNS libraries on Android
 *
 * ## Usage
 * ```kotlin
 * val discovery = NsdDiscoveryManager(context, object : NsdDiscoveryManager.DiscoveryListener {
 *     override fun onServerDiscovered(name: String, address: String, path: String) {
 *         // Connect to server at ws://$address$path
 *     }
 *     override fun onServerLost(name: String) {
 *         // Server went offline
 *     }
 * })
 * discovery.startDiscovery()
 * ```
 */
class NsdDiscoveryManager(
    private val context: Context,
    private val listener: DiscoveryListener
) {
    companion object {
        private const val TAG = "NsdDiscoveryManager"
        // SendSpin mDNS service type (as per protocol spec)
        private const val SERVICE_TYPE = "_sendspin-server._tcp."
    }

    /**
     * Callback interface for discovery events.
     */
    interface DiscoveryListener {
        /**
         * Called when a SendSpin server is discovered.
         *
         * @param name Service name (server's advertised name)
         * @param address Host:port address (e.g., "192.168.1.100:8927")
         * @param path WebSocket path from TXT records (default: /sendspin)
         */
        fun onServerDiscovered(name: String, address: String, path: String = "/sendspin")
        
        /**
         * Called when a previously discovered server goes offline.
         *
         * @param name Service name of the lost server
         */
        fun onServerLost(name: String)
        
        /**
         * Called when discovery starts successfully.
         */
        fun onDiscoveryStarted()
        
        /**
         * Called when discovery stops.
         */
        fun onDiscoveryStopped()
        
        /**
         * Called when a discovery error occurs.
         *
         * @param error Human-readable error message
         */
        fun onDiscoveryError(error: String)
    }

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isDiscovering = false

    // Track services we're currently resolving to avoid duplicate resolutions
    private val resolvingServices = mutableSetOf<String>()

    /**
     * Starts mDNS discovery for SendSpin servers.
     *
     * Must be called from main thread (NsdManager callbacks require Looper).
     */
    fun startDiscovery() {
        if (isDiscovering) {
            Log.w(TAG, "Discovery already running")
            return
        }

        // Acquire multicast lock first (required for mDNS)
        acquireMulticastLock()

        // Initialize NsdManager
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        // Create discovery listener
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started for $serviceType")
                isDiscovering = true
                listener.onDiscoveryStarted()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                // Resolve to get IP address and port
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                listener.onServerLost(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for $serviceType")
                isDiscovering = false
                listener.onDiscoveryStopped()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                val errorMsg = nsdErrorToString(errorCode)
                Log.e(TAG, "Start discovery failed: $errorMsg (code: $errorCode)")
                isDiscovering = false
                listener.onDiscoveryError("Failed to start discovery: $errorMsg")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                val errorMsg = nsdErrorToString(errorCode)
                Log.e(TAG, "Stop discovery failed: $errorMsg (code: $errorCode)")
            }
        }

        // Start discovery
        Log.d(TAG, "Starting NSD discovery for $SERVICE_TYPE")
        try {
            nsdManager?.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            listener.onDiscoveryError("Failed to start discovery: ${e.message}")
            releaseMulticastLock()
        }
    }

    /**
     * Resolves a discovered service to get its IP address and port.
     *
     * Note: NsdManager can only resolve one service at a time on older Android versions.
     * We use a tracking set to avoid duplicate resolution attempts.
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName

        // Avoid duplicate resolutions
        synchronized(resolvingServices) {
            if (resolvingServices.contains(serviceName)) {
                Log.d(TAG, "Already resolving $serviceName, skipping")
                return
            }
            resolvingServices.add(serviceName)
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                val errorMsg = nsdErrorToString(errorCode)
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorMsg")
                synchronized(resolvingServices) {
                    resolvingServices.remove(serviceName)
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                synchronized(resolvingServices) {
                    resolvingServices.remove(serviceName)
                }

                val host = serviceInfo.host?.hostAddress
                val port = serviceInfo.port

                if (host != null && port > 0) {
                    val address = "$host:$port"

                    // Extract path from TXT records (key: "path")
                    val attributes = try {
                        serviceInfo.attributes
                    } catch (e: Exception) {
                        emptyMap<String, ByteArray>()
                    }

                    // Log TXT records for debugging
                    if (attributes.isNotEmpty()) {
                        Log.d(TAG, "TXT records for ${serviceInfo.serviceName}:")
                        attributes.forEach { (key, value) ->
                            val valueStr = value?.let { String(it, Charsets.UTF_8) } ?: "(null)"
                            Log.d(TAG, "  $key = $valueStr")
                        }
                    }

                    // Get path with default
                    var path = attributes["path"]?.let { String(it, Charsets.UTF_8) } ?: "/sendspin"
                    if (!path.startsWith("/")) {
                        path = "/$path"
                    }

                    Log.i(TAG, "Server discovered: ${serviceInfo.serviceName} at $address path=$path")
                    listener.onServerDiscovered(serviceInfo.serviceName, address, path)
                } else {
                    Log.w(TAG, "Service resolved but missing host/port: ${serviceInfo.serviceName}")
                }
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
            synchronized(resolvingServices) {
                resolvingServices.remove(serviceName)
            }
        }
    }

    /**
     * Stops mDNS discovery.
     */
    fun stopDiscovery() {
        if (!isDiscovering) {
            Log.d(TAG, "Discovery not running")
            return
        }

        try {
            discoveryListener?.let { listener ->
                nsdManager?.stopServiceDiscovery(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery", e)
        }

        releaseMulticastLock()
        isDiscovering = false
    }

    /**
     * Acquires multicast lock for mDNS discovery.
     *
     * Why needed: Android filters multicast packets by default to save battery.
     * mDNS requires receiving multicast packets on 224.0.0.251.
     */
    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("SendspinAndroid_NSD").apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Multicast lock acquired")
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Multicast lock released")
            }
            multicastLock = null
        }
    }

    /**
     * Converts NSD error codes to human-readable strings.
     */
    private fun nsdErrorToString(errorCode: Int): String = when (errorCode) {
        NsdManager.FAILURE_ALREADY_ACTIVE -> "Already active"
        NsdManager.FAILURE_INTERNAL_ERROR -> "Internal error"
        NsdManager.FAILURE_MAX_LIMIT -> "Max limit reached"
        else -> "Unknown error"
    }

    /**
     * Returns whether discovery is currently running.
     */
    fun isDiscovering(): Boolean = isDiscovering

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        stopDiscovery()
        nsdManager = null
        discoveryListener = null
    }
}
