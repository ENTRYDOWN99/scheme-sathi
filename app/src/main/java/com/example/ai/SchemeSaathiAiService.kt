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
        labelEn = "Standard (gemini-3.8-flash)",
        labelHi = "सामान्य (gemini-3.8-flash)",
        descriptionEn = "Balanced multi-turn bilingual assistant"
    ),
    FAST_LITE(
        modelId = "gemini-3.1-flash-lite",
        labelEn = "Low-Latency (gemini-3.1-flash-lite)",
        labelHi = "तेज़ उत्तर (gemini-3.1-flash-lite)",
        descriptionEn = "Ultra-fast low-latency responses"
    ),
    HIGH_THINKING_CARBON(
        modelId = "gemini-4-carbon",
        labelEn = "High Thinking (gemini-4-carbon)",
        labelHi = "गहन विश्लेषण (gemini-4-carbon)",
        descriptionEn = "Deep reasoning with ThinkingLevel.HIGH"
    ),
    COMPLEX_ARGON(
        modelId = "gemini-4-argon",
        labelEn = "Complex & Vision (gemini-4-argon)",
        labelHi = "दस्तावेज़ व जटिल (gemini-4-argon)",
        descriptionEn = "Complex interactive & multimodal document understanding"
    ),
    LIVE_VOICE(
        modelId = "gemini-3.8-live",
        labelEn = "Live Voice (gemini-3.8-live)",
        labelHi = "लाइव वॉइस (gemini-3.8-live)",
        descriptionEn = "Real-time conversational voice mode"
    ),
    TTS_AUDIO(
        modelId = "gemini-3.8-flash-tts",
        labelEn = "TTS Speech (gemini-3.8-flash-tts)",
        labelHi = "टेक्स्ट-टू-स्पीच (gemini-3.8-flash-tts)",
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
    private val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    private val fastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    // High Thinking model using gemini-4-carbon and ThinkingLevel.HIGH (no maxOutputTokens)
    private val highThinkingCarbonModel by lazy {
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

    private val argonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    private val liveVoiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-live",
            systemInstruction = content {
                text("$systemPromptText Keep spoken responses concise (2-3 sentences) and natural for voice output.")
            }
        )
    }

    private val ttsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash-tts"
        )
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
     * Streams a grounded multi-turn chat response using the selected Gemini model.
     * Enforces Prompt Injection blocking, Aadhaar blocking, PII Redaction, and 60s timeout.
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

        // 2. Check Prompt Injection or Auto-Submit guard
        val (blocked, blockReason) = SecurityUtils.detectPromptInjectionOrAutoSubmit(userPrompt)
        if (blocked && blockReason != null) {
            emit(
                AiStreamUpdate.Completed(
                    text = blockReason,
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

        val selectedModel = when (modelMode) {
            GeminiModelMode.STANDARD_FLASH -> flashModel
            GeminiModelMode.FAST_LITE -> fastLiteModel
            GeminiModelMode.HIGH_THINKING_CARBON -> highThinkingCarbonModel
            GeminiModelMode.COMPLEX_ARGON -> argonModel
            GeminiModelMode.LIVE_VOICE -> liveVoiceModel
            GeminiModelMode.TTS_AUDIO -> ttsModel
        }

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

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                selectedModel.generateContentStream(fullGroundedPrompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(AiStreamUpdate.StreamingChunk(accumulated.toString(), toolsUsed, modelMode.modelId))
                    }
                }
            }
            val finalOutput = accumulated.toString().trim()
            if (finalOutput.isNotBlank()) {
                emit(AiStreamUpdate.Completed(finalOutput, toolsUsed, modelMode.modelId))
            } else {
                // Grounded wizard fallback if model returns empty text
                val fallback = buildDeterministicWizardFallback(language, evaluations)
                emit(AiStreamUpdate.Completed(fallback, toolsUsed, "${modelMode.modelId} (Grounded Fallback)"))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            emit(AiStreamUpdate.Error("Request timed out after 60 seconds. Deterministic summary:\n" + buildDeterministicWizardFallback(language, evaluations)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI call failed", e)
            emit(
                AiStreamUpdate.Error(
                    (e.localizedMessage ?: "AI request failed") +
                        "\n\n" + buildDeterministicWizardFallback(language, evaluations)
                )
            )
        }
    }

    /**
     * Analyzes an uploaded citizen document/certificate image using model `gemini-4-argon`.
     */
    suspend fun analyzeDocumentImageWithArgon(
        bitmap: Bitmap,
        schemeCode: String,
        language: String
    ): Result<String> {
        return try {
            val prompt = if (language == "hi") {
                """
                इस दस्तावेज़ छवि का विश्लेषण योजना '$schemeCode' के लिए करें (gemini-4-argon)।
                1. यह कौन सा दस्तावेज़ प्रतीत होता है? (जैसे खतौनी, राशन कार्ड, बैंक पासबुक, आयु प्रमाण पत्र)।
                2. क्या इसमें नाम, जारी करने की तिथि या आधिकारिक मुहर स्पष्ट दिखाई दे रही है?
                3. सुरक्षा नियम: यदि इसमें कोई 12 अंकों का आधार नंबर दिखे तो उसे कभी न लिखें ([REDACTED] लिखें)।
                हिंदी में स्पष्ट बुलेट पॉइंट्स में उत्तर दें।
                """.trimIndent()
            } else {
                """
                Analyze this citizen welfare document image for scheme '$schemeCode' using gemini-4-argon:
                1. Identify the document type (e.g., Land Khatauni, BPL Ration Card, Bank Passbook, Age Certificate).
                2. Check legibility, visible fields, and whether any information appears missing for '$schemeCode'.
                3. PRIVACY RULE: Redact any 12-digit Aadhaar number as [REDACTED_AADHAAR].
                Provide a clear, structured checklist assessment.
                """.trimIndent()
            }

            val response = withTimeout(60_000L) {
                argonModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(SecurityUtils.redactPiiForLlm(text))
            } else {
                Result.failure(IllegalStateException("gemini-4-argon returned an empty analysis response."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document image analysis with gemini-4-argon failed", e)
            Result.failure(e)
        }
    }

    /**
     * Converts text into natural spoken guidance script using model `gemini-3.8-flash-tts`
     * before speaking aloud via the device audio engine.
     */
    suspend fun prepareSpeechWithFlashTts(
        textToSpeak: String,
        language: String
    ): String {
        return try {
            val prompt = if (language == "hi") {
                "निम्नलिखित संदेश को सरल, स्पष्ट बोलचाल की हिंदी में संक्षिप्त (2 वाक्यों में) वॉइस आउटपुट के लिए तैयार करें:\n$textToSpeak"
            } else {
                "Prepare the following message as a clear, warm 2-sentence spoken audio script for a citizen:\n$textToSpeak"
            }
            val response = withTimeout(30_000L) {
                ttsModel.generateContent(prompt)
            }
            response.text?.trim()?.takeIf { it.isNotBlank() } ?: textToSpeak
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "gemini-3.8-flash-tts fallback to direct text: ${e.message}")
            textToSpeak
        }
    }

    /**
     * Conducts a real-time Live Voice turn using model `gemini-3.8-live`.
     */
    suspend fun generateLiveVoiceTurn(
        spokenTranscript: String,
        language: String,
        profile: CitizenProfile,
        evaluations: List<EligibilityEvaluationResult>
    ): Result<String> {
        return try {
            val (_, groundedContext) = executeDeterministicTools(spokenTranscript, profile, evaluations)
            val prompt = """
                LIVE_VOICE_TURN (gemini-3.8-live)
                LANGUAGE: ${if (language == "hi") "Hindi (हिंदी)" else "English"}
                GROUNDED_ELIGIBILITY_FACTS:
                $groundedContext
                
                USER_SPOKEN_UTTERANCE:
                ${SecurityUtils.redactPiiForLlm(spokenTranscript)}
                
                Reply in 2 short, warm spoken sentences suitable for immediate voice playback. Never invent eligibility or submit forms.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                liveVoiceModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.success(buildDeterministicWizardFallback(language, evaluations))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "gemini-3.8-live turn failed", e)
            Result.failure(e)
        }
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
                appendLine("📋 नियम इंजन (Rule Engine) परिणाम सारांश:")
                appendLine("• पात्र (ELIGIBLE): ${eligible.ifEmpty { listOf("कोई नहीं") }.joinToString(", ")}")
                appendLine("• अतिरिक्त जानकारी आवश्यक (UNKNOWN): ${unknown.ifEmpty { listOf("कोई नहीं") }.joinToString(", ")}")
                appendLine("• अपात्र (NOT_ELIGIBLE): ${notEligible.ifEmpty { listOf("कोई नहीं") }.joinToString(", ")}")
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
