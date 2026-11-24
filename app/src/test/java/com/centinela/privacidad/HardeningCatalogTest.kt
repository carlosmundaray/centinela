package com.centinela.privacidad

import com.centinela.privacidad.hardening.BloatwareCatalog
import com.centinela.privacidad.hardening.BloatwarePackageStatus
import com.centinela.privacidad.hardening.PackageActiveState
import com.centinela.privacidad.hardening.PrivateDnsPreset
import com.centinela.privacidad.hardening.RecommendationLevel
import com.centinela.privacidad.hardening.SystemHardeningController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningCatalogTest {

    @Test
    fun catalogHasUniquePackageNames() {
        val names = BloatwareCatalog.entries.map { it.packageName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun catalogIncludesAllCoreHonorMetaAndGoogleTrackers() {
        val expected = listOf(
            "com.facebook.appmanager",
            "com.facebook.services",
            "com.facebook.system",
            "com.aura.oobe.honor",
            "com.hihonor.autoinstallapkfrommcc",
            "android.autoinstalls.config.honor.device",
            "com.hihonor.hiview",
            "com.hihonor.hiviewtunnel",
            "com.hihonor.collectcenter",
            "com.hihonor.android.chr",
            "com.hihonor.tips",
            "com.hihonor.magazine",
            "com.google.android.feedback",
            "com.google.android.gms.location.history",
            "com.google.android.safetycore",
            "com.google.android.ondevicepersonalization.services",
            "com.google.android.partnersetup"
        )
        val recommended = BloatwareCatalog.entries
            .filter { it.recommendation == RecommendationLevel.RECOMMENDED }
            .map { it.packageName }
            .toSet()

        for (pkg in expected) {
            assertTrue("Expected $pkg in RECOMMENDED catalog", recommended.contains(pkg))
        }
    }

    @Test
    fun buildsReversibleAdbBatchScriptsOnlyForMatchingStates() {
        val metaEntry = BloatwareCatalog.entries.first { it.packageName == "com.facebook.appmanager" }
        val hiviewEntry = BloatwareCatalog.entries.first { it.packageName == "com.hihonor.hiview" }
        val tipsEntry = BloatwareCatalog.entries.first { it.packageName == "com.hihonor.tips" }

        val statuses = listOf(
            BloatwarePackageStatus(metaEntry, "Meta App Manager", PackageActiveState.ACTIVE),
            BloatwarePackageStatus(hiviewEntry, "HiView", PackageActiveState.DISABLED_BY_SYSTEM_OR_ADB),
            BloatwarePackageStatus(tipsEntry, "Tips", PackageActiveState.UNINSTALLED_FOR_USER)
        )

        val disableScript = BloatwareCatalog.buildBatchDisableScript(statuses)
        assertEquals("adb shell pm disable-user --user 0 com.facebook.appmanager", disableScript)

        val enableScript = BloatwareCatalog.buildBatchEnableScript(statuses)
        assertTrue(enableScript.contains("adb shell pm enable com.hihonor.hiview"))
        assertTrue(enableScript.contains("adb shell cmd package install-existing com.hihonor.tips"))
        assertFalse(enableScript.contains("com.facebook.appmanager"))
    }

    @Test
    fun resolvesPrivateDnsPresetsAccurately() {
        assertEquals(
            PrivateDnsPreset.MULLVAD_ADBLOCK,
            PrivateDnsPreset.fromSystemValues("hostname", "adblock.dns.mullvad.net")
        )
        assertEquals(
            PrivateDnsPreset.QUAD9,
            PrivateDnsPreset.fromSystemValues("hostname", "dns.quad9.net")
        )
        assertEquals(
            PrivateDnsPreset.AUTOMATIC,
            PrivateDnsPreset.fromSystemValues("opportunistic", null)
        )
        assertEquals(
            PrivateDnsPreset.AUTOMATIC,
            PrivateDnsPreset.fromSystemValues(null, null)
        )
    }

    @Test
    fun writeSecureSettingsAdbCommandTargetsCentinelaPackage() {
        assertEquals(
            "adb shell pm grant com.centinela.privacidad android.permission.WRITE_SECURE_SETTINGS",
            SystemHardeningController.ADB_GRANT_WRITE_SECURE_SETTINGS_COMMAND
        )
    }
}
