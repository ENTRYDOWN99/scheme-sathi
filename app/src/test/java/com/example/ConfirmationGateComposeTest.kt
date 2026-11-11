package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.ReviewConfirmScreen
import com.example.ui.theme.SchemeSaathiTheme
import com.example.ui.viewmodel.FormFieldProvenance
import com.example.util.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConfirmationGateComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationGate_requiresBothDeclarationsBeforeUnlockingHandoff() {
        val expectedHash = SecurityUtils.computeSnapshotHash(
            schemeId = "PM-KISAN",
            ruleVersion = "v1.0.0-illustrative",
            applicantName = "Sita Devi",
            stateDistrict = "Uttar Pradesh - Varanasi",
            bankIfscSummary = "SBI DBT Enabled",
            checklistSummary = "Land Record: HAVE"
        )
        var confirmedHash: String? = null

        composeRule.setContent {
            var uncertainAck by remember { mutableStateOf(false) }
            var explicitConfirm by remember { mutableStateOf(false) }
            SchemeSaathiTheme {
                ReviewConfirmScreen(
                    language = "en",
                    selectedSchemeId = "PM-KISAN",
                    applicantNameField = FormFieldProvenance("Sita Devi", "profile"),
                    stateDistrictField = FormFieldProvenance("Uttar Pradesh - Varanasi", "profile"),
                    bankSummaryField = FormFieldProvenance("SBI DBT Enabled", "profile"),
                    checklistSummary = "Land Record: HAVE",
                    snapshotHash = expectedHash,
                    uncertainAcknowledged = uncertainAck,
                    explicitCitizenConfirmed = explicitConfirm,
                    onToggleUncertainAck = { uncertainAck = it },
                    onToggleExplicitConfirm = { explicitConfirm = it },
                    onConfirmAndGenerateHandoff = { confirmedHash = it },
                    onNavigate = {}
                )
            }
        }

        // Critical disclosure is visible and confirm button starts disabled
        composeRule
            .onNodeWithText("does NOT automatically submit applications", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("confirm_human_gate_btn")
            .performScrollTo()
            .assertIsNotEnabled()

        // Checking only first box keeps button disabled
        composeRule
            .onNodeWithTag("ack_uncertain_checkbox")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithTag("confirm_human_gate_btn")
            .performScrollTo()
            .assertIsNotEnabled()

        // Checking second box unlocks button and dispatches exact snapshot SHA-256
        composeRule
            .onNodeWithTag("explicit_citizen_confirm_checkbox")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithTag("confirm_human_gate_btn")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(expectedHash, confirmedHash)
    }

    @Test
    fun confirmationGate_rendersHindiDisclosureLabels() {
        composeRule.setContent {
            SchemeSaathiTheme {
                ReviewConfirmScreen(
                    language = "hi",
                    selectedSchemeId = "PM-KISAN",
                    applicantNameField = FormFieldProvenance("सीता देवी", "profile"),
                    stateDistrictField = FormFieldProvenance("Uttar Pradesh - Varanasi", "profile"),
                    bankSummaryField = FormFieldProvenance("SBI DBT", "profile"),
                    checklistSummary = "भूलेख: उपलब्ध",
                    snapshotHash = "hash_1234567890abcdef",
                    uncertainAcknowledged = false,
                    explicitCitizenConfirmed = false,
                    onToggleUncertainAck = {},
                    onToggleExplicitConfirm = {},
                    onConfirmAndGenerateHandoff = {},
                    onNavigate = {}
                )
            }
        }

        composeRule
            .onNodeWithText("मानव पुष्टि गेट", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("confirm_human_gate_btn")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("पुष्टि करें और गाइडेड हैंडऑफ पैक सहेजें")
            .assertIsDisplayed()
    }
}

