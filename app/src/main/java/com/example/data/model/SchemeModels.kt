package com.example.data.model

import com.example.util.DateUtils
import com.example.util.SecurityUtils
import java.util.Calendar

data class CitizenProfile(
    val id: String = "",
    val userId: String = "",
    val encryptedName: String = "",
    val encryptedPhone: String = "",
    val encryptedDob: String = "",
    val birthYear: Int = 1985,
    val ageConfirmedViaVoice: Boolean = true,
    val gender: String = "unknown", // male, female, other, unknown
    val state: String = "Uttar Pradesh",
    val district: String = "Varanasi",
    val residenceType: String = "rural", // rural, urban, unknown
    val annualIncome: Int = -1, // -1 means unknown
    val occupation: String = "farmer",
    val bplStatus: String = "unknown", // yes, no, unknown
    val socialCategory: String = "unknown", // general, obc, sc, st, unknown
    val hasBankAccount: String = "yes", // yes, no, unknown
    val hasLpgConnection: String = "unknown", // yes, no, unknown
    val ownsAgriculturalLand: String = "unknown", // yes, no, unknown
    val isIncomeTaxPayer: String = "no", // yes, no, unknown
    val familySize: Int = 4,
    val consentGranted: Boolean = false,
    val preferredLanguage: String = "hi", // hi, en
    val role: String = "CITIZEN", // CITIZEN, CSC_OPERATOR, SUPERVISOR, REVIEWER
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    /**
     * Resolves citizen age with accurate birthday calculation from encryptedDob when present.
     * Falls back cleanly to birthYear for backward compatibility or when only age is known (voice flow).
     */
    fun resolveAge(
        currentDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
        currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
        currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): Int {
        if (encryptedDob.isNotBlank() && userId.isNotBlank()) {
            val decrypted = SecurityUtils.decryptPii(encryptedDob, userId)
            val calculated = DateUtils.calculateAge(decrypted, currentDay, currentMonth, currentYear)
            if (calculated in 0..120) return calculated
        }
        return if (birthYear in 1901..currentYear) (currentYear - birthYear) else -1
    }
}

data class SchemeApplication(
    val id: String = "",
    val userId: String = "",
    val schemeId: String = "",
    val schemeName: String = "",
    val ruleVersion: String = "v1.0.0-illustrative",
    val status: String = "DRAFT",
    val verified: Boolean = false,
    val applicantName: String = "",
    val applicantNameSource: String = "profile", // profile, user_typed, voice_confirmed
    val stateDistrict: String = "",
    val stateDistrictSource: String = "profile",
    val bankIfscSummary: String = "",
    val bankIfscSource: String = "profile",
    val checklistSummary: String = "",
    val snapshotHash: String = "",
    val explicitCitizenConfirmed: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class CollaborativeTask(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val assigneeName: String = "",
    val assigneeRole: String = "CSC_OPERATOR", // CITIZEN, CSC_OPERATOR, SUPERVISOR, REVIEWER
    val priority: String = "HIGH", // LOW, MEDIUM, HIGH, URGENT
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    val schemeCode: String = "PM-KISAN",
    val dueDateLabel: String = "Tomorrow, 10:00 AM",
    val deliveryChannel: String = "IN_APP", // IN_APP, SMS_MOCK
    val deliveryStatus: String = "SCHEDULED", // SCHEDULED, DELIVERED, RETRY_1M, CANCELLED
    val activityLogSummary: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

enum class EligibilityOutcome {
    ELIGIBLE,
    NOT_ELIGIBLE,
    UNKNOWN
}

enum class RuleOperator {
    EQ,
    NEQ,
    LT,
    LTE,
    GT,
    GTE,
    IN,
    NOT_IN,
    BETWEEN,
    EXISTS,
    EXTERNAL_LOOKUP
}

enum class LogicalCombinator {
    ALL,
    ANY
}

data class RuleCondition(
    val id: String,
    val field: String,
    val operator: RuleOperator,
    val targetValue: Any? = null,
    val secondaryTargetValue: Any? = null,
    val descriptionEn: String,
    val descriptionHi: String,
    val externalGuidanceEn: String = "",
    val externalGuidanceHi: String = ""
)

data class RuleGroup(
    val combinator: LogicalCombinator = LogicalCombinator.ALL,
    val conditions: List<RuleCondition> = emptyList(),
    val nestedGroups: List<RuleGroup> = emptyList()
)

data class DocumentRequirement(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val mandatory: Boolean,
    val alternativesEn: String,
    val alternativesHi: String,
    val howToObtainEn: String,
    val howToObtainHi: String
)

enum class DocumentHaveStatus {
    HAVE,
    MISSING,
    UNKNOWN
}

data class GovernmentScheme(
    val id: String,
    val code: String,
    val nameEn: String,
    val nameHi: String,
    val category: String,
    val benefitSummaryEn: String,
    val benefitSummaryHi: String,
    val descriptionEn: String,
    val descriptionHi: String,
    val officialSourceTitle: String,
    val officialSourceUrl: String,
    val verificationDate: String,
    val isThresholdVerifiedOfficial: Boolean = false,
    val isRuleStaleWarning: Boolean = false,
    val ruleVersion: String,
    val rootRuleGroup: RuleGroup,
    val stateOverrideIncomeCap: Map<String, Int> = emptyMap(),
    val requiredDocuments: List<DocumentRequirement>,
    val applicationInstructionsEn: String,
    val applicationInstructionsHi: String,
    val cscOfficeGuidanceEn: String,
    val cscOfficeGuidanceHi: String
)

data class ConditionResult(
    val conditionId: String,
    val field: String,
    val outcome: EligibilityOutcome,
    val reasonEn: String,
    val reasonHi: String
)

data class EligibilityEvaluationResult(
    val schemeId: String,
    val schemeCode: String,
    val ruleVersion: String,
    val outcome: EligibilityOutcome,
    val conditionResults: List<ConditionResult>,
    val missingFields: List<String>,
    val externalLookupGuidanceEn: List<String>,
    val externalLookupGuidanceHi: List<String>,
    val stateOverrideApplied: String? = null,
    val evaluatedAtEpochMs: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val role: String, // "user", "assistant", "tool"
    val content: String,
    val modelUsed: String = "gemini-3.8-flash",
    val toolCallsSummary: List<String> = emptyList(),
    val isGrounded: Boolean = true,
    val timestampMs: Long = System.currentTimeMillis()
)

data class AuditFinding(
    val id: String,
    val category: String,
    val component: String,
    val severity: String,
    val status: String,
    val riskAssessment: String,
    val remediation: String,
    val mitigationTimeline: String
)
