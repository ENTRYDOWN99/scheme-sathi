package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AuditFinding
import com.example.data.model.CollaborativeTask
import com.example.data.model.DocumentHaveStatus
import com.example.data.model.SchemeApplication
import com.example.domain.EligibilityEngine
import com.example.ui.theme.EligibleGreen
import com.example.ui.theme.NotEligibleRed
import com.example.ui.theme.UnknownAmber
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.FormFieldProvenance

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentChecklistScreen(
    language: String,
    selectedSchemeId: String,
    documentStatuses: Map<String, DocumentHaveStatus>,
    onSelectScheme: (String) -> Unit,
    onUpdateDocStatus: (String, DocumentHaveStatus) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = EligibilityEngine.getSchemeById(selectedSchemeId) ?: EligibilityEngine.initialFiveSchemes.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "दस्तावेज़ जांच सूची" else "Required Documents Checklist",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (language == "hi") {
                "योजना हेतु आवश्यक दस्तावेज़ों की स्थिति चुनें (मेरे पास है / नहीं है / पता नहीं)।"
            } else {
                "Select your document availability status (Have / Missing / Unknown) for ${scheme.code}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EligibilityEngine.initialFiveSchemes.forEach { item ->
                AssistChip(
                    onClick = { onSelectScheme(item.id) },
                    label = { Text(item.code) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Required Documents Checklist for Selected Scheme
        scheme.requiredDocuments.forEach { doc ->
            val currentStatus = documentStatuses[doc.id] ?: DocumentHaveStatus.UNKNOWN
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (language == "hi") doc.nameHi else doc.nameEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (language == "hi") {
                            "विकल्प: ${doc.alternativesHi}"
                        } else {
                            "Alternatives: ${doc.alternativesEn}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (language == "hi") {
                            "कहाँ से प्राप्त करें: ${doc.howToObtainHi}"
                        } else {
                            "How to obtain: ${doc.howToObtainEn}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DocumentHaveStatus.entries.forEach { st ->
                            FilterChip(
                                selected = currentStatus == st,
                                onClick = { onUpdateDocStatus(doc.id, st) },
                                label = {
                                    Text(
                                        text = when (st) {
                                            DocumentHaveStatus.HAVE -> if (language == "hi") "उपलब्ध है" else "Have"
                                            DocumentHaveStatus.MISSING -> if (language == "hi") "नहीं है" else "Missing"
                                            DocumentHaveStatus.UNKNOWN -> if (language == "hi") "पता नहीं" else "Unknown"
                                        },
                                        maxLines = 2,
                                        softWrap = true,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = { onNavigate(AppScreen.APPLICATION_FORM) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("checklist_to_form_btn")
        ) {
            Text(if (language == "hi") "आवेदन फॉर्म तैयार करें →" else "Proceed to Prefilled Application Form →")
        }
    }
}

@Composable
fun ApplicationFormScreen(
    language: String,
    selectedSchemeId: String,
    applicantNameField: FormFieldProvenance,
    stateDistrictField: FormFieldProvenance,
    bankSummaryField: FormFieldProvenance,
    onUpdateField: (String, String, String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = EligibilityEngine.getSchemeById(selectedSchemeId) ?: EligibilityEngine.initialFiveSchemes.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "आवेदन फॉर्म (फील्ड स्रोत ट्रैकिंग)" else "Application Preparation & Field Provenance",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${scheme.code} • Rule Version: ${scheme.ruleVersion}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (language == "hi") {
                "प्रत्येक फ़ील्ड का स्रोत (profile, user_typed, voice_confirmed) ट्रैक किया जाता है। कोई भी व्यक्तिगत जानकारी मनगढ़ंत नहीं भरी जाती।"
            } else {
                "Every form field tracks its provenance source (profile, user_typed, voice_confirmed). Missing personal data is never invented."
            },
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(14.dp))

        ProvenanceTextField(
            label = if (language == "hi") "आवेदक का नाम (Applicant Name)" else "Applicant Full Name",
            provenance = applicantNameField,
            onValueChange = { onUpdateField("applicantName", it, "user_typed") },
            onMarkVoiceConfirmed = { onUpdateField("applicantName", applicantNameField.value, "voice_confirmed") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProvenanceTextField(
            label = if (language == "hi") "राज्य और ज़िला (State & District)" else "State & District",
            provenance = stateDistrictField,
            onValueChange = { onUpdateField("stateDistrict", it, "user_typed") },
            onMarkVoiceConfirmed = { onUpdateField("stateDistrict", stateDistrictField.value, "voice_confirmed") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProvenanceTextField(
            label = if (language == "hi") "बैंक खाता / IFSC और DBT सारांश" else "Bank Account & DBT Seeding Summary",
            provenance = bankSummaryField,
            onValueChange = { onUpdateField("bankSummary", it, "user_typed") },
            onMarkVoiceConfirmed = { onUpdateField("bankSummary", bankSummaryField.value, "voice_confirmed") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onNavigate(AppScreen.REVIEW_CONFIRM) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("form_to_review_gate_btn")
        ) {
            Text(if (language == "hi") "13. मानव पुष्टि गेट (Review & Confirm Gate) →" else "13. Proceed to Human Confirmation Gate →")
        }
    }
}

@Composable
private fun ProvenanceTextField(
    label: String,
    provenance: FormFieldProvenance,
    onValueChange: (String) -> Unit,
    onMarkVoiceConfirmed: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Source: ${provenance.source}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = provenance.value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                AssistChip(
                    onClick = onMarkVoiceConfirmed,
                    label = { Text("Mark voice_confirmed") }
                )
            }
        }
    }
}

@Composable
fun ReviewConfirmScreen(
    language: String,
    selectedSchemeId: String,
    applicantNameField: FormFieldProvenance,
    stateDistrictField: FormFieldProvenance,
    bankSummaryField: FormFieldProvenance,
    checklistSummary: String,
    snapshotHash: String,
    uncertainAcknowledged: Boolean,
    explicitCitizenConfirmed: Boolean,
    onToggleUncertainAck: (Boolean) -> Unit,
    onToggleExplicitConfirm: (Boolean) -> Unit,
    onConfirmAndGenerateHandoff: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = EligibilityEngine.getSchemeById(selectedSchemeId) ?: EligibilityEngine.initialFiveSchemes.first()

    val handoffPackText = remember(scheme, applicantNameField, stateDistrictField, bankSummaryField, checklistSummary, snapshotHash) {
        """
        === SCHEME SAATHI GUIDED HANDOFF PACK ===
        Scheme: ${scheme.code} (${scheme.nameEn})
        Rule Version: ${scheme.ruleVersion} (Illustrative - Verify on Official Portal)
        Official Portal: ${scheme.officialSourceUrl}
        Review Snapshot SHA-256: $snapshotHash
        -----------------------------------------
        Applicant Name: ${applicantNameField.value} [Source: ${applicantNameField.source}]
        State & District: ${stateDistrictField.value} [Source: ${stateDistrictField.source}]
        Bank / DBT Status: ${bankSummaryField.value} [Source: ${bankSummaryField.source}]
        Documents Checklist: $checklistSummary
        -----------------------------------------
        CSC / Local Office Guidance:
        ${scheme.cscOfficeGuidanceEn}
        Instructions:
        ${scheme.applicationInstructionsEn}
        =========================================
        """.trimIndent()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "समीक्षा और मानव पुष्टि गेट (Human Confirmation Gate)" else "Review & Human Confirmation Gate",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Non-Auto-Submit Disclosure Banner (Rule 12 & 13)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == "hi") {
                        "महत्वपूर्ण घोषणा: स्कीम साथी या AI कभी भी सरकारी पोर्टल पर अपने आप आवेदन जमा (Auto-Submit) नहीं करता है। यह स्क्रीन आपके सत्यापन के बाद 'गाइडेड हैंडऑफ पैक' तैयार करती है।"
                    } else {
                        "CRITICAL DISCLOSURE: Scheme Saathi does NOT automatically submit applications to any government server. Confirming here locks your SHA-256 snapshot and generates your Guided Handoff Pack."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${scheme.code} • ${if (language == "hi") scheme.nameHi else scheme.nameEn}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Rule Version: ${scheme.ruleVersion} • Snapshot SHA-256: $snapshotHash",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text("• Name: ${applicantNameField.value} (${applicantNameField.source})")
                Text("• Location: ${stateDistrictField.value} (${stateDistrictField.source})")
                Text("• Bank/DBT: ${bankSummaryField.value} (${bankSummaryField.source})")
                Text("• Documents: $checklistSummary")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = uncertainAcknowledged,
                onCheckedChange = onToggleUncertainAck,
                modifier = Modifier.testTag("ack_uncertain_checkbox")
            )
            Text(
                text = if (language == "hi") {
                    "मैं समझता/समझती हूँ कि पात्रता सीमाएं उदाहरणार्थ हैं और अज्ञात दस्तावेजों को CSC पर दिखाना होगा।"
                } else {
                    "I acknowledge any uncertain/illustrative rules and will carry original documents to the CSC / portal."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = explicitCitizenConfirmed,
                onCheckedChange = onToggleExplicitConfirm,
                modifier = Modifier.testTag("explicit_citizen_confirm_checkbox")
            )
            Text(
                text = if (language == "hi") {
                    "मैं पुष्टि करता/करती हूँ कि उपरोक्त सभी जानकारी सही है और मैं हैंडऑफ पैक बनाना चाहता/चाहती हूँ।"
                } else {
                    "I explicitly confirm the reviewed application snapshot ($snapshotHash) is accurate."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onConfirmAndGenerateHandoff(snapshotHash) },
            enabled = explicitCitizenConfirmed && uncertainAcknowledged,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("confirm_human_gate_btn")
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (language == "hi") {
                    "पुष्टि करें और गाइडेड हैंडऑफ पैक सहेजें"
                } else {
                    "Confirm Snapshot & Generate Guided Handoff Pack"
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Guided Handoff Pack Actions (Copy, Share, Open Official URL)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (language == "hi") "📦 गाइडेड हैंडऑफ पैक (CSC व पोर्टल सारांश)" else "📦 Guided Handoff Pack (Copyable & Printable Summary)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (language == "hi") scheme.cscOfficeGuidanceHi else scheme.cscOfficeGuidanceEn,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Scheme Saathi Handoff", handoffPackText))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (language == "hi") "कॉपी करें" else "Copy Sheet")
                    }
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, handoffPackText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Handoff Summary"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (language == "hi") "साझा/प्रिंट" else "Share/Print")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme.officialSourceUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${scheme.officialSourceUrl}")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApplicationTrackingScreen(
    language: String,
    applications: List<SchemeApplication>,
    onUpdateStatus: (SchemeApplication, String, Boolean) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    activeRole: String = "CITIZEN",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (language == "hi") "14. आवेदन स्थिति ट्रैकिंग (Self-Reported vs Verified)" else "14. Application Status Tracking",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (language == "hi") {
                "नागरिक द्वारा स्व-रिपोर्ट की गई (SELF-REPORTED) और विभाग द्वारा सत्यापित (VERIFIED) स्थितियों को स्पष्ट रूप से अलग दिखाया जाता है।"
            } else {
                "Clearly distinguishes Citizen Self-Reported status from Department-Verified (verified=true) status."
            },
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (applications.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (language == "hi") "अभी कोई पुष्टि किया गया आवेदन नहीं है।" else "No confirmed applications yet.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onNavigate(AppScreen.REVIEW_CONFIRM) }) {
                        Text(if (language == "hi") "पहला हैंडऑफ आवेदन बनाएं" else "Create First Handoff Application")
                    }
                }
            }
        } else {
            applications.forEach { app ->
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
                                text = "${app.schemeId} • ${app.applicantName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = if (app.verified) EligibleGreen else UnknownAmber,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (app.verified) "VERIFIED (विभाग सत्यापित)" else "SELF-REPORTED (स्व-रिपोर्ट)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Status: ${app.status} • Rule: ${app.ruleVersion} • Hash: ${app.snapshotHash.take(12)}...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Location: ${app.stateDistrict} • Bank: ${app.bankIfscSummary}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        val isStaff = activeRole == "CSC_OPERATOR" || activeRole == "REVIEWER" || activeRole == "SUPERVISOR"
                        val canApprove = activeRole == "REVIEWER" || activeRole == "SUPERVISOR"
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = { onUpdateStatus(app, "SUBMITTED_SELF_REPORTED", false) },
                                label = { Text("Mark Self-Reported Submitted") }
                            )
                            if (isStaff) {
                                AssistChip(
                                    onClick = { onUpdateStatus(app, "ACKNOWLEDGED", true) },
                                    label = { Text("CSC Acknowledged (Verified)") }
                                )
                            }
                            if (canApprove) {
                                AssistChip(
                                    onClick = { onUpdateStatus(app, "APPROVED", true) },
                                    label = { Text("Approved (Verified)") }
                                )
                            }
                        }
                        if (!canApprove) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.rbac_locked_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertsAndSettingsScreen(
    language: String,
    currentRole: String,
    tasks: List<CollaborativeTask>,
    auditFindings: List<AuditFinding>,
    auditCsvContent: String,
    onCreateTask: (String, String, String, String, String, String, String, String) -> Unit,
    onAdvanceTask: (CollaborativeTask) -> Unit,
    onCancelTask: (CollaborativeTask) -> Unit,
    onSignOut: () -> Unit,
    darkThemeMode: Boolean = false,
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onBackendDemoSignIn: (String) -> Unit = {},
    onExportDpdpJson: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var taskTitle by remember { mutableStateOf("") }
    var taskDesc by remember { mutableStateOf("") }
    var assigneeName by remember { mutableStateOf("Rakesh Kumar (CSC Operator)") }
    var assigneeRole by remember { mutableStateOf("CSC_OPERATOR") }
    var priority by remember { mutableStateOf("HIGH") }
    var schemeCode by remember { mutableStateOf("PM-KISAN") }
    var dueDate by remember { mutableStateOf("Tomorrow 10:00 AM") }
    var deliveryChannel by remember { mutableStateOf("IN_APP") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (language == "hi") "सहयोगात्मक कार्य, अलर्ट और सुरक्षा ऑडिट" else "Collaborative Tasks, Alerts & Audit Report",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("sign_out_btn")
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (language == "hi") "लॉगआउट" else "Sign Out")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Theme, Shared Backend Role Switcher (RBAC), & DPDP Data Export Controls
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.settings_dark_mode),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = darkThemeMode,
                        onCheckedChange = onToggleDarkTheme,
                        modifier = Modifier.testTag("dark_theme_switch")
                    )
                }
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.settings_backend_sync),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CITIZEN", "CSC_OPERATOR", "REVIEWER", "SUPERVISOR").forEach { roleOpt ->
                        FilterChip(
                            selected = currentRole == roleOpt,
                            onClick = { onBackendDemoSignIn(roleOpt) },
                            label = { Text(roleOpt) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onExportDpdpJson,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_dpdp_export))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Create Collaborative Task & Reminder Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                val roleDisplay = when (currentRole) {
                    "CITIZEN" -> if (language == "hi") "नागरिक" else "Citizen"
                    "CSC_OPERATOR" -> if (language == "hi") "सीएससी ऑपरेटर" else "CSC Operator"
                    "SUPERVISOR" -> if (language == "hi") "पर्यवेक्षक" else "Supervisor"
                    "REVIEWER" -> if (language == "hi") "समीक्षक" else "Reviewer"
                    else -> currentRole
                }
                Text(
                    text = if (language == "hi") {
                        "नया सहयोगात्मक कार्य और रिमाइंडर जोड़ें (भूमिका: $roleDisplay)"
                    } else {
                        "Assign Collaborative Task & Schedule Reminder (Role: $roleDisplay)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text(if (language == "hi") "कार्य शीर्षक (Task Title)" else "Task Title (e.g., Verify Khatauni at CSC)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = assigneeName,
                        onValueChange = { assigneeName = it },
                        label = { Text(if (language == "hi") "सौंपा गया (Assignee)" else "Assignee Name") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text(if (language == "hi") "समय सीमा" else "Due Date") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CITIZEN", "CSC_OPERATOR", "SUPERVISOR", "REVIEWER").forEach { r ->
                        FilterChip(
                            selected = assigneeRole == r,
                            onClick = { assigneeRole = r },
                            label = { Text(r) }
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("IN_APP" to "In-App Notification", "SMS_MOCK" to "SMS Adapter (Retry 1m/5m/30m)").forEach { (ch, lbl) ->
                        FilterChip(
                            selected = deliveryChannel == ch,
                            onClick = { deliveryChannel = ch },
                            label = { Text(lbl) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (taskTitle.isNotBlank()) {
                            onCreateTask(taskTitle, taskDesc, assigneeName, assigneeRole, priority, schemeCode, dueDate, deliveryChannel)
                            taskTitle = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_task_btn")
                ) {
                    Icon(Icons.Default.AddAlert, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (language == "hi") "कार्य सौंपें और रिमाइंडर शेड्यूल करें" else "Assign Task & Schedule Reminder")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Collaborative Tasks & Activity Logs
        Text(
            text = if (language == "hi") "सक्रिय सहयोगात्मक कार्य और गतिविधि लॉग (${tasks.size})" else "Collaborative Tasks, Reminders & Activity Logs (${tasks.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        tasks.forEach { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${task.title} (${task.schemeCode})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${task.status} • ${task.deliveryStatus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Assignee: ${task.assigneeName} [${task.assigneeRole}] • Priority: ${task.priority} • Due: ${task.dueDateLabel}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "📋 Activity Log: ${task.activityLogSummary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAdvanceTask(task) }) {
                            Text(if (language == "hi") "स्थिति आगे बढ़ाएं" else "Advance Status")
                        }
                        OutlinedButton(
                            onClick = {
                                val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                    data = CalendarContract.Events.CONTENT_URI
                                    putExtra(CalendarContract.Events.TITLE, "Scheme Saathi: ${task.title}")
                                    putExtra(CalendarContract.Events.DESCRIPTION, task.activityLogSummary)
                                }
                                try {
                                    context.startActivity(calIntent)
                                } catch (_: Exception) {
                                }
                            }
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (language == "hi") "कैलेंडर" else "Calendar")
                        }
                        if (task.status != "CANCELLED") {
                            OutlinedButton(onClick = { onCancelTask(task) }) {
                                Text(if (language == "hi") "रद्द करें" else "Cancel")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stage 8 Security & Risk Assessment Report + CSV Export
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GppGood, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == "hi") {
                            "स्टेज 8: सुरक्षा ऑडिट, जोखिम मूल्यांकन और CSV रिपोर्ट"
                        } else {
                            "Stage 8: Security Audit, Risk Assessment & CSV Export"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                auditFindings.forEach { finding ->
                    Text(
                        text = "• [${finding.id} | ${finding.severity}] ${finding.category}: ${finding.status} — Timeline: ${finding.mitigationTimeline}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SchemeSaathi_Audit_Findings.csv", auditCsvContent))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_audit_csv_btn")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Audit CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "SchemeSaathi_Security_Audit_Report.csv")
                                putExtra(Intent.EXTRA_TEXT, auditCsvContent)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Audit CSV"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV")
                    }
                }
            }
        }
    }
}
