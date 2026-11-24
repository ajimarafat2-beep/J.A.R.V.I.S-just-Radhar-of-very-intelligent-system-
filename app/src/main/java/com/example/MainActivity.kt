package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agent.AgentRoute
import com.example.agent.JarvisAgentViewModel
import com.example.ai.configureAppCheck
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.JarvisCreationsRepository
import com.example.permissions.PermissionHelper
import com.example.ui.JarvisAuthScreen
import com.example.ui.JarvisBluetoothScreen
import com.example.ui.JarvisChatScreen
import com.example.ui.JarvisConnectorScreen
import com.example.ui.JarvisEdgeBorderOverlay
import com.example.ui.JarvisHologramOrb
import com.example.ui.JarvisPhoneControlScreen
import com.example.ui.JarvisSecurityScreen
import com.example.ui.JarvisSettingsScreen
import com.example.ui.JarvisSetupScreen
import com.example.ui.JarvisSmartHomeScreen
import com.example.ui.JarvisStudioScreen
import com.example.ui.JarvisTaskQueueScreen
import com.example.ui.JarvisWifiScreen
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var assistTriggerToken by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        if (intent?.action == Intent.ACTION_ASSIST) {
            assistTriggerToken++
        }
        setContent {
            JarvisTheme {
                JarvisAppRoot(assistTriggerToken = assistTriggerToken)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST) {
            assistTriggerToken++
        }
    }
}

@Composable
private fun JarvisAppRoot(assistTriggerToken: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val onboardingPrefs = remember(context) {
        context.getSharedPreferences("jarvis_onboarding", Context.MODE_PRIVATE)
    }
    var setupCompleted by rememberSaveable {
        mutableStateOf(onboardingPrefs.getBoolean("setup_done", false))
    }
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    when {
        !setupCompleted -> {
            JarvisSetupScreen(
                initialAgreementAccepted = onboardingPrefs.getBoolean("privacy_accepted", false),
                onContinueToSignIn = {
                    onboardingPrefs.edit()
                        .putBoolean("privacy_accepted", true)
                        .putBoolean("setup_done", true)
                        .apply()
                    setupCompleted = true
                }
            )
        }
        currentUser == null -> {
            JarvisAuthScreen(
                onAuthSuccess = { /* authStateFlow emits signed-in user automatically */ },
                onBackToSetup = { setupCompleted = false }
            )
        }
        else -> {
            val signedInUser = currentUser!!
            key(signedInUser.uid) {
                val app = context.applicationContext as Application
                val creationsRepository = remember(signedInUser.uid) {
                    JarvisCreationsRepository(app)
                }
                val agentViewModel: JarvisAgentViewModel = viewModel(
                    key = signedInUser.uid,
                    factory = JarvisAgentViewModel.provideFactory(
                        application = app,
                        currentUserId = signedInUser.uid,
                        creationsRepository = creationsRepository,
                        userDisplayName = signedInUser.displayName,
                        userEmail = signedInUser.email,
                        userPhotoUrl = signedInUser.photoUrl?.toString()
                    )
                )
                JarvisWorkspaceApp(
                    assistTriggerToken = assistTriggerToken,
                    currentUser = signedInUser,
                    agentViewModel = agentViewModel,
                    onReopenSetup = { setupCompleted = false },
                    onSignOut = {
                        signOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = {},
                            onSignOutComplete = {}
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JarvisWorkspaceApp(
    assistTriggerToken: Int = 0,
    currentUser: FirebaseUser,
    agentViewModel: JarvisAgentViewModel,
    onReopenSetup: () -> Unit,
    onSignOut: () -> Unit
) {
    LaunchedEffect(assistTriggerToken) {
        if (assistTriggerToken > 0) {
            agentViewModel.onSystemAssistInvoked()
        }
    }
    val agentState by agentViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentRoute by rememberSaveable { mutableStateOf(AgentRoute.CHAT) }

    var pendingVoiceAfterMic by rememberSaveable { mutableStateOf(false) }
    var pendingWakeAfterMic by rememberSaveable { mutableStateOf(false) }

    val wirelessPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        agentViewModel.refreshPermissions()
    }

    val vaultUnlockLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        agentViewModel.onVaultUnlockedResult(result.resultCode == Activity.RESULT_OK)
    }

    val cameraAndMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        agentViewModel.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        agentViewModel.refreshPermissions()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                agentViewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[android.Manifest.permission.RECORD_AUDIO] == true ||
            PermissionHelper.hasMicrophonePermission(context)
        val shouldStartVoice = pendingVoiceAfterMic
        val shouldStartWake = pendingWakeAfterMic
        pendingVoiceAfterMic = false
        pendingWakeAfterMic = false
        agentViewModel.onMicPermissionResult(
            granted = granted,
            startListeningIfGranted = shouldStartVoice,
            enableWakeIfGranted = shouldStartWake
        )
    }

    if (currentRoute != AgentRoute.CHAT) {
        BackHandler {
            currentRoute = AgentRoute.CHAT
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = JarvisSurface,
                drawerContentColor = JarvisTextPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        JarvisHologramOrb(
                            size = 38.dp,
                            activeGold = true,
                            voiceRmsLevel = agentState.voiceRmsLevel
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "J.A.R.V.I.S",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisGold
                            )
                            Text(
                                text = "Full Phone & Multi-AI Suite",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted
                            )
                        }
                    }

                    HorizontalDivider(color = JarvisBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_chat), style = MaterialTheme.typography.titleMedium) },
                        icon = { JarvisHologramOrb(size = 24.dp, activeGold = true) },
                        selected = currentRoute == AgentRoute.CHAT,
                        onClick = {
                            currentRoute = AgentRoute.CHAT
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = JarvisSurfaceElevated,
                            selectedIconColor = JarvisGold,
                            selectedTextColor = JarvisGold,
                            unselectedContainerColor = JarvisSurface,
                            unselectedIconColor = JarvisGold,
                            unselectedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.testTag("drawer_nav_chat")
                    )

                    DrawerNavItem(
                        label = stringResource(R.string.nav_studio),
                        icon = Icons.Filled.Explore,
                        selected = currentRoute == AgentRoute.STUDIO,
                        testTag = "drawer_nav_studio"
                    ) {
                        currentRoute = AgentRoute.STUDIO
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_phone_control),
                        icon = Icons.Filled.PhoneAndroid,
                        selected = currentRoute == AgentRoute.PHONE_CONTROL,
                        testTag = "drawer_nav_phone_control"
                    ) {
                        agentViewModel.refreshPermissions()
                        currentRoute = AgentRoute.PHONE_CONTROL
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_smart_home),
                        icon = Icons.Filled.Home,
                        selected = currentRoute == AgentRoute.SMART_HOME,
                        testTag = "drawer_nav_smart_home"
                    ) {
                        currentRoute = AgentRoute.SMART_HOME
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_queue),
                        icon = Icons.Filled.Queue,
                        selected = currentRoute == AgentRoute.QUEUE,
                        badgeCount = agentState.pendingTaskCount,
                        testTag = "drawer_nav_queue"
                    ) {
                        currentRoute = AgentRoute.QUEUE
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_connectors),
                        icon = Icons.Filled.AccountBalance,
                        selected = currentRoute == AgentRoute.CONNECTORS,
                        testTag = "drawer_nav_connectors"
                    ) {
                        agentViewModel.refreshConnectors()
                        currentRoute = AgentRoute.CONNECTORS
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_bluetooth),
                        icon = Icons.Filled.Bluetooth,
                        selected = currentRoute == AgentRoute.BLUETOOTH,
                        testTag = "drawer_nav_bluetooth"
                    ) {
                        currentRoute = AgentRoute.BLUETOOTH
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_wifi),
                        icon = Icons.Filled.Wifi,
                        selected = currentRoute == AgentRoute.WIFI,
                        testTag = "drawer_nav_wifi"
                    ) {
                        currentRoute = AgentRoute.WIFI
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_security),
                        icon = Icons.Filled.Shield,
                        selected = currentRoute == AgentRoute.SECURITY,
                        badgeCount = agentState.shieldState.report.highRiskApps.size,
                        testTag = "drawer_nav_security"
                    ) {
                        currentRoute = AgentRoute.SECURITY
                        scope.launch { drawerState.close() }
                    }

                    DrawerNavItem(
                        label = stringResource(R.string.nav_settings),
                        icon = Icons.Filled.Settings,
                        selected = currentRoute == AgentRoute.SETTINGS,
                        testTag = "drawer_nav_settings"
                    ) {
                        currentRoute = AgentRoute.SETTINGS
                        scope.launch { drawerState.close() }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = JarvisSurfaceElevated,
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Camera, Mic & Wireless Access",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            (PermissionHelper.cameraAndMicrophonePermissions().toList() +
                                PermissionHelper.allWirelessPermissions().toList()).forEach { perm ->
                                val granted = PermissionHelper.isPermissionGranted(context, perm)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (granted) JarvisEmerald else JarvisGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = PermissionHelper.describePermission(perm),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextPrimary
                                    )
                                }
                            }

                            if (!agentState.cameraPermissionGranted || !agentState.micPermissionGranted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        cameraAndMicLauncher.launch(PermissionHelper.cameraAndMicrophonePermissions())
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = JarvisGold,
                                        contentColor = JarvisNavy
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("drawer_grant_camera_mic_button")
                                ) {
                                    Text("Grant Camera & Mic", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!agentState.wirelessPermissionsGranted) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        wirelessPermissionLauncher.launch(
                                            PermissionHelper.allWirelessPermissions()
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = JarvisCyan,
                                        contentColor = JarvisNavy
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("drawer_grant_wireless_button")
                                ) {
                                    Text("Grant Wireless", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = JarvisSurfaceElevated,
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = JarvisEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser.displayName?.takeIf { it.isNotBlank() } ?: "Google Account",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = JarvisTextPrimary
                                    )
                                    currentUser.email?.let { mail ->
                                        Text(
                                            text = mail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JarvisTextMuted
                                        )
                                    }
                                    Text(
                                        text = "Free Full Access • No Subscription",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisEmerald
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        onReopenSetup()
                                    },
                                    border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("drawer_reopen_setup_button")
                                ) {
                                    Text("Permissions", color = JarvisCyan, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        onSignOut()
                                    },
                                    border = BorderStroke(1.dp, JarvisAlertRed.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("drawer_sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Sign out",
                                        tint = JarvisAlertRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sign Out", color = JarvisAlertRed, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        JarvisEdgeBorderOverlay(
            active = agentState.edgeBorderActive,
            voiceRmsLevel = agentState.voiceRmsLevel
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                containerColor = JarvisNavy,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = JarvisNavy,
                            titleContentColor = JarvisTextPrimary
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("open_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open navigation menu",
                                    tint = JarvisTextPrimary
                                )
                            }
                        },
                        title = {
                            Surface(
                                color = JarvisSurface,
                                border = BorderStroke(1.dp, JarvisBorder),
                                shape = RoundedCornerShape(999.dp),
                                onClick = {
                                    val nextIndex = (AgentRoute.entries.indexOf(currentRoute) + 1) % AgentRoute.entries.size
                                    currentRoute = AgentRoute.entries[nextIndex]
                                },
                                modifier = Modifier.testTag("top_mode_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    JarvisHologramOrb(
                                        size = 20.dp,
                                        activeGold = true,
                                        voiceRmsLevel = agentState.voiceRmsLevel
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (currentRoute) {
                                            AgentRoute.CHAT -> if (agentState.isOnline && !agentState.settings.offlineIsolationMode) "J.A.R.V.I.S • AI Agent" else "J.A.R.V.I.S • Agent Offline"
                                            AgentRoute.STUDIO -> "J.A.R.V.I.S • Studio"
                                            AgentRoute.PHONE_CONTROL -> "J.A.R.V.I.S • Phone Control"
                                            AgentRoute.SMART_HOME -> "J.A.R.V.I.S • Google Home"
                                            AgentRoute.QUEUE -> "J.A.R.V.I.S • Queue (${agentState.pendingTaskCount})"
                                            AgentRoute.CONNECTORS -> "J.A.R.V.I.S • Airbnb & Bank"
                                            AgentRoute.BLUETOOTH -> "J.A.R.V.I.S • Bluetooth"
                                            AgentRoute.WIFI -> "J.A.R.V.I.S • Wi-Fi"
                                            AgentRoute.SECURITY -> "J.A.R.V.I.S • 24/7 Threat Shield"
                                            AgentRoute.SETTINGS -> "J.A.R.V.I.S • Settings"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = JarvisTextPrimary
                                    )
                                }
                            }
                        },
                    actions = {
                        IconButton(
                            onClick = agentViewModel::toggleVoiceReply,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_tts_button")
                        ) {
                            Icon(
                                imageVector = if (agentState.settings.voiceReplyEnabled) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = "Toggle voice reply",
                                tint = if (agentState.settings.voiceReplyEnabled) JarvisCyan else JarvisTextMuted
                            )
                        }
                        IconButton(
                            onClick = {
                                agentViewModel.startNewChat()
                                currentRoute = AgentRoute.CHAT
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddComment,
                                contentDescription = stringResource(R.string.chat_new),
                                tint = JarvisTextPrimary
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            val screenModifier = Modifier.padding(innerPadding)
            when (currentRoute) {
                AgentRoute.CHAT -> JarvisChatScreen(
                    uiState = agentState,
                    onSendMessage = agentViewModel::sendMessage,
                    onSpeakMessage = agentViewModel::speak,
                    onToggleLiveVoice = agentViewModel::startOrInterruptLiveVoice,
                    onToggleWakeWord = agentViewModel::toggleWakeWord,
                    onToggleEdgeBorder = agentViewModel::toggleEdgeBorderMode,
                    onNavigateRoute = { currentRoute = it },
                    onOpenUrl = agentViewModel::openExternalUrl,
                    onRequestWirelessPermissions = {
                        wirelessPermissionLauncher.launch(PermissionHelper.allWirelessPermissions())
                    },
                    onRequestMicPermission = {
                        pendingVoiceAfterMic = true
                        pendingWakeAfterMic = false
                        micPermissionLauncher.launch(PermissionHelper.microphonePermissions())
                    },
                    onRequestCameraAndMicPermission = {
                        cameraAndMicLauncher.launch(PermissionHelper.cameraAndMicrophonePermissions())
                    },
                    onDismissVoiceNotice = agentViewModel::dismissVoiceNotice,
                    modifier = screenModifier
                )

                AgentRoute.STUDIO -> JarvisStudioScreen(
                    mediaGenState = agentState.mediaGenState,
                    audioGenState = agentState.audioGenState,
                    videoGenState = agentState.videoGenState,
                    webBuildState = agentState.webBuildState,
                    visionState = agentState.visionState,
                    selectedAspectRatio = agentState.selectedAspectRatioName,
                    selectedVoiceName = agentState.selectedVoiceName,
                    cloudCreations = agentState.cloudCreations,
                    cloudCreationsError = agentState.cloudCreationsError,
                    discoveryLinks = agentState.discoveryLinks,
                    discoveryLoading = agentState.discoveryLoading,
                    discoverySummary = agentState.discoverySummary,
                    onSelectAspectRatio = agentViewModel::setSelectedAspectRatio,
                    onSelectVoiceName = agentViewModel::setSelectedVoice,
                    onGenerateImage = agentViewModel::generateStudioImage,
                    onGenerateAudio = agentViewModel::generateStudioAudio,
                    onGenerateVideo = agentViewModel::generateStudioVideo,
                    onDeleteCloudCreation = agentViewModel::deleteCloudCreation,
                    onBuildWebsite = agentViewModel::buildStudioWebsite,
                    onAnalyzeImage = agentViewModel::analyzeStudioImage,
                    onRunLiveDiscovery = agentViewModel::runLiveDiscovery,
                    onOpenUrl = agentViewModel::openExternalUrl,
                    modifier = screenModifier
                )

                AgentRoute.PHONE_CONTROL -> JarvisPhoneControlScreen(
                    controlState = agentState.phoneControlState,
                    onRefreshStatus = agentViewModel::refreshPermissions,
                    onRequestCameraAndMic = {
                        cameraAndMicLauncher.launch(PermissionHelper.cameraAndMicrophonePermissions())
                    },
                    onCapturedCameraFrame = { bmp ->
                        agentViewModel.sendMessage("Analyze what my camera sees right now, Sir.", bmp)
                        currentRoute = AgentRoute.CHAT
                    },
                    onToggleTorch = agentViewModel::togglePhoneTorch,
                    onSetBrightness = agentViewModel::setPhoneBrightness,
                    onToggleAutoBrightness = agentViewModel::setPhoneAutoBrightness,
                    onSetStreamVolume = agentViewModel::setPhoneStreamVolume,
                    onSetRingerMode = agentViewModel::setPhoneRingerMode,
                    onDispatchMediaKey = agentViewModel::dispatchPhoneMediaKey,
                    onGlobalAction = agentViewModel::executeGlobalPhoneAction,
                    onOpenSystemPanel = agentViewModel::openPhoneSystemPanel,
                    onOpenDialer = agentViewModel::openPhoneDialer,
                    onOpenCameraApp = agentViewModel::openPhoneCameraApp,
                    onSetTimerMinutes = agentViewModel::setPhoneQuickTimer,
                    onStartVoiceListening = agentViewModel::startOrInterruptLiveVoice,
                    modifier = screenModifier
                )

                AgentRoute.SMART_HOME -> JarvisSmartHomeScreen(
                    devices = agentState.smartDevices,
                    lanNodes = agentState.lanSmartNodes,
                    isScanningLan = agentState.isScanningLan,
                    hubConfig = agentState.smartHubConfig,
                    googleHomeInstalled = agentState.googleHomeInstalled,
                    statusMessage = agentState.smartHomeStatusMessage,
                    onOpenGoogleHome = agentViewModel::openGoogleHomeApp,
                    onOpenDeviceControls = agentViewModel::openSystemDeviceControls,
                    onRelayGoogleAssistant = agentViewModel::relayToGoogleAssistant,
                    onScanLan = agentViewModel::scanLanSmartDevices,
                    onSaveHubConfig = agentViewModel::saveSmartHubConfig,
                    onAddDevice = agentViewModel::addSmartDevice,
                    onRemoveDevice = agentViewModel::removeSmartDevice,
                    onTogglePower = agentViewModel::toggleSmartDevicePower,
                    onSetWashingCycle = agentViewModel::setWashingCycle,
                    onAdjustWashingRpm = agentViewModel::adjustWashingRpm,
                    onAdjustTemp = agentViewModel::adjustSmartTemperature,
                    onSetMode = agentViewModel::setSmartMode,
                    onAdjustTvVolume = agentViewModel::adjustTvVolume,
                    onAdjustLevel = agentViewModel::adjustSmartLevel,
                    onToggleDoorLock = agentViewModel::toggleSmartDoorLock,
                    modifier = screenModifier
                )

                AgentRoute.QUEUE -> JarvisTaskQueueScreen(
                    tasks = agentState.tasks,
                    metrics = agentState.taskMetrics,
                    nlpStatus = agentState.nlpStatus,
                    isOnline = agentState.isOnline,
                    offlineIsolationMode = agentState.settings.offlineIsolationMode,
                    onSyncPendingNow = agentViewModel::flushQueueNow,
                    onRetryTask = agentViewModel::retryQueueTask,
                    onPromotePriority = agentViewModel::promoteQueueTaskPriority,
                    onCancelTask = agentViewModel::cancelQueueTask,
                    onDeleteTask = agentViewModel::deleteQueueTask,
                    onClearCompleted = agentViewModel::clearCompletedTasks,
                    modifier = screenModifier
                )

                AgentRoute.CONNECTORS -> JarvisConnectorScreen(
                    installedApps = agentState.installedApps,
                    connectedWebsites = agentState.connectedWebsites,
                    isOnline = agentState.isOnline,
                    offlineIsolationMode = agentState.settings.offlineIsolationMode,
                    vaultUnlocked = agentState.vaultUnlocked,
                    vaultDeviceSecured = agentState.vaultDeviceSecured,
                    airbnbInstalled = agentState.airbnbInstalled,
                    airbnbIcalUrl = agentState.airbnbIcalUrl,
                    airbnbReservations = agentState.airbnbReservations,
                    airbnbSyncLoading = agentState.airbnbSyncLoading,
                    airbnbStatusMessage = agentState.airbnbStatusMessage,
                    detectedBankApps = agentState.detectedBankApps,
                    bankPortals = agentState.bankPortals,
                    accountStatusMessage = agentState.accountStatusMessage,
                    onRequestVaultUnlock = {
                        val unlockIntent = agentViewModel.createVaultUnlockIntent()
                        if (unlockIntent != null) {
                            vaultUnlockLauncher.launch(unlockIntent)
                        } else {
                            agentViewModel.onVaultUnlockedResult(true)
                        }
                    },
                    onOpenAirbnb = agentViewModel::openAirbnbSection,
                    onSyncAirbnbIcal = agentViewModel::syncAirbnbIcal,
                    onOpenBankPortal = agentViewModel::openBankPortalVerified,
                    onAddBankPortal = agentViewModel::addAndVerifyBankPortal,
                    onRemoveBankPortal = agentViewModel::removeBankPortal,
                    onRefreshApps = agentViewModel::refreshConnectors,
                    onLaunchApp = agentViewModel::launchInstalledApp,
                    onTogglePinApp = agentViewModel::togglePinApp,
                    onAddAndConnectWebsite = agentViewModel::addAndConnectWebsite,
                    onRemoveWebsite = agentViewModel::removeWebsite,
                    onRunWebSearch = { query ->
                        agentViewModel.sendMessage(query)
                        currentRoute = AgentRoute.CHAT
                    },
                    modifier = screenModifier
                )

                AgentRoute.BLUETOOTH -> JarvisBluetoothScreen(modifier = screenModifier)

                AgentRoute.WIFI -> JarvisWifiScreen(modifier = screenModifier)

                AgentRoute.SECURITY -> JarvisSecurityScreen(
                    shieldState = agentState.shieldState,
                    notificationPermissionGranted = agentState.notificationPermissionGranted,
                    onRequestNotificationPermission = {
                        val perms = PermissionHelper.notificationPermissions()
                        if (perms.isNotEmpty()) {
                            notificationPermissionLauncher.launch(perms)
                        }
                    },
                    onToggleContinuousShield = agentViewModel::toggleContinuousShield,
                    onRunDeepScan = agentViewModel::runDeepVirusScan,
                    onDeleteThreat = agentViewModel::deleteThreatPackage,
                    onDeleteNextThreat = agentViewModel::deleteNextThreat,
                    onTrustPackage = agentViewModel::trustAppInShield,
                    onUntrustPackage = agentViewModel::untrustAppInShield,
                    modifier = screenModifier
                )

                AgentRoute.SETTINGS -> JarvisSettingsScreen(
                    settings = agentState.settings,
                    micPermissionGranted = agentState.micPermissionGranted,
                    voiceDiagnostic = agentState.voiceDiagnostic,
                    onSelectLanguage = agentViewModel::setLanguage,
                    onSaveWakeWord = agentViewModel::setWakeWord,
                    onToggleWakeWord = agentViewModel::toggleWakeWord,
                    onRequestMicPermission = {
                        pendingVoiceAfterMic = false
                        pendingWakeAfterMic = true
                        micPermissionLauncher.launch(PermissionHelper.microphonePermissions())
                    },
                    onToggleLiveVoiceChat = agentViewModel::setLiveVoiceChatEnabled,
                    onToggleVoiceReply = agentViewModel::setVoiceReplyEnabled,
                    onSpeechRateChange = agentViewModel::setSpeechRate,
                    onSpeechPitchChange = agentViewModel::setSpeechPitch,
                    onRunVoiceCheck = { agentViewModel.runAgentAndVoiceCheck(speakResult = true) },
                    onApplyCrystalClearPreset = agentViewModel::applyCrystalClearVoicePreset,
                    onToggleOfflineIsolation = agentViewModel::setOfflineIsolationMode,
                    onClearChat = agentViewModel::startNewChat,
                    onResetDefaults = agentViewModel::resetSettings,
                    modifier = screenModifier
                )
            }
        }
        }
    }
}

@Composable
private fun DrawerNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.titleMedium) },
        icon = { Icon(icon, contentDescription = null) },
        badge = if (badgeCount > 0) {
            {
                Badge(containerColor = JarvisGold, contentColor = JarvisNavy) {
                    Text(badgeCount.toString())
                }
            }
        } else null,
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = JarvisSurfaceElevated,
            selectedIconColor = JarvisCyan,
            selectedTextColor = JarvisCyan,
            unselectedContainerColor = JarvisSurface,
            unselectedIconColor = JarvisTextMuted,
            unselectedTextColor = JarvisTextPrimary
        ),
        modifier = Modifier.testTag(testTag)
    )
}
