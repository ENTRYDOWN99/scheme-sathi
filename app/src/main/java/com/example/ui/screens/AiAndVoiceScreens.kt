package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.GeminiModelMode
import com.example.data.model.ChatMessage
import com.example.ui.theme.EligibleGreen
import com.example.ui.theme.NotEligibleRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiChatScreen(
    language: String,
    messages: List<ChatMessage>,
    selectedModelMode: GeminiModelMode,
    isGenerating: Boolean,
    onSelectModelMode: (GeminiModelMode) -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    val chatModes = listOf(
        GeminiModelMode.STANDARD_FLASH,
        GeminiModelMode.FAST_LITE,
        GeminiModelMode.HIGH_THINKING_CARBON
    )

    val samplePrompts = if (language == "hi") {
        listOf(
            "वृद्धावस्था पेंशन (NSAP-IGNOAPS) के लिए मेरी पात्रता और कारण बताएं",
            "मुझे PM-KISAN के लिए कौन से दस्तावेज़ चाहिए?",
            "उज्ज्वला योजना (PMUY) और आयुष्मान भारत (PM-JAY) में मेरी पात्रता क्या है?",
            "स्वचालित आवेदन का प्रयास करें (मना करेगा)"
        )
    } else {
        listOf(
            "Explain my eligibility for NSAP-IGNOAPS Old Age Pension",
            "What documents do I need for PM-KISAN and what are alternatives?",
            "Compare my eligibility across all 5 schemes using deep thinking",
            "Try: ask it to auto-submit (it will refuse)"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = if (language == "hi") "Gemini AI चैट सहायक" else "Gemini AI Chat Assistant",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Model Switcher: Standard, Fast Answers, Deep Thinking, Document & Vision
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chatModes.forEach { mode ->
                FilterChip(
                    selected = selectedModelMode == mode,
                    onClick = { onSelectModelMode(mode) },
                    leadingIcon = {
                        when (mode) {
                            GeminiModelMode.FAST_LITE -> Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            GeminiModelMode.HIGH_THINKING_CARBON -> Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            else -> null
                        }
                    },
                    label = {
                        Text(
                            text = if (language == "hi") mode.labelHi else mode.labelEn,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.testTag("chat_model_${mode.name.lowercase()}")
                )
            }
        }

        // Quick prompt chips (scrollable horizontally without truncation)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            samplePrompts.forEach { sample ->
                AssistChip(
                    onClick = { onSendMessage(sample) },
                    label = {
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Scrollable message thread
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_messages_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUser) {
                                        if (language == "hi") "आप (Citizen)" else "You"
                                    } else {
                                        "Scheme Saathi • ${msg.modelUsed}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                if (!isUser) {
                                    IconButton(
                                        onClick = { onSpeakMessage(msg.content) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("tts_msg_${msg.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Read aloud with gemini-3.8-flash-tts",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }

                            if (com.example.BuildConfig.DEBUG && msg.toolCallsSummary.isNotEmpty() && !isUser) {
                                Text(
                                    text = "🔧 Tools: ${msg.toolCallsSummary.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        if (language == "hi") {
                            "हिंदी या अंग्रेज़ी में अपना प्रश्न लिखें..."
                        } else {
                            "Ask in Hindi or English..."
                        }
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = !isGenerating && inputText.isNotBlank(),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("chat_send_btn")
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAssistantScreen(
    language: String,
    pendingVoiceAge: Int?,
    voiceEchoPrompt: String?,
    liveVoiceResponse: String?,
    lastSpokenTtsScript: String?,
    isProcessing: Boolean,
    onProcessUtterance: (String) -> Unit,
    onConfirmVoiceAge: (Boolean) -> Unit,
    onSpeakAloud: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var manualTranscript by remember { mutableStateOf("") }
    var isListeningMic by remember { mutableStateOf(false) }
    var micStatusMessage by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && speechRecognizer != null) {
            isListeningMic = true
            micStatusMessage = if (language == "hi") "सुन रहा हूँ... कृपया बोलें" else "Listening... Speak now"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "hi") "hi-IN" else "en-IN")
            }
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListeningMic = false
                }
                override fun onError(error: Int) {
                    isListeningMic = false
                    micStatusMessage = if (language == "hi") {
                        "माइक्रोफ़ोन पहचान पूर्ण नहीं हुई (त्रुटि कोड $error)। नीचे दिए गए वॉइस बटन या टेक्स्ट बॉक्स का उपयोग करें।"
                    } else {
                        "Speech recognition finished (code $error). You can also tap the quick spoken phrases below."
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListeningMic = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val first = matches?.firstOrNull()
                    if (!first.isNullOrBlank()) {
                        manualTranscript = first
                        onProcessUtterance(first)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        } else {
            micStatusMessage = if (language == "hi") {
                "माइक्रोफ़ोन अनुमति उपलब्ध नहीं है; आप नीचे हिंदी संख्या व वॉइस सिमुलेशन बटन का उपयोग कर सकते हैं।"
            } else {
                "Microphone permission not granted or emulator lacks audio input; use the spoken numeral buttons below."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") {
                "लाइव वॉइस असिस्टेंट"
            } else {
                "Live Voice Assistant"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (language == "hi") {
                "हिंदी और अंग्रेज़ी वॉइस पहचान, हिंदी संख्या पार्सिंग (बासठ = 62, बयासठ = 62) और अनिवार्य 'हाँ/नहीं' पुष्टि गेट।"
            } else {
                "Real-time voice conversation with gemini-3.8-live, Hindi numeral parsing (बासठ = 62, बयासठ = 62), and mandatory Echo-Back Confirmation."
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Mandatory Echo-Back Confirmation Card when spoken age is detected
        if (voiceEchoPrompt != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_echo_confirmation_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == "hi") "अनिवार्य वॉइस पुष्टि (Echo-Back Gate)" else "Mandatory Voice Echo-Back Confirmation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = voiceEchoPrompt,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (language == "hi") {
                            "नियम: बिना 'हाँ' पुष्टि के वॉइस से प्राप्त उम्र का उपयोग अंतिम पात्रता निर्णय के लिए नहीं किया जाता (तब तक स्थिति UNKNOWN रहती है)।"
                        } else {
                            "Rule: Unconfirmed voice age cannot be used for final eligibility (evaluates to UNKNOWN until confirmed)."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (pendingVoiceAge != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onConfirmVoiceAge(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = EligibleGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("voice_confirm_haan_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("हाँ, सही है (Yes, $pendingVoiceAge)")
                            }
                            Button(
                                onClick = { onConfirmVoiceAge(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = NotEligibleRed),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("voice_confirm_nahi_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("नहीं (No)")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Microphone Action Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListeningMic) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_voice_mic_btn")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Microphone")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isListeningMic) {
                            if (language == "hi") "सुन रहा हूँ... (hi-IN)" else "Listening... (en-IN)"
                        } else {
                            if (language == "hi") "माइक्रोफ़ोन से बोलें (hi-IN / en-IN)" else "Tap to Speak with Microphone (hi-IN / en-IN)"
                        }
                    )
                }

                if (micStatusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = micStatusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Spoken Hindi Numeral & Live Voice Test Chips
        Text(
            text = if (language == "hi") "त्वरित वॉइस वाक्यांश परीक्षण (बासठ = 62, बयासठ = 62):" else "Quick Spoken Utterance & Hindi Numeral Tests:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val testUtterances = listOf(
                "मेरी उम्र बासठ साल है (बासठ = 62)" to "मेरी उम्र बासठ साल है",
                "बयासठ साल (बयासठ = 62)" to "बयासठ",
                "उम्र उनसठ साल (उनसठ = 59)" to "मेरी उम्र उनसठ साल है",
                "मेरे लिए कौन सी पेंशन और किसान योजना उपलब्ध है?" to "मेरे लिए कौन सी पेंशन और किसान योजना उपलब्ध है?",
                "Tell me in live voice about PM Ujjwala Yojana" to "Tell me in live voice about PM Ujjwala Yojana"
            )
            testUtterances.forEach { (label, phrase) ->
                AssistChip(
                    onClick = {
                        manualTranscript = phrase
                        onProcessUtterance(phrase)
                    },
                    label = { Text(label) },
                    modifier = Modifier.testTag("voice_phrase_${phrase.hashCode()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Manual / Spoken Transcript Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualTranscript,
                onValueChange = { manualTranscript = it },
                label = {
                    Text(
                        if (language == "hi") {
                            "बोला गया वाक्य (जैसे: मेरी उम्र बासठ साल है)"
                        } else {
                            "Spoken transcript (e.g., मेरी उम्र बासठ साल है)"
                        }
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("voice_transcript_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onProcessUtterance(manualTranscript) },
                enabled = !isProcessing && manualTranscript.isNotBlank(),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("voice_submit_utterance_btn")
            ) {
                Text(if (language == "hi") "भेजें" else "Send")
            }
        }

        if (isProcessing) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (language == "hi") "gemini-3.8-live वॉइस उत्तर तैयार कर रहा है..." else "gemini-3.8-live generating real-time voice response...")
            }
        }

        if (!liveVoiceResponse.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎙️ gemini-3.8-live Response",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { onSpeakAloud(liveVoiceResponse) }
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TTS")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = liveVoiceResponse,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!lastSpokenTtsScript.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🔊 gemini-3.8-flash-tts Audio Script: $lastSpokenTtsScript",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
