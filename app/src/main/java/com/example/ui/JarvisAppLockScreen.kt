package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

/**
 * App Lock screen: shown every time the app opens while App Lock is on.
 * The PIN is the one set in Settings > App Lock. If it is forgotten, the
 * app data must be cleared (Settings > Apps > Jarvis > Storage) to reset it.
 */
@Composable
fun JarvisAppLockScreen(
    correctPin: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pinDraft by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisHologramOrb(size = 72.dp, activeGold = true)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Jarvis is locked",
            style = MaterialTheme.typography.titleLarge,
            color = JarvisTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter your 4-digit PIN to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = JarvisTextMuted
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = pinDraft,
            onValueChange = {
                pinDraft = it.filter { ch -> ch.isDigit() }.take(4)
                wrongPin = false
            },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .testTag("app_lock_pin_field")
        )
        if (wrongPin) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Wrong PIN — try again.",
                color = JarvisAlertRed,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (pinDraft == correctPin) onUnlock() else wrongPin = true
            },
            enabled = pinDraft.length == 4,
            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .height(48.dp)
                .testTag("app_lock_unlock_button")
        ) {
            Text("Unlock", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Forgot the PIN? Clear the app data (Settings > Apps > Jarvis > Storage) to reset it.",
            style = MaterialTheme.typography.bodySmall,
            color = JarvisTextMuted
        )
    }
}
