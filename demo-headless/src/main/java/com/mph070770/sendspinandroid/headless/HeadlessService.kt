package com.mph070770.sendspinandroid.headless

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mph070770.sendspinandroid.SendspinClient
import com.mph070770.sendspinandroid.discovery.AutoConnectManager
import com.mph070770.sendspinandroid.discovery.ServerRepository

class HeadlessService : Service() {

    private var client: SendspinClient? = null
    private var autoConnectManager: AutoConnectManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "HeadlessService created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        ServerRepository.initialize(this)
        client = SendspinClient.create(this)
        autoConnectManager = AutoConnectManager(this, client!!)
        autoConnectManager?.startAutoConnect()
        Log.i(TAG, "Sendspin client initialized and auto-connecting")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "HeadlessService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "HeadlessService destroyed")
        autoConnectManager?.cleanup()
        client?.disconnect()
        client?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sendspin Headless",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sendspin headless background playback"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sendspin Headless")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "HeadlessService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sendspin_headless"
    }
}