package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.connector.InstalledAppEntry
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

@Composable
fun JarvisAccountsVaultTab(
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
    onLaunchBankApp: (String) -> Unit,
    onOpenBankPortal: (String) -> Unit,
    onAddBankPortal: (String, String) -> Unit,
    onRemoveBankPortal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var icalDraft by remember(airbnbIcalUrl) { mutableStateOf(airbnbIcalUrl) }
    var bankNameDraft by remember { mutableStateOf("") }
    var bankUrlDraft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.widthIn(max = 760.dp).fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!vaultUnlocked && vaultDeviceSecured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisGold),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = JarvisGold, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Airbnb & Bank Account Vault Locked",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Authenticate with your Android screen lock / fingerprint to access Airbnb reservations and banking portals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRequestVaultUnlock,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("accounts_unlock_vault_button")
                        ) {
                            Text("Unlock Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Security Assurance Banner
            item {
                Surface(
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisEmerald.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = JarvisEmerald)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Zero-Password Vault: JARVIS never stores raw bank PINs/passwords. Every portal URL is checked for TLS/phishing before opening.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextPrimary
                        )
                    }
                }
            }

            // 1. Airbnb Access & Live iCal Sync
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Hotel, contentDescription = null, tint = JarvisGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Airbnb Account Access", style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
                                Text(
                                    text = if (airbnbInstalled) "Official Airbnb App detected on device" else "Connect via verified Airbnb HTTPS portals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (airbnbInstalled) JarvisEmerald else JarvisTextMuted
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenAirbnb("https://www.airbnb.com/trips") },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(46.dp).testTag("airbnb_open_trips_button")
                            ) {
                                Text(if (airbnbInstalled) "Open Airbnb" else "Trips", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onOpenAirbnb("https://www.airbnb.com/hosting") },
                                border = BorderStroke(1.dp, JarvisBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Hosting", color = JarvisCyan)
                            }
                            OutlinedButton(
                                onClick = { onOpenAirbnb("https://www.airbnb.com/account-settings") },
                                border = BorderStroke(1.dp, JarvisBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Text("Account", color = JarvisTextPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = icalDraft,
                            onValueChange = { icalDraft = it },
                            singleLine = true,
                            label = { Text("Airbnb Host Calendar iCal URL (https://...ics)") },
                            colors = vaultFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("airbnb_ical_input")
                        )
                        Button(
                            onClick = { onSyncAirbnbIcal(icalDraft) },
                            enabled = !airbnbSyncLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisSurfaceElevated, contentColor = JarvisCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("airbnb_sync_ical_button")
                        ) {
                            if (airbnbSyncLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = JarvisCyan)
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Live Airbnb Reservations")
                        }

                        airbnbStatusMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = JarvisCyan)
                        }

                        airbnbReservations.forEach { res ->
                            Surface(
                                color = JarvisSurfaceElevated,
                                border = BorderStroke(1.dp, JarvisBorder),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(res.summary, style = MaterialTheme.typography.titleSmall, color = JarvisTextPrimary)
                                    Text("Check-in: ${res.checkIn}  →  Out: ${res.checkOut}", style = MaterialTheme.typography.labelSmall, color = JarvisGold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Bank Account & MFS Access
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = JarvisCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Bank & Financial Account Access", style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
                                Text(
                                    text = "${detectedBankApps.size} installed bank/wallet app(s) + ${bankPortals.size} TLS-verified portals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                        }

                        accountStatusMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = JarvisGold)
                        }

                        if (detectedBankApps.isNotEmpty()) {
                            Text("Installed Banking / MFS Apps", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                            detectedBankApps.forEach { app ->
                                Surface(
                                    color = JarvisSurfaceElevated,
                                    border = BorderStroke(1.dp, JarvisBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(app.appName, style = MaterialTheme.typography.titleSmall, color = JarvisTextPrimary)
                                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = JarvisTextMuted)
                                        }
                                        Button(
                                            onClick = { onLaunchBankApp(app.packageName) },
                                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Open", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = bankNameDraft,
                            onValueChange = { bankNameDraft = it },
                            singleLine = true,
                            label = { Text("Bank Name (e.g. Dutch-Bangla, Chase)") },
                            colors = vaultFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("bank_name_input")
                        )
                        OutlinedTextField(
                            value = bankUrlDraft,
                            onValueChange = { bankUrlDraft = it },
                            singleLine = true,
                            label = { Text("Official Bank Portal URL (https://...)") },
                            colors = vaultFieldColors(),
                            modifier = Modifier.fillMaxWidth().testTag("bank_url_input")
                        )
                        Button(
                            onClick = {
                                if (bankUrlDraft.isNotBlank()) {
                                    onAddBankPortal(bankNameDraft, bankUrlDraft)
                                    bankNameDraft = ""
                                    bankUrlDraft = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("bank_add_portal_button")
                        ) {
                            Text("Verify TLS & Save Bank Portal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(bankPortals, key = { it.url }) { portal ->
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
                            Text(portal.bankName, style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
                            Text(portal.url, style = MaterialTheme.typography.labelSmall, color = JarvisCyan)
                        }
                        IconButton(onClick = { onRemoveBankPortal(portal.url) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove portal", tint = JarvisAlertRed)
                        }
                        Button(
                            onClick = { onOpenBankPortal(portal.url) },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun vaultFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = JarvisCyan,
    unfocusedBorderColor = JarvisBorder,
    focusedTextColor = JarvisTextPrimary,
    unfocusedTextColor = JarvisTextPrimary,
    focusedLabelColor = JarvisCyan,
    unfocusedLabelColor = JarvisTextMuted
)
