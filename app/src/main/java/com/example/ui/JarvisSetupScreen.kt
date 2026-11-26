package com.example.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.control.JarvisAccessibilityService
import com.example.permissions.PermissionHelper
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@Composable
fun JarvisSetupScreen(
    initialAgreementAccepted: Boolean,
    onContinueToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var privacyAccepted by remember { mutableStateOf(initialAgreementAccepted) }
    var termsExpanded by remember { mutableStateOf(false) }

    val accessibilityConnected by JarvisAccessibilityService.isConnected.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val cameraGranted = remember(refreshTick) { PermissionHelper.hasCameraPermission(context) }
    val micGranted = remember(refreshTick) { PermissionHelper.hasMicrophonePermission(context) }
    val wirelessGranted = remember(refreshTick) { PermissionHelper.hasAllWirelessPermissions(context) }
    val notificationGranted = remember(refreshTick) { PermissionHelper.hasNotificationPermission(context) }
    val writeSettingsGranted = remember(refreshTick) { PermissionHelper.hasWriteSettingsPermission(context) }
    val dndGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }
    }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    val enabledCount = listOf(
        cameraGranted,
        micGranted,
        accessibilityConnected,
        writeSettingsGranted,
        dndGranted,
        wirelessGranted,
        notificationGranted
    ).count { it }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    JarvisHologramOrb(size = 76.dp, activeGold = true)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "J.A.R.V.I.S // Setup & Permissions",
                        style = MaterialTheme.typography.headlineSmall,
                        color = JarvisGold,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Step 1 of 2: Turn on Permissions & Privacy Agreements → Step 2: Google Sign-In",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = JarvisEmerald.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, JarvisEmerald.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = JarvisEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Free Full Access • No Subscription Required",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { termsExpanded = !termsExpanded }
                        ) {
                            Icon(Icons.Filled.Policy, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Privacy Policy & Terms Agreement",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Tap to read camera, mic, accessibility & security disclosures",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                            Icon(
                                imageVector = if (termsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = "Toggle policy details",
                                tint = JarvisTextMuted
                            )
                        }

                        AnimatedVisibility(visible = termsExpanded) {
                            Surface(
                                color = JarvisSurfaceElevated,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "• Camera & Microphone: Used only when you invoke Live Vision, photo capture, or voice/wake-word commands. Private faces are never identified.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = "• Accessibility Service: Used strictly on your command to navigate screens, read active UI text, tap buttons, and type inside apps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = "• 24/7 Threat Shield & Ethical White-Hat Policy: Scans installed packages locally for malware indicators; uninstalls always open Android system confirmation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = "• Google Account Sync & No Subscription: Sign in with Google to save your AI Image, Audio, and Video Studio history privately. Zero subscription fees.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JarvisEmerald
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { privacyAccepted = !privacyAccepted }
                                .testTag("setup_privacy_agreement_row")
                        ) {
                            Checkbox(
                                checked = privacyAccepted,
                                onCheckedChange = { privacyAccepted = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = JarvisGold,
                                    checkmarkColor = JarvisNavy,
                                    uncheckedColor = JarvisTextMuted
                                ),
                                modifier = Modifier.testTag("setup_privacy_checkbox")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I agree to the J.A.R.V.I.S Privacy Policy, Ethical White-Hat Terms & Permission Disclosures.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Device & Agent Permissions ($enabledCount/7 Enabled)",
                    style = MaterialTheme.typography.titleSmall,
                    color = JarvisCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Button(
                    onClick = {
                        val allRuntime = buildSet {
                            addAll(PermissionHelper.cameraAndMicrophonePermissions())
                            addAll(PermissionHelper.allWirelessPermissions())
                            addAll(PermissionHelper.notificationPermissions())
                        }.toTypedArray()
                        runtimePermLauncher.launch(allRuntime)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("setup_grant_all_runtime_button")
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Camera, Mic, Wireless & Alert Permissions", fontWeight = FontWeight.Bold)
                }
            }

            item {
                SetupPermissionCard(
                    icon = Icons.Filled.CameraAlt,
                    title = "Camera & Microphone",
                    description = "Required for Live Camera Vision, hands-free voice, and custom wake word.",
                    granted = cameraGranted && micGranted,
                    actionLabel = if (cameraGranted && micGranted) "Enabled" else "Grant Camera & Mic",
                    testTag = "setup_perm_camera_mic",
                    onClick = {
                        runtimePermLauncher.launch(PermissionHelper.cameraAndMicrophonePermissions())
                    }
                )
            }

            item {
                SetupPermissionCard(
                    icon = Icons.Filled.AccessibilityNew,
                    title = "Accessibility (Full Mobile Control)",
                    description = "Enables J.A.R.V.I.S Agent to tap UI buttons, type text, scroll, lock, screenshot & control apps.",
                    granted = accessibilityConnected,
                    actionLabel = if (accessibilityConnected) "Connected" else "Open Accessibility Settings",
                    testTag = "setup_perm_accessibility",
                    onClick = {
                        runCatching { context.startActivity(PermissionHelper.createAccessibilitySettingsIntent()) }
                    }
                )
            }

            item {
                SetupPermissionCard(
                    icon = Icons.Filled.Tune,
                    title = "Modify System Settings",
                    description = "Allows J.A.R.V.I.S to adjust screen brightness and display settings directly.",
                    granted = writeSettingsGranted,
                    actionLabel = if (writeSettingsGranted) "Enabled" else "Allow System Settings",
                    testTag = "setup_perm_write_settings",
                    onClick = {
                        runCatching { context.startActivity(PermissionHelper.createWriteSettingsIntent(context)) }
                    }
                )
            }

            item {
                SetupPermissionCard(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications & Do Not Disturb Control",
                    description = "Supports Silent/Ring mode switching and 24/7 Threat Shield alerts.",
                    granted = dndGranted && notificationGranted,
                    actionLabel = when {
                        !notificationGranted -> "Allow Notifications"
                        !dndGranted -> "Allow DND Control"
                        else -> "Enabled"
                    },
                    testTag = "setup_perm_dnd_notifications",
                    onClick = {
                        if (!notificationGranted && PermissionHelper.notificationPermissions().isNotEmpty()) {
                            runtimePermLauncher.launch(PermissionHelper.notificationPermissions())
                        } else {
                            runCatching { context.startActivity(PermissionHelper.createDndPolicySettingsIntent()) }
                        }
                    }
                )
            }

            item {
                SetupPermissionCard(
                    icon = Icons.Filled.Wifi,
                    title = "Bluetooth & Wi-Fi Scanning",
                    description = "Allows scanning and managing nearby Bluetooth devices and Wi-Fi networks.",
                    granted = wirelessGranted,
                    actionLabel = if (wirelessGranted) "Enabled" else "Grant Wireless",
                    testTag = "setup_perm_wireless",
                    onClick = {
                        runtimePermLauncher.launch(PermissionHelper.allWirelessPermissions())
                    }
                )
            }

            item {
                SetupPermissionCard(
                    icon = Icons.Filled.Settings,
                    title = "Default Phone Assistant & System Settings",
                    description = "Optional: Set J.A.R.V.I.S as your default phone digital assistant.",
                    granted = false,
                    actionLabel = "Open Assistant Settings",
                    testTag = "setup_perm_assistant",
                    onClick = {
                        val pm = context.packageManager
                        val target = listOf(
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
                            Intent(Settings.ACTION_SETTINGS)
                        ).firstOrNull { it.resolveActivity(pm) != null }
                            ?: Intent(Settings.ACTION_SETTINGS)
                        runCatching {
                            context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onContinueToSignIn,
                    enabled = privacyAccepted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisGold,
                        contentColor = JarvisNavy,
                        disabledContainerColor = JarvisSurfaceElevated,
                        disabledContentColor = JarvisTextMuted
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("setup_continue_to_signin_button")
                ) {
                    Text(
                        text = if (privacyAccepted) {
                            "Continue to Google Sign-In"
                        } else {
                            "Accept Privacy Policy & Terms Above First"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    if (privacyAccepted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Filled.ArrowForward, contentDescription = null)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SetupPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = JarvisSurface,
        border = BorderStroke(
            1.dp,
            if (granted) JarvisEmerald.copy(alpha = 0.55f) else JarvisBorder
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) JarvisEmerald else JarvisCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = JarvisTextPrimary)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                }
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Enabled",
                        tint = JarvisEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (!granted) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onClick,
                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag(testTag)
                ) {
                    Text(text = actionLabel, color = JarvisCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
