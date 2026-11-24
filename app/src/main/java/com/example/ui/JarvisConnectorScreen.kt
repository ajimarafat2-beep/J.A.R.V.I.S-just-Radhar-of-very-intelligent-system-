package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounts.AirbnbReservation
import com.example.accounts.BankPortalEntry
import com.example.connector.ConnectedWebsiteEntry
import com.example.connector.InstalledAppEntry
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@Composable
fun JarvisConnectorScreen(
    installedApps: List<InstalledAppEntry>,
    connectedWebsites: List<ConnectedWebsiteEntry>,
    isOnline: Boolean,
    offlineIsolationMode: Boolean,
    vaultUnlocked: Boolean,
    vaultDeviceSecured: Boolean,
    airbnbInstalled: Boolean,
    airbnbIcalUrl: String,
    airbnbReservations: List<AirbnbReservation>,
    airbnbSyncLoading: Boolean,
    airbnbStatusMessage: String?,
    detectedBankApps: List<InstalledAppEntry>,
    bankPortals: List<BankPortalEntry>,
    accountStatusMessage: String?,
    onRequestVaultUnlock: () -> Unit,
    onOpenAirbnb: (String) -> Unit,
    onSyncAirbnbIcal: (String) -> Unit,
    onOpenBankPortal: (String) -> Unit,
    onAddBankPortal: (String, String) -> Unit,
    onRemoveBankPortal: (String) -> Unit,
    onRefreshApps: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onTogglePinApp: (String) -> Unit,
    onAddAndConnectWebsite: (String, String) -> Unit,
    onRemoveWebsite: (String) -> Unit,
    onRunWebSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(2) }
    var appQuery by remember { mutableStateOf("") }
    var siteTitle by remember { mutableStateOf("") }
    var siteUrl by remember { mutableStateOf("") }
    var webSearchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 740.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        JarvisHologramOrb(size = 24.dp, activeGold = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "JARVIS // ACCOUNTS & CONNECTORS",
                            style = MaterialTheme.typography.titleLarge,
                            color = JarvisCyan
                        )
                    }
                    Text(
                        text = "Airbnb & Bank Vault, Installed Phone Apps, and Global Websites",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextMuted
                    )
                }
                IconButton(
                    onClick = onRefreshApps,
                    modifier = Modifier.size(48.dp).testTag("connectors_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh apps", tint = JarvisCyan)
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = JarvisSurface,
            contentColor = JarvisCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = JarvisCyan
                )
            },
            modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Apps (${installedApps.size})") },
                icon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                modifier = Modifier.testTag("connectors_tab_apps")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Websites (${connectedWebsites.size})") },
                icon = { Icon(Icons.Filled.Language, contentDescription = null) },
                modifier = Modifier.testTag("connectors_tab_websites")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Airbnb & Bank") },
                icon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                modifier = Modifier.testTag("connectors_tab_accounts")
            )
        }

        if (selectedTab == 2) {
            JarvisAccountsVaultTab(
                vaultUnlocked = vaultUnlocked,
                vaultDeviceSecured = vaultDeviceSecured,
                airbnbInstalled = airbnbInstalled,
                airbnbIcalUrl = airbnbIcalUrl,
                airbnbReservations = airbnbReservations,
                airbnbSyncLoading = airbnbSyncLoading,
                airbnbStatusMessage = airbnbStatusMessage,
                detectedBankApps = detectedBankApps,
                bankPortals = bankPortals,
                accountStatusMessage = accountStatusMessage,
                onRequestVaultUnlock = onRequestVaultUnlock,
                onOpenAirbnb = onOpenAirbnb,
                onSyncAirbnbIcal = onSyncAirbnbIcal,
                onLaunchBankApp = onLaunchApp,
                onOpenBankPortal = onOpenBankPortal,
                onAddBankPortal = onAddBankPortal,
                onRemoveBankPortal = onRemoveBankPortal,
                modifier = Modifier.weight(1f)
            )
        } else if (selectedTab == 0) {
            val filteredApps = remember(installedApps, appQuery) {
                if (appQuery.isBlank()) installedApps
                else installedApps.filter {
                    it.appName.contains(appQuery, ignoreCase = true) ||
                        it.packageName.contains(appQuery, ignoreCase = true)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).widthIn(max = 740.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = appQuery,
                        onValueChange = { appQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = JarvisCyan) },
                        label = { Text("Search installed apps") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("connectors_app_search_input")
                    )
                }
                if (filteredApps.isEmpty()) {
                    item {
                        Text(
                            text = "No matching apps found on this device.",
                            color = JarvisTextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                            border = BorderStroke(1.dp, if (app.isPinned) JarvisGold else JarvisBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = JarvisTextPrimary
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisTextMuted
                                    )
                                }
                                IconButton(
                                    onClick = { onTogglePinApp(app.packageName) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Pin app",
                                        tint = if (app.isPinned) JarvisGold else JarvisTextMuted
                                    )
                                }
                                Button(
                                    onClick = { onLaunchApp(app.packageName) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = JarvisCyan,
                                        contentColor = JarvisNavy
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text("Open", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).widthIn(max = 740.dp).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (!isOnline || offlineIsolationMode) {
                                    "Offline / Isolation active — website connections will auto-queue and launch once online."
                                } else {
                                    "Connect to any website or run a global search"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!isOnline || offlineIsolationMode) JarvisGold else JarvisCyan
                            )
                            OutlinedTextField(
                                value = siteTitle,
                                onValueChange = { siteTitle = it },
                                singleLine = true,
                                label = { Text("Label (optional, e.g. OpenAI)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisBorder,
                                    focusedTextColor = JarvisTextPrimary,
                                    unfocusedTextColor = JarvisTextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("connectors_site_title_input")
                            )
                            OutlinedTextField(
                                value = siteUrl,
                                onValueChange = { siteUrl = it },
                                singleLine = true,
                                label = { Text("Website URL (e.g. openai.com)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisBorder,
                                    focusedTextColor = JarvisTextPrimary,
                                    unfocusedTextColor = JarvisTextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("connectors_site_url_input")
                            )
                            Button(
                                onClick = {
                                    if (siteUrl.isNotBlank()) {
                                        onAddAndConnectWebsite(siteTitle, siteUrl)
                                        siteTitle = ""
                                        siteUrl = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisCyan,
                                    contentColor = JarvisNavy
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("connectors_add_site_button")
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save & Connect Website", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = webSearchQuery,
                                    onValueChange = { webSearchQuery = it },
                                    singleLine = true,
                                    label = { Text("Global Web Search") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = JarvisCyan,
                                        unfocusedBorderColor = JarvisBorder,
                                        focusedTextColor = JarvisTextPrimary,
                                        unfocusedTextColor = JarvisTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f).testTag("connectors_web_search_input")
                                )
                                Button(
                                    onClick = {
                                        if (webSearchQuery.isNotBlank()) {
                                            onRunWebSearch("search $webSearchQuery")
                                            webSearchQuery = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = JarvisSurfaceElevated,
                                        contentColor = JarvisCyan
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(52.dp).testTag("connectors_web_search_button")
                                ) {
                                    Text("Search")
                                }
                            }
                        }
                    }
                }

                items(connectedWebsites, key = { it.url }) { site ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                        border = BorderStroke(1.dp, JarvisBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = site.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = site.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan
                                )
                            }
                            IconButton(
                                onClick = { onRemoveWebsite(site.url) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove website",
                                    tint = JarvisAlertRed
                                )
                            }
                            Button(
                                onClick = { onAddAndConnectWebsite(site.title, site.url) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisCyan,
                                    contentColor = JarvisNavy
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
