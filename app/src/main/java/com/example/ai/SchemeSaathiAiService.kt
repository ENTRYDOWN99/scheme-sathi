package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.CitizenProfile
import com.example.data.model.EligibilityEvaluationResult
import com.example.data.model.EligibilityOutcome
import com.example.domain.EligibilityEngine
import com.example.util.SecurityUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

enum class GeminiModelMode(
    val modelId: String,
    val labelEn: String,
    val labelHi: String,
    val descriptionEn: String
) {
    STANDARD_FLASH(
        modelId = "gemini-3.8-flash",
        labelEn = "Standard",
        labelHi = "सामान्य",
        descriptionEn = "Balanced multi-turn bilingual assistant"
    ),
    FAST_LITE(
        modelId = "gemini-3.1-flash-lite",
        labelEn = "Fast Answers",
        labelHi = "तेज़ उत्तर",
        descriptionEn = "Ultra-fast low-latency responses"
    ),
    HIGH_THINKING_CARBON(
        modelId = "gemini-4-carbon",
        labelEn = "Deep Thinking",
        labelHi = "गहन विश्लेषण",
        descriptionEn = "Deep reasoning with ThinkingLevel.HIGH (Fallback: gemini-3.8-flash)"
    ),
    COMPLEX_ARGON(
        modelId = "gemini-4-argon",
        labelEn = "Document & Vision",
        labelHi = "दस्तावेज़ विश्लेषण",
        descriptionEn = "Multimodal document understanding (Fallback: gemini-3.8-flash)"
    ),
    LIVE_VOICE(
        modelId = "gemini-3.8-flash",
        labelEn = "Live Voice",
        labelHi = "लाइव वॉइस",
        descriptionEn = "Real-time conversational voice mode (Free Tier)"
    ),
    TTS_AUDIO(
        modelId = "gemini-3.8-flash-tts",
        labelEn = "Text-to-Speech",
        labelHi = "टेक्स्ट-टू-स्पीच",
        descriptionEn = "Speech script & phonetic synthesis"
    )
}

class SchemeSaathiAiService {

    private val systemPromptText = """
        You are Scheme Saathi (स्कीम साथी), a warm, trustworthy Hindi and English Government Welfare Scheme Assistant for Indian citizens.
        STRICT SAFETY & GROUNDING RULES:
        1. NEVER decide eligibility yourself. Always rely strictly on the Deterministic Eligibility Engine results provided in the grounded context.
        2. Missing information ALWAYS results in UNKNOWN, never automatically NOT_ELIGIBLE.
        3. NEVER confirm or submit any government application. Tell the citizen they must review and confirm on the Human Confirmation Gate screen and use the Guided Handoff Pack.
        4. NEVER ask for, collect, or store an Aadhaar number.
        5. NEVER invent scheme rules, financial benefits, or URLs outside the 5 verified schemes: PM-KISAN, NSAP-IGNOAPS, PMUY, PM-JAY, and APY.
        6. Always state that scheme thresholds are illustrative until verified on the official portal.
        7. Reply in simple, respectful Hindi or English matching the user's language preference.
    """.trimIndent()

    // Lazy initialization per Firebase AI Logic skill
    val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    val highThinkingCarbonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            },
            systemInstruction = content { text(systemPromptText) }
        )
    }

    val carbonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-carbon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            },
            systemInstruction = content { text(systemPromptText) }
        )
    }

    val argonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    val liveVoiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text("$systemPromptText Keep spoken responses concise (2-3 sentences) and natural for voice output.")
            }
        )
    }

    val ttsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash-tts"
        )
    }

    /**
     * Central error mapper: translates exceptions into friendly bilingual user messages.
     * Raw exceptions and URLs are logged with Log.e only and never leaked to the UI.
     */
    fun mapAiErrorToFriendlyMessage(throwable: Throwable?, language: String): String {
        val rawMessage = throwable?.message.orEmpty()
        val rawLocalized = throwable?.localizedMessage.orEmpty()
        val combined = "$rawMessage $rawLocalized".lowercase()

        val isQuota = combined.contains("quota") ||
            combined.contains("429") ||
            combined.contains("resource_exhausted") ||
            combined.contains("limit: 0") ||
            combined.contains("limit '0'") ||
            combined.contains("too many requests") ||
            combined.contains("rate limit")

        val isNetwork = throwable is java.net.UnknownHostException ||
            throwable is java.net.SocketTimeoutException ||
            throwable is TimeoutCancellationException ||
            combined.contains("unable to resolve host") ||
            combined.contains("connection refused") ||
            combined.contains("network error") ||
            combined.contains("timeout") ||
            combined.contains("connect timed out")

        return when {
            isQuota -> if (language == "hi") {
                "AI सर्वर अभी व्यस्त है, कृपया एक मिनट बाद पुनः प्रयास करें।"
            } else {
                "AI is busy right now, please try again in a minute."
            }
            isNetwork -> if (language == "hi") {
                "कृपया अपना इंटरनेट कनेक्शन जांचें और पुनः प्रयास करें।"
            } else {
                "Please check your internet connection and try again."
            }
            else -> if (language == "hi") {
                "AI सेवा अस्थायी रूप से अनुपलब्ध है। कृपया थोड़ी देर बाद प्रयास करें।"
            } else {
                "AI service is temporarily unavailable. Please try again shortly."
            }
        }
    }

    /**
     * Executes deterministic tool calls first (up to 6 tool iterations budget) to ground the AI response
     * in real EligibilityEngine facts and redacted profile summary.
     */
    fun executeDeterministicTools(
        userQuery: String,
        profile: CitizenProfile,
        evaluations: List<EligibilityEvaluationResult>
    ): Pair<List<String>, String> {
        val executedTools = mutableListOf<String>()
        val contextBuilder = StringBuilder()

        // Tool 1: get_profile_summary (redacted PII)
        executedTools.add("get_profile_summary")
        val age = if (profile.birthYear in 1901..2026) (2026 - profile.birthYear) else -1
        contextBuilder.appendLine(
            "REDACTED_PROFILE_SUMMARY: age=${if (age > 0) age else "UNKNOWN"}, " +
                "ageVoiceConfirmed=${profile.ageConfirmedViaVoice}, gender=${profile.gender}, " +
                "state=${profile.state}, residence=${profile.residenceType}, " +
                "income=${if (profile.annualIncome >= 0) profile.annualIncome else "UNKNOWN"}, " +
                "bpl=${profile.bplStatus}, category=${profile.socialCategory}, " +
                "land=${profile.ownsAgriculturalLand}, lpg=${profile.hasLpgConnection}, " +
                "bank=${profile.hasBankAccount}, taxPayer=${profile.isIncomeTaxPayer}"
        )

        // Tool 2: check_eligibility & explain_eligibility (Deterministic Engine)
        executedTools.add("check_eligibility")
        executedTools.add("explain_eligibility")
        contextBuilder.appendLine("DETERMINISTIC_ELIGIBILITY_ENGINE_RESULTS:")
        for (eval in evaluations) {
            val scheme = EligibilityEngine.getSchemeById(eval.schemeId)
            contextBuilder.appendLine(
                "- ${eval.schemeCode} (${scheme?.nameEn}): OUTCOME=${eval.outcome.name}, " +
                    "RULE_VERSION=${eval.ruleVersion}, " +
                    "MISSING_FIELDS=${eval.missingFields.joinToString(",")}, " +
                    "STATE_OVERRIDE=${eval.stateOverrideApplied ?: "none"}, " +
                    "OFFICIAL_URL=${scheme?.officialSourceUrl}"
            )
            eval.conditionResults.forEach { cond ->
                contextBuilder.appendLine("   * [${cond.outcome}] ${cond.reasonEn}")
            }
        }

        // Tool 3: get_required_documents if query mentions documents / कागज़ात / दस्तावेज़
        val lower = userQuery.lowercase()
        if (lower.contains("doc") || lower.contains("दस्तावे") || lower.contains("काग") || lower.contains("apply") || lower.contains("आवेदन")) {
            executedTools.add("get_required_documents")
            contextBuilder.appendLine("REQUIRED_DOCUMENTS_BY_SCHEME:")
            EligibilityEngine.initialFiveSchemes.forEach { scheme ->
                val docs = scheme.requiredDocuments.joinToString("; ") { "${it.nameEn} (Alt: ${it.alternativesEn})" }
                contextBuilder.appendLine("- ${scheme.code}: $docs")
            }
        }

        // Tool 4: list_missing_fields
        val allMissing = evaluations.flatMap { it.missingFields }.distinct()
        if (allMissing.isNotEmpty()) {
            executedTools.add("list_missing_fields")
            contextBuilder.appendLine("MISSING_PROFILE_FIELDS_CAUSING_UNKNOWN: ${allMissing.joinToString(", ")}")
        }

        return executedTools.take(6) to contextBuilder.toString()
    }

    /**
     * Streams a grounded multi-turn chat response using the selected Gemini model with a model fallback chain.
     * If the selected model encounters quota, rate limit, or error, it automatically falls back
     * to the next model in the chain (gemini-3.8-flash -> gemini-3.1-flash-lite).
     * If all models fail, it falls back to the deterministic offline summary.
     */
    fun streamGroundedChat(
        userPrompt: String,
        language: String,
        modelMode: GeminiModelMode,
        profile: CitizenProfile,
        evaluations: List<EligibilityEvaluationResult>,
        decryptedNameForRedaction: String,
        decryptedPhoneForRedaction: String,
        conversationHistory: List<Pair<String, String>>
    ): Flow<AiStreamUpdate> = flow {
        // 1. Check Aadhaar guard
        if (SecurityUtils.containsAadhaarNumber(userPrompt)) {
            emit(
                AiStreamUpdate.Completed(
                    text = if (language == "hi") {
                        "⚠️ सुरक्षा नियम: स्कीम साथी में आधार नंबर कभी नहीं मांगा या सहेजा जाता है। कृपया 12 अंकों का आधार नंबर साझा न करें।"
                    } else {
                        "⚠️ Security Policy: Scheme Saathi never collects or stores Aadhaar numbers. Please do not enter a 12-digit Aadhaar number."
                    },
                    toolsUsed = listOf("aadhaar_privacy_guard"),
                    modelUsed = modelMode.modelId
                )
            )
            return@flow
        }

        // 2. Check Prompt Injection or Auto-Submit guard (Human Confirmation Gate)
        val (blocked, blockReason) = SecurityUtils.detectPromptInjectionOrAutoSubmit(userPrompt)
        if (blocked && blockReason != null) {
            val politeMessage = if (blockReason.startsWith("HUMAN_GATE_BLOCK")) {
                if (language == "hi") {
                    "🛡️ सुरक्षा नीति (Human Confirmation Gate): स्कीम साथी एआई को सरकारी आवेदन सीधे जमा या पुष्टि करने की अनुमति नहीं है। सरकारी सुरक्षा और पारदर्शिता नियमों के अनुसार, नागरिक या अधिकृत ऑपरेटर को 'समीक्षा व पुष्टि' (Review & Confirm) स्क्रीन पर व्यक्तिगत रूप से जांच कर डिजिटल पुष्टि करनी होती है।"
                } else {
                    "🛡️ Safety Guardrail (Human Confirmation Gate): Scheme Saathi AI is strictly prohibited from auto-submitting government applications. Per security and governance policies, citizen welfare submissions require mandatory human review on the 'Review & Confirm' screen with a verifiable SHA-256 snapshot hash."
                }
            } else {
                blockReason
            }
            emit(
                AiStreamUpdate.Completed(
                    text = politeMessage,
                    toolsUsed = listOf("ai_safety_guard", "human_confirmation_gate"),
                    modelUsed = modelMode.modelId
                )
            )
            return@flow
        }

        // 3. Redact PII before sending to Gemini
        val redactedPrompt = SecurityUtils.redactPiiForLlm(
            input = userPrompt,
            citizenName = decryptedNameForRedaction,
            citizenPhone = decryptedPhoneForRedaction
        )

        // 4. Execute deterministic tools
        val (toolsUsed, groundedContext) = executeDeterministicTools(redactedPrompt, profile, evaluations)
        emit(AiStreamUpdate.ToolExecution(toolsUsed))

        val historyText = conversationHistory.takeLast(6).joinToString("\n") { (role, msg) ->
            "$role: ${SecurityUtils.redactPiiForLlm(msg, decryptedNameForRedaction, decryptedPhoneForRedaction)}"
        }

        val fullGroundedPrompt = """
            LANGUAGE_PREFERENCE: ${if (language == "hi") "Hindi (हिंदी)" else "English"}
            GROUNDED_TOOL_RESULTS (AUTHORITATIVE - DO NOT CONTRADICT):
            $groundedContext
            
            RECENT_CONVERSATION_HISTORY:
            $historyText
            
            CITIZEN_MESSAGE:
            $redactedPrompt
            
            Respond helpfully in ${if (language == "hi") "Hindi (Devanagari script)" else "English"}, citing the deterministic eligibility outcomes (ELIGIBLE, NOT_ELIGIBLE, or UNKNOWN) and official scheme URLs from GROUNDED_TOOL_RESULTS. Remind the user that thresholds are illustrative and final submission requires their explicit review.
        """.trimIndent()

        // Fallback chain for chat:
        val modelChain = when (modelMode) {
            GeminiModelMode.HIGH_THINKING_CARBON -> listOf(
                carbonModel to "gemini-4-carbon",
                highThinkingCarbonModel to "gemini-3.8-flash",
                fastLiteModel to "gemini-3.1-flash-lite"
            )
            GeminiModelMode.COMPLEX_ARGON -> listOf(
                argonModel to "gemini-4-argon",
                flashModel to "gemini-3.8-flash",
                fastLiteModel to "gemini-3.1-flash-lite"
            )
            GeminiModelMode.FAST_LITE -> listOf(
                fastLiteModel to "gemini-3.1-flash-lite",
                flashModel to "gemini-3.8-flash"
            )
            GeminiModelMode.STANDARD_FLASH -> listOf(
                flashModel to "gemini-3.8-flash",
                fastLiteModel to "gemini-3.1-flash-lite"
            )
            GeminiModelMode.LIVE_VOICE -> listOf(
                liveVoiceModel to "gemini-3.8-flash",
                fastLiteModel to "gemini-3.1-flash-lite"
            )
            GeminiModelMode.TTS_AUDIO -> listOf(
                ttsModel to "gemini-3.8-flash-tts",
                flashModel to "gemini-3.8-flash"
            )
        }

        var succeeded = false
        var lastException: Throwable? = null

        for ((model, modelId) in modelChain) {
            val accumulated = StringBuilder()
            try {
                withTimeout(45_000L) {
                    model.generateContentStream(fullGroundedPrompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            emit(AiStreamUpdate.StreamingChunk(accumulated.toString(), toolsUsed, modelId))
                        }
                    }
                }
                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isNotBlank()) {
                    emit(AiStreamUpdate.Completed(finalOutput, toolsUsed, modelId))
                    succeeded = true
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Model $modelId failed (will attempt fallback): ${e.message}")
            }
        }

        if (!succeeded) {
            Log.e(TAG, "All models in fallback chain failed. Emitting offline deterministic fallback.", lastException)
            val friendlyMsg = mapAiErrorToFriendlyMessage(lastException, language)
            val offlineFallback = buildDeterministicWizardFallback(language, evaluations)
            emit(
                AiStreamUpdate.Completed(
                    text = "⚠️ $friendlyMsg\n\n$offlineFallback",
                    toolsUsed = toolsUsed + listOf("offline_rule_engine"),
                    modelUsed = "Deterministic Engine (Offline Fallback)"
                )
            )
        }
    }

    /**
     * Analyzes an uploaded citizen document/certificate image.
     * Uses multimodal vision fallback chain: gemini-4-argon -> gemini-3.8-flash.
     * Note: gemini-3.1-flash-lite is text-only and not used for vision.
     * If all vision models fail, gracefully returns a friendly message instructing the citizen
     * to verify the document checklist manually, never showing raw exceptions or URLs.
     */
    suspend fun analyzeDocumentImage(
        bitmap: Bitmap,
        schemeCode: String,
        language: String
    ): Result<String> {
        val prompt = if (language == "hi") {
            """
            इस दस्तावेज़ छवि का विश्लेषण योजना '$schemeCode' के लिए करें:
            1. यह कौन सा दस्तावेज़ प्रतीत होता है? (जैसे खतौनी, राशन कार्ड, बैंक पासबुक, आयु प्रमाण पत्र)।
            2. क्या इसमें नाम, जारी करने की तिथि या आधिकारिक मुहर स्पष्ट दिखाई दे रही है?
            3. सुरक्षा नियम: यदि इसमें कोई 12 अंकों का आधार नंबर दिखे तो उसे कभी न लिखें ([REDACTED] लिखें)।
            हिंदी में स्पष्ट बुलेट पॉइंट्स में उत्तर दें।
            """.trimIndent()
        } else {
            """
            Analyze this citizen welfare document image for scheme '$schemeCode':
            1. Identify the document type (e.g., Land Khatauni, BPL Ration Card, Bank Passbook, Age Certificate).
            2. Check legibility, visible fields, and whether any information appears missing for '$schemeCode'.
            3. PRIVACY RULE: Redact any 12-digit Aadhaar number as [REDACTED_AADHAAR].
            Provide a clear, structured checklist assessment.
            """.trimIndent()
        }

        val visionModels = listOf(
            argonModel to "gemini-4-argon",
            flashModel to "gemini-3.8-flash"
        )

        var lastException: Throwable? = null

        for ((model, modelId) in visionModels) {
            try {
                val response = withTimeout(45_000L) {
                    model.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    return Result.success(SecurityUtils.redactPiiForLlm(text))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Vision model $modelId failed (will attempt fallback): ${e.message}")
            }
        }

        Log.e(TAG, "All vision models failed for document image analysis", lastException)
        val friendlyMsg = mapAiErrorToFriendlyMessage(lastException, language)
        val manualHint = if (language == "hi") {
            "फोटो का विश्लेषण नहीं किया जा सका, कृपया चेकलिस्ट को मैन्युअल रूप से चिह्नित करें।"
        } else {
            "Could not analyze photo automatically, please tick the checklist manually."
        }
        return Result.success("⚠️ $friendlyMsg\n\n$manualHint")
    }

    // Keep alias for backwards compatibility
    suspend fun analyzeDocumentImageWithArgon(
        bitmap: Bitmap,
        schemeCode: String,
        language: String
    ): Result<String> = analyzeDocumentImage(bitmap, schemeCode, language)

    /**
     * Converts text into natural spoken guidance script using model `gemini-3.8-flash-tts`
     * with fallback to `gemini-3.8-flash` before speaking aloud via the device audio engine.
     */
    suspend fun prepareSpeechWithFlashTts(
        textToSpeak: String,
        language: String
    ): String {
        val models = listOf(
            ttsModel to "gemini-3.8-flash-tts",
            flashModel to "gemini-3.8-flash"
        )
        for ((model, modelId) in models) {
            try {
                val prompt = if (language == "hi") {
                    "निम्नलिखित संदेश को सरल, स्पष्ट बोलचाल की हिंदी में संक्षिप्त (2 वाक्यों में) वॉइस आउटपुट के लिए तैयार करें:\n$textToSpeak"
                } else {
                    "Prepare the following message as a clear, warm 2-sentence spoken audio script for a citizen:\n$textToSpeak"
                }
                val response = withTimeout(20_000L) {
                    model.generateContent(prompt)
                }
                val resText = response.text?.trim()
                if (!resText.isNullOrBlank()) {
                    return resText
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "TTS script prep model $modelId failed, trying fallback: ${e.message}")
            }
        }
        return textToSpeak
    }

    /**
     * Conducts a real-time Live Voice turn using model `gemini-3.8-flash` with fallback to `gemini-3.1-flash-lite`.
     */
    suspend fun generateLiveVoiceTurn(
        spokenTranscript: String,
        language: String,
        profile: CitizenProfile,
        evaluations: List<EligibilityEvaluationResult>
    ): Result<String> {
        val voiceModels = listOf(
            liveVoiceModel to "gemini-3.8-flash",
            fastLiteModel to "gemini-3.1-flash-lite"
        )
        val (_, groundedContext) = executeDeterministicTools(spokenTranscript, profile, evaluations)
        val prompt = """
            LIVE_VOICE_TURN
            LANGUAGE: ${if (language == "hi") "Hindi (हिंदी)" else "English"}
            GROUNDED_ELIGIBILITY_FACTS:
            $groundedContext
            
            USER_SPOKEN_UTTERANCE:
            ${SecurityUtils.redactPiiForLlm(spokenTranscript)}
            
            Reply in 2 short, warm spoken sentences suitable for immediate voice playback. Never invent eligibility or submit forms.
        """.trimIndent()

        var lastException: Throwable? = null
        for ((model, modelId) in voiceModels) {
            try {
                val response = withTimeout(30_000L) {
                    model.generateContent(prompt)
                }
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    return Result.success(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Live voice model $modelId failed, trying next: ${e.message}")
            }
        }
        Log.e(TAG, "All voice models failed", lastException)
        val fallback = buildDeterministicWizardFallback(language, evaluations)
        return Result.success(fallback)
    }

    fun buildDeterministicWizardFallback(
        language: String,
        evaluations: List<EligibilityEvaluationResult>
    ): String {
        val eligible = evaluations.filter { it.outcome == EligibilityOutcome.ELIGIBLE }.map { it.schemeCode }
        val unknown = evaluations.filter { it.outcome == EligibilityOutcome.UNKNOWN }.map { it.schemeCode }
        val notEligible = evaluations.filter { it.outcome == EligibilityOutcome.NOT_ELIGIBLE }.map { it.schemeCode }

        return if (language == "hi") {
            buildString {
                appendLine("📋 नियम इंजन परिणाम सारांश:")
                appendLine("• पात्र: ${eligible.ifEmpty { listOf("कोई नहीं") }.joinToString(", ")}")
                appendLine("• अतिरिक्त जानकारी आवश्यक: ${unknown.ifEmpty { listOf("कोई नहीं") }.joinToString(", ")}")
                appendLine("• अपात्र: ${notEligible.ifEmpty { listOf("कोई नहीं") }.joinToString(", ")}")
                append("नोट: सभी नियम उदाहरणार्थ हैं और अंतिम आवेदन केवल आपकी समीक्षा के बाद जन सेवा केंद्र (CSC) या आधिकारिक पोर्टल पर होता है।")
            }
        } else {
            buildString {
                appendLine("📋 Deterministic Rule Engine Summary:")
                appendLine("• ELIGIBLE: ${eligible.ifEmpty { listOf("None") }.joinToString(", ")}")
                appendLine("• UNKNOWN (Needs Info/Lookup): ${unknown.ifEmpty { listOf("None") }.joinToString(", ")}")
                appendLine("• NOT_ELIGIBLE: ${notEligible.ifEmpty { listOf("None") }.joinToString(", ")}")
                append("Note: Thresholds are illustrative until verified on official portals. Applications require your explicit review.")
            }
        }
    }

    private companion object {
        const val TAG = "SchemeSaathiAiService"
    }
}

sealed interface AiStreamUpdate {
    data class ToolExecution(val tools: List<String>) : AiStreamUpdate
    data class StreamingChunk(val partialText: String, val toolsUsed: List<String>, val modelUsed: String) : AiStreamUpdate
    data class Completed(val text: String, val toolsUsed: List<String>, val modelUsed: String) : AiStreamUpdate
    data class Error(val message: String) : AiStreamUpdate
}
