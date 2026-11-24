package com.centinela.privacidad

import com.centinela.privacidad.data.AttributionConfidence
import com.centinela.privacidad.monitor.AttributionCandidate
import com.centinela.privacidad.monitor.AttributionDecisionEngine
import com.centinela.privacidad.monitor.CandidateSource
import com.centinela.privacidad.ui.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributionDecisionEngineTest {

    @Test
    fun selectsCurrentForegroundAppWithPermissionAsHighConfidence() {
        val result = AttributionDecisionEngine.selectBestAttribution(
            candidates = listOf(
                AttributionCandidate(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    hasRequiredPermission = true,
                    source = CandidateSource.ACCESSIBILITY_CURRENT,
                    ageMs = 500L
                )
            ),
            isOnHomeScreen = false,
            hasAccessibilityAccess = true,
            hasUsageAccess = true
        )

        assertEquals("com.whatsapp", result.packageName)
        assertEquals("WhatsApp", result.appName)
        assertEquals(AttributionConfidence.HIGH, result.confidence)
    }

    @Test
    fun avoidsBlamingClosedAppWhenUserIsOnHomeScreenAndPicksForegroundService() {
        val result = AttributionDecisionEngine.selectBestAttribution(
            candidates = listOf(
                AttributionCandidate(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    hasRequiredPermission = true,
                    source = CandidateSource.USAGE_FOREGROUND_ACTIVITY,
                    ageMs = 4_000L
                ),
                AttributionCandidate(
                    packageName = "com.recorder.bg",
                    appName = "Grabadora",
                    hasRequiredPermission = true,
                    source = CandidateSource.USAGE_FOREGROUND_SERVICE,
                    ageMs = 15_000L
                )
            ),
            isOnHomeScreen = true,
            hasAccessibilityAccess = true,
            hasUsageAccess = true
        )

        assertEquals("com.recorder.bg", result.packageName)
        assertEquals("Grabadora", result.appName)
        assertEquals(AttributionConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun skipsForegroundAppWithoutRequiredPermission() {
        val result = AttributionDecisionEngine.selectBestAttribution(
            candidates = listOf(
                AttributionCandidate(
                    packageName = "com.calculator",
                    appName = "Calculadora",
                    hasRequiredPermission = false,
                    source = CandidateSource.ACCESSIBILITY_CURRENT,
                    ageMs = 200L
                ),
                AttributionCandidate(
                    packageName = "com.voip.call",
                    appName = "Llamada VoIP",
                    hasRequiredPermission = true,
                    source = CandidateSource.USAGE_FOREGROUND_SERVICE,
                    ageMs = 10_000L
                )
            ),
            isOnHomeScreen = false,
            hasAccessibilityAccess = true,
            hasUsageAccess = true
        )

        assertEquals("com.voip.call", result.packageName)
        assertEquals(AttributionConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun promptsUserWhenNoAttributionPermissionsAreGranted() {
        val result = AttributionDecisionEngine.selectBestAttribution(
            candidates = emptyList(),
            isOnHomeScreen = false,
            hasAccessibilityAccess = false,
            hasUsageAccess = false
        )

        assertNull(result.packageName)
        assertEquals(AttributionConfidence.LOW, result.confidence)
        assertTrue(result.reason.contains("Accesibilidad"))
    }

    @Test
    fun formatsDurationsCorrectly() {
        assertEquals("0s", Formatters.formatDuration(0L))
        assertEquals("14s", Formatters.formatDuration(14_200L))
        assertEquals("2m 05s", Formatters.formatDuration(125_000L))
        assertEquals("1h 02m 03s", Formatters.formatDuration(3_723_000L))
    }
}
