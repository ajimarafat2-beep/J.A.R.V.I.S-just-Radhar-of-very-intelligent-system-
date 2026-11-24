package com.example.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ShieldLiveState(
    val shieldServiceActive: Boolean = false,
    val continuousProtectionEnabled: Boolean = true,
    val isScanningNow: Boolean = false,
    val report: DeepDeviceScanReport = DeepDeviceScanReport(),
    val latestAlertMessage: String? = null
)

/**
 * Shared state holder connecting [JarvisShieldService], [BootSecurityReceiver], ViewModel, and UI.
 */
object JarvisShieldStateHolder {
    private val _state = MutableStateFlow(ShieldLiveState())
    val state: StateFlow<ShieldLiveState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncPreference(context: Context) {
        val preferred = JarvisSecurityEngine.isContinuousShieldPreferred(context)
        _state.update { it.copy(continuousProtectionEnabled = preferred) }
    }

    fun setServiceActive(active: Boolean) {
        _state.update { it.copy(shieldServiceActive = active) }
    }

    fun setContinuousEnabled(context: Context, enabled: Boolean) {
        JarvisSecurityEngine.setContinuousShieldPreferred(context, enabled)
        _state.update { it.copy(continuousProtectionEnabled = enabled) }
        if (enabled) startShield(context) else stopShield(context)
    }

    fun startShield(context: Context) {
        runCatching {
            val intent = Intent(context, JarvisShieldService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopShield(context: Context) {
        runCatching {
            context.stopService(Intent(context, JarvisShieldService::class.java))
        }
        _state.update { it.copy(shieldServiceActive = false) }
    }

    fun scanNowAsync(context: Context, onComplete: ((DeepDeviceScanReport) -> Unit)? = null) {
        scope.launch {
            val report = scanNowSuspend(context)
            onComplete?.invoke(report)
        }
    }

    suspend fun scanNowSuspend(context: Context): DeepDeviceScanReport {
        _state.update { it.copy(isScanningNow = true) }
        val report = JarvisSecurityEngine.runDeepDeviceScan(context)
        val alert = if (report.highRiskApps.isNotEmpty()) {
            "Alert: ${report.highRiskApps.size} high-risk threat app(s) detected (${report.highRiskApps.joinToString { it.appName }}). Tap Delete to remove."
        } else {
            null
        }
        _state.update {
            it.copy(
                isScanningNow = false,
                report = report,
                latestAlertMessage = alert
            )
        }
        return report
    }

    internal fun updateOnPackageAlert(message: String) {
        _state.update { it.copy(latestAlertMessage = message) }
    }
}

/**
 * Always-On Foreground Security Shield Service:
 * - Monitors real-time app installations & updates via [ACTION_PACKAGE_ADDED] / [ACTION_PACKAGE_REPLACED].
 * - Periodically scans all installed apps & OS integrity.
 * - Fires an immediate alert notification when a high-risk/virus-pattern app is detected.
 */
class JarvisShieldService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            val action = intent?.action ?: return
            val pkg = intent.data?.schemeSpecificPart ?: return

            if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
                serviceScope.launch {
                    val audit = JarvisSecurityEngine.inspectSinglePackage(ctx, pkg)
                    val report = JarvisShieldStateHolder.scanNowSuspend(ctx)
                    updateOngoingNotification(report)
                    if (audit != null && audit.riskLevel != ThreatLevel.SAFE && !audit.isTrustedWhitelisted) {
                        val alertMsg = "Threat detected in ${audit.appName}: ${audit.riskFlags.firstOrNull() ?: "Suspicious behavior"}"
                        JarvisShieldStateHolder.updateOnPackageAlert(alertMsg)
                        postThreatAlertNotification(audit)
                    }
                }
            } else if (action == Intent.ACTION_PACKAGE_REMOVED) {
                serviceScope.launch {
                    val report = JarvisShieldStateHolder.scanNowSuspend(ctx)
                    updateOngoingNotification(report)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = buildOngoingNotification(
            "J.A.R.V.I.S. 24/7 Threat Shield Active",
            "Monitoring new installs, permissions & phone integrity."
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_SHIELD_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_SHIELD_ID, initialNotification)
        }
        JarvisShieldStateHolder.setServiceActive(true)

        if (periodicJob?.isActive != true) {
            periodicJob = serviceScope.launch {
                while (isActive) {
                    val report = JarvisShieldStateHolder.scanNowSuspend(applicationContext)
                    updateOngoingNotification(report)
                    report.highRiskApps.firstOrNull()?.let { postThreatAlertNotification(it) }
                    delay(SCAN_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        JarvisShieldStateHolder.setServiceActive(false)
        runCatching { unregisterReceiver(packageReceiver) }
        periodicJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val ongoing = NotificationChannel(
                CHANNEL_SHIELD,
                "J.A.R.V.I.S Always-On Shield",
                NotificationManager.IMPORTANCE_LOW
            )
            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                "J.A.R.V.I.S Threat & Virus Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ongoing)
            nm.createNotificationChannel(alerts)
        }
    }

    private fun updateOngoingNotification(report: DeepDeviceScanReport) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val title = if (report.highRiskApps.isEmpty()) {
            "J.A.R.V.I.S. Shield: Phone Secure (${report.overallScore}%)"
        } else {
            "J.A.R.V.I.S. Alert: ${report.highRiskApps.size} Threat(s) Detected!"
        }
        val body = "${report.totalAppsScanned} apps scanned • ${report.highRiskApps.size} high-risk • ${report.cautionApps.size} caution"
        nm.notify(NOTIF_SHIELD_ID, buildOngoingNotification(title, body))
    }

    private fun buildOngoingNotification(title: String, body: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_SHIELD)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun postThreatAlertNotification(audit: AppSecurityAudit) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val uninstallPending = PendingIntent.getActivity(
            this,
            audit.packageName.hashCode(),
            JarvisSecurityEngine.createUninstallIntent(audit.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Threat Found: ${audit.appName}")
            .setContentText("Score ${audit.threatScore}/100 — Tap to uninstall and protect your phone.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(uninstallPending)
            .build()
        nm.notify(NOTIF_ALERT_BASE + (audit.packageName.hashCode() and 0x7FFF), notif)
    }

    companion object {
        private const val CHANNEL_SHIELD = "jarvis_shield_channel"
        private const val CHANNEL_ALERTS = "jarvis_threat_alerts"
        private const val NOTIF_SHIELD_ID = 4101
        private const val NOTIF_ALERT_BASE = 4200
        private const val SCAN_INTERVAL_MS = 180_000L
    }
}
