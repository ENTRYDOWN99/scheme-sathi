package com.example.util

import java.util.Calendar

/**
 * Utility for parsing, validating, and calculating age from citizen Date of Birth (DD/MM/YYYY).
 * Works across all Android API levels using standard calendar arithmetic.
 */
object DateUtils {

    sealed class DobValidationResult {
        object Valid : DobValidationResult()
        object Incomplete : DobValidationResult()
        object InvalidFormat : DobValidationResult()
        object InvalidCalendarDate : DobValidationResult()
        object FutureDate : DobValidationResult()
        object AgeOutOfRange : DobValidationResult()
    }

    data class ParsedDate(val day: Int, val month: Int, val year: Int)

    /**
     * Determines whether a year is a leap year in the Gregorian calendar.
     */
    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    /**
     * Returns the maximum days in the given month (1..12) for the given year.
     */
    fun getDaysInMonth(month: Int, year: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }
    }

    /**
     * Parses a string in DD/MM/YYYY format into day, month, year integers.
     * Returns null if the format does not match 2-digit day, 2-digit month, 4-digit year.
     */
    fun parseDob(dobStr: String): ParsedDate? {
        val trimmed = dobStr.trim()
        val parts = trimmed.split("/")
        if (parts.size != 3) return null
        if (parts[0].length != 2 || parts[1].length != 2 || parts[2].length != 4) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return ParsedDate(day, month, year)
    }

    /**
     * Validates a date of birth string (DD/MM/YYYY) against the current reference date.
     * Checks:
     * 1. Incomplete input (< 10 chars)
     * 2. Format syntax
     * 3. Real calendar validity (e.g. rejects 31/02/2000, 29/02/2001)
     * 4. Future dates
     * 5. Age between 0 and 120 years
     */
    fun validateDob(
        dobStr: String,
        refDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
        refMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
        refYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): DobValidationResult {
        val trimmed = dobStr.trim()
        if (trimmed.isEmpty()) return DobValidationResult.Incomplete
        if (trimmed.length < 10) return DobValidationResult.Incomplete

        val parsed = parseDob(trimmed) ?: return DobValidationResult.InvalidFormat

        if (parsed.month !in 1..12) return DobValidationResult.InvalidCalendarDate
        val maxDays = getDaysInMonth(parsed.month, parsed.year)
        if (parsed.day !in 1..maxDays) return DobValidationResult.InvalidCalendarDate

        // Check if date is in the future
        val isFuture = parsed.year > refYear ||
            (parsed.year == refYear && parsed.month > refMonth) ||
            (parsed.year == refYear && parsed.month == refMonth && parsed.day > refDay)

        if (isFuture) return DobValidationResult.FutureDate

        // Compute age
        val age = calculateAge(trimmed, refDay, refMonth, refYear)
        if (age !in 0..120) return DobValidationResult.AgeOutOfRange

        return DobValidationResult.Valid
    }

    /**
     * Calculates the citizen's exact age in completed years, accurately accounting
     * for whether the birthday has occurred yet in the reference year.
     */
    fun calculateAge(
        dobStr: String,
        refDay: Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
        refMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
        refYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): Int {
        val parsed = parseDob(dobStr) ?: return -1
        if (parsed.month !in 1..12) return -1
        val maxDays = getDaysInMonth(parsed.month, parsed.year)
        if (parsed.day !in 1..maxDays) return -1

        var age = refYear - parsed.year
        // If birthday has not yet occurred this year, subtract 1
        if (refMonth < parsed.month || (refMonth == parsed.month && refDay < parsed.day)) {
            age--
        }
        return age
    }

    /**
     * Auto-inserts '/' separators into numeric input while typing.
     * Prevents trapped backspaces by not adding a trailing slash on 2nd or 4th digit.
     */
    fun formatDobInput(rawInput: String): String {
        val digits = rawInput.filter { it.isDigit() }.take(8)
        return buildString {
            for (i in digits.indices) {
                if (i == 2 || i == 4) {
                    append('/')
                }
                append(digits[i])
            }
        }
    }

    /**
     * Returns a localized validation error message, or null if valid/incomplete.
     */
    fun getValidationErrorMessage(result: DobValidationResult, language: String): String? {
        return when (result) {
            is DobValidationResult.Valid, is DobValidationResult.Incomplete -> null
            is DobValidationResult.InvalidFormat -> if (language == "hi") {
                "कृपया वैध जन्म तिथि (DD/MM/YYYY) दर्ज करें"
            } else {
                "Please enter a valid date of birth (DD/MM/YYYY)"
            }
            is DobValidationResult.InvalidCalendarDate -> if (language == "hi") {
                "कृपया वास्तविक कैलेंडर तिथि दर्ज करें (उदा. 31/02 अमान्य है)"
            } else {
                "Please enter a valid calendar date (e.g. 31/02 is invalid)"
            }
            is DobValidationResult.FutureDate -> if (language == "hi") {
                "जन्म तिथि भविष्य की नहीं हो सकती"
            } else {
                "Date of birth cannot be in the future"
            }
            is DobValidationResult.AgeOutOfRange -> if (language == "hi") {
                "उम्र 0 से 120 वर्ष के बीच होनी चाहिए"
            } else {
                "Age must be between 0 and 120 years"
            }
        }
    }
}
