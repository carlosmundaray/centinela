package com.centinela.privacidad.hardening

enum class TrackerCategory(val label: String) {
    META("Meta oculto"),
    HONOR_HUAWEI("Honor / Huawei"),
    GOOGLE("Google Telemetría"),
    OEM_CARRIER("Fabricantes / Otros")
}

enum class RecommendationLevel(val label: String) {
    RECOMMENDED("Seguro"),
    OPTIONAL("Opcional")
}

enum class PackageActiveState(val label: String) {
    ACTIVE("Activo"),
    DISABLED_BY_SYSTEM_OR_ADB("Desactivado (ADB/Sistema)"),
    HIDDEN_BY_DEVICE_OWNER("Desactivado (Centinela)"),
    UNINSTALLED_FOR_USER("Desinstalado (Usuario)");

    val isDisabled: Boolean
        get() = this != ACTIVE
}

enum class PrivateDnsPreset(
    val label: String,
    val hostname: String?,
    val description: String
) {
    MULLVAD_ADBLOCK(
        label = "Mullvad Adblock",
        hostname = "adblock.dns.mullvad.net",
        description = "Sin registros. Bloquea rastreadores, telemetría y anuncios en todo el sistema."
    ),
    QUAD9(
        label = "Quad9 Seguridad",
        hostname = "dns.quad9.net",
        description = "Sin registros. Bloquea malware y phishing sin bloquear anuncios."
    ),
    ADGUARD(
        label = "AdGuard DNS",
        hostname = "dns.adguard-dns.com",
        description = "Bloquea anuncios y rastreadores en apps y navegadores."
    ),
    AUTOMATIC(
        label = "Automático (Sistema)",
        hostname = null,
        description = "Restaura el DNS oportunista predeterminado de Android."
    );

    companion object {
        fun fromSystemValues(mode: String?, specifier: String?): PrivateDnsPreset? {
            if (mode == null || mode == "opportunistic" || mode == "off") return AUTOMATIC
            if (mode != "hostname") return null
            return entries.firstOrNull {
                it.hostname != null && it.hostname.equals(specifier?.trim(), ignoreCase = true)
            }
        }
    }
}

data class TrackerPackageEntry(
    val packageName: String,
    val displayName: String,
    val category: TrackerCategory,
    val recommendation: RecommendationLevel,
    val description: String
)

data class BloatwarePackageStatus(
    val entry: TrackerPackageEntry,
    val resolvedAppName: String,
    val state: PackageActiveState
) {
    val isDisabled: Boolean
        get() = state.isDisabled
}

data class SystemSettingsHardeningState(
    val privateDnsMode: String?,
    val privateDnsSpecifier: String?,
    val telemetryDisabledCount: Int,
    val telemetryTotalCount: Int,
    val adsDisabledCount: Int,
    val adsTotalCount: Int,
    val wifiScanAlwaysEnabled: Boolean,
    val bleScanAlwaysEnabled: Boolean
) {
    val isPrivateDnsHardened: Boolean
        get() = privateDnsMode == "hostname" && !privateDnsSpecifier.isNullOrBlank()

    val isTelemetryBlocked: Boolean
        get() = telemetryTotalCount > 0 && telemetryDisabledCount == telemetryTotalCount

    val isAdTrackingBlocked: Boolean
        get() = adsTotalCount > 0 && adsDisabledCount == adsTotalCount

    val isBackgroundScanningBlocked: Boolean
        get() = !wifiScanAlwaysEnabled && !bleScanAlwaysEnabled

    val activePrivateDnsPreset: PrivateDnsPreset?
        get() = PrivateDnsPreset.fromSystemValues(privateDnsMode, privateDnsSpecifier)
}

data class HardeningSnapshot(
    val hasWriteSecureSettings: Boolean,
    val isDeviceOwner: Boolean,
    val systemSettings: SystemSettingsHardeningState,
    val detectedPackages: List<BloatwarePackageStatus>
) {
    val recommendedPackages: List<BloatwarePackageStatus>
        get() = detectedPackages.filter { it.entry.recommendation == RecommendationLevel.RECOMMENDED }

    val disabledRecommendedCount: Int
        get() = recommendedPackages.count { it.isDisabled }

    val disabledTotalCount: Int
        get() = detectedPackages.count { it.isDisabled }
}

sealed class HardeningActionResult {
    data class Success(val message: String) : HardeningActionResult()
    data class RequiresPermission(val message: String) : HardeningActionResult()
    data class Error(val message: String) : HardeningActionResult()
}

object BloatwareCatalog {

    val entries: List<TrackerPackageEntry> = listOf(
        // Meta / Facebook hidden system stubs (safe even if user uses WhatsApp/Instagram/Facebook)
        TrackerPackageEntry(
            packageName = "com.facebook.appmanager",
            displayName = "Meta App Manager",
            category = TrackerCategory.META,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Actualizador silencioso y telemetría de Meta en segundo plano. Desactivarlo no afecta a WhatsApp ni Instagram."
        ),
        TrackerPackageEntry(
            packageName = "com.facebook.services",
            displayName = "Meta Services",
            category = TrackerCategory.META,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Servicios persistentes de recolección de datos de Meta preinstalados en el sistema."
        ),
        TrackerPackageEntry(
            packageName = "com.facebook.system",
            displayName = "Meta App Installer",
            category = TrackerCategory.META,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Instalador oculto de sistema de Meta."
        ),

        // Honor / Huawei telemetry & adware
        TrackerPackageEntry(
            packageName = "com.aura.oobe.honor",
            displayName = "IronSource / Aura (Honor)",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Servicio publicitario que sugiere o instala aplicaciones patrocinadas automáticamente."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.autoinstallapkfrommcc",
            displayName = "Autoinstalador regional Honor",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Instala APKs automáticamente según operadora o región."
        ),
        TrackerPackageEntry(
            packageName = "android.autoinstalls.config.honor.device",
            displayName = "Config. autoinstalación Honor",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Configuración de preinstalación automática de apps del fabricante."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.hiview",
            displayName = "Honor HiView",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Recopila registros, estadísticas de uso y diagnósticos del teléfono."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.hiviewtunnel",
            displayName = "Honor HiView Tunnel",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Envía en segundo plano los paquetes de telemetría de HiView a servidores de Honor."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.collectcenter",
            displayName = "Honor Collect Center",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Centro de recopilación de métricas y telemetría del dispositivo."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.android.chr",
            displayName = "Honor CHR Diagnostics",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Servicio de reportes de diagnóstico de red y telemetría."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.tips",
            displayName = "Honor Tips",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Notificaciones promocionales y consejos del sistema."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.magazine",
            displayName = "Desbloqueo Revista Honor",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Carrusel de fondos y contenido patrocinado en la pantalla de bloqueo."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.gamecenter",
            displayName = "Honor Game Center",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "Tienda de juegos y notificaciones promocionales de Honor."
        ),
        TrackerPackageEntry(
            packageName = "com.hihonor.appmarket",
            displayName = "Honor App Market",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "Tienda de aplicaciones alternativa de Honor."
        ),
        // Huawei equivalents for multi-device compatibility
        TrackerPackageEntry(
            packageName = "com.huawei.hiview",
            displayName = "Huawei HiView",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Recolección de registros y diagnósticos de Huawei."
        ),
        TrackerPackageEntry(
            packageName = "com.huawei.hiviewtunnel",
            displayName = "Huawei HiView Tunnel",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Envío en segundo plano de telemetría HiView."
        ),
        TrackerPackageEntry(
            packageName = "com.huawei.android.chr",
            displayName = "Huawei CHR Diagnostics",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Reportes de diagnóstico y telemetría."
        ),
        TrackerPackageEntry(
            packageName = "com.huawei.tips",
            displayName = "Huawei Tips",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Sugerencias y notificaciones promocionales."
        ),
        TrackerPackageEntry(
            packageName = "com.huawei.magazine",
            displayName = "Huawei Magazine Unlock",
            category = TrackerCategory.HONOR_HUAWEI,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Carrusel promocional en pantalla de bloqueo."
        ),

        // Google telemetry / ad personalization / SafetyCore (safe: keeps Play Store & banking apps intact)
        TrackerPackageEntry(
            packageName = "com.google.android.feedback",
            displayName = "Google Feedback",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Envío de comentarios e informes de fallos a Google."
        ),
        TrackerPackageEntry(
            packageName = "com.google.android.gms.location.history",
            displayName = "Historial de ubicaciones",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Servicio de historial continuo de ubicaciones de Google."
        ),
        TrackerPackageEntry(
            packageName = "com.google.android.safetycore",
            displayName = "Google SafetyCore",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Servicio silencioso de clasificación local de imágenes y contenido."
        ),
        TrackerPackageEntry(
            packageName = "com.google.android.ondevicepersonalization.services",
            displayName = "Personalización publicitaria",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Perfilado en el dispositivo para anuncios personalizados."
        ),
        TrackerPackageEntry(
            packageName = "com.google.android.partnersetup",
            displayName = "Google Partner Setup",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Configuración promocional de socios y operadoras."
        ),
        TrackerPackageEntry(
            packageName = "com.google.android.adservices.api",
            displayName = "Android AdServices (Privacy Sandbox)",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "API publicitaria de seguimiento temático de Android."
        ),
        TrackerPackageEntry(
            packageName = "com.android.hotwordenrollment.okgoogle",
            displayName = "Escucha «Ok Google»",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "Activación pasiva por voz para el asistente de Google."
        ),
        TrackerPackageEntry(
            packageName = "com.android.hotwordenrollment.xgoogle",
            displayName = "Escucha «Hey Google»",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "Detección pasiva de palabra clave del asistente."
        ),
        TrackerPackageEntry(
            packageName = "com.google.android.apps.wellbeing",
            displayName = "Bienestar Digital",
            category = TrackerCategory.GOOGLE,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "Monitorea el tiempo de uso continuo de todas tus aplicaciones."
        ),

        // Other OEM / carrier trackers
        TrackerPackageEntry(
            packageName = "com.microsoft.appmanager",
            displayName = "Enlace a Windows",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.OPTIONAL,
            description = "Sincronización en segundo plano con PC Windows."
        ),
        TrackerPackageEntry(
            packageName = "com.miui.msa.global",
            displayName = "MIUI System Ads (msa)",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Motor de anuncios del sistema en Xiaomi / Redmi / POCO."
        ),
        TrackerPackageEntry(
            packageName = "com.miui.analytics",
            displayName = "MIUI Analytics",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Telemetría y analíticas persistentes de Xiaomi."
        ),
        TrackerPackageEntry(
            packageName = "com.miui.daemon",
            displayName = "MIUI Daemon",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Recolección de estadísticas de uso en dispositivos Xiaomi."
        ),
        TrackerPackageEntry(
            packageName = "com.samsung.android.rubin.app",
            displayName = "Samsung Customization Service",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Perfilado de comportamiento y personalización de Samsung."
        ),
        TrackerPackageEntry(
            packageName = "com.sec.android.diagmonagent",
            displayName = "Samsung DiagMonAgent",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Envío de diagnósticos y telemetría en dispositivos Samsung."
        ),
        TrackerPackageEntry(
            packageName = "com.aura.oobe.samsung",
            displayName = "Samsung AppCloud / IronSource",
            category = TrackerCategory.OEM_CARRIER,
            recommendation = RecommendationLevel.RECOMMENDED,
            description = "Instalador promocional de aplicaciones de terceros."
        )
    )

    fun buildDisableCommand(packageName: String): String {
        return "adb shell pm disable-user --user 0 $packageName"
    }

    fun buildEnableCommand(packageName: String): String {
        return "adb shell pm enable $packageName"
    }

    fun buildRestoreUninstalledCommand(packageName: String): String {
        return "adb shell cmd package install-existing $packageName"
    }

    fun buildBatchDisableScript(packages: List<BloatwarePackageStatus>): String {
        val activeTargets = packages.filter { !it.isDisabled }
        if (activeTargets.isEmpty()) return ""
        return activeTargets.joinToString("\n") { buildDisableCommand(it.entry.packageName) }
    }

    fun buildBatchEnableScript(packages: List<BloatwarePackageStatus>): String {
        val disabledTargets = packages.filter { it.isDisabled }
        if (disabledTargets.isEmpty()) return ""
        return disabledTargets.joinToString("\n") { item ->
            if (item.state == PackageActiveState.UNINSTALLED_FOR_USER) {
                buildRestoreUninstalledCommand(item.entry.packageName)
            } else {
                buildEnableCommand(item.entry.packageName)
            }
        }
    }
}
