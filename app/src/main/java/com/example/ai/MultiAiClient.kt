package com.example.ai

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Multi-AI fallback clients: OpenAI (ChatGPT) and Anthropic (Claude).
 *
 * Jarvis is Browser-first: every question is answered by live web search.
 * When a question needs the AI brain (images, video, complex reasoning),
 * this client lets Jarvis ask Claude or ChatGPT — using the user's own API
 * keys, stored on the phone in the app's settings, never sent anywhere else.
 * If no key is set (or a provider fails), the caller falls back to Gemini.
 */

// ---------- OpenAI (ChatGPT) ----------

@JsonClass(generateAdapter = true)
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    @Json(name = "max_tokens") val maxTokens: Int = 1024
)

@JsonClass(generateAdapter = true)
data class OpenAiChatChoice(
    val message: OpenAiChatMessage
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    val choices: List<OpenAiChatChoice> = emptyList()
)

interface OpenAiService {
    @POST("v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse
}

// ---------- Anthropic (Claude) ----------

@JsonClass(generateAdapter = true)
data class AnthropicMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class AnthropicRequest(
    val model: String,
    @Json(name = "max_tokens") val maxTokens: Int = 1024,
    val messages: List<AnthropicMessage>
)

@JsonClass(generateAdapter = true)
data class AnthropicContentBlock(
    val type: String,
    val text: String = ""
)

@JsonClass(generateAdapter = true)
data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList()
)

interface AnthropicService {
    @POST("v1/messages")
    suspend fun message(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

/**
 * Tries Claude first (when its key is set), then ChatGPT (when its key is set).
 * Returns the answer text, or null when no provider is configured or all fail.
 */
class MultiAiClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val openAiService: OpenAiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenAiService::class.java)
    }

    private val anthropicService: AnthropicService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(AnthropicService::class.java)
    }

    suspend fun chat(
        prompt: String,
        openAiKey: String?,
        anthropicKey: String?
    ): String? = withContext(Dispatchers.IO) {
        // Claude first.
        if (!anthropicKey.isNullOrBlank()) {
            runCatching {
                val response = anthropicService.message(
                    apiKey = anthropicKey.trim(),
                    request = AnthropicRequest(
                        model = "claude-3-5-sonnet-20241022",
                        messages = listOf(AnthropicMessage("user", prompt))
                    )
                )
                val text = response.content.firstOrNull { it.type == "text" }?.text?.trim()
                if (!text.isNullOrEmpty()) return@withContext text
            }.onFailure { e ->
                Log.w(TAG, "Claude request failed, trying ChatGPT", e)
            }
        }
        // Then ChatGPT.
        if (!openAiKey.isNullOrBlank()) {
            runCatching {
                val response = openAiService.chat(
                    auth = "Bearer ${openAiKey.trim()}",
                    request = OpenAiChatRequest(
                        model = "gpt-4o-mini",
                        messages = listOf(OpenAiChatMessage("user", prompt))
                    )
                )
                val text = response.choices.firstOrNull()?.message?.content?.trim()
                if (!text.isNullOrEmpty()) return@withContext text
            }.onFailure { e ->
                Log.w(TAG, "ChatGPT request failed", e)
            }
        }
        null
    }

    companion object {
        private const val TAG = "MultiAiClient"
    }
}
