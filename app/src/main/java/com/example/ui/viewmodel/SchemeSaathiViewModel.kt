package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiStreamUpdate
import com.example.ai.GeminiModelMode
import com.example.ai.SchemeSaathiAiService
import com.example.data.model.AuditFinding
import com.example.data.model.ChatMessage
import com.example.data.model.CitizenProfile
import com.example.data.model.CollaborativeTask
import com.example.data.model.DocumentHaveStatus
import com.example.data.model.EligibilityEvaluationResult
import com.example.data.model.SchemeApplication
import com.example.data.repository.SchemeSaathiRepository
import com.example.domain.EligibilityEngine
import com.example.util.DateUtils
import com.example.util.SecurityUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    WELCOME,
    LANGUAGE_SELECTION,
    CONSENT,
    PROFILE_SETUP,
    HOME_DASHBOARD,
    AI_CHAT,
    VOICE_ASSISTANT,
    SCHEME_RESULTS,
    SCHEME_DETAILS,
    ELIGIBILITY_RESULTS,
    DOCUMENT_CHECKLIST,
    APPLICATION_FORM,
    REVIEW_CONFIRM,
    APPLICATION_TRACKING,
    ALERTS_SETTINGS
}

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

data class FormFieldProvenance(
    val value: String,
    val source: String, // "profile", "user_typed", "voice_confirmed"
    val isUncertain: Boolean = false
)

class SchemeSaathiViewModel(
    private val repository: SchemeSaathiRepository,
    private val currentUserId: String,
    private val aiService: SchemeSaathiAiService = SchemeSaathiAiService()
) : ViewModel() {

    private val _screenStack = MutableStateFlow(listOf(AppScreen.HOME_DASHBOARD))
    private val _currentScreen = MutableStateFlow(AppScreen.HOME_DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _language = MutableStateFlow("hi")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _largeTextMode = MutableStateFlow(false)
    val largeTextMode: StateFlow<Boolean> = _largeTextMode.asStateFlow()

    private val _selectedSchemeId = MutableStateFlow("PM-KISAN")
    val selectedSchemeId: StateFlow<String> = _selectedSchemeId.asStateFlow()

    private val _selectedModelMode = MutableStateFlow(GeminiModelMode.STANDARD_FLASH)
    val selectedModelMode: StateFlow<GeminiModelMode> = _selectedModelMode.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Local working copy of profile so user can edit smoothly and persist to Firestore
    private val _editableProfile = MutableStateFlow(
        CitizenProfile(
            userId = currentUserId,
            encryptedDob = SecurityUtils.encryptPii("01/01/1964", currentUserId),
            birthYear = 1964,
            ageConfirmedViaVoice = true,
            gender = "female",
            state = "Uttar Pradesh",
            district = "Varanasi",
            residenceType = "rural",
            annualIncome = 45000,
            occupation = "farmer",
            bplStatus = "yes",
            socialCategory = "obc",
            hasBankAccount = "yes",
            hasLpgConnection = "no",
            ownsAgriculturalLand = "yes",
            isIncomeTaxPayer = "no",
            familySize = 4,
            consentGranted = true,
            preferredLanguage = "hi",
            role = "CITIZEN"
        )
    )
    val editableProfile: StateFlow<CitizenProfile> = _editableProfile.asStateFlow()

    private val _decryptedName = MutableStateFlow("सीता देवी (Sita Devi)")
    val decryptedName: StateFlow<String> = _decryptedName.asStateFlow()

    private val _decryptedPhone = MutableStateFlow("9876543210")
    val decryptedPhone: StateFlow<String> = _decryptedPhone.asStateFlow()

    private val _decryptedDob = MutableStateFlow("01/01/1964")
    val decryptedDob: StateFlow<String> = _decryptedDob.asStateFlow()

    // Two-tier Kotlin Flow error handling for Firestore streams
    val profileUiState: StateFlow<DataUiState<CitizenProfile?>> = repository.observeProfile(currentUserId)
        .map<CitizenProfile?, DataUiState<CitizenProfile?>> { remoteProfile ->
            if (remoteProfile != null) {
                _editableProfile.value = remoteProfile
                _language.value = remoteProfile.preferredLanguage.ifBlank { "hi" }
                val decName = SecurityUtils.decryptPii(remoteProfile.encryptedName, currentUserId)
                val decPhone = SecurityUtils.decryptPii(remoteProfile.encryptedPhone, currentUserId)
                val decDob = SecurityUtils.decryptPii(remoteProfile.encryptedDob, currentUserId)
                if (decName.isNotBlank()) _decryptedName.value = decName
                if (decPhone.isNotBlank()) _decryptedPhone.value = decPhone
                if (DateUtils.validateDob(decDob) is DateUtils.DobValidationResult.Valid) {
                    _decryptedDob.value = decDob
                } else {
                    // Backward compatibility: If DOB is missing/blank, show DOB field empty
                    // so the user is prompted to complete it, while age falls back to birthYear.
                    _decryptedDob.value = ""
                }
            }
            DataUiState.Success(remoteProfile)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(DataUiState.Error(rootError.message ?: "Failed to observe citizen profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    val applicationsUiState: StateFlow<DataUiState<List<SchemeApplication>>> = repository.observeApplications(currentUserId)
        .map<List<SchemeApplication>, DataUiState<List<SchemeApplication>>> { list ->
            DataUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing applications", rootError)
            emit(DataUiState.Error(rootError.message ?: "Failed to observe applications"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    val tasksUiState: StateFlow<DataUiState<List<CollaborativeTask>>> = repository.observeTasks(currentUserId)
        .map<List<CollaborativeTask>, DataUiState<List<CollaborativeTask>>> { list ->
            DataUiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing collaborative tasks", rootError)
            emit(DataUiState.Error(rootError.message ?: "Failed to observe collaborative tasks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Loading
        )

    // Document Checklist state (documentId -> HAVE / MISSING / UNKNOWN)
    private val _documentStatuses = MutableStateFlow<Map<String, DocumentHaveStatus>>(
        mapOf(
            "doc_land_record" to DocumentHaveStatus.HAVE,
            "doc_bank_passbook" to DocumentHaveStatus.HAVE,
            "doc_identity_card" to DocumentHaveStatus.HAVE,
            "doc_age_proof" to DocumentHaveStatus.HAVE,
            "doc_bpl_card" to DocumentHaveStatus.HAVE,
            "doc_pension_bank" to DocumentHaveStatus.HAVE,
            "doc_ration_deprivation" to DocumentHaveStatus.HAVE,
            "doc_woman_bank" to DocumentHaveStatus.HAVE,
            "doc_family_id" to DocumentHaveStatus.UNKNOWN,
            "doc_personal_id" to DocumentHaveStatus.HAVE,
            "doc_apy_bank" to DocumentHaveStatus.HAVE,
            "doc_apy_age_nominee" to DocumentHaveStatus.HAVE
        )
    )
    val documentStatuses: StateFlow<Map<String, DocumentHaveStatus>> = _documentStatuses.asStateFlow()

    // Application Form Provenance fields
    private val _formApplicantName = MutableStateFlow(FormFieldProvenance("सीता देवी (Sita Devi)", "profile"))
    val formApplicantName: StateFlow<FormFieldProvenance> = _formApplicantName.asStateFlow()

    private val _formStateDistrict = MutableStateFlow(FormFieldProvenance("Uttar Pradesh - Varanasi", "profile"))
    val formStateDistrict: StateFlow<FormFieldProvenance> = _formStateDistrict.asStateFlow()

    private val _formBankSummary = MutableStateFlow(FormFieldProvenance("State Bank of India - DBT Enabled (SBIN0001234)", "profile"))
    val formBankSummary: StateFlow<FormFieldProvenance> = _formBankSummary.asStateFlow()

    private val _uncertainFieldsAcknowledged = MutableStateFlow(false)
    val uncertainFieldsAcknowledged: StateFlow<Boolean> = _uncertainFieldsAcknowledged.asStateFlow()

    private val _explicitCitizenConfirmationChecked = MutableStateFlow(false)
    val explicitCitizenConfirmationChecked: StateFlow<Boolean> = _explicitCitizenConfirmationChecked.asStateFlow()

    // Multi-turn Chat state
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                id = "welcome_msg",
                role = "assistant",
                content = "नमस्ते! मैं स्कीम साथी हूँ। मैं नियम इंजन (Deterministic Rule Engine) के माध्यम से PM-KISAN, वृद्धावस्था पेंशन (NSAP-IGNOAPS), उज्ज्वला योजना (PMUY), आयुष्मान भारत (PM-JAY) और अटल पेंशन योजना (APY) की पात्रता और आवश्यक दस्तावेजों की जांच में आपकी मदद कर सकता हूँ।\n\nHello! I am Scheme Saathi. Ask me in Hindi or English about your eligibility, documents, or application preparation!",
                modelUsed = "gemini-3.8-flash",
                toolCallsSummary = emptyList()
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    // Voice & Live Assistant state
    private val _pendingVoiceAge = MutableStateFlow<Int?>(null)
    val pendingVoiceAge: StateFlow<Int?> = _pendingVoiceAge.asStateFlow()

    private val _voiceEchoPrompt = MutableStateFlow<String?>(null)
    val voiceEchoPrompt: StateFlow<String?> = _voiceEchoPrompt.asStateFlow()

    private val _liveVoiceResponse = MutableStateFlow<String?>(null)
    val liveVoiceResponse: StateFlow<String?> = _liveVoiceResponse.asStateFlow()

    private val _isLiveVoiceProcessing = MutableStateFlow(false)
    val isLiveVoiceProcessing: StateFlow<Boolean> = _isLiveVoiceProcessing.asStateFlow()

    private val _lastSpokenTtsScript = MutableStateFlow<String?>(null)
    val lastSpokenTtsScript: StateFlow<String?> = _lastSpokenTtsScript.asStateFlow()

    // Stage 8 Security & Audit Report Findings
    val auditFindings: List<AuditFinding> = listOf(
        AuditFinding(
            id = "SEC-01",
            category = "Authentication & Session",
            component = "CredentialManager Google Sign-In",
            severity = "CRITICAL",
            status = "PASS (Mitigated)",
            riskAssessment = "Anonymous or unverified auth could allow unauthorized access to citizen welfare drafts.",
            remediation = "Enforced Google Sign-In via GetSignInWithGoogleOption and strict UID ownership rules in firestore.rules.",
            mitigationTimeline = "Immediate (Completed)"
        ),
        AuditFinding(
            id = "SEC-02",
            category = "PII & Aadhaar Prohibition",
            component = "SecurityUtils.encryptPii & Aadhaar Guard",
            severity = "CRITICAL",
            status = "PASS (Mitigated)",
            riskAssessment = "Plaintext storage of citizen Name/Phone or accidental collection of 12-digit Aadhaar numbers.",
            remediation = "Implemented AES-256-GCM encryption for Name/Phone, regex Aadhaar blocker, and PII redaction before Gemini calls.",
            mitigationTimeline = "Immediate (Completed)"
        ),
        AuditFinding(
            id = "SEC-03",
            category = "Deterministic Eligibility Boundary",
            component = "EligibilityEngine.kt",
            severity = "HIGH",
            status = "PASS (Mitigated)",
            riskAssessment = "LLM hallucinating eligibility decisions or treating missing fields as NOT_ELIGIBLE.",
            remediation = "Pure synchronous EligibilityEngine evaluates all 5 schemes; missing fields and unconfirmed voice inputs return UNKNOWN.",
            mitigationTimeline = "Immediate (Completed)"
        ),
        AuditFinding(
            id = "SEC-04",
            category = "Human Confirmation Gate",
            component = "ReviewConfirmScreen & Snapshot Hash",
            severity = "CRITICAL",
            status = "PASS (Mitigated)",
            riskAssessment = "AI or unverified workflow automatically submitting government forms without citizen consent.",
            remediation = "SHA-256 review snapshot hash verification, explicit citizen checkbox, and no submit tools exposed to Gemini.",
            mitigationTimeline = "Immediate (Completed)"
        ),
        AuditFinding(
            id = "SEC-05",
            category = "Official Threshold Verification",
            component = "Scheme Catalog (5 Schemes)",
            severity = "MEDIUM",
            status = "MONITORED (Illustrative)",
            riskAssessment = "State top-ups and annual income thresholds evolve across state gazette notifications.",
            remediation = "Labeled all rule versions as illustrative with stale-rule warnings and direct official portal handoff links.",
            mitigationTimeline = "Quarterly Portal Sync (30 Days)"
        )
    )

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            _screenStack.value = (_screenStack.value + screen).takeLast(12)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        val stack = _screenStack.value
        return if (stack.size > 1) {
            val newStack = stack.dropLast(1)
            _screenStack.value = newStack
            _currentScreen.value = newStack.last()
            true
        } else if (_currentScreen.value != AppScreen.HOME_DASHBOARD) {
            _screenStack.value = listOf(AppScreen.HOME_DASHBOARD)
            _currentScreen.value = AppScreen.HOME_DASHBOARD
            true
        } else {
            false
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        _editableProfile.value = _editableProfile.value.copy(preferredLanguage = lang)
    }

    fun toggleLargeTextMode(enabled: Boolean) {
        _largeTextMode.value = enabled
    }

    fun selectScheme(schemeId: String) {
        _selectedSchemeId.value = schemeId
    }

    fun selectModelMode(mode: GeminiModelMode) {
        _selectedModelMode.value = mode
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun computeAllEvaluations(): List<EligibilityEvaluationResult> {
        return EligibilityEngine.evaluateAllSchemes(_editableProfile.value)
    }

    // --- PROFILE & CONSENT MANAGEMENT ---

    fun updateConsent(granted: Boolean) {
        val updated = _editableProfile.value.copy(consentGranted = granted)
        _editableProfile.value = updated
        viewModelScope.launch {
            try {
                if (granted) {
                    val encName = SecurityUtils.encryptPii(_decryptedName.value, currentUserId)
                    val encPhone = SecurityUtils.encryptPii(_decryptedPhone.value, currentUserId)
                    val encDob = if (_decryptedDob.value.isNotBlank()) SecurityUtils.encryptPii(_decryptedDob.value, currentUserId) else updated.encryptedDob
                    repository.saveProfile(updated.copy(encryptedName = encName, encryptedPhone = encPhone, encryptedDob = encDob))
                    _statusBanner.value = if (_language.value == "hi") {
                        "सहमति सुरक्षित रूप से दर्ज की गई (AES-256-GCM एन्क्रिप्टेड)।"
                    } else {
                        "Privacy consent recorded with AES-256-GCM encryption."
                    }
                } else {
                    repository.deleteProfile(currentUserId)
                    _statusBanner.value = if (_language.value == "hi") {
                        "सहमति वापस ले ली गई और क्लाउड प्रोफ़ाइल हटा दी गई।"
                    } else {
                        "Consent withdrawn and cloud profile deleted."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to update consent"
            }
        }
    }

    fun updateDob(dob: String) {
        _decryptedDob.value = dob
        val validation = DateUtils.validateDob(dob)
        if (validation is DateUtils.DobValidationResult.Valid) {
            val parsed = DateUtils.parseDob(dob)
            if (parsed != null) {
                val encDob = SecurityUtils.encryptPii(dob, currentUserId)
                _editableProfile.value = _editableProfile.value.copy(
                    encryptedDob = encDob,
                    birthYear = parsed.year,
                    ageConfirmedViaVoice = true
                )
            }
        }
    }

    fun updateEditableProfile(
        name: String = _decryptedName.value,
        phone: String = _decryptedPhone.value,
        updater: (CitizenProfile) -> CitizenProfile
    ) {
        if (SecurityUtils.containsAadhaarNumber(name) || SecurityUtils.containsAadhaarNumber(phone)) {
            _statusBanner.value = if (_language.value == "hi") {
                "सुरक्षा नियम: 12 अंकों का आधार नंबर दर्ज करना वर्जित है।"
            } else {
                "Security Rule: Entering a 12-digit Aadhaar number is strictly prohibited."
            }
            return
        }
        _decryptedName.value = name
        _decryptedPhone.value = phone
        val updated = updater(_editableProfile.value)
        _editableProfile.value = updated
        // Sync form provenance fields from profile
        _formApplicantName.value = FormFieldProvenance(name, "profile")
        _formStateDistrict.value = FormFieldProvenance("${updated.state} - ${updated.district}", "profile")
    }

    fun saveCitizenProfileToCloud() {
        val profile = _editableProfile.value
        if (!profile.consentGranted) {
            _statusBanner.value = if (_language.value == "hi") {
                "प्रोफ़ाइल सहेजने से पहले कृपया गोपनीयता सहमति दें।"
            } else {
                "Please grant privacy consent before saving your profile to the cloud."
            }
            navigateTo(AppScreen.CONSENT)
            return
        }
        viewModelScope.launch {
            try {
                val encName = SecurityUtils.encryptPii(_decryptedName.value, currentUserId)
                val encPhone = SecurityUtils.encryptPii(_decryptedPhone.value, currentUserId)
                val encDob = if (_decryptedDob.value.isNotBlank()) SecurityUtils.encryptPii(_decryptedDob.value, currentUserId) else profile.encryptedDob
                repository.saveProfile(
                    profile.copy(
                        userId = currentUserId,
                        encryptedName = encName,
                        encryptedPhone = encPhone,
                        encryptedDob = encDob
                    )
                )
                _statusBanner.value = if (_language.value == "hi") {
                    "✅ प्रोफ़ाइल AES-256-GCM एन्क्रिप्शन के साथ सुरक्षित हो गई और पात्रता अपडेट की गई!"
                } else {
                    "✅ Profile saved with AES-256-GCM encryption & eligibility recalculated!"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to save profile"
            }
        }
    }

    // --- VOICE NUMERAL PARSING & ECHO-BACK CONFIRMATION GATE ---

    fun processSpokenUtterance(utterance: String, onSpeakBack: (String) -> Unit) {
        if (utterance.isBlank()) return
        val lang = _language.value

        // Check if utterance contains a spoken age or Hindi numeral (e.g., बासठ = 62, बयासठ = 62, 62 = 62)
        val parsedNumber = SecurityUtils.parseHindiOrEnglishNumber(utterance)
        if (parsedNumber != null && parsedNumber in 18..105) {
            _pendingVoiceAge.value = parsedNumber
            val echoText = SecurityUtils.formatAgeEchoConfirmation(parsedNumber, lang)
            _voiceEchoPrompt.value = echoText
            // Mark age as unconfirmed until explicit Haan / Yes button is pressed.
            // Voice sets age directly; clear encryptedDob so fallback birthYear is used.
            _editableProfile.value = _editableProfile.value.copy(
                birthYear = 2026 - parsedNumber,
                encryptedDob = "",
                ageConfirmedViaVoice = false
            )
            onSpeakBack(echoText)
            return
        }

        // Otherwise run Live Voice turn using gemini-3.8-live
        viewModelScope.launch {
            _isLiveVoiceProcessing.value = true
            val evals = computeAllEvaluations()
            val result = aiService.generateLiveVoiceTurn(
                spokenTranscript = utterance,
                language = lang,
                profile = _editableProfile.value,
                evaluations = evals
            )
            _isLiveVoiceProcessing.value = false
            val reply = result.getOrElse {
                aiService.buildDeterministicWizardFallback(lang, evals)
            }
            _liveVoiceResponse.value = reply
            val ttsScript = aiService.prepareSpeechWithFlashTts(reply, lang)
            _lastSpokenTtsScript.value = ttsScript
            onSpeakBack(ttsScript)
        }
    }

    fun confirmVoiceAge(confirmedHaan: Boolean) {
        val pendingAge = _pendingVoiceAge.value ?: return
        if (confirmedHaan) {
            _editableProfile.value = _editableProfile.value.copy(
                birthYear = 2026 - pendingAge,
                encryptedDob = "",
                ageConfirmedViaVoice = true
            )
            _decryptedDob.value = ""
            _pendingVoiceAge.value = null
            _voiceEchoPrompt.value = null
            _statusBanner.value = if (_language.value == "hi") {
                "✅ उम्र $pendingAge वर्ष की पुष्टि हो गई (voice_confirmed)!"
            } else {
                "✅ Age $pendingAge years confirmed (voice_confirmed)!"
            }
            saveCitizenProfileToCloud()
        } else {
            _editableProfile.value = _editableProfile.value.copy(ageConfirmedViaVoice = false)
            _pendingVoiceAge.value = null
            _voiceEchoPrompt.value = if (_language.value == "hi") {
                "उम्र अस्वीकार की गई। कृपया दोबारा बोलें या प्रोफ़ाइल में टाइप करें।"
            } else {
                "Spoken age rejected. Please speak again or type in Profile Setup."
            }
        }
    }

    // --- MULTI-TURN GEMINI CHAT & TTS ---

    fun sendChatMessage(userMessage: String, onAutoSpeakReply: ((String) -> Unit)? = null) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty() || _isChatGenerating.value) return

        val userMsgObj = ChatMessage(
            id = "u_${UUID.randomUUID()}",
            role = "user",
            content = trimmed,
            modelUsed = _selectedModelMode.value.modelId
        )
        val historyPairs = _chatMessages.value.map { it.role to it.content }
        _chatMessages.value = _chatMessages.value + userMsgObj

        val assistantMsgId = "a_${UUID.randomUUID()}"
        val placeholderAssistant = ChatMessage(
            id = assistantMsgId,
            role = "assistant",
            content = "⏳ ...",
            modelUsed = _selectedModelMode.value.modelId
        )
        _chatMessages.value = _chatMessages.value + placeholderAssistant
        _isChatGenerating.value = true

        viewModelScope.launch {
            val evals = computeAllEvaluations()
            aiService.streamGroundedChat(
                userPrompt = trimmed,
                language = _language.value,
                modelMode = _selectedModelMode.value,
                profile = _editableProfile.value,
                evaluations = evals,
                decryptedNameForRedaction = _decryptedName.value,
                decryptedPhoneForRedaction = _decryptedPhone.value,
                conversationHistory = historyPairs
            ).collect { update ->
                when (update) {
                    is AiStreamUpdate.ToolExecution -> {
                        updateAssistantMessage(assistantMsgId) {
                            it.copy(toolCallsSummary = update.tools)
                        }
                    }
                    is AiStreamUpdate.StreamingChunk -> {
                        updateAssistantMessage(assistantMsgId) {
                            it.copy(
                                content = update.partialText,
                                toolCallsSummary = update.toolsUsed,
                                modelUsed = update.modelUsed
                            )
                        }
                    }
                    is AiStreamUpdate.Completed -> {
                        updateAssistantMessage(assistantMsgId) {
                            it.copy(
                                content = update.text,
                                toolCallsSummary = update.toolsUsed,
                                modelUsed = update.modelUsed
                            )
                        }
                        _isChatGenerating.value = false
                        if (onAutoSpeakReply != null) {
                            val script = aiService.prepareSpeechWithFlashTts(update.text, _language.value)
                            _lastSpokenTtsScript.value = script
                            onAutoSpeakReply(script)
                        }
                    }
                    is AiStreamUpdate.Error -> {
                        updateAssistantMessage(assistantMsgId) {
                            it.copy(
                                content = update.message,
                                isGrounded = true
                            )
                        }
                        _isChatGenerating.value = false
                    }
                }
            }
        }
    }

    fun speakTextWithGeminiTts(text: String, onReadyToSpeak: (String) -> Unit) {
        viewModelScope.launch {
            val script = aiService.prepareSpeechWithFlashTts(text, _language.value)
            _lastSpokenTtsScript.value = script
            onReadyToSpeak(script)
        }
    }

    private fun updateAssistantMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.id == id) transform(msg) else msg
        }
    }

    // --- DOCUMENT CHECKLIST ---

    fun updateDocumentHaveStatus(docId: String, status: DocumentHaveStatus) {
        _documentStatuses.value = _documentStatuses.value.toMutableMap().apply {
            put(docId, status)
        }
    }

    // --- APPLICATION PREPARATION & HUMAN CONFIRMATION GATE ---

    fun updateFormField(fieldKey: String, newValue: String, source: String = "user_typed") {
        if (SecurityUtils.containsAadhaarNumber(newValue)) {
            _statusBanner.value = "⚠️ Aadhaar numbers are strictly prohibited."
            return
        }
        when (fieldKey) {
            "applicantName" -> _formApplicantName.value = FormFieldProvenance(newValue, source)
            "stateDistrict" -> _formStateDistrict.value = FormFieldProvenance(newValue, source)
            "bankSummary" -> _formBankSummary.value = FormFieldProvenance(newValue, source)
        }
    }

    fun setUncertainFieldsAcknowledged(ack: Boolean) {
        _uncertainFieldsAcknowledged.value = ack
    }

    fun setExplicitCitizenConfirmationChecked(checked: Boolean) {
        _explicitCitizenConfirmationChecked.value = checked
    }

    fun getCurrentChecklistSummaryForSelectedScheme(): String {
        val scheme = EligibilityEngine.getSchemeById(_selectedSchemeId.value)
            ?: EligibilityEngine.initialFiveSchemes.first()
        return scheme.requiredDocuments.joinToString("; ") { doc ->
            val st = _documentStatuses.value[doc.id] ?: DocumentHaveStatus.UNKNOWN
            "${doc.nameEn}: ${st.name}"
        }
    }

    fun computeCurrentReviewSnapshotHash(): String {
        val scheme = EligibilityEngine.getSchemeById(_selectedSchemeId.value)
            ?: EligibilityEngine.initialFiveSchemes.first()
        return SecurityUtils.computeSnapshotHash(
            schemeId = scheme.id,
            ruleVersion = scheme.ruleVersion,
            applicantName = _formApplicantName.value.value,
            stateDistrict = _formStateDistrict.value.value,
            bankIfscSummary = _formBankSummary.value.value,
            checklistSummary = getCurrentChecklistSummaryForSelectedScheme()
        )
    }

    fun confirmAndCreateHandoffApplication(expectedSnapshotHash: String) {
        val actualHash = computeCurrentReviewSnapshotHash()
        if (expectedSnapshotHash != actualHash) {
            _statusBanner.value = "❌ Snapshot Hash Mismatch! Please re-verify form fields before confirming."
            return
        }
        if (!_explicitCitizenConfirmationChecked.value) {
            _statusBanner.value = "❌ Explicit citizen confirmation checkbox is required."
            return
        }
        val scheme = EligibilityEngine.getSchemeById(_selectedSchemeId.value)
            ?: EligibilityEngine.initialFiveSchemes.first()

        val app = SchemeApplication(
            userId = currentUserId,
            schemeId = scheme.id,
            schemeName = if (_language.value == "hi") scheme.nameHi else scheme.nameEn,
            ruleVersion = scheme.ruleVersion,
            status = "HANDED_OFF",
            verified = false,
            applicantName = _formApplicantName.value.value,
            applicantNameSource = _formApplicantName.value.source,
            stateDistrict = _formStateDistrict.value.value,
            stateDistrictSource = _formStateDistrict.value.source,
            bankIfscSummary = _formBankSummary.value.value,
            bankIfscSource = _formBankSummary.value.source,
            checklistSummary = getCurrentChecklistSummaryForSelectedScheme(),
            snapshotHash = actualHash,
            explicitCitizenConfirmed = true
        )

        viewModelScope.launch {
            try {
                repository.createApplication(app)
                _statusBanner.value = if (_language.value == "hi") {
                    "✅ आवेदन की पुष्टि हो गई और गाइडेड हैंडऑफ पैक तैयार है!"
                } else {
                    "✅ Application confirmed via Human Gate & Guided Handoff Pack generated!"
                }
                navigateTo(AppScreen.APPLICATION_TRACKING)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to save confirmed application"
            }
        }
    }

    fun updateApplicationStatus(
        application: SchemeApplication,
        newStatus: String,
        departmentVerified: Boolean
    ) {
        // Security Rule: Department-confirmed statuses (ACKNOWLEDGED, APPROVED, REJECTED) require verified=true
        val departmentStatuses = setOf("ACKNOWLEDGED", "APPROVED", "REJECTED")
        if (newStatus in departmentStatuses && !departmentVerified) {
            _statusBanner.value = "Security Policy: Department statuses ($newStatus) require verified=true."
            return
        }
        viewModelScope.launch {
            try {
                repository.updateApplication(
                    application.copy(
                        status = newStatus,
                        verified = departmentVerified
                    )
                )
                _statusBanner.value = "Status updated to $newStatus (${if (departmentVerified) "VERIFIED" else "SELF-REPORTED"})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to update application status"
            }
        }
    }

    // --- COLLABORATIVE TASKS, RBAC & REMINDERS ---

    fun createCollaborativeTask(
        title: String,
        description: String,
        assigneeName: String,
        assigneeRole: String,
        priority: String,
        schemeCode: String,
        dueDateLabel: String,
        deliveryChannel: String
    ) {
        if (title.isBlank()) return
        val task = CollaborativeTask(
            userId = currentUserId,
            title = title.trim(),
            description = description.trim().ifBlank { "Document verification & CSC handoff follow-up" },
            assigneeName = assigneeName.trim().ifBlank { "Gram CSC Operator" },
            assigneeRole = assigneeRole,
            priority = priority,
            status = "PENDING",
            schemeCode = schemeCode,
            dueDateLabel = dueDateLabel.trim().ifBlank { "Tomorrow 10:00 AM" },
            deliveryChannel = deliveryChannel,
            deliveryStatus = "SCHEDULED",
            activityLogSummary = "[${_editableProfile.value.role}] Created task & scheduled $deliveryChannel reminder (Retry policy: 1m, 5m, 30m)"
        )
        viewModelScope.launch {
            try {
                repository.createTask(task)
                _statusBanner.value = "✅ Collaborative task & reminder scheduled ($deliveryChannel)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to create task"
            }
        }
    }

    fun advanceTaskStatus(task: CollaborativeTask) {
        val nextStatus = when (task.status) {
            "PENDING" -> "IN_PROGRESS"
            "IN_PROGRESS" -> "COMPLETED"
            else -> "COMPLETED"
        }
        val nextDelivery = if (nextStatus == "COMPLETED") "DELIVERED" else "DELIVERED"
        val updatedLog = "${task.activityLogSummary} -> Transitioned to $nextStatus by ${_editableProfile.value.role}"
        viewModelScope.launch {
            try {
                repository.updateTask(
                    task.copy(
                        status = nextStatus,
                        deliveryStatus = nextDelivery,
                        activityLogSummary = updatedLog.take(950)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to update task"
            }
        }
    }

    fun cancelTaskReminder(task: CollaborativeTask) {
        viewModelScope.launch {
            try {
                repository.updateTask(
                    task.copy(
                        status = "CANCELLED",
                        deliveryStatus = "CANCELLED",
                        activityLogSummary = "${task.activityLogSummary} -> Reminder cancelled by user".take(950)
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to cancel reminder"
            }
        }
    }

    fun buildAuditReportCsv(): String {
        val header = "FindingID,Category,Component,Severity,Status,RiskAssessment,Remediation,MitigationTimeline"
        val rows = auditFindings.map { f ->
            "\"${f.id}\",\"${f.category}\",\"${f.component}\",\"${f.severity}\",\"${f.status}\",\"${f.riskAssessment}\",\"${f.remediation}\",\"${f.mitigationTimeline}\""
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private companion object {
        const val TAG = "SchemeSaathiVM"
    }
}
