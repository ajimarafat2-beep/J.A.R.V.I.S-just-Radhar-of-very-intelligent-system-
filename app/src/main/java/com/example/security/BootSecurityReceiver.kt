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
            // On Android 14+ / strict OEMs, background dataSync FGS at boot may be blocked;
            // runCatching ensures boot never crashes and shield resumes when app opens.
            runCatching {
                JarvisShieldStateHolder.startShield(ctx)
            }
        }
    }
}
