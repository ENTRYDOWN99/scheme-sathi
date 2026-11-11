package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CitizenProfile
import com.example.data.model.EligibilityEvaluationResult
import com.example.data.model.EligibilityOutcome
import com.example.domain.EligibilityEngine
import com.example.ui.theme.EligibleGreen
import com.example.ui.theme.NotEligibleRed
import com.example.ui.theme.UnknownAmber
import com.example.ui.viewmodel.AppScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDashboardScreen(
    language: String,
    profile: CitizenProfile,
    decryptedName: String,
    evaluations: List<EligibilityEvaluationResult>,
    onSelectScheme: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onSpeakSummary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val eligibleCount = evaluations.count { it.outcome == EligibilityOutcome.ELIGIBLE }
    val unknownCount = evaluations.count { it.outcome == EligibilityOutcome.UNKNOWN }
    val notEligibleCount = evaluations.count { it.outcome == EligibilityOutcome.NOT_ELIGIBLE }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_welfare_1794371724484),
                    contentDescription = "Scheme Saathi Hero Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (language == "hi") "नमस्ते, $decryptedName" else "Namaste, $decryptedName",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${profile.state} • ${profile.district} • Role: ${profile.role}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val text = if (language == "hi") {
                                    "नमस्ते $decryptedName, आप 5 में से $eligibleCount योजनाओं के लिए पात्र हैं और $unknownCount योजनाओं में अतिरिक्त जानकारी आवश्यक है।"
                                } else {
                                    "Hello $decryptedName, you are eligible for $eligibleCount out of 5 schemes, and $unknownCount schemes need additional verification."
                                }
                                onSpeakSummary(text)
                            },
                            modifier = Modifier.testTag("home_tts_speak_btn")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Speak Summary")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "hi") "सुनें (TTS)" else "Speak (TTS)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Deterministic Eligibility Summary Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatCard(
                            label = if (language == "hi") "पात्र (ELIGIBLE)" else "ELIGIBLE",
                            count = eligibleCount,
                            badgeColor = EligibleGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatCard(
                            label = if (language == "hi") "अज्ञात (UNKNOWN)" else "UNKNOWN",
                            count = unknownCount,
                            badgeColor = UnknownAmber,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatCard(
                            label = if (language == "hi") "अपात्र (NOT)" else "NOT ELIGIBLE",
                            count = notEligibleCount,
                            badgeColor = NotEligibleRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary AI & Voice Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onNavigate(AppScreen.AI_CHAT) },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("home_open_ai_chat_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (language == "hi") "AI चैट सहायक" else "Gemini AI Chat")
            }

            Button(
                onClick = { onNavigate(AppScreen.VOICE_ASSISTANT) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("home_open_voice_btn")
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (language == "hi") "बोलकर पूछें (Live)" else "Voice Live")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // All 15 Screens Quick Directory
        Text(
            text = if (language == "hi") "सभी 15 चरण और सुविधाएं (Complete Workflow)" else "All 15 Screens & Workflow Hub",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickLinks = listOf(
                AppScreen.WELCOME to (if (language == "hi") "1. स्वागत" else "1. Welcome"),
                AppScreen.LANGUAGE_SELECTION to (if (language == "hi") "2. भाषा/22sp" else "2. Language"),
                AppScreen.CONSENT to (if (language == "hi") "4. सहमति" else "4. Consent"),
                AppScreen.PROFILE_SETUP to (if (language == "hi") "5. प्रोफ़ाइल" else "5. Profile"),
                AppScreen.AI_CHAT to (if (language == "hi") "6. AI चैट" else "6. AI Chat"),
                AppScreen.VOICE_ASSISTANT to (if (language == "hi") "7. वॉइस असिस्टेंट" else "7. Voice Live"),
                AppScreen.SCHEME_RESULTS to (if (language == "hi") "8. योजनाएं (5)" else "8. Schemes (5)"),
                AppScreen.SCHEME_DETAILS to (if (language == "hi") "9. योजना विवरण" else "9. Scheme Details"),
                AppScreen.ELIGIBILITY_RESULTS to (if (language == "hi") "10. नियम इंजन" else "10. Eligibility"),
                AppScreen.DOCUMENT_CHECKLIST to (if (language == "hi") "11. दस्तावेज़ स्कैनर" else "11. Docs & Vision"),
                AppScreen.APPLICATION_FORM to (if (language == "hi") "12. आवेदन फॉर्म" else "12. App Form"),
                AppScreen.REVIEW_CONFIRM to (if (language == "hi") "13. समीक्षा व पुष्टि" else "13. Human Gate"),
                AppScreen.APPLICATION_TRACKING to (if (language == "hi") "14. स्थिति ट्रैकिंग" else "14. Tracking"),
                AppScreen.ALERTS_SETTINGS to (if (language == "hi") "15. कार्य, अलर्ट व ऑडिट" else "15. Tasks & Audit")
            )
            quickLinks.forEach { (screen, label) ->
                AssistChip(
                    onClick = { onNavigate(screen) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live 5 Government Schemes Cards
        Text(
            text = if (language == "hi") "5 सरकारी कल्याण योजनाएं (Deterministic Evaluation)" else "5 Government Welfare Schemes (Deterministic Engine)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        EligibilityEngine.initialFiveSchemes.forEach { scheme ->
            val eval = evaluations.find { it.schemeId == scheme.id }
            val outcome = eval?.outcome ?: EligibilityOutcome.UNKNOWN
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable {
                        onSelectScheme(scheme.id)
                        onNavigate(AppScreen.SCHEME_DETAILS)
                    }
                    .testTag("scheme_card_${scheme.code}"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "hi") scheme.nameHi else scheme.nameEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutcomePill(outcome = outcome, language = language)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (language == "hi") scheme.benefitSummaryHi else scheme.benefitSummaryEn,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (eval?.stateOverrideApplied != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚡ ${eval.stateOverrideApplied}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSelectScheme(scheme.id)
                                onNavigate(AppScreen.ELIGIBILITY_RESULTS)
                            }
                        ) {
                            Icon(Icons.Default.Rule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "hi") "पात्रता कारण" else "Why?")
                        }
                        Button(
                            onClick = {
                                onSelectScheme(scheme.id)
                                onNavigate(AppScreen.DOCUMENT_CHECKLIST)
                            }
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "hi") "दस्तावेज़ व फॉर्म" else "Prepare Form")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryStatCard(
    label: String,
    count: Int,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = badgeColor.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = badgeColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun OutcomePill(
    outcome: EligibilityOutcome,
    language: String
) {
    val (bg, label) = when (outcome) {
        EligibilityOutcome.ELIGIBLE -> EligibleGreen to (if (language == "hi") "पात्र (ELIGIBLE)" else "ELIGIBLE")
        EligibilityOutcome.NOT_ELIGIBLE -> NotEligibleRed to (if (language == "hi") "अपात्र (NOT_ELIGIBLE)" else "NOT_ELIGIBLE")
        EligibilityOutcome.UNKNOWN -> UnknownAmber to (if (language == "hi") "अज्ञात (UNKNOWN)" else "UNKNOWN")
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SchemeResultsScreen(
    language: String,
    evaluations: List<EligibilityEvaluationResult>,
    onSelectScheme: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSchemes = EligibilityEngine.initialFiveSchemes.filter { scheme ->
        searchQuery.isBlank() ||
            scheme.nameEn.contains(searchQuery, ignoreCase = true) ||
            scheme.nameHi.contains(searchQuery, ignoreCase = true) ||
            scheme.code.contains(searchQuery, ignoreCase = true) ||
            scheme.category.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "8. सरकारी योजना खोज परिणाम (5 योजनाएं)" else "8. Government Scheme Discovery (5 Schemes)",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text(if (language == "hi") "योजना खोजें (PM-KISAN, पेंशन, उज्ज्वला...)" else "Search schemes (PM-KISAN, Pension, LPG...)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scheme_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        filteredSchemes.forEach { scheme ->
            val eval = evaluations.find { it.schemeId == scheme.id }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${scheme.code} • ${scheme.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        OutcomePill(
                            outcome = eval?.outcome ?: EligibilityOutcome.UNKNOWN,
                            language = language
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (language == "hi") scheme.nameHi else scheme.nameEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (language == "hi") scheme.descriptionHi else scheme.descriptionEn,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                onSelectScheme(scheme.id)
                                onNavigate(AppScreen.SCHEME_DETAILS)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (language == "hi") "पूरा विवरण" else "View Details")
                        }
                        Button(
                            onClick = {
                                onSelectScheme(scheme.id)
                                onNavigate(AppScreen.ELIGIBILITY_RESULTS)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (language == "hi") "पात्रता जांचें" else "Rule Breakdown")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchemeDetailsScreen(
    language: String,
    selectedSchemeId: String,
    evaluations: List<EligibilityEvaluationResult>,
    onSelectScheme: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = EligibilityEngine.getSchemeById(selectedSchemeId) ?: EligibilityEngine.initialFiveSchemes.first()
    val eval = evaluations.find { it.schemeId == scheme.id }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Scheme Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EligibilityEngine.initialFiveSchemes.forEach { item ->
                AssistChip(
                    onClick = { onSelectScheme(item.id) },
                    label = { Text(item.code) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (language == "hi") "9. ${scheme.nameHi}" else "9. ${scheme.nameEn}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))
        OutcomePill(outcome = eval?.outcome ?: EligibilityOutcome.UNKNOWN, language = language)

        Spacer(modifier = Modifier.height(12.dp))

        // Illustrative & Stale Rule Warning Banner (Rule 15 & Stage 3/6 requirement)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == "hi") {
                        "सूचना: नियम संस्करण (${scheme.ruleVersion}) की सीमाएं उदाहरणार्थ (Illustrative) हैं। अंतिम पात्रता के लिए आधिकारिक पोर्टल (${scheme.officialSourceUrl}) पर सत्यापित करें।"
                    } else {
                        "Notice: Rule thresholds (${scheme.ruleVersion}, verified ${scheme.verificationDate}) are illustrative until verified against official government gazettes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (language == "hi") "योजना लाभ (Benefits)" else "Scheme Benefits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (language == "hi") scheme.benefitSummaryHi else scheme.benefitSummaryEn,
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text(
                    text = if (language == "hi") "आधिकारिक स्रोत (Official Source)" else "Official Source & Verification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = scheme.officialSourceTitle, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "URL: ${scheme.officialSourceUrl} • Rule Version: ${scheme.ruleVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme.officialSourceUrl))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (language == "hi") "आधिकारिक पोर्टल खोलें" else "Open Official Portal")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onNavigate(AppScreen.ELIGIBILITY_RESULTS) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(if (language == "hi") "10. पात्रता नियम देखें" else "10. Rule Breakdown")
            }
            Button(
                onClick = { onNavigate(AppScreen.DOCUMENT_CHECKLIST) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("scheme_details_to_checklist_btn")
            ) {
                Text(if (language == "hi") "11. दस्तावेज़ जांचें →" else "11. Document Checklist →")
            }
        }
    }
}

@Composable
fun EligibilityResultsScreen(
    language: String,
    selectedSchemeId: String,
    evaluations: List<EligibilityEvaluationResult>,
    onSelectScheme: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = EligibilityEngine.getSchemeById(selectedSchemeId) ?: EligibilityEngine.initialFiveSchemes.first()
    val eval = evaluations.find { it.schemeId == scheme.id } ?: evaluations.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "10. नियम इंजन पात्रता विश्लेषण (Deterministic Engine)" else "10. Deterministic Eligibility Engine Breakdown",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EligibilityEngine.initialFiveSchemes.forEach { item ->
                AssistChip(
                    onClick = { onSelectScheme(item.id) },
                    label = { Text(item.code) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == "hi") scheme.nameHi else scheme.nameEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutcomePill(outcome = eval.outcome, language = language)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Rule Version: ${eval.ruleVersion} • Synchronous Pure Evaluator",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (eval.stateOverrideApplied != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚡ ${eval.stateOverrideApplied}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = if (language == "hi") "शर्त-दर-शर्त मूल्यांकन (Condition Results):" else "Condition-by-Condition Evaluation:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                eval.conditionResults.forEach { cond ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val icon = when (cond.outcome) {
                            EligibilityOutcome.ELIGIBLE -> Icons.Default.CheckCircle
                            EligibilityOutcome.NOT_ELIGIBLE -> Icons.Default.Cancel
                            EligibilityOutcome.UNKNOWN -> Icons.Default.HelpOutline
                        }
                        val tint = when (cond.outcome) {
                            EligibilityOutcome.ELIGIBLE -> EligibleGreen
                            EligibilityOutcome.NOT_ELIGIBLE -> NotEligibleRed
                            EligibilityOutcome.UNKNOWN -> UnknownAmber
                        }
                        Icon(icon, contentDescription = cond.outcome.name, tint = tint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (language == "hi") cond.reasonHi else cond.reasonEn,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Field: ${cond.field} • Status: ${cond.outcome.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (eval.externalLookupGuidanceEn.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text(
                        text = if (language == "hi") "🔍 बाहरी पोर्टल सत्यापन मार्गदर्शन:" else "🔍 External Beneficiary Lookup Guidance:",
                        style = MaterialTheme.typography.titleMedium,
                        color = UnknownAmber
                    )
                    val guidanceList = if (language == "hi") eval.externalLookupGuidanceHi else eval.externalLookupGuidanceEn
                    guidanceList.forEach { g ->
                        Text(text = "• $g", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onNavigate(AppScreen.PROFILE_SETUP) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (language == "hi") "प्रोफ़ाइल संपादित करें" else "Edit Profile")
            }
            Button(
                onClick = { onNavigate(AppScreen.DOCUMENT_CHECKLIST) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("eligibility_to_docs_btn")
            ) {
                Icon(Icons.Default.Assignment, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (language == "hi") "दस्तावेज़ और आवेदन →" else "Documents & Form →")
            }
        }
    }
}
