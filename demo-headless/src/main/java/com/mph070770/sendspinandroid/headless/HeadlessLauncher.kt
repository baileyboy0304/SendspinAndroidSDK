package com.mph070770.sendspinandroid.headless

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Launcher activity that starts HeadlessService and finishes immediately.
 * Required for Android Studio to launch the app, but app runs headless after that.
 */
class HeadlessLauncher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start the headless service
        val serviceIntent = Intent(this, HeadlessService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        // Finish immediately - service runs in background
        finish()
    }
}
