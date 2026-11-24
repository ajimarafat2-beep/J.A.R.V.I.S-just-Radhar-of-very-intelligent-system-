package com.example.agent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.example.bluetooth.BluetoothAdapterState
import com.example.bluetooth.BondStatus
import com.example.bluetooth.JarvisBluetoothManager
import com.example.connector.UniversalConnector
import com.example.control.JarvisAccessibilityService
import com.example.control.JarvisPhoneControlManager
import com.example.control.PhoneRingerMode
import com.example.nlp.JarvisIntent
import com.example.nlp.LocalNlpEngine
import com.example.permissions.PermissionHelper
import com.example.queue.OfflineTaskManager
import com.example.queue.TaskPriority
import com.example.queue.TaskStatus
import com.example.queue.TaskType
import com.example.security.JarvisSecurityEngine
import com.example.security.JarvisShieldStateHolder
import com.example.security.WebsiteDecoderEngine
import com.example.settings.AppLanguage
import com.example.settings.JarvisSettings
import com.example.smarthome.JarvisSmartHomeManager
import com.example.voice.JarvisVoiceEngine
import com.example.wifi.JarvisWifiManager
import com.example.wifi.WifiRadioState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionExecutionResult(
    val responseText: String,
    val shortcut: AgentActionShortcut? = null
)

/**
 * Offline-first JARVIS action engine.
 * - Executes local phone controls (apps, media playback, flashlight, volume, alarms, Bluetooth, Wi-Fi, telemetry).
 * - Controls Google Home & Smart Appliances (Washing Machine, AC, TV, Refrigerator, Lights, Fans, Locks).
 * - Runs White-Hat Website & Payload Decoding (URLs, TLS, Headers, HTML, JWT, Base64, Hex).
 * - Performs Agent & Live Voice Clarity Self-Checks.
 * - Automatically queues internet-dependent tasks in [OfflineTaskManager] when offline.
 */
class OfflineActionEngine(
    private val context: Context,
    private val bluetoothManager: JarvisBluetoothManager,
    private val wifiManager: JarvisWifiManager,
    private val connector: UniversalConnector,
    private val smartHomeManager: JarvisSmartHomeManager,
    private val taskManager: OfflineTaskManager,
    private val nlpEngine: LocalNlpEngine,
    private val voiceEngineProvider: () -> JarvisVoiceEngine?,
    private val phoneControlManager: JarvisPhoneControlManager = JarvisPhoneControlManager(context)
) {
    private var torchOn = false

    suspend fun execute(rawPrompt: String, settings: JarvisSettings = JarvisSettings()): ActionExecutionResult {
        return tryLocalAction(rawPrompt, settings) ?: ActionExecutionResult(
            localized(
                resolveReplyLanguage(rawPrompt, settings.language),
                en = "At your service, Sir. Connect to the internet to engage Gemini AI, or use offline controls like Camera, Torch, Smart Home, Bluetooth, Wi-Fi, or Decoder.",
                bn = "আমি প্রস্তুত, স্যার। Gemini AI-এর জন্য ইন্টারনেট চালু করুন, অথবা অফলাইনে ক্যামেরা, টর্চ, স্মার্ট হোম, ব্লুটুথ, ওয়াই-ফাই বা ডিকোডার ব্যবহার করুন।",
                hi = "मैं तैयार हूँ, सर।"
            )
        )
    }

    /**
     * Executes deterministic offline device, smart-home, decoder, security, and queued web actions.
     * Returns null when the prompt is conversational or generative and should route to [com.example.ai.JarvisAiOrchestrator].
     */
    suspend fun tryLocalAction(rawPrompt: String, settings: JarvisSettings = JarvisSettings()): ActionExecutionResult? {
        val prompt = rawPrompt.trim()
        val lower = prompt.lowercase(Locale.getDefault())
        val replyLang = resolveReplyLanguage(prompt, settings.language)
        val nlp = nlpEngine.classify(prompt)
        val priority = if (nlp.slots.isHighPriority) TaskPriority.HIGH else TaskPriority.NORMAL

        // 1. White-Hat ethical guardrail
        if (Regex("(hack|phish|ddos|malware|ransomware|steal password|crack wifi|হ্যাক)")
                .containsMatchIn(lower)
        ) {
            return ActionExecutionResult(
                responseText = localized(
                    replyLang,
                    en = "I operate strictly under White-Hat defensive Cybersecurity principles, Sir. I cannot perform unauthorized attacks, but I can run SOC Network Audits, OWASP TLS/Header Grading, Secret/SAST Code Scans, Threat Shield & Payload Decoding.",
                    bn = "আমি কেবল White-Hat প্রতিরক্ষামূলক সাইবার সিকিউরিটি নীতিতে কাজ করি, স্যার। ক্ষতিকর কাজে সাহায্য করতে পারি না; তবে SOC নেটওয়ার্ক অডিট, OWASP ওয়েবসাইট নিরাপত্তা গ্রেডিং, কোড/সিক্রেট লিক স্ক্যান ও ভাইরাস শিল্ড ব্যবহার করতে পারবেন।",
                    hi = "मैं केवल White-Hat सुरक्षा सिद्धांतों पर काम करता हूँ, सर।"
                ),
                shortcut = AgentActionShortcut("Open Cybersecurity Hub", AgentRoute.SECURITY)
            )
        }

        // 1b. Defensive Cybersecurity Expert Offline & Live Tools
        if (Regex("(cyber audit|attack surface|network security|vpn check|dns check|hardening|সাইবার অডিট|নেটওয়ার্ক সুরক্ষা|নেটওয়ার্ক সুরক্ষা)")
                .containsMatchIn(lower)
        ) {
            val net = com.example.security.CyberSecurityExpertEngine.runNetworkAndDeviceHardeningAudit(context)
            val reply = localized(
                replyLang,
                en = buildString {
                    appendLine("SOC Network & Attack Surface Audit — Grade ${net.grade} (${net.hardeningScore}%):")
                    appendLine("• Transport: ${net.transportSummary} | VPN: ${if (net.vpnActive) "ACTIVE" else "OFF"} | Private DNS: ${if (net.privateDnsActive) "ON" else "OFF"}")
                    appendLine("• Proxy (MITM check): ${net.proxyConfigured ?: "Direct (No proxy)"} | Patch: ${net.integrity.securityPatchLevel}")
                    net.findings.take(4).forEach { appendLine("• $it") }
                    appendLine("Recommendation: ${net.recommendations.firstOrNull().orEmpty()}")
                },
                bn = buildString {
                    appendLine("SOC নেটওয়ার্ক ও সাইবার অডিট — গ্রেড ${net.grade} (${net.hardeningScore}%):")
                    appendLine("• নেটওয়ার্ক: ${net.transportSummary} | VPN: ${if (net.vpnActive) "চালু" else "বন্ধ"} | Private DNS: ${if (net.privateDnsActive) "চালু" else "বন্ধ"}")
                    appendLine("• প্রক্সি (MITM চেক): ${net.proxyConfigured ?: "নেই (নিরাপদ)"} | প্যাচ: ${net.integrity.securityPatchLevel}")
                    net.findings.take(4).forEach { appendLine("• $it") }
                    appendLine("পরামর্শ: ${net.recommendations.firstOrNull().orEmpty()}")
                },
                hi = "SOC नेटवर्क ऑडिट — ग्रेड ${net.grade} (${net.hardeningScore}%)"
            ).trim()
            return ActionExecutionResult(reply, AgentActionShortcut("Open Cybersecurity SOC", AgentRoute.SECURITY))
        }

        if (Regex("(owasp|security headers|tls grade|ssl check|পোস্টার গ্রেড|হেডার চেক|এসএসএল চেক)").containsMatchIn(lower)) {
            val urlCandidate = Regex("(?i)(https?://\\S+|[a-z0-9-]+\\.(?:com|org|net|io|dev|ai|edu|gov|bd|in|co|app|info|me)(?:/\\S*)?)")
                .find(prompt)?.value
            if (urlCandidate != null) {
                val rep = com.example.security.CyberSecurityExpertEngine.auditOwaspPosture(urlCandidate, settings.offlineIsolationMode)
                val reply = buildString {
                    appendLine("OWASP & TLS Posture (${rep.host}) — Grade ${rep.grade} (${rep.score}/100):")
                    if (rep.resolvedIps.isNotEmpty()) appendLine("• DNS IPs: ${rep.resolvedIps.joinToString(", ")}")
                    appendLine("• ${rep.tlsProtocolInfo}")
                    appendLine("• Passed: ${rep.passedChecks.size} | Missing Headers: ${rep.missingHeaders.size}")
                    rep.missingHeaders.take(4).forEach { appendLine("  ✗ $it") }
                }.trim()
                return ActionExecutionResult(reply, AgentActionShortcut("Open OWASP Auditor", AgentRoute.SECURITY))
            }
        }

        if (Regex("^(?:generate|create)\\s+(?:strong\\s+)?(?:password|key)$|^(?:শক্তিশালী\\s+)?পাসওয়ার্ড\\s*(?:বানাও|তৈরি করো)$|^(?:শক্তিশালী\\s+)?পাসওয়ার্ড\\s*(?:বানাও|তৈরি করো)$", RegexOption.IGNORE_CASE)
                .matches(lower)
        ) {
            val pw = com.example.security.CyberSecurityExpertEngine.generateCryptographicPassword(20)
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Generated ${pw.strengthLabel} Password (${pw.entropyBits} bits entropy):\n${pw.password}",
                    bn = "তৈরি হয়েছে ${pw.strengthLabel} পাসওয়ার্ড (${pw.entropyBits} বিট এনট্রপি):\n${pw.password}",
                    hi = "मजबूत पासवर्ड तैयार है (${pw.entropyBits} bits):\n${pw.password}"
                ),
                AgentActionShortcut("Open Crypto Lab", AgentRoute.SECURITY)
            )
        }

        val hashMatch = Regex("(?i)^(?:hash|sha256|sha-256|md5|হ্যাশ বের করো|হ্যাশ করো)\\s+(.+)$").find(prompt)
        if (hashMatch != null) {
            val target = hashMatch.groupValues[1].trim()
            val h = com.example.security.CyberSecurityExpertEngine.computeHashes(target)
            return ActionExecutionResult(
                "Cryptographic Hashes (${h.inputLength} chars):\n• SHA-256: ${h.sha256}\n• SHA-1: ${h.sha1}\n• MD5: ${h.md5}",
                AgentActionShortcut("Open Crypto Lab", AgentRoute.SECURITY)
            )
        }

        val sastMatch = Regex("(?i)^(?:scan secrets|scan code|sast scan|check leak|সিক্রেট স্ক্যান|কোড স্ক্যান)\\s+([\\s\\S]+)$").find(prompt)
        if (sastMatch != null) {
            val snippet = sastMatch.groupValues[1].trim()
            val rep = com.example.security.CyberSecurityExpertEngine.scanSecretsAndVulnerabilities(snippet)
            val reply = buildString {
                appendLine(rep.summary)
                rep.findings.forEach { f ->
                    appendLine("• [${f.severity.name}] ${f.category} (${f.maskedPreview}): ${f.description}")
                }
            }.trim()
            return ActionExecutionResult(reply, AgentActionShortcut("Open SAST Scanner", AgentRoute.SECURITY))
        }

        // 2. Agent & Live Voice Clarity Self-Check
        if (Regex("(check voice|voice check|test voice|check agent|agent check|voice test|ভয়েস চেক|ভয়েস চেক|এজেন্ট চেক|কথা স্পষ্ট)")
                .containsMatchIn(lower)
        ) {
            val report = voiceEngineProvider()?.runVoiceDiagnostics(settings, speakVerification = true)
            return ActionExecutionResult(
                responseText = report?.summaryText ?: "Voice engine initialized, Sir.",
                shortcut = AgentActionShortcut("Voice & Language Settings", AgentRoute.SETTINGS)
            )
        }

        // 2b. Airbnb & Bank Account Vault quick access
        if (Regex("(airbnb|এয়ারবিএনবি|এয়ারবিএনবি)").containsMatchIn(lower)) {
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Airbnb Vault ready, Sir. Open the Accounts Hub to launch Airbnb Trips/Hosting or sync your live iCal reservation calendar.",
                    bn = "এয়ারবিএনবি ভল্ট প্রস্তুত, স্যার। Accounts Hub-এ গিয়ে সরাসরি Airbnb অ্যাপ, ট্রিপ বা লাইভ রিজার্ভেশন ক্যালেন্ডার ব্যবহার করুন।",
                    hi = "Airbnb वॉल्ट तैयार है, सर।"
                ),
                AgentActionShortcut("Open Airbnb & Bank Hub", AgentRoute.CONNECTORS)
            )
        }
        if (Regex("(bank account|my bank|bank vault|bkash|nagad|ব্যাংক|বিকাশ|নগদ)").containsMatchIn(lower)) {
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Protected Bank & Financial Vault ready, Sir. Verify with your device lock in Accounts Hub to open installed banking apps or TLS-verified bank portals.",
                    bn = "নিরাপদ ব্যাংক অ্যাকাউন্ট ভল্ট প্রস্তুত, স্যার। Accounts Hub-এ ডিভাইস লক যাচাই করে আপনার ব্যাংক অ্যাপ বা যাচাইকৃত পোর্টাল খুলুন।",
                    hi = "सुरक्षित बैंक वॉल्ट तैयार है, सर।"
                ),
                AgentActionShortcut("Open Bank Vault", AgentRoute.CONNECTORS)
            )
        }
        if (Regex("(open studio|ai studio|website builder|image studio|স্টুডিও)").containsMatchIn(lower)) {
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Opening AI Studio & Discovery Hub (Nano Banana Image Gen, Web/PWA Builder, Vision Scan & Live Price Finder), Sir.",
                    bn = "AI Studio ও ডিসকভারি হাব খোলা হচ্ছে, স্যার।",
                    hi = "AI Studio खोला जा रहा है, सर।"
                ),
                AgentActionShortcut("Open AI Studio", AgentRoute.STUDIO)
            )
        }
        if (Regex("(phone control|control phone|live camera|পুরো ফোন|ফোন কন্ট্রোল|ক্যামেরা অ্যাক্সেস|মাইক্রোফোন অ্যাক্সেস)").containsMatchIn(lower)) {
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Opening Full Phone Control, Live CameraX Vision & Sensor Access Hub, Sir.",
                    bn = "ফুল ফোন কন্ট্রোল, লাইভ ক্যামেরা ও মাইক্রোফোন এক্সেস হাব খোলা হচ্ছে, স্যার।",
                    hi = "फ़ोन कंट्रोल और लाइव कैमरा हब खोला जा रहा है, सर।"
                ),
                AgentActionShortcut("Open Phone Control & Camera", AgentRoute.PHONE_CONTROL)
            )
        }

        // 2b2. Tony Stark J.A.R.V.I.S. Suit & Autonomous Agent Daily Briefing
        if (Regex("(daily briefing|briefing|brief me|agent status|ব্রিফিং|সারাদিনের রিপোর্ট|এজেন্ট স্ট্যাটাস)").containsMatchIn(lower)) {
            val scan = JarvisShieldStateHolder.scanNowSuspend(context)
            val telemetry = buildDeviceTelemetry(replyLang)
            val pendingCount = taskManager.tasks.value.count { it.status == TaskStatus.PENDING_NETWORK }
            val online = taskManager.canExecuteOnlineNow()
            val text = localized(
                replyLang,
                en = buildString {
                    appendLine("J.A.R.V.I.S. Agent Daily Briefing, Sir:")
                    appendLine("• Core Mode: ${if (online) "Online AI Agent Ready" else "Offline Local Agent Active"}")
                    appendLine("• 24/7 Virus Shield: ${scan.overallScore}% (${scan.totalAppsScanned} apps, ${scan.highRiskApps.size} threats)")
                    appendLine("• Task Queue: $pendingCount pending task(s)")
                    appendLine("• Smart Appliances: ${smartHomeManager.devices.value.size} configured")
                    appendLine()
                    append(telemetry)
                },
                bn = buildString {
                    appendLine("J.A.R.V.I.S. এআই এজেন্ট ডেইলি ব্রিফিং, স্যার:")
                    appendLine("• কোর স্ট্যাটাস: ${if (online) "অনলাইন এআই এজেন্ট সক্রিয়" else "অফলাইন লোকাল এজেন্ট সচল"}")
                    appendLine("• ২৪/৭ ভাইরাস শিল্ড: ${scan.overallScore}% (${scan.totalAppsScanned}টি অ্যাপ, ${scan.highRiskApps.size}টি ঝুঁকি)")
                    appendLine("• টাস্ক কিউ: $pendingCount টি পেন্ডিং")
                    appendLine("• স্মার্ট ডিভাইস: ${smartHomeManager.devices.value.size}টি যুক্ত")
                    appendLine()
                    append(telemetry)
                },
                hi = "J.A.R.V.I.S. एजेंट दैनिक ब्रीफिंग, सर:\n• सुरक्षा: ${scan.overallScore}%\n$telemetry"
            )
            return ActionExecutionResult(
                responseText = text,
                shortcut = AgentActionShortcut("Open 24/7 Threat Shield", AgentRoute.SECURITY)
            )
        }
        if (Regex("(run diagnostics|system diagnostics|full diagnostics|suit status|jarvis status|ডায়াগনস্টিক|ডায়াগনস্টিক|পুরো রিপোর্ট)").containsMatchIn(lower)) {
            return ActionExecutionResult(
                responseText = buildStarkSuiteDiagnostics(replyLang),
                shortcut = AgentActionShortcut("Open Phone Control", AgentRoute.PHONE_CONTROL)
            )
        }
        if (Regex("^(wake up|daddy's home|hello jarvis|hi jarvis|hey jarvis|হ্যালো জার্ভিস|জার্ভিস জেগে ওঠো)[!.?]*$").containsMatchIn(lower)) {
            val pcs = phoneControlManager.state.value
            val netLabel = if (taskManager.canExecuteOnlineNow()) "Online" else "Offline Core"
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "At your service, Sir. J.A.R.V.I.S. systems nominal — $netLabel, Camera ${if (pcs.cameraGranted) "armed" else "standby"}, Microphone ${if (pcs.micGranted) "ready" else "standby"}, and ${smartHomeManager.devices.value.size} smart appliances online. Shall we begin?",
                    bn = "আপনার সেবায় প্রস্তুত, স্যার। J.A.R.V.I.S. সম্পূর্ণ সচল — $netLabel, ক্যামেরা ${if (pcs.cameraGranted) "সক্রিয়" else "স্ট্যান্ডবাই"}, মাইক্রোফোন ${if (pcs.micGranted) "প্রস্তুত" else "স্ট্যান্ডবাই"} এবং ${smartHomeManager.devices.value.size}টি স্মার্ট ডিভাইস যুক্ত। কোথা থেকে শুরু করব?",
                    hi = "आपकी सेवा में हाज़िर हूँ, सर। सभी J.A.R.V.I.S. सिस्टम तैयार हैं।"
                ),
                AgentActionShortcut("Run Full Diagnostics", AgentRoute.PHONE_CONTROL)
            )
        }

        // 2c. Full Phone Navigation, Hardware, Brightness, Ringer & Timer Commands (Offline)
        handleFullPhoneControl(prompt, lower, replyLang)?.let { result ->
            taskManager.recordCompletedOfflineTask(prompt, "phone_control", result.responseText, "PHONE_CONTROL", 0.96f)
            return result
        }

        // 3. Google Home App & Smart Appliance Controls (Washing Machine, AC, TV, Fridge, Lights, Fan, Lock)
        if (Regex("(google home|গুগল হোম|smart home|স্মার্ট হোম|appliances)").containsMatchIn(lower)) {
            if (Regex("(open|launch|start|খোলো|খুলো)").containsMatchIn(lower)) {
                smartHomeManager.openGoogleHomeApp()
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "Opening Google Home and Smart Appliance Hub, Sir. You can control your Washing Machine, AC, TV, and Refrigerator directly.",
                        bn = "Google Home ও স্মার্ট অ্যাপ্লায়েন্স হাব খোলা হচ্ছে, স্যার। ওয়াশিং মেশিন, এসি, টিভি ও ফ্রিজ এখান থেকে কন্ট্রোল করতে পারবেন।",
                        hi = "Google Home और स्मार्ट डिवाइस हब खोला जा रहा है, सर।"
                    ),
                    AgentActionShortcut("Open Google Home & IoT", AgentRoute.SMART_HOME)
                )
            }
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Google Home & Smart IoT Hub ready, Sir. ${smartHomeManager.devices.value.size} appliances configured (Washing Machine, AC, TV, Refrigerator, Light, Fan).",
                    bn = "Google Home ও স্মার্ট IoT হাব প্রস্তুত, স্যার। ওয়াশিং মেশিন, এসি, টিভি ও ফ্রিজসহ ${smartHomeManager.devices.value.size}টি ডিভাইস যুক্ত আছে।",
                    hi = "Google Home और स्मार्ट हब तैयार है, सर।"
                ),
                AgentActionShortcut("Open Google Home & IoT", AgentRoute.SMART_HOME)
            )
        }

        smartHomeManager.executeVoiceOrTextCommand(prompt)?.let { smartResult ->
            taskManager.recordCompletedOfflineTask(
                prompt = prompt,
                targetValue = "smart_home",
                summary = smartResult.summary,
                intentName = "SMART_HOME_CONTROL",
                confidence = 0.96f
            )
            return ActionExecutionResult(
                responseText = smartResult.summary,
                shortcut = AgentActionShortcut("Open Google Home & IoT", AgentRoute.SMART_HOME)
            )
        }

        // 4. Website & Payload Decoder (URL / HTML / TLS / Headers / JWT / Base64 / Hex)
        if (Regex("(decode|inspect|ডিকোড)").containsMatchIn(lower)) {
            val urlCandidate = Regex("(?i)(https?://\\S+|[a-z0-9-]+\\.(?:com|org|net|io|dev|ai|edu|gov|bd|in|co|app|info|me)(?:/\\S*)?)")
                .find(prompt)?.value
            if (urlCandidate != null) {
                val report = WebsiteDecoderEngine.decodeWebsite(urlCandidate, settings.offlineIsolationMode)
                val reply = buildString {
                    appendLine("Website Decoded (${report.scheme.uppercase()} • ${report.host}):")
                    appendLine("• Title: ${report.pageTitle}")
                    report.httpStatusCode?.let { appendLine("• HTTP Status: $it (${report.responseTimeMs ?: 0} ms)") }
                    if (report.queryParameters.isNotEmpty()) {
                        appendLine("• Query Params: ${report.queryParameters.joinToString { "${it.first}=${it.second}" }}")
                    }
                    if (report.detectedTechnologies.isNotEmpty()) {
                        appendLine("• Tech Stack: ${report.detectedTechnologies.joinToString(", ")}")
                    }
                    appendLine("• Scripts: ${report.scriptSources.size} | Outbound Links: ${report.extractedLinks.size}")
                }.trim()
                taskManager.recordCompletedOfflineTask(
                    prompt = prompt,
                    targetValue = urlCandidate,
                    summary = reply,
                    intentName = "WEBSITE_DECODE",
                    confidence = 0.96f
                )
                return ActionExecutionResult(
                    responseText = reply,
                    shortcut = AgentActionShortcut("Open Full Decoder", AgentRoute.SECURITY)
                )
            }

            val payload = prompt
                .replace(Regex("(?i)^(please |jarvis )?(decode|base64|jwt|hex)\\s*"), "")
                .replace("ডিকোড করো", "")
                .trim()
            if (payload.isNotBlank()) {
                val decoded = WebsiteDecoderEngine.decodePayload(payload)
                val reply = "Decoded (${decoded.detectedFormat}):\n${decoded.decodedOutput.take(450)}"
                taskManager.recordCompletedOfflineTask(
                    prompt = prompt,
                    targetValue = "payload_decode",
                    summary = decoded.detectedFormat,
                    intentName = "PAYLOAD_DECODE",
                    confidence = 0.95f
                )
                return ActionExecutionResult(
                    responseText = reply,
                    shortcut = AgentActionShortcut("Open Decoder Tab", AgentRoute.SECURITY)
                )
            }
        }

        // 5. Media & Additional Device Controls (Offline)
        if (Regex("(play music|pause music|resume music|next track|next song|previous track|গান|মিউজিক)").containsMatchIn(lower)) {
            val keyCode = when {
                Regex("(next|পরের)").containsMatchIn(lower) -> KeyEvent.KEYCODE_MEDIA_NEXT
                Regex("(previous|back|আগের)").containsMatchIn(lower) -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            return ActionExecutionResult(
                localized(replyLang, "Media playback command sent, Sir.", "মিডিয়া প্লেব্যাক কমান্ড পাঠানো হয়েছে, স্যার।", "मीडिया नियंत्रण भेजा गया, सर।")
            )
        }

        // 6. Flashlight / Torch control (Offline)
        if (Regex("(flashlight|torch|টর্চ|ফ্ল্যাশ)").containsMatchIn(lower)) {
            val turnOff = Regex("(off|stop|close|বন্ধ|নিভাও)").containsMatchIn(lower)
            val turnOn = Regex("(on|start|open|জ্বালাও|চালু|অন)").containsMatchIn(lower)
            val targetState = when {
                turnOff -> false
                turnOn -> true
                else -> !torchOn
            }
            val res = toggleFlashlight(targetState, replyLang)
            taskManager.recordCompletedOfflineTask(prompt, "flashlight", res.responseText, nlp.intent.name, nlp.confidence)
            return res
        }

        // 7. Volume / Audio control (Offline)
        if (Regex("(volume|sound|mute|সাউন্ড|ভলিউম|আওয়াজ|মিউট)").containsMatchIn(lower) ||
            nlp.intent in listOf(JarvisIntent.VOLUME_UP, JarvisIntent.VOLUME_DOWN, JarvisIntent.VOLUME_MUTE)
        ) {
            val res = adjustVolume(lower, replyLang)
            taskManager.recordCompletedOfflineTask(prompt, "audio_volume", res.responseText, nlp.intent.name, nlp.confidence)
            return res
        }

        // 8. Web search (Online — auto-queued if offline!)
        val searchMatch = Regex("(?i)^(?:search|google|find)\\s+(?:for\\s+)?(.+)$").find(prompt)
        val searchQuery = searchMatch?.groupValues?.getOrNull(1)?.trim() ?: nlp.slots.searchQuery
        if (searchQuery != null) {
            if (!taskManager.canExecuteOnlineNow()) {
                taskManager.enqueuePendingTask(
                    prompt = prompt,
                    taskType = TaskType.WEB_SEARCH,
                    targetValue = searchQuery,
                    priority = priority,
                    intentName = nlp.intent.name,
                    confidence = nlp.confidence
                )
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "No internet connection right now, Sir. I added web search \"$searchQuery\" to your Task Queue and will run it automatically as soon as you're back online.",
                        bn = "এখন ইন্টারনেট সংযোগ নেই, স্যার। \"$searchQuery\" সার্চটি টাস্ক কিউতে রাখা হয়েছে—ইন্টারনেট ফিরে আসলেই কাজটি হয়ে যাবে।",
                        hi = "अभी इंटरनेट उपलब्ध नहीं है, सर। \"$searchQuery\" को Task Queue में जोड़ दिया गया है।"
                    ),
                    AgentActionShortcut("View Task Queue", AgentRoute.QUEUE)
                )
            }
            connector.openWebSearch(searchQuery)
            taskManager.recordCompletedOfflineTask(
                prompt = prompt,
                targetValue = searchQuery,
                summary = "Opened web search for \"$searchQuery\".",
                intentName = nlp.intent.name,
                confidence = nlp.confidence
            )
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Searching the web for \"$searchQuery\", Sir.",
                    bn = "\"$searchQuery\" ওয়েবে খোঁজা হচ্ছে, স্যার।",
                    hi = "वेब पर \"$searchQuery\" खोजा जा रहा है, सर।"
                )
            )
        }

        // 9. Connect / Open / Check any website worldwide (Online — auto-queued if offline!)
        val domainMatch = Regex(
            "(?i)(?:https?://)?([a-z0-9-]+\\.(?:com|org|net|io|dev|ai|edu|gov|bd|in|co|app|info|me)(?:/\\S*)?)"
        ).find(prompt)
        if (domainMatch != null) {
            val rawUrl = domainMatch.value
            val isCheckOnly = Regex("(check|audit|scan|verify|চেক)").containsMatchIn(lower)
            val taskType = if (isCheckOnly) TaskType.WEBSITE_AUDIT else TaskType.WEB_CONNECT

            if (!taskManager.canExecuteOnlineNow()) {
                taskManager.enqueuePendingTask(
                    prompt = prompt,
                    taskType = taskType,
                    targetValue = rawUrl,
                    priority = priority,
                    intentName = nlp.intent.name,
                    confidence = nlp.confidence
                )
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "You're currently offline, Sir. Added $rawUrl to the Task Queue — I'll connect and verify it automatically once internet returns.",
                        bn = "এখন ইন্টারনেট সংযোগ বিচ্ছিন্ন, স্যার। $rawUrl টাস্ক কিউতে রাখা হয়েছে—ইন্টারনেট কানেক্ট হলেই স্বয়ংক্রিয়ভাবে সম্পন্ন হবে।",
                        hi = "अभी आप ऑफ़लाइन हैं, सर। $rawUrl को Task Queue में रखा गया है।"
                    ),
                    AgentActionShortcut("View Task Queue", AgentRoute.QUEUE)
                )
            }

            val saved = connector.saveWebsite("", rawUrl)
            connector.openWebsite(saved.url)
            taskManager.recordCompletedOfflineTask(
                prompt = prompt,
                targetValue = saved.url,
                summary = "Connected to ${saved.url}",
                intentName = nlp.intent.name,
                confidence = nlp.confidence
            )
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Connecting to ${saved.url}, Sir. Saved to Connected Websites.",
                    bn = "${saved.url}-এ যুক্ত হচ্ছি, স্যার। Connected Websites-এ সেভ করা হয়েছে।",
                    hi = "${saved.url} से कनेक्ट किया जा रहा है, सर।"
                ),
                AgentActionShortcut("Open Connectors", AgentRoute.CONNECTORS)
            )
        }

        // 10. Open installed apps or system panels (Offline)
        if (Regex("(open |launch |start |connect |খোলো|খুলো|চালু করো|ওপেন)").containsMatchIn(lower) ||
            nlp.intent == JarvisIntent.OPEN_APP
        ) {
            val target = extractAppTarget(prompt).ifEmpty { nlp.slots.appTarget.orEmpty() }
            if (target.isNotEmpty()) {
                tryLaunchTarget(target, replyLang)?.let {
                    taskManager.recordCompletedOfflineTask(prompt, target, it.responseText, nlp.intent.name, nlp.confidence)
                    return it
                }
            }
        }

        // 11. Task queue, Connectors, Settings shortcuts
        if (Regex("(queue|tasks|pending|কিউ|টাস্ক)").containsMatchIn(lower)) {
            val pendingCount = taskManager.tasks.value.count { it.status == TaskStatus.PENDING_NETWORK }
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "You currently have $pendingCount pending network task(s) in the queue, Sir.",
                    bn = "টাস্ক কিউতে বর্তমানে $pendingCount টি পেন্ডিং নেটওয়ার্ক টাস্ক রয়েছে, স্যার।",
                    hi = "Task Queue में अभी $pendingCount पेंडिंग कार्य हैं, सर।"
                ),
                AgentActionShortcut("Open Task Queue", AgentRoute.QUEUE)
            )
        }

        if (Regex("(apps|websites|connector|অ্যাপস|ওয়েবসাইট|ওয়েবসাইট)").containsMatchIn(lower)) {
            val installedCount = connector.listInstalledApps().size
            val sitesCount = connector.getConnectedWebsites().size
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Universal Connector ready, Sir. $installedCount phone app(s) and $sitesCount saved website(s) available.",
                    bn = "ইউনিভার্সাল কানেক্টর প্রস্তুত, স্যার। ফোনে $installedCount টি অ্যাপ ও $sitesCount টি ওয়েবসাইট পাওয়া গেছে।",
                    hi = "यूनिवर्सल कनेक्टर तैयार है, सर।"
                ),
                AgentActionShortcut("Open App & Web Connectors", AgentRoute.CONNECTORS)
            )
        }

        if (Regex("(jarvis settings|wake word|language|সেটিংস|ওয়েক|ভাষা)").containsMatchIn(lower)) {
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Current wake word is \"${settings.wakeWord}\" and language is ${settings.language.displayName}. Open Settings to customize them, Sir.",
                    bn = "বর্তমান ওয়েক ওয়ার্ড \"${settings.wakeWord}\" এবং ভাষা ${settings.language.displayName}। পরিবর্তন করতে Settings খুলুন, স্যার।",
                    hi = "वर्तमान वेक वर्ड \"${settings.wakeWord}\" है। Settings खोलें, सर।"
                ),
                AgentActionShortcut("Open Settings", AgentRoute.SETTINGS)
            )
        }

        // 12. Permission inspection
        if (Regex("(permission|পারমিশন|অনুমতি)").containsMatchIn(lower)) {
            val missing = PermissionHelper.missingWirelessPermissions(context)
            return if (missing.isEmpty()) {
                ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "All Bluetooth and Wi-Fi scanning permissions are granted, Sir.",
                        bn = "সব ব্লুটুথ ও ওয়াই-ফাই পারমিশন চালু আছে, স্যার।",
                        hi = "सभी अनुमतियाँ सक्रिय हैं, सर।"
                    )
                )
            } else {
                val labels = missing.joinToString(", ") { PermissionHelper.describePermission(it) }
                ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "Missing permissions: $labels. Tap below to grant access.",
                        bn = "অনুমতি বাকি আছে: $labels। নিচের বাটন থেকে অনুমতি দিন।",
                        hi = "अनुपस्थित अनुमतियाँ: $labels।"
                    ),
                    AgentActionShortcut("Grant Wireless Permissions", requestPermissions = true)
                )
            }
        }

        // 13. Bluetooth discovery & pairing (Offline)
        if (Regex("(bluetooth|ব্লুটুথ|pair|পেয়ার|পেয়ার)").containsMatchIn(lower)) {
            if (!PermissionHelper.hasBluetoothPermissions(context)) {
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "BLUETOOTH_SCAN and BLUETOOTH_CONNECT permissions are required, Sir.",
                        bn = "ব্লুটুথ স্ক্যানের জন্য BLUETOOTH_SCAN ও BLUETOOTH_CONNECT অনুমতি দিন, স্যার।",
                        hi = "ब्लूटूथ स्कैन के लिए अनुमति आवश्यक है, सर।"
                    ),
                    AgentActionShortcut("Grant Wireless Permissions", requestPermissions = true)
                )
            }
            if (bluetoothManager.adapterState.value !is BluetoothAdapterState.Enabled) {
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "Bluetooth radio is currently off, Sir. Open Bluetooth Manager to enable it.",
                        bn = "ব্লুটুথ রেডিও বন্ধ আছে। Bluetooth Manager থেকে চালু করুন, স্যার।",
                        hi = "ब्लूटूथ बंद है, सर।"
                    ),
                    AgentActionShortcut("Open Bluetooth Manager", AgentRoute.BLUETOOTH)
                )
            }
            bluetoothManager.startDiscovery(includeBle = true)
            val list = bluetoothManager.devices.value
            val paired = list.count { it.bondStatus == BondStatus.BONDED }
            taskManager.recordCompletedOfflineTask(prompt, "bluetooth_scan", "Scanned Bluetooth ($paired paired)", nlp.intent.name, nlp.confidence)
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Bluetooth Classic + BLE scan started, Sir. $paired paired and ${list.size} total device(s) detected.",
                    bn = "ব্লুটুথ Classic + BLE স্ক্যান চালু হয়েছে, স্যার। $paired টি পেয়ার্ড এবং মোট ${list.size} টি ডিভাইস পাওয়া গেছে।",
                    hi = "ब्लूटूथ स्कैन शुरू हो गया है, सर।"
                ),
                AgentActionShortcut("Open Bluetooth Manager", AgentRoute.BLUETOOTH)
            )
        }

        // 14. Wi-Fi scanner & connection (Offline)
        if (Regex("(wifi|wi-fi|ওয়াইফাই|ওয়াইফাই|ওইফাই|network|নেটওয়ার্ক|নেটওয়ার্ক)").containsMatchIn(lower)) {
            if (!PermissionHelper.hasWifiPermissions(context)) {
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "Wi-Fi scan permission (ACCESS_FINE_LOCATION / NEARBY_WIFI_DEVICES) is required, Sir.",
                        bn = "ওয়াই-ফাই স্ক্যানের জন্য লোকেশন ও Wi-Fi অনুমতি দিন, স্যার।",
                        hi = "वाई-फाई स्कैन के लिए अनुमति आवश्यक है, सर।"
                    ),
                    AgentActionShortcut("Grant Wireless Permissions", requestPermissions = true)
                )
            }
            if (wifiManager.radioState.value !is WifiRadioState.Enabled) {
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "Wi-Fi radio is off, Sir. Open Wi-Fi Manager to enable and connect.",
                        bn = "ওয়াই-ফাই রেডিও বন্ধ আছে। Wi-Fi Manager থেকে চালু করুন, স্যার।",
                        hi = "वाई-फाई बंद है, सर।"
                    ),
                    AgentActionShortcut("Open Wi-Fi Manager", AgentRoute.WIFI)
                )
            }
            wifiManager.startScan()
            val active = wifiManager.activeLink.value
            val nets = wifiManager.networks.value
            val weak = nets.count { it.isSecurityWarning }
            taskManager.recordCompletedOfflineTask(prompt, "wifi_scan", "Scanned ${nets.size} Wi-Fi networks", nlp.intent.name, nlp.confidence)
            return ActionExecutionResult(
                localized(
                    replyLang,
                    en = "Wi-Fi scan active, Sir. ${active?.let { "Connected to ${it.ssid} (${it.linkSpeedMbps} Mbps). " } ?: ""}${nets.size} network(s) found ($weak security alert(s)).",
                    bn = "ওয়াই-ফাই স্ক্যান শুরু হয়েছে, স্যার। ${nets.size} টি নেটওয়ার্ক পাওয়া গেছে ($weak টিতে সতর্কতা)।",
                    hi = "वाई-फाई स्कैन शुरू हो गया है, सर।"
                ),
                AgentActionShortcut("Open Wi-Fi Manager", AgentRoute.WIFI)
            )
        }

        // 15. Time, Date, Battery, Storage, RAM telemetry (Offline)
        if (Regex("(time|সময়|সময়|কয়টা|কয়টা)").containsMatchIn(lower)) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return ActionExecutionResult(
                localized(replyLang, "The current time is $timeStr, Sir.", "এখন সময় $timeStr, স্যার।", "अभी समय $timeStr है, सर।")
            )
        }
        if (Regex("(date|তারিখ)").containsMatchIn(lower)) {
            val dateStr = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())
            return ActionExecutionResult(
                localized(replyLang, "Today's date is $dateStr, Sir.", "আজকের তারিখ $dateStr, স্যার।", "आज की तारीख $dateStr है, सर।")
            )
        }
        if (Regex("(battery|ব্যাটারি|storage|স্টোরেজ|ram|memory|status|স্ট্যাটাস|telemetry)").containsMatchIn(lower)) {
            return ActionExecutionResult(buildDeviceTelemetry(replyLang))
        }
        if (Regex("(security|সিকিউরিটি|নিরাপত্তা|password|পাসওয়ার্ড|virus|ভাইরাস|malware|scan phone|ফোন স্ক্যান|সিকিউর|shield|threat|থ্রেট)").containsMatchIn(lower)) {
            if (Regex("(enable shield|activate shield|always protect|keep secure|সবসময় স্ক্যান|সবসময় সুরক্ষিত|সিকিউর রাখো)").containsMatchIn(lower)) {
                JarvisShieldStateHolder.setContinuousEnabled(context, true)
            }
            val report = JarvisShieldStateHolder.scanNowSuspend(context)
            val wantsDelete = Regex("(delete|remove|uninstall|clean|ডিলিট|মুছে|সরাও|পরিষ্কার)").containsMatchIn(lower)
            val firstHighRisk = report.highRiskApps.firstOrNull()
            if (wantsDelete && firstHighRisk != null) {
                runCatching {
                    context.startActivity(JarvisSecurityEngine.createUninstallIntent(firstHighRisk.packageName))
                }
                return ActionExecutionResult(
                    localized(
                        replyLang,
                        en = "Threat detected! Launching Android uninstall confirmation for ${firstHighRisk.appName} (Risk Score ${firstHighRisk.threatScore}/100). Total flagged: ${report.highRiskApps.size} high-risk, ${report.cautionApps.size} caution.",
                        bn = "সতর্কতা! সন্দেহজনক অ্যাপ ${firstHighRisk.appName} (স্কোর ${firstHighRisk.threatScore}/100) ডিলিট করার জন্য সিস্টেম আনইনস্টল প্রম্পট খোলা হয়েছে। মোট ${report.highRiskApps.size}টি উচ্চ-ঝুঁকিপূর্ণ অ্যাপ পাওয়া গেছে।",
                        hi = "${firstHighRisk.appName} को हटाने के लिए सिस्टम अनइंस्टॉल प्रॉम्प्ट खोला गया है, सर।"
                    ),
                    AgentActionShortcut("Manage Threats in Shield", AgentRoute.SECURITY)
                )
            }

            val threatList = report.highRiskApps.joinToString(", ") { "${it.appName} (${it.threatScore}/100)" }
            return ActionExecutionResult(
                if (report.highRiskApps.isNotEmpty()) {
                    localized(
                        replyLang,
                        en = "Deep Phone Scan Complete (Score ${report.overallScore}%): ${report.totalAppsScanned} apps scanned. ALERT: ${report.highRiskApps.size} high-risk app(s) detected: $threatList. Open Threat Shield to delete them immediately.",
                        bn = "ডিপ ফোন স্ক্যান সম্পন্ন (সুরক্ষা স্কোর ${report.overallScore}%): ${report.totalAppsScanned}টি অ্যাপ স্ক্যান হয়েছে। সতর্কতা: ${report.highRiskApps.size}টি ঝুঁকিপূর্ণ অ্যাপ পাওয়া গেছে: $threatList। ডিলিট করতে Threat Shield খুলুন।",
                        hi = "फ़ोन स्कैन पूरा हुआ (${report.overallScore}%): ${report.highRiskApps.size} जोखिम वाले ऐप मिले: $threatList।"
                    )
                } else {
                    localized(
                        replyLang,
                        en = "Phone Secure (${report.overallScore}%), Sir. Scanned ${report.totalAppsScanned} installed apps — 0 high-risk threats found (${report.cautionApps.size} caution). 24/7 Continuous Shield is ${if (JarvisShieldStateHolder.state.value.continuousProtectionEnabled) "ACTIVE" else "PAUSED"}.",
                        bn = "আপনার ফোন সম্পূর্ণ নিরাপদ (${report.overallScore}%), স্যার। ${report.totalAppsScanned}টি অ্যাপ স্ক্যান করে কোনো ভাইরাস বা উচ্চ-ঝুঁকির অ্যাপ পাওয়া যায়নি (${report.cautionApps.size}টি সতর্কতা)। ২৪/৭ শিল্ড ${if (JarvisShieldStateHolder.state.value.continuousProtectionEnabled) "সক্রিয়" else "বন্ধ"} আছে।",
                        hi = "फ़ोन सुरक्षित है (${report.overallScore}%), सर। ${report.totalAppsScanned} ऐप स्कैन किए गए और कोई वायरस नहीं मिला।"
                    )
                },
                AgentActionShortcut("Open 24/7 Threat Shield", AgentRoute.SECURITY)
            )
        }

        // 16. Direct app-name match fallback (Offline)
        tryLaunchTarget(lower, replyLang)?.let {
            taskManager.recordCompletedOfflineTask(prompt, lower, it.responseText, nlp.intent.name, nlp.confidence)
            return it
        }

        return null
    }

    private fun resolveReplyLanguage(prompt: String, configured: AppLanguage): AppLanguage {
        return when (configured) {
            AppLanguage.ENGLISH -> AppLanguage.ENGLISH
            AppLanguage.BENGALI -> AppLanguage.BENGALI
            AppLanguage.HINDI -> AppLanguage.HINDI
            AppLanguage.AUTO -> when {
                Regex("[\\u0980-\\u09FF]").containsMatchIn(prompt) -> AppLanguage.BENGALI
                Regex("[\\u0900-\\u097F]").containsMatchIn(prompt) -> AppLanguage.HINDI
                else -> AppLanguage.ENGLISH
            }
        }
    }

    private fun localized(lang: AppLanguage, en: String, bn: String, hi: String): String {
        return when (lang) {
            AppLanguage.BENGALI -> bn
            AppLanguage.HINDI -> hi
            else -> en
        }
    }

    private fun toggleFlashlight(enable: Boolean, lang: AppLanguage): ActionExecutionResult {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                ActionExecutionResult(
                    localized(lang, "No camera flash hardware detected on this device, Sir.", "এই ডিভাইসে ফ্ল্যাশলাইট হার্ডওয়্যার নেই, স্যার।", "फ़्लैशलाइट हार्डवेयर उपलब्ध नहीं है, सर।")
                )
            } else {
                cm.setTorchMode(cameraId, enable)
                torchOn = enable
                ActionExecutionResult(
                    localized(
                        lang,
                        en = if (enable) "Flashlight turned ON, Sir." else "Flashlight turned OFF, Sir.",
                        bn = if (enable) "টর্চলাইট চালু করা হয়েছে, স্যার।" else "টর্চলাইট বন্ধ করা হয়েছে, স্যার।",
                        hi = if (enable) "फ़्लैशलाइट चालू की गई, सर।" else "फ़्लैशलाइट बंद की गई, सर।"
                    )
                )
            }
        } catch (e: Exception) {
            ActionExecutionResult("Unable to toggle flashlight: ${e.localizedMessage}")
        }
    }

    private fun adjustVolume(lower: String, lang: AppLanguage): ActionExecutionResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionExecutionResult("Audio service unavailable, Sir.")
        return when {
            Regex("(mute|silent|মিউট|নীরব)").containsMatchIn(lower) -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(
                    localized(lang, "Media audio muted, Sir.", "সাউন্ড মিউট করা হয়েছে, স্যার।", "ऑडियो म्यूट किया गया, सर।")
                )
            }
            Regex("(down|lower|decrease|কমাও|কম)").containsMatchIn(lower) -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(
                    localized(lang, "Volume decreased, Sir.", "ভলিউম কমানো হয়েছে, স্যার।", "वॉल्यूम कम किया गया, सर।")
                )
            }
            else -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                ActionExecutionResult(
                    localized(lang, "Volume increased, Sir.", "ভলিউম বাড়ানো হয়েছে, স্যার।", "वॉल्यूम बढ़ाया गया, सर।")
                )
            }
        }
    }

    private fun extractAppTarget(prompt: String): String {
        return prompt
            .replace(Regex("(?i)^(please |jarvis |hey jarvis )"), "")
            .replace(Regex("(?i)\\b(open|launch|start|connect to|connect)\\b"), "")
            .replace(Regex("(খোলো|খুলো|চালু করো|ওপেন করুন|ওপেন)"), "")
            .trim()
    }

    private fun tryLaunchTarget(targetRaw: String, lang: AppLanguage): ActionExecutionResult? {
        val target = targetRaw.trim().lowercase(Locale.getDefault())
        if (target.isEmpty()) return null

        val systemIntent: Pair<String, Intent>? = when {
            Regex("(wifi settings|wi-fi settings|ওয়াইফাই সেটিংস)").containsMatchIn(target) ->
                "Wi-Fi Settings" to Intent(Settings.ACTION_WIFI_SETTINGS)
            Regex("(bluetooth settings|ব্লুটুথ সেটিংস)").containsMatchIn(target) ->
                "Bluetooth Settings" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            Regex("(brightness|display|ব্রাইটনেস|ডিসপ্লে)").containsMatchIn(target) ->
                "Display & Brightness" to Intent(Settings.ACTION_DISPLAY_SETTINGS)
            Regex("(battery saver|পাওয়ার সেভার)").containsMatchIn(target) ->
                "Battery Settings" to Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            Regex("(system settings|device settings|সেটিংস)").containsMatchIn(target) ->
                "System Settings" to Intent(Settings.ACTION_SETTINGS)
            Regex("(camera|ক্যামেরা)").containsMatchIn(target) ->
                "Camera" to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            Regex("(dialer|phone|ফোন|ডায়ালার)").containsMatchIn(target) ->
                "Phone Dialer" to Intent(Intent.ACTION_DIAL)
            Regex("(clock|alarm|অ্যালার্ম|ঘড়ি|ঘড়ি)").containsMatchIn(target) ->
                "Clock & Alarms" to Intent(AlarmClock.ACTION_SHOW_ALARMS)
            Regex("(browser|chrome|ব্রাউজার|ক্রোম)").containsMatchIn(target) ->
                "Web Browser" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
            Regex("(calculator|ক্যালকুলেটর)").containsMatchIn(target) ->
                "Calculator" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            Regex("(calendar|ক্যালেন্ডার)").containsMatchIn(target) ->
                "Calendar" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            Regex("(contacts|কন্টাক্টস)").containsMatchIn(target) ->
                "Contacts" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)
            Regex("(message|sms|মেসেজ)").containsMatchIn(target) ->
                "Messages" to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            else -> null
        }

        if (systemIntent != null) {
            return launchIntentSafe(systemIntent.second, systemIntent.first, lang)
        }

        val normalizedQuery = when {
            target.contains("ইউটিউব") -> "youtube"
            target.contains("হোয়াটসঅ্যাপ") || target.contains("হোয়াটসঅ্যাপ") -> "whatsapp"
            target.contains("ফেসবুক") -> "facebook"
            target.contains("টেলিগ্রাম") -> "telegram"
            else -> target
        }

        val apps = connector.listInstalledApps()
        val match = apps.firstOrNull { app ->
            val name = app.appName.lowercase(Locale.getDefault())
            name == normalizedQuery || name.contains(normalizedQuery)
        }

        if (match != null) {
            val launched = connector.launchApp(match.packageName)
            if (launched.isSuccess) {
                return ActionExecutionResult(
                    localized(lang, "Opening ${match.appName}, Sir.", "${match.appName} খোলা হচ্ছে, স্যার।", "${match.appName} खोला जा रहा है, सर।")
                )
            }
        }
        return null
    }

    private fun launchIntentSafe(intent: Intent, label: String, lang: AppLanguage): ActionExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionExecutionResult(
                localized(lang, "Opening $label, Sir.", "$label খোলা হচ্ছে, স্যার।", "$label खोला जा रहा है, सर।")
            )
        } catch (_: Exception) {
            ActionExecutionResult(
                localized(lang, "$label is not available on this device, Sir.", "এই ডিভাইসে $label পাওয়া যায়নি, স্যার।", "$label इस डिवाइस पर उपलब्ध नहीं है, सर।")
            )
        }
    }

    private fun handleFullPhoneControl(
        prompt: String,
        lower: String,
        lang: AppLanguage
    ): ActionExecutionResult? {
        fun wrapControlResult(res: Result<String>, fallbackRoute: AgentRoute = AgentRoute.PHONE_CONTROL): ActionExecutionResult {
            return res.fold(
                onSuccess = { ActionExecutionResult(it, AgentActionShortcut("Open Phone Control", fallbackRoute)) },
                onFailure = { err ->
                    ActionExecutionResult(
                        err.localizedMessage ?: "Permission required in Phone Control, Sir.",
                        AgentActionShortcut("Grant Phone Control Access", AgentRoute.PHONE_CONTROL)
                    )
                }
            )
        }

        val normalizedDigitsPrompt = normalizeBengaliDigits(prompt)
        val normalizedDigitsLower = normalizedDigitsPrompt.lowercase(Locale.getDefault())

        // System-wide Voice Access Navigation, Numbered Overlay & Gestures
        when {
            Regex("(show numbers|show labels|number grid|voice access grid|নম্বর দেখাও|গ্রিড দেখাও|ভয়েস অ্যাক্সেস|ভয়েস অ্যাক্সেস)").containsMatchIn(lower) -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    return ActionExecutionResult(
                        localized(lang, "Enable 'J.A.R.V.I.S Phone Control' in Accessibility Settings to show Voice Access tap numbers, Sir.", "স্ক্রিনে ট্যাপ নম্বর দেখাতে Accessibility Settings চালু করুন, স্যার।", "स्क्रीन पर टैप नंबर दिखाने के लिए Accessibility सक्षम करें, सर।"),
                        AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                    )
                }
                val count = JarvisAccessibilityService.showNumberedTapGrid()
                return ActionExecutionResult(
                    if (count > 0) localized(
                        lang,
                        en = "Voice Access numbered grid active ($count targets). Say 'Tap 1'..'Tap $count' or 'Hide numbers', Sir.",
                        bn = "ভয়েস অ্যাক্সেস নম্বর গ্রিড চালু হয়েছে ($count টি বাটন)। এখন 'ট্যাপ ১'..'ট্যাপ $count' বলুন অথবা 'নম্বর লুকাও' বলুন, স্যার।",
                        hi = "वॉइस एक्सेस नंबर ग्रिड सक्रिय है ($count बटन)।"
                    ) else localized(
                        lang,
                        en = "No clickable targets found on this screen window, Sir.",
                        bn = "এই স্ক্রিনে ট্যাপ করার মতো বাটন পাওয়া যায়নি, স্যার।",
                        hi = "इस स्क्रीन पर कोई टैप करने योग्य बटन नहीं मिला।"
                    ),
                    AgentActionShortcut("Voice Access Hub", AgentRoute.PHONE_CONTROL)
                )
            }
            Regex("(hide numbers|hide labels|close grid|নম্বর লুকাও|নম্বর বন্ধ|গ্রিড বন্ধ)").containsMatchIn(lower) -> {
                JarvisAccessibilityService.hideNumberedTapGrid()
                phoneControlManager.refreshState()
                return ActionExecutionResult(
                    localized(lang, "Voice Access numbered grid hidden, Sir.", "ভয়েস অ্যাক্সেস নম্বর গ্রিড লুকানো হয়েছে, স্যার।", "नंबर ग्रिड बंद कर दिया गया, सर।")
                )
            }
            Regex("(go home|home screen|হোমে যাও|হোম স্ক্রিন)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("home"))
            Regex("(go back|navigate back|ফিরে যাও|ব্যাক করো)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("back"))
            Regex("(recent apps|recents|app switcher|রিসেন্ট)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("recents"))
            Regex("(open notifications|notification shade|নোটিফিকেশন)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("notifications"))
            Regex("(quick settings|কুইক সেটিংস)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("quick_settings"))
            Regex("(take screenshot|screenshot|স্ক্রিনশট)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("screenshot"))
            Regex("(lock screen|lock phone|স্ক্রিন লক|ফোন লক)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("lock"))
            Regex("(power menu|power dialog|পাওয়ার মেনু|পাওয়ার মেনু)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("power"))
            Regex("(scroll down|নিচে স্ক্রল|নিচে যাও)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("scroll_down"))
            Regex("(scroll up|উপরে স্ক্রল|উপরে যাও)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("scroll_up"))
            Regex("(scroll left|swipe left|বামে স্ক্রল|বামে যাও)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("scroll_left"))
            Regex("(scroll right|swipe right|ডানে স্ক্রল|ডানে যাও)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.performGlobalNavigation("scroll_right"))
            Regex("(read screen|what is on screen|স্ক্রিন পড়ো|স্ক্রিনে কী)").containsMatchIn(lower) -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    return ActionExecutionResult(
                        localized(lang, "Enable 'J.A.R.V.I.S Phone Control' in Accessibility Settings to read the active screen, Sir.", "স্ক্রিন পড়তে Accessibility Settings চালু করুন, স্যার।", "स्क्रीन पढ़ने के लिए Accessibility सक्षम करें, सर।"),
                        AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                    )
                }
                val screenText = JarvisAccessibilityService.readActiveScreenText()
                return ActionExecutionResult(
                    if (screenText.isBlank()) "No readable labels visible on the current window, Sir."
                    else "Visible on screen, Sir:\n$screenText"
                )
            }
        }

        // Voice Access: Tap by numbered overlay index ("Tap 3", "3 চাপো", or "3" when grid is open)
        val explicitNumberTap = Regex("(?i)^(?:tap|click|press|number|চাপো|ক্লিক করো|ট্যাপ করো|ট্যাপ|নম্বর)\\s+(\\d{1,2})(?:\\s*(?:চাপো|ট্যাপ|নম্বর))?$")
            .find(normalizedDigitsLower)
            ?: Regex("^(\\d{1,2})\\s*(?:নম্বর\\s*)?(?:চাপো|ট্যাপ|ক্লিক)$").find(normalizedDigitsLower)
            ?: if (JarvisAccessibilityService.overlayActive.value) Regex("^(\\d{1,2})$").find(normalizedDigitsLower) else null
        if (explicitNumberTap != null) {
            val number = explicitNumberTap.groupValues[1].toIntOrNull()
            if (number != null && number in 1..36) {
                if (!JarvisAccessibilityService.isConnected.value) {
                    return ActionExecutionResult(
                        localized(lang, "Enable Accessibility in Phone Control to tap #$number on screen, Sir.", "নম্বর $number চাপতে Accessibility চালু করুন, স্যার।", "नंबर $number टैप करने के लिए Accessibility सक्षम करें, सर।"),
                        AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                    )
                }
                val tapped = JarvisAccessibilityService.tapNumberedElement(number)
                phoneControlManager.refreshState()
                return ActionExecutionResult(
                    if (tapped) localized(lang, "Tapped item #$number on screen, Sir.", "স্ক্রিনের $number নম্বর আইটেমে ট্যাপ করা হয়েছে, স্যার।", "आइटम #$number टैप किया गया, सर।")
                    else localized(lang, "Target #$number is not active — say 'Show numbers' first, Sir.", "নম্বর $number পাওয়া যায়নি—আগে 'নম্বর দেখাও' বলুন, স্যার।", "पहले 'Show numbers' बोलें, सर।")
                )
            }
        }

        // Click UI button by visible label
        val clickMatch = Regex("(?i)^(?:tap|click|press|চাপো|ক্লিক করো|ট্যাপ করো|ট্যাপ)\\s+(.+)$").find(prompt)
        if (clickMatch != null) {
            val targetLabel = clickMatch.groupValues[1].trim()
            if (!JarvisAccessibilityService.isConnected.value) {
                return ActionExecutionResult(
                    localized(lang, "Enable 'J.A.R.V.I.S Phone Control' in Accessibility Settings to tap '$targetLabel' on screen, Sir.", "স্ক্রিনে '$targetLabel' চাপতে Accessibility Settings চালু করুন, স্যার।", "स्क्रीन पर '$targetLabel' टैप करने के लिए Accessibility सक्षम करें, सर।"),
                    AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                )
            }
            val clicked = JarvisAccessibilityService.clickElementByText(targetLabel)
            return ActionExecutionResult(
                if (clicked) localized(lang, "Tapped '$targetLabel' on screen, Sir.", "স্ক্রিনে '$targetLabel' ট্যাপ করা হয়েছে, স্যার।", "स्क्रीन पर '$targetLabel' टैप किया गया, सर।")
                else localized(lang, "Could not find a clickable '$targetLabel' on the active screen, Sir.", "বর্তমান স্ক্রিনে '$targetLabel' বাটন খুঁজে পাওয়া যায়নি, স্যার।", "स्क्रीन पर '$targetLabel' बटन नहीं मिला, सर।")
            )
        }

        // Voice Access: Full Text Editing & Voice Typing Commands (Clear, Backspace, Delete Word, Replace, Copy/Paste, Append, Dictate)
        when {
            Regex("^(clear text|clear field|delete all|erase all|সব মুছো|লেখা মুছো|ক্লিয়ার করো|ক্লিয়ার করো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    return ActionExecutionResult(
                        localized(lang, "Enable Accessibility to edit text by voice, Sir.", "ভয়েস দিয়ে লেখা মুছতে Accessibility চালু করুন, স্যার।", "वॉइस एडिटिंग के लिए Accessibility सक्षम करें, सर।"),
                        AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                    )
                }
                val ok = JarvisAccessibilityService.clearFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Cleared the active text box, Sir.", "টেক্সট বক্সের সব লেখা মুছে ফেলা হয়েছে, স্যার।", "टेक्स्ट साफ़ कर दिया गया, सर।")
                    else localized(lang, "Tap an editable text box first, Sir.", "প্রথমে একটি লেখার বক্সে ট্যাপ করুন, স্যার।", "पहले एक टेक्स्ट बॉक्स चुनें, सर।")
                )
            }
            Regex("^(delete last word|delete word|erase word|undo word|শেষের শব্দ মুছো|শব্দ মুছো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    return ActionExecutionResult(
                        localized(lang, "Enable Accessibility to edit text by voice, Sir.", "ভয়েস দিয়ে শব্দ মুছতে Accessibility চালু করুন, স্যার।", "वॉइस एडिटिंग के लिए Accessibility सक्षम करें, सर।"),
                        AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                    )
                }
                val ok = JarvisAccessibilityService.deleteLastWordInFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Deleted the last word, Sir.", "শেষের শব্দটি মুছে ফেলা হয়েছে, স্যার।", "अंतिम शब्द हटा दिया गया, सर।")
                    else localized(lang, "No editable word found to delete, Sir.", "মোছার মতো কোনো শব্দ পাওয়া যায়নি, স্যার।", "हटाने के लिए कोई शब्द नहीं मिला, सर।")
                )
            }
            Regex("^(backspace|delete character|erase character|অক্ষর মুছো|একটি অক্ষর মুছো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    return ActionExecutionResult(
                        localized(lang, "Enable Accessibility to edit text by voice, Sir.", "ভয়েস এডিটিংয়ের জন্য Accessibility চালু করুন, স্যার।", "Accessibility सक्षम करें, सर।"),
                        AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                    )
                }
                val ok = JarvisAccessibilityService.backspaceFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Deleted the last character, Sir.", "শেষের অক্ষরটি মুছে ফেলা হয়েছে, স্যার।", "अंतिम अक्षर हटा दिया गया, सर।")
                    else localized(lang, "Text box is empty or not editable, Sir.", "টেক্সট বক্স খালি অথবা নির্বাচন করা নেই, স্যার।", "टेक्स्ट बॉक्स खाली है, सर।")
                )
            }
            Regex("^(select all|সব সিলেক্ট করো|সব নির্বাচন করো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                val ok = JarvisAccessibilityService.isConnected.value && JarvisAccessibilityService.selectAllInFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Selected all text in the active field, Sir.", "বক্সের সব লেখা সিলেক্ট করা হয়েছে, স্যার।", "सभी टेक्स्ट चयनित, सर।")
                    else localized(lang, "Enable Accessibility and focus a text box to select all, Sir.", "সব সিলেক্ট করতে Accessibility ও একটি লেখার বক্স সক্রিয় রাখুন, স্যার।", "Accessibility और टेक्स्ट बॉक्स सक्रिय करें, सर।")
                )
            }
            Regex("^(copy text|কপি করো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                val ok = JarvisAccessibilityService.isConnected.value && JarvisAccessibilityService.copyFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Copied text to clipboard, Sir.", "লেখা কপি করা হয়েছে, স্যার।", "टेक्स्ट कॉपी किया गया, सर।")
                    else localized(lang, "Select text in an active box first to copy, Sir.", "কপি করতে প্রথমে লেখা সিলেক্ট করুন, স্যার।", "कॉपी करने के लिए टेक्स्ट चुनें, सर।")
                )
            }
            Regex("^(cut text|কাট করো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                val ok = JarvisAccessibilityService.isConnected.value && JarvisAccessibilityService.cutFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Cut selected text, Sir.", "সিলেক্ট করা লেখা কাট করা হয়েছে, স্যার।", "टेक्स्ट कट किया गया, सर।")
                    else localized(lang, "Select text in an active box first to cut, Sir.", "কাট করতে প্রথমে লেখা সিলেক্ট করুন, স্যার।", "कट करने के लिए टेक्स्ट चुनें, सर।")
                )
            }
            Regex("^(paste|paste text|পেস্ট করো)$", RegexOption.IGNORE_CASE).matches(lower) -> {
                val ok = JarvisAccessibilityService.isConnected.value && JarvisAccessibilityService.pasteIntoFocusedField()
                return ActionExecutionResult(
                    if (ok) localized(lang, "Pasted clipboard text, Sir.", "ক্লিপবোর্ড থেকে পেস্ট করা হয়েছে, স্যার।", "पेस्ट किया गया, सर।")
                    else localized(lang, "Focus a text box with Accessibility enabled to paste, Sir.", "পেস্ট করতে একটি লেখার বক্সে ট্যাপ করুন, স্যার।", "पेस्ट करने के लिए टेक्स्ट बॉक्स चुनें, सर।")
                )
            }
        }

        val replaceMatch = Regex("(?i)^replace\\s+(.+?)\\s+with\\s+(.+)$").find(prompt)
            ?: Regex("^(.+?)\\s+বদলে\\s+(.+?)\\s*(?:লিখো|বসাও)?$").find(prompt)
        if (replaceMatch != null) {
            val oldPart = replaceMatch.groupValues[1].trim()
            val newPart = replaceMatch.groupValues[2].trim()
            if (!JarvisAccessibilityService.isConnected.value) {
                return ActionExecutionResult(
                    localized(lang, "Enable Accessibility to replace text by voice, Sir.", "ভয়েস দিয়ে লেখা পরিবর্তন করতে Accessibility চালু করুন, স্যার।", "Accessibility सक्षम करें, सर।"),
                    AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                )
            }
            val ok = JarvisAccessibilityService.replaceInFocusedField(oldPart, newPart)
            return ActionExecutionResult(
                if (ok) localized(lang, "Replaced \"$oldPart\" with \"$newPart\", Sir.", "\"$oldPart\" পরিবর্তন করে \"$newPart\" লেখা হয়েছে, স্যার।", "\"$oldPart\" को \"$newPart\" से बदला गया, सर।")
                else localized(lang, "Could not find \"$oldPart\" in the focused text box, Sir.", "লেখার বক্সে \"$oldPart\" খুঁজে পাওয়া যায়নি, স্যার।", "\"$oldPart\" नहीं मिला, सर।")
            )
        }

        val appendMatch = Regex("(?i)^(?:append|add text)\\s+(.+)$").find(prompt)
            ?: Regex("^যোগ করো\\s+(.+)$").find(prompt)
        if (appendMatch != null) {
            val extra = appendMatch.groupValues[1].trim()
            if (!JarvisAccessibilityService.isConnected.value) {
                return ActionExecutionResult(
                    localized(lang, "Enable Accessibility in Phone Control to append text, Sir.", "অ্যাপে লেখা যোগ করতে Accessibility চালু করুন, স্যার।", "Accessibility सक्षम करें, सर।"),
                    AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                )
            }
            val ok = JarvisAccessibilityService.appendTextToFocusedField(extra)
            return ActionExecutionResult(
                if (ok) localized(lang, "Appended \"$extra\" to the active field, Sir.", "বক্সে \"$extra\" যোগ করা হয়েছে, স্যার।", "\"$extra\" जोड़ा गया, सर।")
                else localized(lang, "Focus a text box first so I can append \"$extra\", Sir.", "প্রথমে একটি লেখার বক্সে ট্যাপ করুন, স্যার।", "कृपया पहले टेक्स्ट बॉक्स चुनें, सर।")
            )
        }

        val typeMatch = Regex("(?i)^(?:type|write|dictate|ইনপুট দাও|লিখো|টাইপ করো)\\s+(.+)$").find(prompt)
        if (typeMatch != null) {
            val payloadText = typeMatch.groupValues[1].trim()
            if (!JarvisAccessibilityService.isConnected.value) {
                return ActionExecutionResult(
                    localized(lang, "Enable Accessibility in Phone Control to type directly into apps, Sir.", "অ্যাপে সরাসরি লিখতে Accessibility চালু করুন, স্যার।", "ऐप में टाइप करने के लिए Accessibility सक्षम करें, सर।"),
                    AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                )
            }
            val typed = JarvisAccessibilityService.inputTextIntoFocusedField(payloadText)
            return ActionExecutionResult(
                if (typed) localized(lang, "Typed \"$payloadText\" into the active field, Sir.", "ইনপুট বক্সে \"$payloadText\" লেখা হয়েছে, স্যার।", "\"$payloadText\" टाइप कर दिया गया, सर।")
                else localized(lang, "Focus an input text field first so I can type \"$payloadText\", Sir.", "প্রথমে একটি লেখার বক্সে ট্যাপ করুন, স্যার।", "कृपया पहले टेक्स्ट बॉक्स चुनें, सर।")
            )
        }

        // Brightness control
        if (Regex("(brightness|ব্রাইটনেস|আলো)").containsMatchIn(lower)) {
            if (Regex("(auto|adaptive|অটো)").containsMatchIn(lower)) {
                return wrapControlResult(phoneControlManager.setAutoBrightness(true))
            }
            val pctMatch = Regex("(\\d{1,3})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val targetPct = when {
                pctMatch != null -> pctMatch.coerceIn(5, 100)
                Regex("(max|full|high|বাড়াও|বেশি)").containsMatchIn(lower) -> 95
                Regex("(min|low|dim|কমাও|কম)").containsMatchIn(lower) -> 20
                else -> 55
            }
            return wrapControlResult(phoneControlManager.setBrightnessPercent(targetPct))
        }

        // Ringer mode (Silent / Vibrate / Normal)
        when {
            Regex("(vibrate mode|set vibrate|ভাইব্রেট)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.setRingerMode(PhoneRingerMode.VIBRATE))
            Regex("(silent mode|do not disturb|dnd|সাইলেন্ট)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.setRingerMode(PhoneRingerMode.SILENT))
            Regex("(ring mode|normal sound|রিং মোড)").containsMatchIn(lower) ->
                return wrapControlResult(phoneControlManager.setRingerMode(PhoneRingerMode.NORMAL))
        }

        // Quick Timer / Alarm
        val timerMatch = Regex("(?:timer|টাইমার).*?(\\d{1,3})").find(lower)
        if (timerMatch != null) {
            val mins = timerMatch.groupValues[1].toIntOrNull() ?: 5
            return wrapControlResult(phoneControlManager.setQuickTimer(mins))
        }

        // Dial number if digits present with call/dial keyword
        if (Regex("(call |dial |কল করো|ডায়াল)").containsMatchIn(lower)) {
            val phone = Regex("[+]?[0-9][0-9\\-\\s]{5,}").find(prompt)?.value?.replace("\\s+".toRegex(), "")
            if (!phone.isNullOrBlank()) {
                return wrapControlResult(phoneControlManager.openDialer(phone))
            }
        }

        return null
    }

    private fun buildStarkSuiteDiagnostics(lang: AppLanguage): String {
        val pcs = phoneControlManager.state.value
        val telemetry = buildDeviceTelemetry(lang)
        val pendingCount = taskManager.tasks.value.count { it.status == TaskStatus.PENDING_NETWORK }
        val online = taskManager.canExecuteOnlineNow()
        return localized(
            lang,
            en = buildString {
                appendLine("J.A.R.V.I.S. Full System Diagnostics, Sir:")
                appendLine("• Neural Core: ${if (online) "Online & Streaming Ready" else "Offline NLP Core Active"}")
                appendLine("• Camera Access: ${if (pcs.cameraGranted) "Granted (CameraX Live)" else "Needs Permission"}")
                appendLine("• Microphone Access: ${if (pcs.micGranted) "Granted (Live Voice + Wake)" else "Needs Permission"}")
                appendLine("• Full Phone Control: ${if (pcs.accessibilityEnabled) "Connected (System-Wide)" else "Standby (Enable Accessibility)"}")
                appendLine("• Smart Home Appliances: ${smartHomeManager.devices.value.size} connected")
                appendLine("• Task Queue: $pendingCount pending task(s)")
                appendLine()
                append(telemetry)
            },
            bn = buildString {
                appendLine("J.A.R.V.I.S. সম্পূর্ণ সিস্টেম ডায়াগনস্টিকস, স্যার:")
                appendLine("• নিউরাল কোর: ${if (online) "অনলাইন ও লাইভ প্রস্তুত" else "অফলাইন কোর সচল"}")
                appendLine("• ক্যামেরা এক্সেস: ${if (pcs.cameraGranted) "অনুমতিপ্রাপ্ত" else "অনুমতি প্রয়োজন"}")
                appendLine("• মাইক্রোফোন এক্সেস: ${if (pcs.micGranted) "অনুমতিপ্রাপ্ত" else "অনুমতি প্রয়োজন"}")
                appendLine("• ফুল ফোন কন্ট্রোল: ${if (pcs.accessibilityEnabled) "সক্রিয়" else "স্ট্যান্ডবাই (Accessibility চালু করুন)"}")
                appendLine("• স্মার্ট হোম ডিভাইস: ${smartHomeManager.devices.value.size}টি যুক্ত")
                appendLine("• টাস্ক কিউ: $pendingCount টি পেন্ডিং")
                appendLine()
                append(telemetry)
            },
            hi = "J.A.R.V.I.S. सिस्टम डायग्नोस्टिक्स पूर्ण, सर:\n$telemetry"
        )
    }

    private fun buildDeviceTelemetry(lang: AppLanguage): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = bm?.isCharging == true

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val freeRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val networkLabel = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> "Offline"
        }

        return localized(
            lang,
            en = String.format(
                Locale.US,
                "System Telemetry, Sir:\n• Battery: %s (%s)\n• Available RAM: %.1f / %.1f GB\n• Free Storage: %.1f GB\n• Network: %s",
                if (batteryPct >= 0) "$batteryPct%" else "N/A",
                if (isCharging) "Charging" else "Discharging",
                freeRamGb,
                totalRamGb,
                freeStorageGb,
                networkLabel
            ),
            bn = String.format(
                Locale.US,
                "সিস্টেম টেলিমেট্রি, স্যার:\n• ব্যাটারি: %s (%s)\n• ফ্রি RAM: %.1f / %.1f GB\n• ফ্রি স্টোরেজ: %.1f GB\n• নেটওয়ার্ক: %s",
                if (batteryPct >= 0) "$batteryPct%" else "অজানা",
                if (isCharging) "চার্জ হচ্ছে" else "ব্যাটারিতে চলছে",
                freeRamGb,
                totalRamGb,
                freeStorageGb,
                networkLabel
            ),
            hi = String.format(
                Locale.US,
                "सिस्टम टेलीमेट्री, सर:\n• बैटरी: %s (%s)\n• उपलब्ध RAM: %.1f / %.1f GB\n• खाली स्टोरेज: %.1f GB\n• नेटवर्क: %s",
                if (batteryPct >= 0) "$batteryPct%" else "N/A",
                if (isCharging) "Charging" else "Discharging",
                freeRamGb,
                totalRamGb,
                freeStorageGb,
                networkLabel
            )
        )
    }

    fun isCompoundGoalPrompt(prompt: String): Boolean {
        val clauses = splitCompoundClauses(prompt)
        return clauses.size >= 2
    }

    /**
     * Executes multi-step local commands in a single user utterance
     * (e.g., "Scan phone, dim brightness to 25% and set silent mode" or
     *  "টর্চ জ্বালাও তারপর ফোন স্ক্যান করো").
     */
    suspend fun tryCompoundLocalAction(
        rawPrompt: String,
        settings: JarvisSettings = JarvisSettings()
    ): Pair<List<String>, ActionExecutionResult>? {
        val clauses = splitCompoundClauses(rawPrompt)
        if (clauses.size < 2) return null

        val executedSteps = mutableListOf<String>()
        var primaryShortcut: AgentActionShortcut? = null
        val matchedResponses = mutableListOf<String>()

        for (clause in clauses) {
            val res = tryLocalAction(clause, settings)
            if (res != null) {
                executedSteps.add(res.responseText.lineSequence().firstOrNull().orEmpty().take(90))
                matchedResponses.add("✓ ${res.responseText}")
                if (primaryShortcut == null && res.shortcut != null) {
                    primaryShortcut = res.shortcut
                }
            }
        }

        // Only handle locally if EVERY clause resolved to a concrete device/agent tool;
        // otherwise return null so Gemini Agent Planner handles nuanced goals cleanly.
        if (executedSteps.size < 2 || executedSteps.size < clauses.size) return null

        return executedSteps to ActionExecutionResult(
            responseText = matchedResponses.joinToString("\n\n"),
            shortcut = primaryShortcut
        )
    }

    /**
     * Executes a single tool call planned by J.A.R.V.I.S. AI Agent Planner against live Android APIs.
     */
    suspend fun executeAgentTool(
        toolName: String,
        argument: String,
        settings: JarvisSettings = JarvisSettings()
    ): Pair<String, AgentActionShortcut?> {
        val lang = settings.language
        return when (toolName) {
            "scan_phone_security" -> {
                val report = JarvisShieldStateHolder.scanNowSuspend(context)
                val high = report.highRiskApps.size
                val summary = if (high > 0) {
                    "Security Scan: ${report.overallScore}% (${report.totalAppsScanned} apps) — ALERT: $high high-risk (${report.highRiskApps.joinToString { it.appName }})"
                } else {
                    "Security Scan: ${report.overallScore}% (${report.totalAppsScanned} apps) — 0 high-risk threats"
                }
                summary to AgentActionShortcut("Open 24/7 Threat Shield", AgentRoute.SECURITY)
            }
            "delete_threat_app" -> {
                val report = JarvisShieldStateHolder.scanNowSuspend(context)
                val target = report.highRiskApps.firstOrNull {
                    argument.isBlank() || it.appName.contains(argument, true) || it.packageName.contains(argument, true)
                } ?: report.highRiskApps.firstOrNull()
                if (target != null) {
                    runCatching { context.startActivity(JarvisSecurityEngine.createUninstallIntent(target.packageName)) }
                    "Launched OS uninstall prompt for ${target.appName}" to AgentActionShortcut("Open Threat Shield", AgentRoute.SECURITY)
                } else {
                    "No high-risk threat apps present to delete" to AgentActionShortcut("Open Threat Shield", AgentRoute.SECURITY)
                }
            }
            "toggle_flashlight" -> {
                val enable = !argument.equals("off", ignoreCase = true) && !argument.equals("false", ignoreCase = true)
                val res = toggleFlashlight(enable, lang)
                res.responseText to null
            }
            "set_brightness" -> {
                val res = if (argument.equals("auto", ignoreCase = true)) {
                    phoneControlManager.setAutoBrightness(true)
                } else {
                    val pct = Regex("(\\d{1,3})").find(argument)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 50
                    phoneControlManager.setBrightnessPercent(pct.coerceIn(5, 100))
                }
                res.fold(
                    onSuccess = { it to AgentActionShortcut("Phone Control", AgentRoute.PHONE_CONTROL) },
                    onFailure = { (it.localizedMessage ?: "Write Settings permission needed") to AgentActionShortcut("Grant Permission", AgentRoute.PHONE_CONTROL) }
                )
            }
            "set_ringer_mode" -> {
                val mode = when (argument.uppercase(Locale.US)) {
                    "SILENT" -> PhoneRingerMode.SILENT
                    "VIBRATE" -> PhoneRingerMode.VIBRATE
                    else -> PhoneRingerMode.NORMAL
                }
                phoneControlManager.setRingerMode(mode).fold(
                    onSuccess = { it to AgentActionShortcut("Phone Control", AgentRoute.PHONE_CONTROL) },
                    onFailure = { (it.localizedMessage ?: "DND access needed") to AgentActionShortcut("Grant DND Access", AgentRoute.PHONE_CONTROL) }
                )
            }
            "adjust_volume" -> {
                val res = adjustVolume(argument.lowercase(Locale.US), lang)
                res.responseText to null
            }
            "set_quick_timer" -> {
                val mins = Regex("(\\d{1,3})").find(argument)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
                phoneControlManager.setQuickTimer(mins).fold(
                    onSuccess = { it to null },
                    onFailure = { (it.localizedMessage ?: "Clock timer unavailable") to null }
                )
            }
            "open_app" -> {
                val res = tryLaunchTarget(argument, lang)
                (res?.responseText ?: "App '$argument' not found installed") to null
            }
            "smart_home_command" -> {
                val smartRes = smartHomeManager.executeVoiceOrTextCommand(argument)
                (smartRes?.summary ?: "Routed '$argument' to Smart Home Hub") to AgentActionShortcut("Open Smart Home", AgentRoute.SMART_HOME)
            }
            "scan_wifi" -> {
                if (!PermissionHelper.hasWifiPermissions(context)) {
                    "Wi-Fi scan permission missing" to AgentActionShortcut("Grant Permissions", requestPermissions = true)
                } else {
                    wifiManager.startScan()
                    "Wi-Fi scan triggered (${wifiManager.networks.value.size} visible networks)" to AgentActionShortcut("Open Wi-Fi", AgentRoute.WIFI)
                }
            }
            "scan_bluetooth" -> {
                if (!PermissionHelper.hasBluetoothPermissions(context)) {
                    "Bluetooth permission missing" to AgentActionShortcut("Grant Permissions", requestPermissions = true)
                } else {
                    bluetoothManager.startDiscovery(includeBle = true)
                    "Bluetooth scan triggered (${bluetoothManager.devices.value.size} devices)" to AgentActionShortcut("Open Bluetooth", AgentRoute.BLUETOOTH)
                }
            }
            "get_device_telemetry" -> {
                val telemetry = buildDeviceTelemetry(lang)
                telemetry to AgentActionShortcut("Phone Control", AgentRoute.PHONE_CONTROL)
            }
            "global_phone_navigation" -> {
                phoneControlManager.performGlobalNavigation(argument).fold(
                    onSuccess = { it to AgentActionShortcut("Phone Control", AgentRoute.PHONE_CONTROL) },
                    onFailure = { (it.localizedMessage ?: "Enable Accessibility Service") to AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL) }
                )
            }
            "click_screen_element" -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    "Accessibility needed to tap '$argument'" to AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                } else {
                    val ok = JarvisAccessibilityService.clickElementByText(argument)
                    (if (ok) "Tapped '$argument' on active screen" else "Element '$argument' not clickable on screen") to null
                }
            }
            "type_on_screen" -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    "Accessibility needed to type '$argument'" to AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                } else {
                    val ok = JarvisAccessibilityService.inputTextIntoFocusedField(argument)
                    (if (ok) "Typed '$argument' into active field" else "No focused input box found") to null
                }
            }
            "read_screen" -> {
                if (!JarvisAccessibilityService.isConnected.value) {
                    "Accessibility needed to read screen" to AgentActionShortcut("Enable Accessibility", AgentRoute.PHONE_CONTROL)
                } else {
                    val text = JarvisAccessibilityService.readActiveScreenText()
                    (if (text.isBlank()) "No readable UI labels on screen" else "Screen labels: $text") to null
                }
            }
            "decode_website" -> {
                val report = WebsiteDecoderEngine.decodeWebsite(argument, settings.offlineIsolationMode)
                "Decoded ${report.host}: ${report.pageTitle} (HTTP ${report.httpStatusCode ?: "N/A"}, Tech: ${report.detectedTechnologies.joinToString().ifBlank { "Standard" }})" to
                    AgentActionShortcut("Open Decoder", AgentRoute.SECURITY)
            }
            "analyze_scam" -> {
                val report = JarvisSecurityEngine.analyzeMessageOrAlert(argument)
                "Scam Check: ${report.riskLevel.name} (${report.score}/100) — ${report.recommendation}" to
                    AgentActionShortcut("Open Threat Shield", AgentRoute.SECURITY)
            }
            "open_screen" -> {
                val route = runCatching { AgentRoute.valueOf(argument.uppercase(Locale.US)) }.getOrNull() ?: AgentRoute.CHAT
                "Shortcut ready for ${route.name}" to AgentActionShortcut("Open ${route.name}", route)
            }
            else -> "Skipped unrecognized tool '$toolName'" to null
        }
    }

    private fun normalizeBengaliDigits(input: String): String {
        val bn = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        var out = input
        for (i in bn.indices) {
            out = out.replace(bn[i], ('0'.code + i).toChar())
        }
        return out
    }

    private fun splitCompoundClauses(prompt: String): List<String> {
        return prompt
            .split(Regex("(?i)\\s*(?:,|\\band\\b|\\bthen\\b|তারপর|এবং|আর|ও)\\s*"))
            .map { it.trim() }
            .filter { it.length >= 3 }
    }
}
