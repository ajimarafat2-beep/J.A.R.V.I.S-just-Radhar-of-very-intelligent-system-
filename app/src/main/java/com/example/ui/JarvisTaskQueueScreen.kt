package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nlp.LocalNlpStatus
import com.example.queue.OfflineTaskEntity
import com.example.queue.OfflineTaskMetrics
import com.example.queue.TaskPriority
import com.example.queue.TaskStatus
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JarvisTaskQueueScreen(
    tasks: List<OfflineTaskEntity>,
    metrics: OfflineTaskMetrics,
    nlpStatus: LocalNlpStatus,
    isOnline: Boolean,
    offlineIsolationMode: Boolean,
    onSyncPendingNow: () -> Unit,
    onRetryTask: (Long) -> Unit,
    onPromotePriority: (Long) -> Unit,
    onCancelTask: (Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePending = metrics.pendingCount + metrics.runningCount
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JarvisHologramOrb(size = 24.dp, activeGold = true)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JARVIS // OFFLINE TASK MANAGER",
                        style = MaterialTheme.typography.titleLarge,
                        color = JarvisCyan
                    )
                }
                Text(
                    text = "Priority-scheduled local Room queue + On-Device Local NLP Engine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextMuted
                )
            }
        }

        // Local NLP Engine Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = JarvisCyan)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local NLP Engine: ${nlpStatus.modelName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = JarvisTextPrimary
                        )
                        val lastSummary = nlpStatus.lastIntent?.let {
                            "Last: ${it.name} (${(nlpStatus.lastConfidence * 100).toInt()}%, ${nlpStatus.lastLatencyMicros}µs)"
                        } ?: "Ready offline • ${nlpStatus.vocabSize} tokens • ${nlpStatus.intentCount} intents"
                        Text(
                            text = lastSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                    }
                }
            }
        }

        // Connectivity & Sync Card
        item {
            val statusColor = when {
                offlineIsolationMode -> JarvisGold
                isOnline -> JarvisEmerald
                else -> JarvisAlertRed
            }
            val statusLabel = when {
                offlineIsolationMode -> "Offline Isolation Active — Pending tasks held ($activePending)"
                isOnline -> "Online & Ready — Auto-sync active ($activePending pending)"
                else -> "Offline — $activePending task(s) queued until internet returns"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isOnline && !offlineIsolationMode) {
                                Icons.Filled.CloudDone
                            } else {
                                Icons.Filled.CloudOff
                            },
                            contentDescription = null,
                            tint = statusColor
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pending: ${metrics.pendingCount} • Done: ${metrics.completedCount} • Failed: ${metrics.failedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSyncPendingNow,
                            enabled = isOnline && !offlineIsolationMode && activePending > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisNavy
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("queue_sync_now_button")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Pending", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onClearCompleted,
                            border = BorderStroke(1.dp, JarvisBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("queue_clear_completed_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = JarvisTextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Done", color = JarvisTextPrimary)
                        }
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "OfflineTaskManager Queue is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send commands while offline (e.g. \"open camera\", \"scan wifi\", or \"search AI news\") — local actions run via Local NLP immediately, while web tasks queue and auto-resume.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                val badgeColor = when (task.status) {
                    TaskStatus.COMPLETED -> JarvisEmerald
                    TaskStatus.PENDING_NETWORK -> JarvisGold
                    TaskStatus.RUNNING -> JarvisCyan
                    else -> JarvisAlertRed
                }
                val timeFmt = remember(task.createdAtMillis) {
                    formatter.format(Date(task.createdAtMillis))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface),
                    border = BorderStroke(1.dp, JarvisBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = task.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = JarvisNavy,
                                border = BorderStroke(1.dp, JarvisBorder),
                                shape = RoundedCornerShape(999.dp),
                                modifier = Modifier.clickable { onPromotePriority(task.id) }
                            ) {
                                Text(
                                    text = TaskPriority.label(task.priority),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (task.priority == TaskPriority.HIGH) JarvisGold else JarvisCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = timeFmt,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteTask(task.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete task",
                                    tint = JarvisTextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = task.prompt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = JarvisTextPrimary
                        )
                        Text(
                            text = "NLP Intent: ${task.intentName} (${(task.confidence * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan
                        )
                        task.resultSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextMuted
                            )
                        }

                        if (task.status == TaskStatus.FAILED ||
                            task.status == TaskStatus.CANCELLED ||
                            task.status == TaskStatus.PENDING_NETWORK
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                if (task.status != TaskStatus.PENDING_NETWORK) {
                                    TextButton(onClick = { onRetryTask(task.id) }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", color = JarvisCyan)
                                    }
                                } else {
                                    TextButton(onClick = { onCancelTask(task.id) }) {
                                        Text("Cancel", color = JarvisAlertRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
