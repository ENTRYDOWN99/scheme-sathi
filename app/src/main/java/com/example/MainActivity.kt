package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.SchemeSaathiDatabase
import com.example.data.model.CollaborativeTask
import com.example.data.model.SchemeApplication
import com.example.data.remote.BackendApiClient
import com.example.data.repository.SchemeSaathiRepository
import com.example.security.TokenStore
import com.example.util.NotificationHelper
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AiChatScreen
import com.example.ui.screens.AlertsAndSettingsScreen
import com.example.ui.screens.ApplicationFormScreen
import com.example.ui.screens.ApplicationTrackingScreen
import com.example.ui.screens.AuthGateScreen
import com.example.ui.screens.ConsentScreen
import com.example.ui.screens.DocumentChecklistScreen
import com.example.ui.screens.EligibilityResultsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LanguageSelectionScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.ReviewConfirmScreen
import com.example.ui.screens.SchemeDetailsScreen
import com.example.ui.screens.SchemeResultsScreen
import com.example.ui.screens.VoiceAssistantScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.SchemeSaathiTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.SchemeSaathiViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            SchemeSaathiRootApp()
        }
    }
}

@Composable
fun SchemeSaathiRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var unauthLanguage by remember { mutableStateOf("hi") }

    // Attempt silent auto-sign-in on startup via CredentialManager
    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            val credentialManager = CredentialManager.create(context)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    // Initialize Android TextToSpeech for playing gemini-3.8-flash-tts prepared speech scripts
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale("hi", "IN")
            }
        }
        ttsEngine = instance
        onDispose {
            instance?.stop()
            instance?.shutdown()
        }
    }

    val speakAloud: (String, String) -> Unit = { script, lang ->
        ttsEngine?.let { engine ->
            engine.language = if (lang == "hi") Locale("hi", "IN") else Locale("en", "IN")
            engine.speak(script, TextToSpeech.QUEUE_FLUSH, null, "scheme_saathi_tts")
        }
    }

    val user = currentUser
    if (user == null) {
        SchemeSaathiTheme(largeTextMode = false) {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                AuthGateScreen(
                    language = unauthLanguage,
                    onLanguageChange = { unauthLanguage = it },
                    onAuthSuccess = {},
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: SchemeSaathiViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = try {
                            app.getString(R.string.firestore_database_id)
                        } catch (e: Exception) {
                            "(default)"
                        }
                        val db = try {
                            FirebaseFirestore.getInstance(databaseId)
                        } catch (e: Exception) {
                            FirebaseFirestore.getInstance()
                        }
                        val appContext = context.applicationContext
                        val localDb = SchemeSaathiDatabase.getInstance(appContext)
                        val tokenStore = TokenStore(appContext)
                        val backendClient = BackendApiClient(tokenStore)
                        SchemeSaathiViewModel(
                            repository = SchemeSaathiRepository(db, FirebaseAuth.getInstance()),
                            currentUserId = uid,
                            localDb = localDb,
                            tokenStore = tokenStore,
                            backendClient = backendClient,
                            onNotifyTask = { title, body ->
                                NotificationHelper.showTaskNotification(appContext, title, body)
                            }
                        )
                    }
                }
            )
            AuthenticatedSchemeSaathiApp(
                viewModel = viewModel,
                onSpeakAloud = speakAloud
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedSchemeSaathiApp(
    viewModel: SchemeSaathiViewModel,
    onSpeakAloud: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val largeTextMode by viewModel.largeTextMode.collectAsStateWithLifecycle()
    val darkThemeMode by viewModel.darkThemeMode.collectAsStateWithLifecycle()
    val selectedSchemeId by viewModel.selectedSchemeId.collectAsStateWithLifecycle()
    val selectedModelMode by viewModel.selectedModelMode.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val editableProfile by viewModel.editableProfile.collectAsStateWithLifecycle()
    val decryptedName by viewModel.decryptedName.collectAsStateWithLifecycle()
    val decryptedPhone by viewModel.decryptedPhone.collectAsStateWithLifecycle()
    val decryptedDob by viewModel.decryptedDob.collectAsStateWithLifecycle()

    // Collect Firestore streams
    val profileUiState by viewModel.profileUiState.collectAsStateWithLifecycle()
    val applicationsUiState by viewModel.applicationsUiState.collectAsStateWithLifecycle()
    val tasksUiState by viewModel.tasksUiState.collectAsStateWithLifecycle()

    val documentStatuses by viewModel.documentStatuses.collectAsStateWithLifecycle()

    val formApplicantName by viewModel.formApplicantName.collectAsStateWithLifecycle()
    val formStateDistrict by viewModel.formStateDistrict.collectAsStateWithLifecycle()
    val formBankSummary by viewModel.formBankSummary.collectAsStateWithLifecycle()
    val uncertainAck by viewModel.uncertainFieldsAcknowledged.collectAsStateWithLifecycle()
    val explicitConfirm by viewModel.explicitCitizenConfirmationChecked.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatGenerating by viewModel.isChatGenerating.collectAsStateWithLifecycle()

    val pendingVoiceAge by viewModel.pendingVoiceAge.collectAsStateWithLifecycle()
    val voiceEchoPrompt by viewModel.voiceEchoPrompt.collectAsStateWithLifecycle()
    val liveVoiceResponse by viewModel.liveVoiceResponse.collectAsStateWithLifecycle()
    val lastSpokenTtsScript by viewModel.lastSpokenTtsScript.collectAsStateWithLifecycle()
    val isLiveVoiceProcessing by viewModel.isLiveVoiceProcessing.collectAsStateWithLifecycle()

    // Compute deterministic eligibility across all 5 schemes whenever profile changes
    val evaluations = remember(editableProfile) {
        viewModel.computeAllEvaluations()
    }

    val applicationsList: List<SchemeApplication> = when (val st = applicationsUiState) {
        is DataUiState.Success -> st.data
        else -> emptyList()
    }

    val tasksList: List<CollaborativeTask> = when (val st = tasksUiState) {
        is DataUiState.Success -> st.data
        else -> emptyList()
    }

    // BackHandler on all secondary screens per navigation guidelines
    if (currentScreen != AppScreen.HOME_DASHBOARD) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    SchemeSaathiTheme(darkTheme = darkThemeMode, largeTextMode = largeTextMode) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (language == "hi") "स्कीम साथी (Scheme Saathi)" else "Scheme Saathi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (language == "hi") {
                                    "नियम इंजन • Gemini AI • वॉइस • सहयोगात्मक कार्य"
                                } else {
                                    "Deterministic Rules • Gemini AI • Voice • Tasks"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentScreen != AppScreen.HOME_DASHBOARD) {
                            IconButton(
                                onClick = { viewModel.navigateBack() },
                                modifier = Modifier.testTag("top_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = language == "hi",
                            onClick = { viewModel.setLanguage(if (language == "hi") "en" else "hi") },
                            label = { Text(if (language == "hi") "हिंदी / EN" else "EN / हिंदी") },
                            modifier = Modifier.testTag("top_lang_toggle")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.VOICE_ASSISTANT) },
                            modifier = Modifier.testTag("top_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Assistant"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen in setOf(
                            AppScreen.HOME_DASHBOARD,
                            AppScreen.SCHEME_RESULTS,
                            AppScreen.SCHEME_DETAILS,
                            AppScreen.ELIGIBILITY_RESULTS
                        ),
                        onClick = { viewModel.navigateTo(AppScreen.HOME_DASHBOARD) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = {
                            Text(
                                text = if (language == "hi") "होम" else "Home",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = currentScreen in setOf(AppScreen.AI_CHAT, AppScreen.VOICE_ASSISTANT),
                        onClick = { viewModel.navigateTo(AppScreen.AI_CHAT) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                        label = {
                            Text(
                                text = if (language == "hi") "चैट" else "Chat",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("nav_chat")
                    )
                    NavigationBarItem(
                        selected = currentScreen in setOf(
                            AppScreen.DOCUMENT_CHECKLIST,
                            AppScreen.APPLICATION_FORM,
                            AppScreen.REVIEW_CONFIRM,
                            AppScreen.APPLICATION_TRACKING
                        ),
                        onClick = { viewModel.navigateTo(AppScreen.DOCUMENT_CHECKLIST) },
                        icon = { Icon(Icons.Default.Assignment, contentDescription = "Apply") },
                        label = {
                            Text(
                                text = if (language == "hi") "आवेदन" else "Apply",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("nav_applications")
                    )
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.ALERTS_SETTINGS,
                        onClick = { viewModel.navigateTo(AppScreen.ALERTS_SETTINGS) },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Alerts") },
                        label = {
                            Text(
                                text = if (language == "hi") "कार्य/अलर्ट" else "Alerts",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("nav_alerts")
                    )
                    NavigationBarItem(
                        selected = currentScreen in setOf(
                            AppScreen.PROFILE_SETUP,
                            AppScreen.LANGUAGE_SELECTION,
                            AppScreen.CONSENT,
                            AppScreen.WELCOME
                        ),
                        onClick = { viewModel.navigateTo(AppScreen.PROFILE_SETUP) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = {
                            Text(
                                text = if (language == "hi") "प्रोफ़ाइल" else "Settings",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Status / Notification Banner
                if (!statusBanner.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusBanner!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearBanner() }) {
                                Text("OK")
                            }
                        }
                    }
                }

                // Surface any Firestore error state transparently
                if (profileUiState is DataUiState.Error) {
                    val err = (profileUiState as DataUiState.Error).message
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cloud Sync Notice: $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                when (currentScreen) {
                    AppScreen.WELCOME -> WelcomeScreen(
                        language = language,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.LANGUAGE_SELECTION -> LanguageSelectionScreen(
                        language = language,
                        largeTextMode = largeTextMode,
                        onLanguageChange = viewModel::setLanguage,
                        onToggleLargeText = viewModel::toggleLargeTextMode,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.CONSENT -> ConsentScreen(
                        language = language,
                        consentGranted = editableProfile.consentGranted,
                        onUpdateConsent = viewModel::updateConsent,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.PROFILE_SETUP -> ProfileSetupScreen(
                        language = language,
                        profile = editableProfile,
                        decryptedName = decryptedName,
                        decryptedPhone = decryptedPhone,
                        decryptedDob = decryptedDob,
                        onUpdateProfile = viewModel::updateEditableProfile,
                        onUpdateDob = viewModel::updateDob,
                        onSaveToCloud = viewModel::saveCitizenProfileToCloud,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.HOME_DASHBOARD -> HomeDashboardScreen(
                        language = language,
                        profile = editableProfile,
                        decryptedName = decryptedName,
                        evaluations = evaluations,
                        onSelectScheme = viewModel::selectScheme,
                        onNavigate = viewModel::navigateTo,
                        onSpeakSummary = { text ->
                            viewModel.speakTextWithGeminiTts(text) { script ->
                                onSpeakAloud(script, language)
                            }
                        }
                    )
                    AppScreen.AI_CHAT -> AiChatScreen(
                        language = language,
                        messages = chatMessages,
                        selectedModelMode = selectedModelMode,
                        isGenerating = isChatGenerating,
                        onSelectModelMode = viewModel::selectModelMode,
                        onSendMessage = { msg -> viewModel.sendChatMessage(msg) },
                        onSpeakMessage = { text ->
                            viewModel.speakTextWithGeminiTts(text) { script ->
                                onSpeakAloud(script, language)
                            }
                        }
                    )
                    AppScreen.VOICE_ASSISTANT -> VoiceAssistantScreen(
                        language = language,
                        pendingVoiceAge = pendingVoiceAge,
                        voiceEchoPrompt = voiceEchoPrompt,
                        liveVoiceResponse = liveVoiceResponse,
                        lastSpokenTtsScript = lastSpokenTtsScript,
                        isProcessing = isLiveVoiceProcessing,
                        onProcessUtterance = { utterance ->
                            viewModel.processSpokenUtterance(utterance) { scriptToSpeak ->
                                onSpeakAloud(scriptToSpeak, language)
                            }
                        },
                        onConfirmVoiceAge = viewModel::confirmVoiceAge,
                        onSpeakAloud = { text ->
                            viewModel.speakTextWithGeminiTts(text) { script ->
                                onSpeakAloud(script, language)
                            }
                        }
                    )
                    AppScreen.SCHEME_RESULTS -> SchemeResultsScreen(
                        language = language,
                        evaluations = evaluations,
                        onSelectScheme = viewModel::selectScheme,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.SCHEME_DETAILS -> SchemeDetailsScreen(
                        language = language,
                        selectedSchemeId = selectedSchemeId,
                        evaluations = evaluations,
                        onSelectScheme = viewModel::selectScheme,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.ELIGIBILITY_RESULTS -> EligibilityResultsScreen(
                        language = language,
                        selectedSchemeId = selectedSchemeId,
                        evaluations = evaluations,
                        onSelectScheme = viewModel::selectScheme,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.DOCUMENT_CHECKLIST -> DocumentChecklistScreen(
                        language = language,
                        selectedSchemeId = selectedSchemeId,
                        documentStatuses = documentStatuses,
                        onSelectScheme = viewModel::selectScheme,
                        onUpdateDocStatus = viewModel::updateDocumentHaveStatus,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.APPLICATION_FORM -> ApplicationFormScreen(
                        language = language,
                        selectedSchemeId = selectedSchemeId,
                        applicantNameField = formApplicantName,
                        stateDistrictField = formStateDistrict,
                        bankSummaryField = formBankSummary,
                        onUpdateField = viewModel::updateFormField,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.REVIEW_CONFIRM -> ReviewConfirmScreen(
                        language = language,
                        selectedSchemeId = selectedSchemeId,
                        applicantNameField = formApplicantName,
                        stateDistrictField = formStateDistrict,
                        bankSummaryField = formBankSummary,
                        checklistSummary = viewModel.getCurrentChecklistSummaryForSelectedScheme(),
                        snapshotHash = viewModel.computeCurrentReviewSnapshotHash(),
                        uncertainAcknowledged = uncertainAck,
                        explicitCitizenConfirmed = explicitConfirm,
                        onToggleUncertainAck = viewModel::setUncertainFieldsAcknowledged,
                        onToggleExplicitConfirm = viewModel::setExplicitCitizenConfirmationChecked,
                        onConfirmAndGenerateHandoff = viewModel::confirmAndCreateHandoffApplication,
                        onNavigate = viewModel::navigateTo
                    )
                    AppScreen.APPLICATION_TRACKING -> ApplicationTrackingScreen(
                        language = language,
                        applications = applicationsList,
                        onUpdateStatus = viewModel::updateApplicationStatus,
                        onNavigate = viewModel::navigateTo,
                        activeRole = editableProfile.role
                    )
                    AppScreen.ALERTS_SETTINGS -> AlertsAndSettingsScreen(
                        language = language,
                        currentRole = editableProfile.role,
                        tasks = tasksList,
                        auditFindings = viewModel.auditFindings,
                        auditCsvContent = viewModel.buildAuditReportCsv(),
                        onCreateTask = viewModel::createCollaborativeTask,
                        onAdvanceTask = viewModel::advanceTaskStatus,
                        onCancelTask = viewModel::cancelTaskReminder,
                        onSignOut = {
                            signOutUser(credentialManager, scope)
                        },
                        darkThemeMode = darkThemeMode,
                        onToggleDarkTheme = viewModel::toggleDarkThemeMode,
                        onBackendDemoSignIn = viewModel::backendDemoSignIn,
                        onExportDpdpJson = {
                            viewModel.exportDpdpBundleJson { bundle ->
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "SchemeSaathi_DPDP_Export.json")
                                    putExtra(android.content.Intent.EXTRA_TEXT, bundle)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(shareIntent, "Export DPDP Data")
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
