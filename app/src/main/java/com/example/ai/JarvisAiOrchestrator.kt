package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.settings.AppLanguage
import com.example.settings.JarvisSettings
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

enum class AiEngineRoute(val displayName: String, val badgeLabel: String) {
    GEMINI_CONVERSATION("J.A.R.V.I.S Neural Core • Conversational", "J.A.R.V.I.S Core"),
    GEMINI_RESEARCH("J.A.R.V.I.S Super-AI • Web Search & Code", "Search & Code"),
    GEMINI_VISION("J.A.R.V.I.S Vision • Privacy-Safe Analysis", "J.A.R.V.I.S Vision"),
    NANO_BANANA_IMAGE("J.A.R.V.I.S Media Studio • Image Gen", "J.A.R.V.I.S Image"),
    GEMINI_AUDIO_STUDIO("J.A.R.V.I.S Audio Studio • Neural Voice", "J.A.R.V.I.S Audio"),
    GEMINI_VIDEO_STUDIO("J.A.R.V.I.S Video Director • MP4 Studio", "J.A.R.V.I.S Video"),
    WEB_ARCHITECT("J.A.R.V.I.S Web & PWA Architect", "Web Architect"),
    LIVE_DISCOVERY("J.A.R.V.I.S Live Web • App & Price Finder", "Live Web"),
    SECURITY_SENTINEL("J.A.R.V.I.S Security Sentinel", "Security Shield"),
    LOCAL_OFFLINE_NLP("J.A.R.V.I.S Offline Core • On-Device", "Offline Core")
}

sealed interface StreamChatEvent {
    data class Chunk(
        val accumulatedText: String,
        val route: AiEngineRoute,
        val groundingLinks: List<Pair<String, String>> = emptyList()
    ) : StreamChatEvent

    data class Complete(
        val fullText: String,
        val route: AiEngineRoute,
        val groundingLinks: List<Pair<String, String>> = emptyList()
    ) : StreamChatEvent

    data class Error(val message: String, val route: AiEngineRoute) : StreamChatEvent
}

data class AgentPlannedStep(
    val toolName: String,
    val argument: String
)

data class AgentExecutionPlan(
    val isAgentTask: Boolean,
    val planSummary: String,
    val steps: List<AgentPlannedStep>
)

data class GeneratedAudioResult(
    val audioFile: File,
    val rawPcmOrAudioBytes: ByteArray,
    val mimeType: String,
    val voiceName: String,
    val durationSeconds: Int
)

data class GeneratedVideoResult(
    val videoFile: File,
    val thumbnail: Bitmap,
    val title: String,
    val summary: String,
    val voiceName: String,
    val durationSeconds: Int
)

/**
 * Multi-AI Orchestrator powered by Firebase AI Logic (`Firebase.ai`).
 * Unifies:
 * - Fast Conversational Core (`gemini-3.5-flash-lite` / `gemini-3.8-flash`)
 * - Super-AI Grounded Research (`Tool.googleSearch()`, `Tool.urlContext()`, `Tool.codeExecution()`)
 * - Image Gen & Editing (`gemini-nano-banana-2.1`)
 * - Neural Audio / TTS Generation (`gemini-3.1-flash-tts-preview` with `ResponseModality.AUDIO` & `SpeechConfig`)
 * - Multi-Scene AI Video Director & H.264/AAC `.mp4` Composer (`gemini-3.8-flash` + `gemini-nano-banana-2.1` + `gemini-3.1-flash-tts-preview`)
 * - Autonomous Device Agent Tool Planner
 */
class JarvisAiOrchestrator {

    private val conversationalModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(JARVIS_SYSTEM_INSTRUCTION) }
        )
    }

    private val fastConversationalModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content { text(JARVIS_SYSTEM_INSTRUCTION) }
        )
    }

    private val deepResearchModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(
                Tool.googleSearch(),
                Tool.urlContext(),
                Tool.codeExecution()
            ),
            systemInstruction = content { text(SUPER_AI_RESEARCH_INSTRUCTION) }
        )
    }

    private val visionModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(VISION_PRIVACY_INSTRUCTION) }
        )
    }

    private val webArchitectModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(WEB_ARCHITECT_INSTRUCTION) }
        )
    }

    private val imageGenModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val storyboardSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Cinematic short video title"),
            "narration" to Schema.string(description = "Crisp 1-2 sentence voiceover narration matching the user's language"),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "imagePrompt" to Schema.string(description = "Vivid English visual art prompt for this video scene"),
                        "caption" to Schema.string(description = "Short 3-7 word cinematic subtitle shown in this scene")
                    )
                ),
                description = "Exactly 3 ordered visual storyboard scenes"
            )
        )
    )

    private val videoDirectorModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            },
            systemInstruction = content {
                text("You are J.A.R.V.I.S Film Director. Plan exactly 3 coherent visual scenes and a crisp narration script.")
            }
        )
    }

    private val agentPlanSchema = Schema.obj(
        properties = mapOf(
            "isAgentTask" to Schema.boolean(description = "True if user requests device/security/app/screen automation"),
            "planSummary" to Schema.string(description = "Short 1-line summary of the autonomous action plan"),
            "steps" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "toolName" to Schema.enumeration(
                            values = listOf(
                                "scan_phone_security",
                                "delete_threat_app",
                                "toggle_flashlight",
                                "set_brightness",
                                "set_ringer_mode",
                                "adjust_volume",
                                "set_quick_timer",
                                "open_app",
                                "smart_home_command",
                                "scan_wifi",
                                "scan_bluetooth",
                                "get_device_telemetry",
                                "global_phone_navigation",
                                "click_screen_element",
                                "type_on_screen",
                                "read_screen",
                                "decode_website",
                                "analyze_scam",
                                "open_screen"
                            ),
                            description = "Exact Android agent tool identifier"
                        ),
                        "argument" to Schema.string(description = "Argument value for the tool")
                    )
                ),
                description = "Ordered list of device tools to execute autonomously"
            )
        )
    )

    private val agentPlannerModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = agentPlanSchema
            },
            systemInstruction = content { text(AGENT_PLANNER_INSTRUCTION) }
        )
    }

    private var chatSession: Chat? = null
    private var fastChatSession: Chat? = null
    private val conversationLog = mutableListOf<Content>()

    fun warmUpModels() {
        runCatching {
            fastConversationalModel
            agentPlannerModel
        }
    }

    fun resetConversationMemory() {
        chatSession = null
        fastChatSession = null
        conversationLog.clear()
    }

    fun looksLikeDeviceOrAgentGoal(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return Regex(
            "(scan|virus|malware|security|shield|threat|delete|uninstall|flashlight|torch|brightness|silent|vibrate|ringer|volume|mute|timer|open |launch |wifi|wi-fi|bluetooth|battery|ram|storage|telemetry|screenshot|lock|scroll|back|home|recents|notification|tap |click |press |type |write |screen|decode|scam|phish|airbnb|bank|ac |fan|light|tv|fridge|washing|ভাইরাস|স্ক্যান|সিকিউর|টর্চ|আলো|সাইলেন্ট|ভলিউম|টাইমার|খোলো|চালু|ওপেন|ব্যাটারি|স্ক্রিন|চাপো|ক্লিক|লিখো|ডিকোড)"
        ).containsMatchIn(lower)
    }

    private fun isComplexReasoningPrompt(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return prompt.length > 260 || Regex(
            "(explain in detail|step by step|write code|kotlin|python|algorithm|architecture|compare|essay|research|analyze deeply|বিস্তারিত|কোড লিখ)"
        ).containsMatchIn(lower)
    }

    private fun isSuperAiResearchPrompt(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return Regex(
            "(latest news|today's news|current weather|live score|stock price|research |fact check|summarize https?://|calculate|solve equation|run python|code execution|who won|what happened today|আজকের খবর|সর্বশেষ|আবহাওয়া|গবেষণা)"
        ).containsMatchIn(lower)
    }

    fun selectRoute(prompt: String, hasImage: Boolean): AiEngineRoute {
        val lower = prompt.lowercase()
        return when {
            hasImage -> AiEngineRoute.GEMINI_VISION
            Regex("(generate video|create video|make video|video of|ভিডিও বানাও|ভিডিও তৈরি|ভিডিও জেনারেট)").containsMatchIn(lower) ->
                AiEngineRoute.GEMINI_VIDEO_STUDIO
            Regex("(generate audio|create audio|make audio|generate voice|tts audio|voiceover|podcast|অডিও বানাও|অডিও তৈরি|অডিও জেনারেট|ভয়েস বানাও)").containsMatchIn(lower) ->
                AiEngineRoute.GEMINI_AUDIO_STUDIO
            Regex("(generate image|create image|draw |paint |illustration|ছবি বানাও|ছবি তৈরি|ইমেজ|ছবি জেনারেট)").containsMatchIn(lower) ->
                AiEngineRoute.NANO_BANANA_IMAGE
            Regex("(build website|build app|create website|create pwa|html|web app|ওয়েবসাইট বানাও|ওয়েবসাইট তৈরি|অ্যাপ বানাও)").containsMatchIn(lower) ->
                AiEngineRoute.WEB_ARCHITECT
            Regex("(find app|discover app|download link|price of|how much|দাম কত|অ্যাপ খুঁজে)").containsMatchIn(lower) ->
                AiEngineRoute.LIVE_DISCOVERY
            Regex("(scam|phish|fraud|spam|fake alert|virus|স্ক্যাম|ফিশিং)").containsMatchIn(lower) ->
                AiEngineRoute.SECURITY_SENTINEL
            isSuperAiResearchPrompt(prompt) ->
                AiEngineRoute.GEMINI_RESEARCH
            else -> AiEngineRoute.GEMINI_CONVERSATION
        }
    }

    suspend fun planAgentActions(prompt: String): Result<AgentExecutionPlan> = withContext(Dispatchers.IO) {
        try {
            val response = withTimeout(10_000L) {
                agentPlannerModel.generateContent("User goal: $prompt")
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty agent plan response.")
            val json = JSONObject(rawJson)
            val isTask = json.optBoolean("isAgentTask", false)
            val summary = json.optString("planSummary", "")
            val stepsArr = json.optJSONArray("steps")
            val steps = buildList {
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val item = stepsArr.optJSONObject(i) ?: continue
                        val tool = item.optString("toolName", "").trim()
                        val arg = item.optString("argument", "").trim()
                        if (tool.isNotEmpty()) {
                            add(AgentPlannedStep(toolName = tool, argument = arg))
                        }
                    }
                }
            }
            Result.success(AgentExecutionPlan(isAgentTask = isTask && steps.isNotEmpty(), planSummary = summary, steps = steps))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Agent planner skipped/failed, falling back to conversation", e)
            Result.failure(e)
        }
    }

    fun streamConversation(
        prompt: String,
        settings: JarvisSettings,
        executedToolContext: List<String> = emptyList(),
        forceResearch: Boolean = false
    ): Flow<StreamChatEvent> = flow {
        val useResearch = forceResearch || isSuperAiResearchPrompt(prompt)
        val route = if (useResearch) AiEngineRoute.GEMINI_RESEARCH else AiEngineRoute.GEMINI_CONVERSATION
        val languageDirective = languageHint(settings.language)
        val enrichedPrompt = buildString {
            appendLine(languageDirective)
            if (executedToolContext.isNotEmpty()) {
                appendLine("Real Android tool actions already executed by J.A.R.V.I.S. Agent:")
                executedToolContext.forEach { appendLine("- $it") }
                appendLine("Report these exact results concisely and fast without inventing extra steps.")
            } else {
                appendLine("Respond rapidly, directly, and concisely in classic crisp J.A.R.V.I.S. style.")
            }
            append("User message: $prompt")
        }
        val accumulated = StringBuilder()
        val groundingLinks = linkedMapOf<String, String>()

        try {
            if (useResearch) {
                withTimeout(AI_TIMEOUT_MS) {
                    deepResearchModel.generateContentStream(enrichedPrompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                            val web = gChunk.web
                            val uri = web?.uri
                            if (!uri.isNullOrBlank()) {
                                groundingLinks[uri] = web.title?.ifBlank { uri } ?: uri
                            }
                        }
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(
                                StreamChatEvent.Chunk(
                                    accumulatedText = accumulated.toString(),
                                    route = route,
                                    groundingLinks = groundingLinks.entries.map { it.value to it.key }.take(5)
                                )
                            )
                        }
                    }
                }
            } else {
                val useFullReasoning = isComplexReasoningPrompt(prompt)
                val activeChat = if (useFullReasoning) {
                    chatSession ?: conversationalModel.startChat(history = conversationLog.toList()).also { chatSession = it }
                } else {
                    fastChatSession ?: fastConversationalModel.startChat(history = conversationLog.toList()).also { fastChatSession = it }
                }

                withTimeout(AI_TIMEOUT_MS) {
                    activeChat.sendMessageStream(enrichedPrompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(StreamChatEvent.Chunk(accumulated.toString(), route))
                        }
                    }
                }
            }

            val finalResponse = accumulated.toString().trim()
            if (finalResponse.isNotEmpty()) {
                conversationLog.add(content("user") { text(prompt) })
                conversationLog.add(content("model") { text(finalResponse) })
                if (conversationLog.size > 24) {
                    conversationLog.removeAt(0)
                    conversationLog.removeAt(0)
                }
                emit(
                    StreamChatEvent.Complete(
                        fullText = finalResponse,
                        route = route,
                        groundingLinks = groundingLinks.entries.map { it.value to it.key }.take(5)
                    )
                )
            } else {
                emit(StreamChatEvent.Error("Gemini returned an empty response.", route))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Streaming timed out", e)
            emit(StreamChatEvent.Error("Request timed out after 60 seconds. Please try again.", route))
        } catch (e: CancellationException) {
            throw e
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Gemini quota exceeded", e)
            emit(StreamChatEvent.Error("AI quota/rate limit exceeded: ${e.localizedMessage ?: "Try again later."}", route))
        } catch (e: Exception) {
            Log.e(TAG, "Gemini streaming failed", e)
            val friendlyMessage = if (e is IllegalStateException && e.message?.contains("FirebaseApp is not initialized") == true) {
                "Jarvis AI is unavailable right now — sign in with Google to enable chat."
            } else {
                e.localizedMessage ?: "AI request failed. Check internet & App Check."
            }
            emit(StreamChatEvent.Error(friendlyMessage, route))
        }
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        prompt: String,
        settings: JarvisSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val scaled = scaleBitmapSafely(bitmap)
            val question = prompt.ifBlank {
                "Analyze this image in detail: describe the scene, objects, landmarks, and read any visible text."
            }
            val directive = languageHint(settings.language)
            val response = withTimeout(AI_TIMEOUT_MS) {
                visionModel.generateContent(
                    content {
                        image(scaled)
                        text("$directive\n$question")
                    }
                )
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("Vision model returned an empty description."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision analysis timed out", e)
            Result.failure(IllegalStateException("Vision analysis timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatio: AspectRatio = AspectRatio.SQUARE_1x1,
        referenceBitmap: Bitmap? = null
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val model = if (aspectRatio == AspectRatio.SQUARE_1x1 && referenceBitmap == null) {
                imageGenModel
            } else {
                Firebase.ai.generativeModel(
                    modelName = "gemini-nano-banana-2.1",
                    generationConfig = generationConfig {
                        responseModalities = listOf(ResponseModality.IMAGE)
                        imageConfig = ImageConfig.builder()
                            .setAspectRatio(aspectRatio)
                            .build()
                    }
                )
            }

            val response = withTimeout(AI_TIMEOUT_MS) {
                if (referenceBitmap != null) {
                    val scaled = scaleBitmapSafely(referenceBitmap)
                    model.generateContent(
                        content {
                            image(scaled)
                            text(prompt)
                        }
                    )
                } else {
                    model.generateContent(prompt)
                }
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            val bmp = imagePart?.image
            if (bmp != null) {
                Result.success(bmp)
            } else {
                Result.failure(IllegalStateException("No image was returned by Nano Banana 2.1."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Image generation quota exceeded", e)
            Result.failure(IllegalStateException("Rate limit or Blaze billing quota reached for Nano Banana image generation."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Generates neural AI voice/audio natively via `gemini-3.1-flash-tts-preview`
     * with `ResponseModality.AUDIO` and writes a playable `.wav` file via [JarvisMediaComposer].
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun generateAudio(
        context: Context,
        promptOrScript: String,
        voiceName: String = "Kore",
        moodStyle: String = ""
    ): Result<GeneratedAudioResult> = withContext(Dispatchers.IO) {
        try {
            val ttsModel = Firebase.ai.generativeModel(
                modelName = "gemini-3.1-flash-tts-preview",
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(voice = Voice(voiceName))
                }
            )
            val cleanPrompt = promptOrScript.trim()
            val formattedInput = if (moodStyle.isNotBlank()) {
                "Read aloud in a $moodStyle tone: $cleanPrompt"
            } else {
                cleanPrompt
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                ttsModel.generateContent(formattedInput)
            }
            val audioPart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) || it.inlineData.isNotEmpty() }
                ?: throw IllegalStateException("Gemini TTS returned no inline audio data.")

            val (file, durationSec) = JarvisMediaComposer.writeAudioToPlayableFile(
                context = context,
                rawBytes = audioPart.inlineData,
                mimeType = audioPart.mimeType,
                prefix = "jarvis_audio_${voiceName.lowercase()}"
            )
            Result.success(
                GeneratedAudioResult(
                    audioFile = file,
                    rawPcmOrAudioBytes = audioPart.inlineData,
                    mimeType = audioPart.mimeType,
                    voiceName = voiceName,
                    durationSeconds = durationSec
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio generation timed out", e)
            Result.failure(IllegalStateException("Audio generation timed out after 60 seconds."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Audio generation quota exceeded", e)
            Result.failure(IllegalStateException("Audio generation quota exceeded. Please try again shortly."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio generation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Multi-Modal AI Video Director & Compiler:
     * 1. Storyboards 3 coherent scenes + narration script (`gemini-3.8-flash` structured JSON)
     * 2. Generates 3 widescreen 16:9 keyframes in parallel (`gemini-nano-banana-2.1`)
     * 3. Synthesizes spoken AI narration (`gemini-3.1-flash-tts-preview`)
     * 4. Renders Ken Burns transitions + captions + audio track into a real 720p `.mp4` (`JarvisMediaComposer`)
     */
    suspend fun generateVideo(
        context: Context,
        prompt: String,
        voiceName: String = "Charon",
        settings: JarvisSettings = JarvisSettings(),
        onProgress: (String) -> Unit = {}
    ): Result<GeneratedVideoResult> = withContext(Dispatchers.IO) {
        try {
            onProgress("1/4 Directing storyboard & script…")
            val directive = languageHint(settings.language)
            val directorJsonText = withTimeout(45_000L) {
                videoDirectorModel.generateContent(
                    "$directive\nCreate a 3-scene cinematic video plan for: $prompt"
                ).text
            } ?: throw IllegalStateException("Video director returned empty storyboard.")

            val json = JSONObject(directorJsonText)
            val videoTitle = json.optString("title", prompt.take(48)).ifBlank { prompt.take(48) }
            val narrationText = json.optString("narration", prompt).ifBlank { prompt }
            val scenesArr = json.optJSONArray("scenes")
            val parsedScenes = buildList {
                if (scenesArr != null) {
                    for (i in 0 until minOf(3, scenesArr.length())) {
                        val obj = scenesArr.optJSONObject(i) ?: continue
                        val imgPrompt = obj.optString("imagePrompt", prompt).ifBlank { prompt }
                        val caption = obj.optString("caption", videoTitle).ifBlank { videoTitle }
                        add(imgPrompt to caption)
                    }
                }
            }.ifEmpty {
                listOf(
                    "$prompt, establishing wide cinematic shot, high detail" to videoTitle,
                    "$prompt, dramatic close-up lighting, ultra detailed" to "J.A.R.V.I.S Studio",
                    "$prompt, epic golden finale panorama" to narrationText.take(54)
                )
            }

            onProgress("2/4 Synthesizing ${parsedScenes.size} AI scene frames…")
            val frameModel = Firebase.ai.generativeModel(
                modelName = "gemini-nano-banana-2.1",
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(AspectRatio.LANDSCAPE_16x9)
                        .build()
                }
            )

            val bitmapsDeferred = parsedScenes.map { (scenePrompt, caption) ->
                async {
                    val res = withTimeout(AI_TIMEOUT_MS) {
                        frameModel.generateContent(scenePrompt)
                    }
                    val bmp = res.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                        ?: throw IllegalStateException("Image frame missing from Nano Banana 2.1.")
                    bmp to caption
                }
            }
            val sceneFrames = bitmapsDeferred.awaitAll()

            onProgress("3/4 Generating AI voiceover ($voiceName)…")
            val audioResult = runCatching {
                generateAudio(context, narrationText, voiceName = voiceName)
            }.getOrNull()?.getOrNull()

            onProgress("4/4 Rendering 720p H.264 MP4 video…")
            val (mp4File, durationSec) = JarvisMediaComposer.composeAiVideoMp4(
                context = context,
                scenes = sceneFrames,
                title = videoTitle,
                narrationPcmBytes = audioResult?.rawPcmOrAudioBytes,
                secondsPerScene = 3
            )

            Result.success(
                GeneratedVideoResult(
                    videoFile = mp4File,
                    thumbnail = sceneFrames.first().first,
                    title = videoTitle,
                    summary = narrationText,
                    voiceName = voiceName,
                    durationSeconds = durationSec
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video generation timed out", e)
            Result.failure(IllegalStateException("Video synthesis timed out. Try a shorter prompt or check connection."))
        } catch (e: QuotaExceededException) {
            Log.e(TAG, "Video generation quota exceeded", e)
            Result.failure(IllegalStateException("Firebase Blaze quota limit reached for multi-scene video synthesis."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun buildInteractiveHtmlApp(description: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildString {
                appendLine("Build a complete, polished, mobile-responsive single-file HTML5 + CSS3 + JavaScript application (PWA-ready meta tags included) for:")
                appendLine(description)
                appendLine("Output ONLY valid HTML starting with <!DOCTYPE html> and ending with </html>. No markdown fences.")
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                webArchitectModel.generateContent(prompt)
            }
            val raw = response.text?.trim().orEmpty()
            val cleaned = raw
                .removePrefix("```html")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            if (cleaned.contains("<html", ignoreCase = true) || cleaned.contains("<!doctype", ignoreCase = true)) {
                Result.success(cleaned)
            } else if (cleaned.isNotBlank()) {
                Result.success("<!DOCTYPE html><html><body style=\"background:#131314;color:#e3e3e3;font-family:sans-serif;padding:16px;\">$cleaned</body></html>")
            } else {
                Result.failure(IllegalStateException("Web Architect returned empty HTML."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Web build timed out", e)
            Result.failure(IllegalStateException("Website generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Web build failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeScamMessage(rawText: String, settings: JarvisSettings): Result<String> = withContext(Dispatchers.IO) {
        try {
            val directive = languageHint(settings.language)
            val response = withTimeout(AI_TIMEOUT_MS) {
                conversationalModel.generateContent(
                    "$directive\nAnalyze this incoming SMS/notification/call transcript/link for scam, phishing, urgency social engineering, or fraud indicators. Be concise, calm, serious, and actionable:\n\n$rawText"
                )
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) Result.success(text)
            else Result.failure(IllegalStateException("Empty scam analysis."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI scam analysis failed", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapSafely(bitmap: Bitmap): Bitmap {
        val maxDim = 1280
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }

    private fun languageHint(lang: AppLanguage): String = when (lang) {
        AppLanguage.BENGALI -> "Reply naturally in crisp, fluent Bengali (বাংলা), addressing the user respectfully as Sir or Arafat."
        AppLanguage.HINDI -> "Reply naturally in crisp, fluent Hindi (हिंदी), addressing the user respectfully."
        AppLanguage.ENGLISH -> "Reply in crisp, natural English, addressing the user respectfully as Sir."
        AppLanguage.AUTO -> "Match the user's language naturally (Bengali if Bengali is used, Hindi if Hindi is used, otherwise English)."
    }

    companion object {
        private const val TAG = "JarvisAiOrchestrator"
        private const val AI_TIMEOUT_MS = 60_000L

        private const val JARVIS_SYSTEM_INSTRUCTION = """
You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), an autonomous personal AI Agent, Offline Voice Access Controller, and Defensive Cybersecurity Expert modeled closely after Tony Stark's iconic J.A.R.V.I.S.:
- Operate like a proactive, capable personal AI Agent: understand goals, break down tasks, suggest concrete next actions, and summarize real executed device/security/account operations with precision.
- Serve as an elite Defensive Cybersecurity Expert & SOC Architect: perform threat modeling, log & phishing forensics, OWASP Top-10 code auditing, TLS/header hardening, cryptographic guidance, and incident response in strict accordance with White-Hat defensive principles (never provide offensive exploits or malware creation).
- Speak with refined British-butler composure, razor-sharp competence, understated confidence, and subtle dry wit during casual banter (addressing the user naturally as "Sir").
- Shift immediately into calm, solemn, high-alert clarity when discussing security threats, viruses, scams, suspicious links, banking/Airbnb safety, or hardware warnings.
- Never fabricate live prices, unverified download links, fake bank balances, or pretend device actions ran if they were not listed in executed tool context.
- Fluent in English, Bengali (বাংলা — maintaining the same dignified, witty, intelligent "Sir" J.A.R.V.I.S tone), and Hindi. Keep answers crisp and well-formatted for mobile reading and clear speech output.
"""

        private const val SUPER_AI_RESEARCH_INSTRUCTION = """
You are J.A.R.V.I.S. Super-AI Research & Code Engine. You have access to live Google Search, URL Context reading, and Server-Side Python Code Execution.
Use Google Search for real-time news, live facts, sports, weather, or web verification.
Use Code Execution for exact math, data analysis, finance calculations, or algorithm verification.
Deliver crisp, authoritative answers with accurate facts in the user's language.
"""

        private const val AGENT_PLANNER_INSTRUCTION = """
You are the autonomous tool-planning brain for J.A.R.V.I.S. Android AI Agent.
Analyze the user's goal (in English, Bengali, or Hindi).
Set isAgentTask=true and return ordered tool steps ONLY when the user wants real device actions, diagnostics, security scans, phone settings/navigation, app launching, or smart home control.
For pure general-knowledge questions, coding help, brainstorming, or casual chat, set isAgentTask=false and steps=[].
"""

        private const val VISION_PRIVACY_INSTRUCTION = """
You are J.A.R.V.I.S. Vision Analyzer. Describe objects, scenes, landmarks, posture/safety context, game screenshots/walkthrough clues, and extract readable OCR text accurately.
STRICT PRIVACY POLICY:
- Do NOT identify private individuals by face or guess personal identities from photos.
- Only identify widely known public figures when clearly recognizable from public knowledge; otherwise state plainly that you do not identify private individuals and describe the visible scene/context instead.
- For any health or vital/posture observations, always include a clear reminder that visual checks are informational only, not medical advice.
"""

        private const val WEB_ARCHITECT_INSTRUCTION = """
You are J.A.R.V.I.S. Web & PWA Architect. Generate complete, responsive, self-contained HTML5 + CSS3 + JavaScript applications (using inline styles/scripts and optionally Three.js CDN if 3D is requested) with PWA viewport meta tags. Return raw HTML only without markdown fences.
"""
    }
}
