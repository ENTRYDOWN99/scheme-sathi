package com.example

import com.example.domain.EligibilityEngine
import com.example.security.TokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5ParityTest {

    @Test
    fun `TokenStore stores retrieves and clears tokens cleanly in JVM test mode`() {
        val store = TokenStore(context = null)
        store.saveTokens("acc.123", "ref.456", "SUPERVISOR")

        assertEquals("acc.123", store.getAccessToken())
        assertEquals("ref.456", store.getRefreshToken())
        assertEquals("SUPERVISOR", store.getSavedRole())

        store.clear()
        assertNull(store.getAccessToken())
        assertNull(store.getRefreshToken())
        assertEquals("CITIZEN", store.getSavedRole())
    }

    @Test
    fun `EligibilityEngine currentReferenceYear resolves dynamically`() {
        val year = EligibilityEngine.currentReferenceYear()
        assertTrue("Expected realistic year >= 2024, got $year", year >= 2024)
    }
}

