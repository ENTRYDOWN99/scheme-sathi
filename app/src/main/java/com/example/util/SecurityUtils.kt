package com.example.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides deterministic SHA-256 snapshot hashing for the Human Confirmation Gate,
 * AES-256-GCM field-level encryption for citizen PII (Name, Phone),
 * PII Redaction before sending context to Gemini AI,
 * and Hindi/English voice numeral parsing with echo-back verification.
 */
object SecurityUtils {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private fun deriveUserKey(userId: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(("scheme-saathi-vault-v1:$userId").toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts sensitive PII (Name, Phone) using AES-256-GCM before storing in Firestore.
     * Never stores Aadhaar numbers.
     */
    fun encryptPii(plainText: String, userId: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, deriveUserKey(userId), spec)
            val cipherText = cipher.doFinal(plainText.trim().toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            "GCM1:" + Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            "ENC_FALLBACK"
        }
    }

    /**
     * Decrypts AES-256-GCM encrypted PII for the authenticated owner.
     */
    fun decryptPii(cipherTextPayload: String, userId: String): String {
        if (cipherTextPayload.isBlank()) return ""
        if (!cipherTextPayload.startsWith("GCM1:")) return cipherTextPayload
        return try {
            val raw = Base64.getDecoder().decode(cipherTextPayload.removePrefix("GCM1:"))
            if (raw.size <= GCM_IV_LENGTH) return ""
            val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, deriveUserKey(userId), spec)
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "[Encrypted]"
        }
    }

    /**
     * Detects and blocks any accidental 12-digit Aadhaar-like input.
     * Rule 14: No Aadhaar number must ever be collected or stored.
     */
    fun containsAadhaarNumber(input: String): Boolean {
        val digitsOnly = input.replace(Regex("[\\s-]"), "")
        return Regex("\\b\\d{12}\\b").containsMatchIn(digitsOnly)
    }

    /**
     * Redacts personal identifiers (names, phone numbers, date of birth, Aadhaar-like numbers, bank accounts)
     * before sending any prompt or profile summary to Gemini AI.
     */
    fun redactPiiForLlm(
        input: String,
        citizenName: String = "",
        citizenPhone: String = "",
        citizenDob: String = ""
    ): String {
        var sanitized = input
        if (citizenName.isNotBlank() && citizenName.length >= 2) {
            sanitized = sanitized.replace(citizenName, "[REDACTED_NAME]", ignoreCase = true)
        }
        if (citizenPhone.isNotBlank() && citizenPhone.length >= 4) {
            sanitized = sanitized.replace(citizenPhone, "[REDACTED_PHONE]")
        }
        if (citizenDob.isNotBlank() && citizenDob.length >= 8) {
            sanitized = sanitized.replace(citizenDob, "[REDACTED_DOB]")
        }
        // Redact standard DD/MM/YYYY or DD-MM-YYYY dates
        sanitized = sanitized.replace(Regex("\\b\\d{2}[/-]\\d{2}[/-]\\d{4}\\b"), "[REDACTED_DATE]")
        // Redact 10-digit Indian mobile numbers
        sanitized = sanitized.replace(Regex("\\b[6-9]\\d{9}\\b"), "[REDACTED_PHONE]")
        // Redact 12-digit Aadhaar-like sequences
        sanitized = sanitized.replace(Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), "[REDACTED_AADHAAR_FORBIDDEN]")
        return sanitized
    }

    /**
     * Checks if user prompt attempts prompt injection or requests automatic government submission.
     */
    fun detectPromptInjectionOrAutoSubmit(prompt: String): Pair<Boolean, String?> {
        val lower = prompt.lowercase()
        val injectionPatterns = listOf(
            "ignore previous instructions",
            "ignore all rules",
            "system prompt",
            "bypass eligibility",
            "mark me eligible",
            "force approve"
        )
        if (injectionPatterns.any { lower.contains(it) }) {
            return true to "SAFETY_BLOCK: Prompt injection attempt detected. Eligibility is strictly evaluated by the deterministic rule engine."
        }
        val autoSubmitPatterns = listOf(
            "submit my application automatically",
            "auto submit",
            "auto-submit",
            "submit automatically",
            "automatically submit",
            "confirm and submit for me",
            "ask it to auto-submit",
            "will refuse",
            "सीधे फॉर्म सबमिट कर दो",
            "सीधे सबमिट",
            "अपने आप आवेदन जमा करें",
            "स्वचालित आवेदन"
        )
        if (autoSubmitPatterns.any { lower.contains(it) }) {
            return true to "HUMAN_GATE_BLOCK: AI is strictly prohibited from confirming or submitting government applications. Per security policy, citizen welfare submissions require mandatory human verification on the Review & Confirm screen with a verifiable SHA-256 snapshot hash."
        }
        return false to null
    }

    /**
     * Computes a deterministic SHA-256 hash of the application review snapshot.
     */
    fun computeSnapshotHash(
        schemeId: String,
        ruleVersion: String,
        applicantName: String,
        stateDistrict: String,
        bankIfscSummary: String,
        checklistSummary: String
    ): String {
        val canonical = listOf(
            schemeId.trim(),
            ruleVersion.trim(),
            applicantName.trim(),
            stateDistrict.trim(),
            bankIfscSummary.trim(),
            checklistSummary.trim()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(canonical.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Parses spoken Hindi and English numbers into integer values.
     * Supports Devanagari digits, Hindi spoken words (e.g., बासठ = 62, बयासठ = 62, साठ = 60, उनसठ = 59),
     * and standard Arabic numerals.
     */
    private val hindiNumberWords: Map<String, Int> = mapOf(
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5,
        "छह" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10,
        "अठारह" to 18, "उन्नीस" to 19, "बीस" to 20, "इक्कीस" to 21, "बाईस" to 22,
        "तेईस" to 23, "चौबीस" to 24, "पच्चीस" to 25, "तीस" to 30, "पैंतीस" to 35,
        "चालीस" to 40, "पैंतालीस" to 45, "पचास" to 50, "पचपन" to 55,
        "छप्पन" to 56, "सत्तावन" to 57, "अट्ठावन" to 58, "उनसठ" to 59,
        "साठ" to 60, "इकसठ" to 61, "बासठ" to 62, "बयासठ" to 62, "तिरेसठ" to 63,
        "चौंसठ" to 64, "पैंसठ" to 65, "छियासठ" to 66, "सड़सठ" to 67, "अड़सठ" to 68,
        "उनहत्तर" to 69, "सत्तर" to 70, "पचहत्तर" to 75, "अस्सी" to 80,
        "पचासी" to 85, "नब्बे" to 90,
        "pacpan" to 55, "unsath" to 59, "saath" to 60, "iksath" to 61,
        "basath" to 62, "baasath" to 62, "bayasath" to 62, "painsath" to 65, "sattar" to 70
    )

    fun parseHindiOrEnglishNumber(utterance: String): Int? {
        val normalizedDevanagari = utterance.map { ch ->
            when (ch) {
                '०' -> '0'
                '१' -> '1'
                '२' -> '2'
                '३' -> '3'
                '४' -> '4'
                '५' -> '5'
                '६' -> '6'
                '७' -> '7'
                '८' -> '8'
                '९' -> '9'
                else -> ch
            }
        }.joinToString("")

        // 1. Check Hindi/transliterated words first
        val tokens = normalizedDevanagari.lowercase()
            .split(Regex("[\\s,।.?]+"))
            .filter { it.isNotBlank() }
        for (token in tokens) {
            hindiNumberWords[token]?.let { return it }
        }
        for ((word, value) in hindiNumberWords) {
            if (normalizedDevanagari.contains(word)) {
                return value
            }
        }

        // 2. Check numeric digits (1 to 3 digits for age/family size, or larger for income)
        val regexMatch = Regex("\\b(\\d{1,7})\\b").find(normalizedDevanagari)
        return regexMatch?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Generates the localized echo-back confirmation prompt for spoken age.
     * Example: "आपकी उम्र 62 साल है, सही है?"
     */
    fun formatAgeEchoConfirmation(age: Int, language: String): String {
        return if (language == "hi") {
            "आपकी उम्र $age साल है, सही है?"
        } else {
            "Your age is $age years, is that correct? (आपकी उम्र $age साल है, सही है?)"
        }
    }
}
