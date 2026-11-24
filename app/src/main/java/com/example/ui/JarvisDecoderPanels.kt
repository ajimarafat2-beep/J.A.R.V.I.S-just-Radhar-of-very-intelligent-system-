package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.PayloadDecodeResult
import com.example.security.WebsiteDecodedReport
import com.example.security.WebsiteDecoderEngine
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
import kotlinx.coroutines.launch

@Composable
fun WebsiteDecoderCard(
    offlineIsolation: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var isDecoding by remember { mutableStateOf(false) }
    var decodedReport by remember { mutableStateOf<WebsiteDecodedReport?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = JarvisCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Website Decoder & Inspector", style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Decodes URL query parameters, TLS certificate, HTTP headers, HTML meta/scripts, tech stack, and outbound links.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarvisTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("https://example.com/path?q=hello%20world") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decoder_url_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    errorMsg = null
                    isDecoding = true
                    scope.launch {
                        runCatching { WebsiteDecoderEngine.decodeWebsite(urlInput, offlineIsolation) }
                            .onSuccess { decodedReport = it }
                            .onFailure {
                                decodedReport = null
                                errorMsg = "Enter a valid website URL to decode."
                            }
                        isDecoding = false
                    }
                },
                enabled = !isDecoding,
                colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan, contentColor = JarvisNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("decoder_url_button")
            ) {
                Text(if (isDecoding) "Decoding Website…" else "Decode Website", fontWeight = FontWeight.Bold)
            }

            errorMsg?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = JarvisAlertRed, style = MaterialTheme.typography.bodyMedium)
            }

            decodedReport?.let { rep ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisCyan.copy(0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${rep.scheme.uppercase()} • ${rep.host}:${rep.port}" +
                                (rep.httpStatusCode?.let { " • HTTP $it (${rep.responseTimeMs ?: 0}ms)" } ?: ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = JarvisCyan
                        )
                        Text("Title: ${rep.pageTitle}", style = MaterialTheme.typography.bodyMedium, color = JarvisTextPrimary)
                        if (rep.metaDescription.isNotBlank()) {
                            Text("Description: ${rep.metaDescription}", style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        }
                        Text("Decoded Path: ${rep.decodedPath}", style = MaterialTheme.typography.bodySmall, color = JarvisTextPrimary)
                        if (rep.queryParameters.isNotEmpty()) {
                            Text("Query Parameters:", style = MaterialTheme.typography.labelMedium, color = JarvisGold)
                            rep.queryParameters.forEach { (k, v) ->
                                Text("• $k = $v", style = MaterialTheme.typography.bodySmall, color = JarvisTextPrimary)
                            }
                        }
                        if (rep.detectedTechnologies.isNotEmpty()) {
                            Text(
                                "Technologies: ${rep.detectedTechnologies.joinToString(", ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = JarvisEmerald
                            )
                        }
                        Text("TLS Certificate:\n${rep.tlsCertificateSummary}", style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        if (rep.securityHeaders.isNotEmpty()) {
                            Text("HTTP Headers:", style = MaterialTheme.typography.labelMedium, color = JarvisCyan)
                            rep.securityHeaders.forEach { (h, v) ->
                                Text("• $h: $v", style = MaterialTheme.typography.bodySmall, color = JarvisTextPrimary)
                            }
                        }
                        if (rep.scriptSources.isNotEmpty()) {
                            Text("Scripts (${rep.scriptSources.size}): ${rep.scriptSources.take(5).joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        }
                        if (rep.extractedLinks.isNotEmpty()) {
                            Text("Outbound Links (${rep.extractedLinks.size}): ${rep.extractedLinks.take(4).joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = JarvisTextMuted)
                        }
                        if (rep.decodedHtmlPreview.isNotBlank()) {
                            Text("Decoded Text Preview:", style = MaterialTheme.typography.labelMedium, color = JarvisGold)
                            Text(rep.decodedHtmlPreview.take(500), style = MaterialTheme.typography.bodySmall, color = JarvisTextPrimary)
                        }
                        rep.notes.forEach { note ->
                            Text("• $note", style = MaterialTheme.typography.bodySmall, color = JarvisGold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UniversalPayloadDecoderCard(modifier: Modifier = Modifier) {
    var payloadInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PayloadDecodeResult?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = JarvisSurface),
        border = BorderStroke(1.dp, JarvisBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DataObject, contentDescription = null, tint = JarvisGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Offline Payload Decoder (JWT / Base64 / URL / Hex)", style = MaterialTheme.typography.titleMedium, color = JarvisTextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = payloadInput,
                onValueChange = { payloadInput = it },
                label = { Text("Paste Base64, JWT, Hex, URL-encoded, or HTML text") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("decoder_payload_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { result = WebsiteDecoderEngine.decodePayload(payloadInput) },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("decoder_payload_button")
            ) {
                Text("Auto-Detect & Decode Offline", fontWeight = FontWeight.Bold)
            }

            result?.let { res ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisGold.copy(0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Detected Format: ${res.detectedFormat}", style = MaterialTheme.typography.labelLarge, color = JarvisGold)
                        res.details.forEach { d ->
                            Text("• $d", style = MaterialTheme.typography.labelSmall, color = JarvisCyan)
                        }
                        Text(res.decodedOutput, style = MaterialTheme.typography.bodyMedium, color = JarvisTextPrimary)
                    }
                }
            }
        }
    }
}
