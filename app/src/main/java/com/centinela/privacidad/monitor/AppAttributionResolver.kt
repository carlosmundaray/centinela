package com.centinela.privacidad.monitor

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.centinela.privacidad.data.AppAttribution
import com.centinela.privacidad.data.AttributionConfidence
import com.centinela.privacidad.data.SensorType

/**
 * Pure candidate signal used for unit-testable attribution resolution.
 */
data class AttributionCandidate(
    val packageName: String,
    val appName: String,
    val hasRequiredPermission: Boolean,
    val source: CandidateSource,
    val ageMs: Long
)

enum class CandidateSource {
    ACCESSIBILITY_CURRENT,
    ACCESSIBILITY_RECENT,
    USAGE_FOREGROUND_ACTIVITY,
    USAGE_FOREGROUND_SERVICE
}

object AttributionDecisionEngine {
    const val RECENT_ACTIVITY_WINDOW_MS = 12_000L
    const val RECENT_FGS_WINDOW_MS = 90_000L

    fun selectBestAttribution(
        candidates: List<AttributionCandidate>,
        isOnHomeScreen: Boolean,
        hasAccessibilityAccess: Boolean,
        hasUsageAccess: Boolean
    ): AppAttribution {
        // 1. Live foreground app from Accessibility with permission granted
        val currentForeground = candidates.firstOrNull {
            it.source == CandidateSource.ACCESSIBILITY_CURRENT && it.hasRequiredPermission
        }
        if (currentForeground != null && !isOnHomeScreen) {
            return AppAttribution(
                packageName = currentForeground.packageName,
                appName = currentForeground.appName,
                confidence = AttributionConfidence.HIGH,
                reason = "App abierta en primer plano con permiso concedido"
            )
        }

        // 2. Most recent resumed activity from UsageStats with permission granted (when not on home screen)
        val usageActivity = candidates
            .filter {
                it.source == CandidateSource.USAGE_FOREGROUND_ACTIVITY &&
                    it.hasRequiredPermission &&
                    it.ageMs <= RECENT_ACTIVITY_WINDOW_MS
            }
            .minByOrNull { it.ageMs }
        if (usageActivity != null && !isOnHomeScreen) {
            val confidence = if (usageActivity.ageMs <= 3_500L) {
                AttributionConfidence.HIGH
            } else {
                AttributionConfidence.MEDIUM
            }
            return AppAttribution(
                packageName = usageActivity.packageName,
                appName = usageActivity.appName,
                confidence = confidence,
                reason = "Actividad activa detectada por Estadísticas de Uso"
            )
        }

        // 3. Foreground service recently started with permission granted (crucial for background mic/camera!)
        val fgsCandidate = candidates
            .filter {
                it.source == CandidateSource.USAGE_FOREGROUND_SERVICE &&
                    it.hasRequiredPermission &&
                    it.ageMs <= RECENT_FGS_WINDOW_MS
            }
            .minByOrNull { it.ageMs }
        if (fgsCandidate != null) {
            return AppAttribution(
                packageName = fgsCandidate.packageName,
                appName = fgsCandidate.appName,
                confidence = AttributionConfidence.MEDIUM,
                reason = "Servicio en segundo plano reciente con permiso concedido"
            )
        }

        // 4. Recent app switch from Accessibility (e.g., camera opened just as window transitioned)
        val recentAccessibility = candidates
            .filter {
                it.source == CandidateSource.ACCESSIBILITY_RECENT &&
                    it.hasRequiredPermission &&
                    it.ageMs <= 5_000L
            }
            .minByOrNull { it.ageMs }
        if (recentAccessibility != null && !isOnHomeScreen) {
            return AppAttribution(
                packageName = recentAccessibility.packageName,
                appName = recentAccessibility.appName,
                confidence = AttributionConfidence.MEDIUM,
                reason = "App usada hace unos instantes con permiso concedido"
            )
        }

        // 5. Fallback when no matching candidate found
        val reason = when {
            !hasAccessibilityAccess && !hasUsageAccess ->
                "Activa Accesibilidad o Acceso de Uso para identificar la app"
            isOnHomeScreen ->
                "Activado en segundo plano desde la pantalla de inicio (verifica en Panel de Privacidad)"
            else ->
                "Proceso en segundo plano o del sistema (verifica en Panel de Privacidad de Android)"
        }

        return AppAttribution(
            packageName = null,
            appName = "Segundo plano / Sistema",
            confidence = AttributionConfidence.LOW,
            reason = reason
        )
    }
}

class AppAttributionResolver(private val context: Context) {

    fun resolve(sensorType: SensorType, nowMs: Long = System.currentTimeMillis()): AppAttribution {
        val requiredPermission = if (sensorType.isCamera) {
            Manifest.permission.CAMERA
        } else {
            Manifest.permission.RECORD_AUDIO
        }

        val hasAccessibility = PermissionAuditor.isAccessibilityServiceEnabled(context)
        val hasUsageAccess = PermissionAuditor.hasUsageStatsPermission(context)

        val candidates = mutableListOf<AttributionCandidate>()
        var isOnHomeScreen = false

        // Gather Accessibility signals
        val currentSnapshot = ForegroundTrackerAccessibilityService.currentSnapshot
        if (hasAccessibility && currentSnapshot != null) {
            if (currentSnapshot.isLauncher) {
                isOnHomeScreen = true
            } else {
                candidates.add(
                    AttributionCandidate(
                        packageName = currentSnapshot.packageName,
                        appName = resolveAppName(currentSnapshot.packageName),
                        hasRequiredPermission = hasGrantedPermission(
                            currentSnapshot.packageName,
                            requiredPermission
                        ),
                        source = CandidateSource.ACCESSIBILITY_CURRENT,
                        ageMs = (nowMs - currentSnapshot.timestampMs).coerceAtLeast(0L)
                    )
                )
            }

            ForegroundTrackerAccessibilityService.getRecentSnapshots()
                .filter { !it.isLauncher && it.packageName != currentSnapshot.packageName }
                .forEach { snap ->
                    candidates.add(
                        AttributionCandidate(
                            packageName = snap.packageName,
                            appName = resolveAppName(snap.packageName),
                            hasRequiredPermission = hasGrantedPermission(
                                snap.packageName,
                                requiredPermission
                            ),
                            source = CandidateSource.ACCESSIBILITY_RECENT,
                            ageMs = (nowMs - snap.timestampMs).coerceAtLeast(0L)
                        )
                    )
                }
        }

        // Gather UsageStatsManager signals
        if (hasUsageAccess) {
            candidates.addAll(queryUsageCandidates(requiredPermission, nowMs))
        }

        return AttributionDecisionEngine.selectBestAttribution(
            candidates = candidates,
            isOnHomeScreen = isOnHomeScreen,
            hasAccessibilityAccess = hasAccessibility,
            hasUsageAccess = hasUsageAccess
        )
    }

    private fun queryUsageCandidates(
        requiredPermission: String,
        nowMs: Long
    ): List<AttributionCandidate> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val lookbackStart = nowMs - AttributionDecisionEngine.RECENT_FGS_WINDOW_MS
        val usageEvents = runCatching {
            usageStatsManager.queryEvents(lookbackStart, nowMs)
        }.getOrNull() ?: return emptyList()

        val event = UsageEvents.Event()
        val latestActivityByPkg = mutableMapOf<String, Long>()
        val latestFgsByPkg = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName || pkg == "com.android.systemui") continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val prev = latestActivityByPkg[pkg] ?: 0L
                    if (event.timeStamp > prev) {
                        latestActivityByPkg[pkg] = event.timeStamp
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        event.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START
                    ) {
                        val prev = latestFgsByPkg[pkg] ?: 0L
                        if (event.timeStamp > prev) {
                            latestFgsByPkg[pkg] = event.timeStamp
                        }
                    }
                }
            }
        }

        val result = mutableListOf<AttributionCandidate>()
        latestActivityByPkg.forEach { (pkg, ts) ->
            result.add(
                AttributionCandidate(
                    packageName = pkg,
                    appName = resolveAppName(pkg),
                    hasRequiredPermission = hasGrantedPermission(pkg, requiredPermission),
                    source = CandidateSource.USAGE_FOREGROUND_ACTIVITY,
                    ageMs = (nowMs - ts).coerceAtLeast(0L)
                )
            )
        }
        latestFgsByPkg.forEach { (pkg, ts) ->
            result.add(
                AttributionCandidate(
                    packageName = pkg,
                    appName = resolveAppName(pkg),
                    hasRequiredPermission = hasGrantedPermission(pkg, requiredPermission),
                    source = CandidateSource.USAGE_FOREGROUND_SERVICE,
                    ageMs = (nowMs - ts).coerceAtLeast(0L)
                )
            )
        }
        return result
    }

    fun resolveAppName(packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    fun hasGrantedPermission(packageName: String, permission: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            if (pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED) {
                return true
            }
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val requested = pkgInfo.requestedPermissions ?: return false
            val flags = pkgInfo.requestedPermissionsFlags ?: return false
            for (i in requested.indices) {
                if (requested[i] == permission &&
                    (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                ) {
                    return true
                }
            }
            false
        }.getOrDefault(false)
    }
}
