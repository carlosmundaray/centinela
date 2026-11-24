package com.centinela.privacidad.hardening

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.centinela.privacidad.blocker.CentinelaDeviceAdminReceiver

class SystemHardeningController(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val pm = appContext.packageManager
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = CentinelaDeviceAdminReceiver.getComponentName(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasWriteSecureSettingsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDeviceOwner(): Boolean {
        return runCatching { dpm.isDeviceOwnerApp(appContext.packageName) }.getOrDefault(false)
    }

    fun getSnapshot(): HardeningSnapshot {
        val hasWritePerm = hasWriteSecureSettingsPermission()
        val deviceOwner = isDeviceOwner()
        return HardeningSnapshot(
            hasWriteSecureSettings = hasWritePerm,
            isDeviceOwner = deviceOwner,
            systemSettings = readSystemSettingsState(),
            detectedPackages = scanInstalledTrackerPackages(deviceOwner)
        )
    }

    private fun readSystemSettingsState(): SystemSettingsHardeningState {
        val dnsMode = runCatching { Settings.Global.getString(resolver, KEY_PRIVATE_DNS_MODE) }.getOrNull()
        val dnsSpec = runCatching { Settings.Global.getString(resolver, KEY_PRIVATE_DNS_SPECIFIER) }.getOrNull()

        val telemetryChecks = listOf(
            readGlobalString("send_action_app_error") == "0",
            readGlobalString("upload_apk_enable") == "0",
            readGlobalString("intelli_user_experience_enabled") == "0",
            readGlobalString("yoyo_assistant_user_experience_0") == "0",
            readSecureString("user_experience_involved") == "0",
            readSecureString("product_experience_involved") == "0",
            readSecureString("user_experience_hiview_location_permission") == "0",
            readSecureString("user_experience_hiview_usage_access_permission") == "0"
        )

        val adsChecks = listOf(
            readSecureString("oaid_limit_state").equals("true", ignoreCase = true),
            readSecureString("state_of_ads_switch") == "0",
            readSecureString("location_honor_ads") == "0",
            readSecureString("limit_ad_tracking") == "1"
        )

        val wifiScan = readGlobalString("wifi_scan_always_enabled") != "0"
        val bleScan = readGlobalString("ble_scan_always_enabled") == "1"

        return SystemSettingsHardeningState(
            privateDnsMode = dnsMode,
            privateDnsSpecifier = dnsSpec,
            telemetryDisabledCount = telemetryChecks.count { it },
            telemetryTotalCount = telemetryChecks.size,
            adsDisabledCount = adsChecks.count { it },
            adsTotalCount = adsChecks.size,
            wifiScanAlwaysEnabled = wifiScan,
            bleScanAlwaysEnabled = bleScan
        )
    }

    private fun scanInstalledTrackerPackages(isDeviceOwner: Boolean): List<BloatwarePackageStatus> {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        return BloatwareCatalog.entries.mapNotNull { entry ->
            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        entry.packageName,
                        PackageManager.ApplicationInfoFlags.of(flags.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(entry.packageName, flags)
                }
            }.getOrNull() ?: return@mapNotNull null

            val isHiddenByOwner = if (isDeviceOwner) {
                runCatching { dpm.isApplicationHidden(adminComponent, entry.packageName) }.getOrDefault(false)
            } else {
                false
            }
            val isInstalledForUser = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
            val enabledSetting = runCatching {
                pm.getApplicationEnabledSetting(entry.packageName)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)

            val isDisabledBySystemOrAdb = !appInfo.enabled ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

            val state = when {
                isHiddenByOwner -> PackageActiveState.HIDDEN_BY_DEVICE_OWNER
                !isInstalledForUser -> PackageActiveState.UNINSTALLED_FOR_USER
                isDisabledBySystemOrAdb -> PackageActiveState.DISABLED_BY_SYSTEM_OR_ADB
                else -> PackageActiveState.ACTIVE
            }

            val resolvedLabel = runCatching {
                pm.getApplicationLabel(appInfo).toString().takeIf {
                    it.isNotBlank() && it != entry.packageName
                }
            }.getOrNull() ?: entry.displayName

            BloatwarePackageStatus(
                entry = entry,
                resolvedAppName = resolvedLabel,
                state = state
            )
        }.sortedWith(
            compareBy<BloatwarePackageStatus> { it.entry.recommendation.ordinal }
                .thenBy { it.isDisabled }
                .thenBy { it.entry.category.ordinal }
                .thenBy { it.entry.displayName.lowercase() }
        )
    }

    fun setPrivateDns(preset: PrivateDnsPreset): HardeningActionResult {
        if (!canModifySystemSettings()) {
            return requiresSecureSettingsError()
        }
        return runCatching {
            if (preset == PrivateDnsPreset.AUTOMATIC || preset.hostname == null) {
                putGlobalString(KEY_PRIVATE_DNS_MODE, "opportunistic")
                putGlobalString(KEY_PRIVATE_DNS_SPECIFIER, null)
                HardeningActionResult.Success("DNS Privado restaurado a Automático.")
            } else {
                putGlobalString(KEY_PRIVATE_DNS_MODE, "hostname")
                putGlobalString(KEY_PRIVATE_DNS_SPECIFIER, preset.hostname)
                HardeningActionResult.Success("DNS Privado configurado a ${preset.label} (${preset.hostname}).")
            }
        }.getOrElse { err ->
            HardeningActionResult.Error("No se pudo cambiar el DNS Privado: ${err.message}")
        }
    }

    fun setTelemetryBlocked(blocked: Boolean): HardeningActionResult {
        if (!canModifySystemSettings()) {
            return requiresSecureSettingsError()
        }
        val value = if (blocked) "0" else "1"
        return runCatching {
            putGlobalString("send_action_app_error", value)
            putGlobalString("upload_apk_enable", value)
            putGlobalString("intelli_user_experience_enabled", value)
            putGlobalString("yoyo_assistant_user_experience_0", value)

            putSecureString("user_experience_involved", value)
            putSecureString("product_experience_involved", value)
            putSecureString("user_experience_hiview_location_permission", value)
            putSecureString("user_experience_hiview_usage_access_permission", value)

            if (blocked) {
                HardeningActionResult.Success("Telemetría, HiView y envío automático de APKs/errores desactivados.")
            } else {
                HardeningActionResult.Success("Ajustes de telemetría restaurados a sus valores predeterminados.")
            }
        }.getOrElse { err ->
            HardeningActionResult.Error("No se pudo modificar la telemetría: ${err.message}")
        }
    }

    fun setAdTrackingBlocked(blocked: Boolean): HardeningActionResult {
        if (!canModifySystemSettings()) {
            return requiresSecureSettingsError()
        }
        return runCatching {
            putSecureString("oaid_limit_state", if (blocked) "true" else "false")
            putSecureString("state_of_ads_switch", if (blocked) "0" else "1")
            putSecureString("location_honor_ads", if (blocked) "0" else "1")
            putSecureString("limit_ad_tracking", if (blocked) "1" else "0")

            if (blocked) {
                HardeningActionResult.Success("Publicidad personalizada, ubicación para anuncios y OAID limitados.")
            } else {
                HardeningActionResult.Success("Ajustes de publicidad restaurados.")
            }
        }.getOrElse { err ->
            HardeningActionResult.Error("No se pudo modificar el rastreo publicitario: ${err.message}")
        }
    }

    fun setBackgroundScanningBlocked(blocked: Boolean): HardeningActionResult {
        if (!canModifySystemSettings()) {
            return requiresSecureSettingsError()
        }
        val value = if (blocked) "0" else "1"
        return runCatching {
            putGlobalString("wifi_scan_always_enabled", value)
            putGlobalString("ble_scan_always_enabled", value)
            if (blocked) {
                HardeningActionResult.Success("Escaneo permanente de Wi-Fi y Bluetooth desactivado.")
            } else {
                HardeningActionResult.Success("Escaneo en segundo plano de Wi-Fi y Bluetooth reactivado.")
            }
        }.getOrElse { err ->
            HardeningActionResult.Error("No se pudo cambiar el escaneo en segundo plano: ${err.message}")
        }
    }

    fun applyFullSystemHardening(
        dnsPreset: PrivateDnsPreset = PrivateDnsPreset.MULLVAD_ADBLOCK
    ): HardeningActionResult {
        if (!canModifySystemSettings()) {
            return requiresSecureSettingsError()
        }
        val results = listOf(
            setPrivateDns(dnsPreset),
            setTelemetryBlocked(true),
            setAdTrackingBlocked(true),
            setBackgroundScanningBlocked(true)
        )
        val firstError = results.firstOrNull { it is HardeningActionResult.Error }
        if (firstError != null) return firstError

        val hiddenExtra = if (isDeviceOwner()) {
            val pkgRes = disableAllRecommendedPackages()
            if (pkgRes is HardeningActionResult.Success) " ${pkgRes.message}" else ""
        } else {
            ""
        }
        return HardeningActionResult.Success(
            "Blindaje de sistema aplicado: DNS ${dnsPreset.label}, telemetría, anuncios y escaneo bloqueados.$hiddenExtra"
        )
    }

    fun revertSystemHardening(): HardeningActionResult {
        if (!canModifySystemSettings()) {
            return requiresSecureSettingsError()
        }
        setPrivateDns(PrivateDnsPreset.AUTOMATIC)
        setTelemetryBlocked(false)
        setAdTrackingBlocked(false)
        setBackgroundScanningBlocked(false)
        if (isDeviceOwner()) {
            restoreAllCentinelaHiddenPackages()
        }
        return HardeningActionResult.Success("Ajustes de blindaje revertidos a su estado estándar.")
    }

    fun setPackageDisabled(packageName: String, disable: Boolean): HardeningActionResult {
        if (!isDeviceOwner()) {
            return HardeningActionResult.RequiresPermission(
                "Para desactivar o reactivar paquetes del sistema con 1 toque necesitas Modo Propietario, o usa «Copiar ADB» / «Ajustes»."
            )
        }
        return runCatching {
            if (disable) {
                val ok = dpm.setApplicationHidden(adminComponent, packageName, true)
                if (ok) {
                    recordCentinelaHiddenPackage(packageName, hidden = true)
                    HardeningActionResult.Success("Paquete desactivado (100 % reversible): $packageName")
                } else {
                    HardeningActionResult.Error("Android protegió el paquete $packageName y no permitió ocultarlo.")
                }
            } else {
                val wasHiddenByOwner = dpm.isApplicationHidden(adminComponent, packageName)
                if (wasHiddenByOwner) {
                    val ok = dpm.setApplicationHidden(adminComponent, packageName, false)
                    if (ok) {
                        recordCentinelaHiddenPackage(packageName, hidden = false)
                        HardeningActionResult.Success("Paquete reactivado correctamente: $packageName")
                    } else {
                        HardeningActionResult.Error("No se pudo reactivar $packageName.")
                    }
                } else {
                    HardeningActionResult.RequiresPermission(
                        "$packageName fue desactivado por ADB o Ajustes. Copia el comando ADB «pm enable» o ábrelo en Ajustes para reactivarlo."
                    )
                }
            }
        }.getOrElse { err ->
            HardeningActionResult.Error("Error modificando $packageName: ${err.message}")
        }
    }

    fun disableAllRecommendedPackages(): HardeningActionResult {
        if (!isDeviceOwner()) {
            return HardeningActionResult.RequiresPermission(
                "Activa Modo Propietario para desactivar paquetes en lote con 1 toque, o usa «Copiar script ADB»."
            )
        }
        val targets = scanInstalledTrackerPackages(isDeviceOwner = true)
            .filter { it.entry.recommendation == RecommendationLevel.RECOMMENDED && !it.isDisabled }

        if (targets.isEmpty()) {
            return HardeningActionResult.Success("Todos los paquetes recomendados detectados ya están desactivados.")
        }

        var count = 0
        for (item in targets) {
            val ok = runCatching {
                dpm.setApplicationHidden(adminComponent, item.entry.packageName, true)
            }.getOrDefault(false)
            if (ok) {
                recordCentinelaHiddenPackage(item.entry.packageName, hidden = true)
                count++
            }
        }
        return HardeningActionResult.Success("$count paquetes de rastreo desactivados (100 % reversibles).")
    }

    fun restoreAllCentinelaHiddenPackages(): HardeningActionResult {
        if (!isDeviceOwner()) {
            return HardeningActionResult.RequiresPermission(
                "Usa «Copiar script ADB (reactivar)» para restaurar paquetes desactivados por ADB."
            )
        }
        val hiddenTracked = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()).orEmpty().toSet()
        val catalogHidden = scanInstalledTrackerPackages(isDeviceOwner = true)
            .filter { it.state == PackageActiveState.HIDDEN_BY_DEVICE_OWNER }
            .map { it.entry.packageName }
        val allToRestore = hiddenTracked + catalogHidden

        var restored = 0
        for (pkg in allToRestore) {
            val ok = runCatching { dpm.setApplicationHidden(adminComponent, pkg, false) }.getOrDefault(false)
            if (ok) {
                recordCentinelaHiddenPackage(pkg, hidden = false)
                restored++
            }
        }
        return if (restored > 0) {
            HardeningActionResult.Success("$restored paquetes ocultos por Centinela fueron reactivados.")
        } else {
            HardeningActionResult.Success("No había paquetes ocultos por Modo Propietario (los desactivados por ADB se reactivan con el script ADB).")
        }
    }

    fun openWirelessOrDnsSettings(context: Context) {
        val candidates = listOf(
            Intent("android.settings.NETWORK_OPERATOR_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }
    }

    private fun canModifySystemSettings(): Boolean {
        return hasWriteSecureSettingsPermission() || isDeviceOwner()
    }

    private fun requiresSecureSettingsError(): HardeningActionResult.RequiresPermission {
        return HardeningActionResult.RequiresPermission(
            "Otorga WRITE_SECURE_SETTINGS con 1 comando ADB (sin root ni quitar cuentas) para aplicar estos ajustes con 1 toque."
        )
    }

    private fun readGlobalString(key: String): String? {
        return runCatching { Settings.Global.getString(resolver, key) }.getOrNull()
    }

    private fun readSecureString(key: String): String? {
        return runCatching { Settings.Secure.getString(resolver, key) }.getOrNull()
    }

    private fun putGlobalString(key: String, value: String?) {
        if (hasWriteSecureSettingsPermission()) {
            Settings.Global.putString(resolver, key, value)
        } else if (isDeviceOwner()) {
            dpm.setGlobalSetting(adminComponent, key, value)
        }
    }

    private fun putSecureString(key: String, value: String?) {
        if (hasWriteSecureSettingsPermission()) {
            Settings.Secure.putString(resolver, key, value)
        } else if (isDeviceOwner()) {
            dpm.setSecureSetting(adminComponent, key, value)
        }
    }

    private fun recordCentinelaHiddenPackage(packageName: String, hidden: Boolean) {
        val current = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()).orEmpty().toMutableSet()
        if (hidden) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, current).apply()
    }

    companion object {
        private const val PREFS_NAME = "centinela_hardening_prefs"
        private const val KEY_HIDDEN_PACKAGES = "hidden_packages"
        private const val KEY_PRIVATE_DNS_MODE = "private_dns_mode"
        private const val KEY_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

        const val ADB_GRANT_WRITE_SECURE_SETTINGS_COMMAND =
            "adb shell pm grant com.centinela.privacidad android.permission.WRITE_SECURE_SETTINGS"
    }
}
