package com.mph070770.sendspinandroid.headless

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Application class for headless demo.
 * Starts HeadlessService on app launch.
 */
class HeadlessApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "HeadlessApplication created - starting service")
        
        // Start headless service immediately
        val serviceIntent = Intent(this, HeadlessService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    companion object {
        private const val TAG = "HeadlessApplication"
    }
}
