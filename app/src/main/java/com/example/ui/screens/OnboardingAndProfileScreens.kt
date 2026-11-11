package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CitizenProfile
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.viewmodel.AppScreen

@Composable
fun AuthGateScreen(
    language: String,
    onLanguageChange: (String) -> Unit,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilterChip(
                selected = language == "hi",
                onClick = { onLanguageChange("hi") },
                label = { Text("हिंदी") },
                modifier = Modifier.testTag("auth_lang_hi")
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = language == "en",
                onClick = { onLanguageChange("en") },
                label = { Text("English") },
                modifier = Modifier.testTag("auth_lang_en")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Image(
            painter = painterResource(id = R.drawable.img_hero_welfare_1794371724484),
            contentDescription = "Scheme Saathi Welfare Hero Illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (language == "hi") "स्कीम साथी (Scheme Saathi)" else "Scheme Saathi",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (language == "hi") {
                "सरकारी कल्याण योजना पात्रता इंजन, Gemini AI सहायक, वॉइस गाइड और सहयोगात्मक कार्य प्रबंधन प्लेटफॉर्म"
            } else {
                "Deterministic Government Welfare Scheme Assistant, Gemini AI Guide & Collaborative Task Platform"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "hi") "नागरिक सुरक्षा और गोपनीयता गारंटी" else "Citizen Privacy & Safety Guarantees",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (language == "hi") {
                        "• कोई आधार नंबर (Aadhaar) कभी नहीं मांगा या सहेजा जाता है।\n" +
                            "• पात्रता का निर्णय केवल नियम इंजन (Deterministic Rule Engine) करता है, AI नहीं।\n" +
                            "• कोई भी सरकारी फॉर्म बिना आपकी स्पष्ट पुष्टि के जमा नहीं होता।"
                    } else {
                        "• No Aadhaar number is ever collected or stored.\n" +
                            "• Eligibility is decided strictly by the Deterministic Rule Engine, never by AI.\n" +
                            "• Applications are never auto-submitted; explicit citizen review is mandatory."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        GoogleSignInButton(
            language = language,
            onAuthSuccess = onAuthSuccess,
            onAuthError = { errorMessage = it }
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    language: String,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_welfare_1794371724484),
            contentDescription = "Scheme Saathi Welcome Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (language == "hi") "स्वागत है – स्कीम साथी" else "Welcome to Scheme Saathi",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (language == "hi") {
                "यह ऐप भारतीय नागरिकों को 5 प्रमुख सरकारी कल्याण योजनाओं (PM-KISAN, NSAP-IGNOAPS, PMUY, PM-JAY, APY) की पात्रता जांचने, दस्तावेज़ तैयार करने और जन सेवा केंद्र (CSC) हैंडऑफ पैक बनाने में मदद करता है।"
            } else {
                "Empowering Indian citizens to verify eligibility across 5 flagship welfare schemes (PM-KISAN, NSAP-IGNOAPS, PMUY, PM-JAY, APY), analyze documents with Gemini AI, and prepare CSC handoff packs."
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onNavigate(AppScreen.LANGUAGE_SELECTION) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_next_language_btn")
        ) {
            Text(if (language == "hi") "अगला: भाषा और टेक्स्ट आकार चुनें →" else "Next: Language & Accessibility →")
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onNavigate(AppScreen.HOME_DASHBOARD) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_home_btn")
        ) {
            Text(if (language == "hi") "मुख्य डैशबोर्ड पर जाएं" else "Go to Home Dashboard")
        }
    }
}

@Composable
fun LanguageSelectionScreen(
    language: String,
    largeTextMode: Boolean,
    onLanguageChange: (String) -> Unit,
    onToggleLargeText: (Boolean) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val comingSoonLanguages = listOf(
        "मराठी (Marathi)",
        "বাংলা (Bengali)",
        "தமிழ் (Tamil)",
        "తెలుగు (Telugu)",
        "ગુજરાતી (Gujarati)",
        "ಕನ್ನಡ (Kannada)"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "भाषा और पठनीयता सेटिंग्स" else "Language & Accessibility Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "hi") "सक्रिय भाषा चुनें (Active Language)" else "Select Active Language",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onLanguageChange("hi") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (language == "hi") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (language == "hi") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("select_hindi_btn")
                    ) {
                        Text("हिंदी (Hindi)")
                    }
                    Button(
                        onClick = { onLanguageChange("en") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (language == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (language == "en") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("select_english_btn")
                    ) {
                        Text("English")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == "hi") "बड़ा अक्षर मोड (22sp Large Text)" else "Large Text Mode (22sp vs 18sp)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = if (language == "hi") "वरिष्ठ नागरिकों के लिए बड़े और स्पष्ट अक्षर" else "High-legibility 22sp font size for senior citizens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = largeTextMode,
                    onCheckedChange = onToggleLargeText,
                    modifier = Modifier.testTag("large_text_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (language == "hi") "अन्य भारतीय भाषाएं (जल्द आ रही हैं / Coming Soon):" else "Other Indian Languages (Coming Soon):",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        comingSoonLanguages.forEach { langLabel ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(langLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (language == "hi") "जल्द आ रहा है" else "Coming Soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onNavigate(AppScreen.CONSENT) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("lang_continue_consent_btn")
        ) {
            Text(if (language == "hi") "अगला: गोपनीयता सहमति (Consent) →" else "Next: Privacy & Consent →")
        }
    }
}

@Composable
fun ConsentScreen(
    language: String,
    consentGranted: Boolean,
    onUpdateConsent: (Boolean) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "गोपनीयता सहमति और डेटा सुरक्षा" else "Privacy Consent & Data Protection",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "hi") "डेटा सुरक्षा नियम (AES-256-GCM)" else "Data Protection Rules (AES-256-GCM)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (language == "hi") {
                        "1. आपका नाम और फ़ोन नंबर AES-256-GCM एन्क्रिप्शन के साथ सुरक्षित रखा जाता है।\n" +
                            "2. आधार नंबर (Aadhaar) कभी नहीं लिया जाता और न ही संग्रहीत किया जाता है।\n" +
                            "3. Gemini AI को भेजने से पहले व्यक्तिगत पहचान (PII) स्वतः हटा (Redact) दी जाती है।\n" +
                            "4. आप किसी भी समय अपनी सहमति वापस ले सकते हैं और अपना डेटा हटा सकते हैं।"
                    } else {
                        "1. Your Name and Phone are encrypted client-side with AES-256-GCM before cloud storage.\n" +
                            "2. No Aadhaar number is ever requested or stored.\n" +
                            "3. Personal identifiers (PII) are automatically redacted before sending context to Gemini AI.\n" +
                            "4. You can withdraw consent and purge your cloud profile at any time."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == "hi") {
                            if (consentGranted) "स्थिति: सहमति प्रदान की गई है ✅" else "स्थिति: सहमति लंबित / वापस ली गई ⚠️"
                        } else {
                            if (consentGranted) "Status: Consent Granted ✅" else "Status: Consent Pending / Withdrawn ⚠️"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == "hi") "पात्रता गणना हेतु प्रोफ़ाइल सहेजने की अनुमति" else "Permission to store encrypted profile for eligibility calculation",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = consentGranted,
                    onCheckedChange = onUpdateConsent,
                    modifier = Modifier.testTag("consent_toggle_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onUpdateConsent(true)
                    onNavigate(AppScreen.PROFILE_SETUP)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("grant_consent_continue_btn")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (language == "hi") "सहमति दें और आगे बढ़ें" else "Grant Consent & Continue")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { onUpdateConsent(false) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("withdraw_consent_delete_btn")
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (language == "hi") "सहमति वापस लें और क्लाउड प्रोफ़ाइल हटाएं" else "Withdraw Consent & Delete Cloud Profile")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    language: String,
    profile: CitizenProfile,
    decryptedName: String,
    decryptedPhone: String,
    onUpdateProfile: (String, String, (CitizenProfile) -> CitizenProfile) -> Unit,
    onSaveToCloud: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    var birthYearInput by remember(profile.birthYear) { mutableStateOf(profile.birthYear.toString()) }
    var incomeInput by remember(profile.annualIncome) {
        mutableStateOf(if (profile.annualIncome >= 0) profile.annualIncome.toString() else "")
    }
    var familySizeInput by remember(profile.familySize) { mutableStateOf(profile.familySize.toString()) }

    val statesList = listOf("Uttar Pradesh", "Bihar", "Maharashtra", "Madhya Pradesh", "Rajasthan", "Delhi")
    val rolesList = listOf("CITIZEN", "CSC_OPERATOR", "SUPERVISOR", "REVIEWER")

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "नागरिक प्रोफ़ाइल सेटअप" else "Citizen Profile Setup",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (language == "hi") {
                "अपूर्ण जानकारी होने पर स्थिति 'अज्ञात' (UNKNOWN) रहती है, अपात्र नहीं। नाम व फ़ोन AES-256-GCM एन्क्रिप्टेड हैं।"
            } else {
                "Missing fields evaluate to UNKNOWN, never automatically NOT_ELIGIBLE. Name & Phone are AES-256-GCM encrypted."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = decryptedName,
            onValueChange = { newName -> onUpdateProfile(newName, decryptedPhone) { it } },
            label = { Text(if (language == "hi") "पूरा नाम (AES-256-GCM एन्क्रिप्टेड)" else "Full Name (AES-256-GCM Encrypted)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_name_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = decryptedPhone,
            onValueChange = { newPhone -> onUpdateProfile(decryptedName, newPhone) { it } },
            label = { Text(if (language == "hi") "मोबाइल नंबर (10 अंक - आधार वर्जित)" else "Mobile Number (10 digits - No Aadhaar)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_phone_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = birthYearInput,
                onValueChange = { yrStr ->
                    birthYearInput = yrStr
                    val yr = yrStr.toIntOrNull()
                    if (yr != null && yr in 1910..2026) {
                        onUpdateProfile(decryptedName, decryptedPhone) {
                            it.copy(birthYear = yr, ageConfirmedViaVoice = true)
                        }
                    }
                },
                label = { Text(if (language == "hi") "जन्म वर्ष (उम्र: ${2026 - profile.birthYear})" else "Birth Year (Age: ${2026 - profile.birthYear})") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("profile_birth_year_input")
            )

            OutlinedTextField(
                value = incomeInput,
                onValueChange = { incStr ->
                    incomeInput = incStr
                    val inc = incStr.toIntOrNull() ?: -1
                    onUpdateProfile(decryptedName, decryptedPhone) {
                        it.copy(annualIncome = inc)
                    }
                },
                label = { Text(if (language == "hi") "वार्षिक आय (₹)" else "Annual Income (₹)") },
                placeholder = { Text("Blank = UNKNOWN") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("profile_income_input")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (language == "hi") "राज्य (State - राज्य ओवरराइड नियम लागू):" else "State (Triggers State Override Rules):",
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            statesList.forEach { st ->
                FilterChip(
                    selected = profile.state == st,
                    onClick = {
                        onUpdateProfile(decryptedName, decryptedPhone) { it.copy(state = st) }
                    },
                    label = { Text(st) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = profile.district,
                onValueChange = { dist ->
                    onUpdateProfile(decryptedName, decryptedPhone) { it.copy(district = dist) }
                },
                label = { Text(if (language == "hi") "ज़िला (District)" else "District") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = familySizeInput,
                onValueChange = { fs ->
                    familySizeInput = fs
                    val count = fs.toIntOrNull() ?: 4
                    onUpdateProfile(decryptedName, decryptedPhone) { it.copy(familySize = count.coerceIn(1, 30)) }
                },
                label = { Text(if (language == "hi") "परिवार सदस्य" else "Family Size") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        TriStateChipSelector(
            title = if (language == "hi") "लिंग (Gender)" else "Gender",
            options = listOf("female" to "Female / महिला", "male" to "Male / पुरुष", "other" to "Other / अन्य", "unknown" to "Unknown"),
            selected = profile.gender,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(gender = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "निवास क्षेत्र (Residence)" else "Residence Type",
            options = listOf("rural" to "Rural / ग्रामीण", "urban" to "Urban / शहरी", "unknown" to "Unknown"),
            selected = profile.residenceType,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(residenceType = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "बीपीएल कार्ड (BPL Status)" else "Below Poverty Line (BPL) Status",
            options = listOf("yes" to "Yes / हाँ", "no" to "No / नहीं", "unknown" to "Unknown / अज्ञात"),
            selected = profile.bplStatus,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(bplStatus = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "सामाजिक वर्ग (Social Category)" else "Social Category",
            options = listOf("general" to "General", "obc" to "OBC", "sc" to "SC", "st" to "ST", "unknown" to "Unknown"),
            selected = profile.socialCategory,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(socialCategory = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "कृषि भूमि स्वामित्व (Agricultural Land)" else "Owns Cultivable Agricultural Land",
            options = listOf("yes" to "Yes / हाँ", "no" to "No / नहीं", "unknown" to "Unknown / अज्ञात"),
            selected = profile.ownsAgriculturalLand,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(ownsAgriculturalLand = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "पहले से एलपीजी गैस कनेक्शन (Existing LPG)" else "Existing LPG Connection in Household",
            options = listOf("yes" to "Yes / हाँ", "no" to "No / नहीं", "unknown" to "Unknown / अज्ञात"),
            selected = profile.hasLpgConnection,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(hasLpgConnection = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "बैंक खाता उपलब्धता (Bank Account)" else "Bank Account Available",
            options = listOf("yes" to "Yes / हाँ", "no" to "No / नहीं", "unknown" to "Unknown"),
            selected = profile.hasBankAccount,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(hasBankAccount = v) } }
        )

        TriStateChipSelector(
            title = if (language == "hi") "आयकर दाता (Income Tax Payer)" else "Income Tax Payer",
            options = listOf("no" to "No / नहीं", "yes" to "Yes / हाँ", "unknown" to "Unknown"),
            selected = profile.isIncomeTaxPayer,
            onSelect = { v -> onUpdateProfile(decryptedName, decryptedPhone) { it.copy(isIncomeTaxPayer = v) } }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        Text(
            text = if (language == "hi") "सहयोगात्मक भूमिका (RBAC Role):" else "Collaborative Platform Role (RBAC):",
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rolesList.forEach { roleOption ->
                FilterChip(
                    selected = profile.role == roleOption,
                    onClick = {
                        onUpdateProfile(decryptedName, decryptedPhone) { it.copy(role = roleOption) }
                    },
                    label = { Text(roleOption) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onSaveToCloud()
                onNavigate(AppScreen.ELIGIBILITY_RESULTS)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("save_profile_check_eligibility_btn")
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (language == "hi") {
                    "प्रोफ़ाइल सहेजें और पात्रता परिणाम देखें →"
                } else {
                    "Save Encrypted Profile & Check Eligibility →"
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriStateChipSelector(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}
