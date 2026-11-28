package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agent.AgentMessage
import com.example.agent.MessageSender
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisNavy
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat List: the conversations on this phone.
 * Today it shows the current conversation (chat history is saved on the
 * next update, with full persistence). Tapping it opens the chat.
 */
@Composable
fun JarvisChatListScreen(
    messages: List<AgentMessage>,
    onOpenChat: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userCount = messages.count { it.sender == MessageSender.USER }
    val lastMessage = messages.lastOrNull()
    val lastTime = lastMessage?.let {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.timestampMillis))
    } ?: ""
    val lastPreview = lastMessage?.text?.take(80)?.ifBlank { "No messages yet" } ?: "No messages yet"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavy)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Chats",
            style = MaterialTheme.typography.titleLarge,
            color = JarvisCyan,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Your conversations on this phone. Full chat history is saved on the next update.",
            style = MaterialTheme.typography.bodyMedium,
            color = JarvisTextMuted
        )
        Surface(
            color = JarvisSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_list_current")
                .clickable { onOpenChat() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(imageVector = Icons.Filled.Chat, contentDescription = null, tint = JarvisGold)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisTextPrimary
                    )
                    Text(
                        text = lastPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextMuted,
                        maxLines = 2
                    )
                    if (lastTime.isNotBlank()) {
                        Text(
                            text = "$userCount messages · $lastTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted
                        )
                    }
                }
            }
        }
        Button(
            onClick = onNewChat,
            colors = ButtonDefaults.buttonColors(containerColor = JarvisGold, contentColor = JarvisNavy),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("chat_list_new_chat_button")
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat", fontWeight = FontWeight.Bold)
        }
    }
}
