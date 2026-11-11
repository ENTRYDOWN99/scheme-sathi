package com.example.util

import com.example.data.model.CitizenProfile
import com.example.data.model.EligibilityOutcome
import com.example.domain.EligibilityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateUtilsTest {

    // Reference date for deterministic tests: 11 November 2026
    private val refDay = 11
    private val refMonth = 11
    private val refYear = 2026

    @Test
    fun calculateAge_birthdayBeforeToday_calculatesFullAge() {
        // Born 10 November 1964 (birthday was yesterday) -> exactly 62 years old
        val age = DateUtils.calculateAge("10/11/1964", refDay, refMonth, refYear)
        assertEquals(62, age)
    }

    @Test
    fun calculateAge_birthdayAfterToday_subtractsOneYear() {
        // Born 15 November 1964 (birthday is in 4 days) -> 61 years old
        val age = DateUtils.calculateAge("15/11/1964", refDay, refMonth, refYear)
        assertEquals(61, age)
    }

    @Test
    fun calculateAge_birthdayToday_calculatesFullAge() {
        // Born 11 November 1964 (birthday is today) -> exactly 62 years old
        val age = DateUtils.calculateAge("11/11/1964", refDay, refMonth, refYear)
        assertEquals(62, age)
    }

    @Test
    fun validateDob_leapDay2000_isValid() {
        // 2000 is divisible by 400, so 29/02/2000 is a valid leap year date
        val result = DateUtils.validateDob("29/02/2000", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.Valid, result)
        assertEquals(26, DateUtils.calculateAge("29/02/2000", refDay, refMonth, refYear))
    }

    @Test
    fun validateDob_leapDay2001_isRejectedAsInvalidCalendarDate() {
        // 2001 is not a leap year, 29 Feb does not exist
        val result = DateUtils.validateDob("29/02/2001", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.InvalidCalendarDate, result)
    }

    @Test
    fun validateDob_invalidCalendarDate_feb31_isRejected() {
        // 31 Feb is always invalid
        val result = DateUtils.validateDob("31/02/2000", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.InvalidCalendarDate, result)
    }

    @Test
    fun validateDob_invalidCalendarDate_april31_isRejected() {
        // April has only 30 days
        val result = DateUtils.validateDob("31/04/1990", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.InvalidCalendarDate, result)
    }

    @Test
    fun validateDob_futureDate_isRejected() {
        // 12 November 2026 is after 11 November 2026
        val resultTomorrow = DateUtils.validateDob("12/11/2026", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.FutureDate, resultTomorrow)

        // Year in future
        val resultFutureYear = DateUtils.validateDob("01/01/2027", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.FutureDate, resultFutureYear)
    }

    @Test
    fun validateDob_ageOutOfRange_isRejected() {
        // Born 1890 on 2026 -> age 136 > 120
        val resultTooOld = DateUtils.validateDob("01/01/1890", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.AgeOutOfRange, resultTooOld)

        // Born today -> age 0 -> Valid
        val resultAgeZero = DateUtils.validateDob("11/11/2026", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.Valid, resultAgeZero)

        // Age 120 -> Valid
        val resultAge120 = DateUtils.validateDob("11/11/1906", refDay, refMonth, refYear)
        assertEquals(DateUtils.DobValidationResult.Valid, resultAge120)
    }

    @Test
    fun formatDobInput_autoInsertsSlashes() {
        assertEquals("0", DateUtils.formatDobInput("0"))
        assertEquals("01", DateUtils.formatDobInput("01"))
        assertEquals("01/0", DateUtils.formatDobInput("010"))
        assertEquals("01/01", DateUtils.formatDobInput("0101"))
        assertEquals("01/01/1", DateUtils.formatDobInput("01011"))
        assertEquals("01/01/1964", DateUtils.formatDobInput("01011964"))
        assertEquals("01/01/1964", DateUtils.formatDobInput("01/01/1964"))
    }

    @Test
    fun backwardCompatibility_birthYearOnlyProfile_resolvesAgeAndPassesEligibility() {
        // Legacy profile with only birthYear = 1964, no encryptedDob
        val legacyProfile = CitizenProfile(
            userId = "test_user_1",
            birthYear = 1964,
            ageConfirmedViaVoice = true,
            bplStatus = "yes",
            annualIncome = 40000,
            state = "Uttar Pradesh"
        )

        // Resolves age to 2026 - 1964 = 62
        val resolvedAge = legacyProfile.resolveAge(refDay, refMonth, refYear)
        assertEquals(62, resolvedAge)

        // Passes NSAP-IGNOAPS (requires age >= 60)
        val ignoaps = EligibilityEngine.getSchemeById("NSAP-IGNOAPS")!!
        val eval = EligibilityEngine.evaluateScheme(legacyProfile, ignoaps, refYear)
        assertEquals(EligibilityOutcome.ELIGIBLE, eval.outcome)
    }

    @Test
    fun encryptedDobProfile_resolvesExactAgeAndEvaluatesInEngine() {
        val uid = "test_user_enc"
        // Born 15/11/1964: On 11/11/2026, citizen is 61, not 62 yet!
        val encDob = SecurityUtils.encryptPii("15/11/1964", uid)
        val profile = CitizenProfile(
            userId = uid,
            encryptedDob = encDob,
            birthYear = 1964,
            ageConfirmedViaVoice = true,
            bplStatus = "yes",
            annualIncome = 40000,
            state = "Uttar Pradesh"
        )

        // Accurate age should be 61
        val resolvedAge = profile.resolveAge(refDay, refMonth, refYear)
        assertEquals(61, resolvedAge)
    }

    @Test
    fun securityUtils_containsAadhaarNumber_doesNotFalsePositiveOnDate() {
        // Date formats must never be flagged as Aadhaar numbers
        assertFalse(SecurityUtils.containsAadhaarNumber("01/01/1964"))
        assertFalse(SecurityUtils.containsAadhaarNumber("15-08-1947"))
        assertFalse(SecurityUtils.containsAadhaarNumber("29/02/2000"))
        assertFalse(SecurityUtils.containsAadhaarNumber("01011964"))

        // Real 12-digit Aadhaar pattern MUST still be caught
        assertTrue(SecurityUtils.containsAadhaarNumber("1234 5678 9012"))
        assertTrue(SecurityUtils.containsAadhaarNumber("123456789012"))
        assertTrue(SecurityUtils.containsAadhaarNumber("1234-5678-9012"))
    }

    @Test
    fun securityUtils_redactPiiForLlm_redactsDatesAndDob() {
        val prompt = "Citizen born on 01/01/1964 with phone 9876543210"
        val redacted = SecurityUtils.redactPiiForLlm(prompt, citizenDob = "01/01/1964")
        assertFalse(redacted.contains("01/01/1964"))
        assertTrue(redacted.contains("[REDACTED_DOB]") || redacted.contains("[REDACTED_DATE]"))
        assertFalse(redacted.contains("9876543210"))
    }
}
