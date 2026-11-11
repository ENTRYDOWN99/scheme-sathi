package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CitizenProfile
import com.example.data.model.CollaborativeTask
import com.example.data.model.EligibilityOutcome
import com.example.domain.EligibilityEngine
import com.example.util.SecurityUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SchemeSaathiRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObserveProfile_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("citizen_alice@schemesaathi.in")
        val repository = SchemeSaathiRepository(firestore, auth)

        val profile = CitizenProfile(
            userId = uid,
            encryptedName = SecurityUtils.encryptPii("Sita Devi", uid),
            encryptedPhone = SecurityUtils.encryptPii("9876543210", uid),
            birthYear = 1962,
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

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveProfile(profile)
        }

        val fetched = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null }
        }
        assertNotNull(fetched)
        assertEquals("Uttar Pradesh", fetched?.state)
        assertEquals("Sita Devi", SecurityUtils.decryptPii(fetched?.encryptedName.orEmpty(), uid))
    }

    @Test
    fun createAndObserveTask_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("citizen_task@schemesaathi.in")
        val repository = SchemeSaathiRepository(firestore, auth)
        val taskId = "task_${UUID.randomUUID()}"

        val task = CollaborativeTask(
            id = taskId,
            userId = uid,
            title = "Verify Khatauni at CSC",
            description = "Bring land record for PM-KISAN verification",
            assigneeName = "Rajesh CSC Operator",
            assigneeRole = "CSC_OPERATOR",
            priority = "HIGH",
            status = "PENDING",
            schemeCode = "PM-KISAN",
            dueDateLabel = "Tomorrow 10:00 AM",
            deliveryChannel = "IN_APP",
            deliveryStatus = "SCHEDULED",
            activityLogSummary = "Task created by citizen"
        )

        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(task)
        }
        assertEquals(taskId, createdId)

        val tasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        assertTrue(tasks.any { it.id == taskId })
    }

    @Test
    fun observeTasks_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso@schemesaathi.in")
        val aliceRepo = SchemeSaathiRepository(firestore, auth)
        val taskId = "task_${UUID.randomUUID()}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask(
                CollaborativeTask(
                    id = taskId,
                    userId = aliceUid,
                    title = "Alice Private Task",
                    description = "Private",
                    assigneeName = "Alice",
                    assigneeRole = "CITIZEN",
                    priority = "MEDIUM",
                    status = "PENDING",
                    schemeCode = "PMUY",
                    dueDateLabel = "Today",
                    deliveryChannel = "IN_APP",
                    deliveryStatus = "SCHEDULED",
                    activityLogSummary = "Init"
                )
            )
        }

        // Switch to Bob and attempt to read Alice's tasks
        signInTestUser("bob_iso@schemesaathi.in")
        val bobRepo = SchemeSaathiRepository(firestore, auth)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeTasks(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception when Bob reads Alice's tasks", caughtException)
        val firestoreException = checkNotNull(caughtException!!.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caughtException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeProfile_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_unauth@schemesaathi.in")
        auth.signOut()
        val repository = SchemeSaathiRepository(firestore, auth)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProfile(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception when unauthenticated caller observes profile", caughtException)
        val firestoreException = checkNotNull(caughtException!!.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caughtException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun deterministicEligibilityEngine_allSevenMandatoryScenarios_pass() {
        val ignoaps = EligibilityEngine.getSchemeById("NSAP-IGNOAPS")!!
        val pmjay = EligibilityEngine.getSchemeById("PM-JAY")!!

        // 1. Age 60 vs 59 for NSAP-IGNOAPS (Reference Year 2026: 1966 = 60, 1967 = 59)
        val profileAge60 = CitizenProfile(
            birthYear = 1966,
            ageConfirmedViaVoice = true,
            bplStatus = "yes",
            annualIncome = 40000,
            state = "Uttar Pradesh"
        )
        val profileAge59 = profileAge60.copy(birthYear = 1967)
        assertEquals(EligibilityOutcome.ELIGIBLE, EligibilityEngine.evaluateScheme(profileAge60, ignoaps, 2026).outcome)
        assertEquals(EligibilityOutcome.NOT_ELIGIBLE, EligibilityEngine.evaluateScheme(profileAge59, ignoaps, 2026).outcome)

        // 2. Missing BPL status -> UNKNOWN (never NOT_ELIGIBLE)
        val profileMissingBpl = profileAge60.copy(bplStatus = "unknown")
        val missingBplRes = EligibilityEngine.evaluateScheme(profileMissingBpl, ignoaps, 2026)
        assertEquals(EligibilityOutcome.UNKNOWN, missingBplRes.outcome)
        assertTrue(missingBplRes.missingFields.contains("bplStatus"))

        // 3. FAIL beats UNKNOWN in ALL group (Age 59 FAIL + BPL UNKNOWN -> NOT_ELIGIBLE)
        val profileFailAndUnknown = profileAge59.copy(bplStatus = "unknown")
        assertEquals(
            EligibilityOutcome.NOT_ELIGIBLE,
            EligibilityEngine.evaluateScheme(profileFailAndUnknown, ignoaps, 2026).outcome
        )

        // 4. Pending voice confirmation -> UNKNOWN
        val profileUnconfirmedVoice = profileAge60.copy(ageConfirmedViaVoice = false)
        val unconfirmedRes = EligibilityEngine.evaluateScheme(profileUnconfirmedVoice, ignoaps, 2026)
        assertEquals(EligibilityOutcome.UNKNOWN, unconfirmedRes.outcome)
        assertTrue(unconfirmedRes.missingFields.contains("age_voice_confirmation"))

        // 5. External lookup (PM-JAY) -> UNKNOWN with guidance
        val pmjayRes = EligibilityEngine.evaluateScheme(profileAge60, pmjay, 2026)
        assertEquals(EligibilityOutcome.UNKNOWN, pmjayRes.outcome)
        assertTrue(pmjayRes.externalLookupGuidanceEn.isNotEmpty())

        // 6. Same input produces exact same output
        val r1 = EligibilityEngine.evaluateScheme(profileAge60, ignoaps, 2026)
        val r2 = EligibilityEngine.evaluateScheme(profileAge60, ignoaps, 2026)
        assertEquals(r1, r2)

        // 7. State overrides (Uttar Pradesh cap is 56,460; Bihar cap is 60,000)
        val profileIncome58kUp = profileAge60.copy(state = "Uttar Pradesh", annualIncome = 58000)
        val profileIncome58kBihar = profileAge60.copy(state = "Bihar", annualIncome = 58000)
        assertEquals(EligibilityOutcome.NOT_ELIGIBLE, EligibilityEngine.evaluateScheme(profileIncome58kUp, ignoaps, 2026).outcome)
        assertEquals(EligibilityOutcome.ELIGIBLE, EligibilityEngine.evaluateScheme(profileIncome58kBihar, ignoaps, 2026).outcome)

        // 8. Hindi numeral parsing (बासठ = 62, बयासठ = 62, 62 = 62)
        assertEquals(62, SecurityUtils.parseHindiOrEnglishNumber("मेरी उम्र बासठ साल है"))
        assertEquals(62, SecurityUtils.parseHindiOrEnglishNumber("बयासठ"))
        assertEquals(62, SecurityUtils.parseHindiOrEnglishNumber("62"))
        assertEquals("आपकी उम्र 62 साल है, सही है?", SecurityUtils.formatAgeEchoConfirmation(62, "hi"))
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
