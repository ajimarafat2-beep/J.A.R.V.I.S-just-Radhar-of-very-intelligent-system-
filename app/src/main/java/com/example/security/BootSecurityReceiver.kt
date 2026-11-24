package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts the Always-On J.A.R.V.I.S. Threat Shield automatically when the phone boots
 * if continuous protection is enabled.
 */
class BootSecurityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED &&
            JarvisSecurityEngine.isContinuousShieldPreferred(ctx)
        ) {
            JarvisShieldStateHolder.startShield(ctx)
        }
    }
}
