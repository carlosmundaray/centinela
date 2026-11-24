package com.centinela.privacidad.monitor

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.centinela.privacidad.blocker.SensorBlockerController
import com.centinela.privacidad.data.AuditedAppInfo

object PermissionAuditor {

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (ForegroundTrackerAccessibilityService.isServiceConnected) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val expectedPackage = context.packageName
        val expectedClass = ForegroundTrackerAccessibilityService::class.java.name
        return enabledServices.any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo
            serviceInfo?.packageName == expectedPackage &&
                (serviceInfo.name == expectedClass ||
                    serviceInfo.name.endsWith("ForegroundTrackerAccessibilityService"))
        }
    }

    fun auditAppsWithSensorPermissions(context: Context): List<AuditedAppInfo> {
        val pm = context.packageManager
        val blocker = SensorBlockerController(context)
        val packages = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }.getOrDefault(emptyList())

        return packages.mapNotNull { pkgInfo ->
            if (pkgInfo.packageName == context.packageName) return@mapNotNull null

            val requested = pkgInfo.requestedPermissions ?: return@mapNotNull null
            val flags = pkgInfo.requestedPermissionsFlags ?: return@mapNotNull null

            var hasCamera = false
            var hasMic = false
            var declaresCamera = false
            var declaresMic = false
            for (i in requested.indices) {
                val granted = (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                when (requested[i]) {
                    Manifest.permission.CAMERA -> {
                        declaresCamera = true
                        if (granted) hasCamera = true
                    }
                    Manifest.permission.RECORD_AUDIO -> {
                        declaresMic = true
                        if (granted) hasMic = true
                    }
                }
            }

            val camBlockedByPolicy = declaresCamera &&
                blocker.isAppPermissionDeniedByPolicy(pkgInfo.packageName, Manifest.permission.CAMERA)
            val micBlockedByPolicy = declaresMic &&
                blocker.isAppPermissionDeniedByPolicy(pkgInfo.packageName, Manifest.permission.RECORD_AUDIO)

            if (!hasCamera && !hasMic && !camBlockedByPolicy && !micBlockedByPolicy) {
                return@mapNotNull null
            }

            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = runCatching {
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkgInfo.packageName)

            AuditedAppInfo(
                packageName = pkgInfo.packageName,
                appName = label,
                hasCameraPermission = hasCamera,
                hasMicPermission = hasMic,
                isSystemApp = isSystem,
                cameraBlockedByPolicy = camBlockedByPolicy,
                micBlockedByPolicy = micBlockedByPolicy
            )
        }.sortedWith(
            compareBy<AuditedAppInfo> { it.isSystemApp }
                .thenByDescending { it.hasCameraPermission && it.hasMicPermission }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAndroidPrivacyDashboard(context: Context) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Intent("android.intent.action.REVIEW_PERMISSION_USAGE"))
            }
            add(Intent(Settings.ACTION_PRIVACY_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }
    }

    fun openPrivacySettings(context: Context) {
        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
