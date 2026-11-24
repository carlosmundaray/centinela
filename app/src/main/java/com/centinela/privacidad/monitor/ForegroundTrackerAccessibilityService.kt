package com.centinela.privacidad.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import java.util.concurrent.ConcurrentLinkedDeque

data class ForegroundWindowSnapshot(
    val packageName: String,
    val timestampMs: Long,
    val isLauncher: Boolean
)

class ForegroundTrackerAccessibilityService : AccessibilityService() {

    private var launcherPackages: Set<String> = emptySet()
    private var imePackages: Set<String> = emptySet()
    private var lastCacheRefreshMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        refreshSystemPackageCaches()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val now = System.currentTimeMillis()
        if (now - lastCacheRefreshMs > 60_000L) {
            refreshSystemPackageCaches()
        }

        if (pkg == "com.android.systemui" ||
            pkg == "android" ||
            pkg == packageName ||
            pkg in imePackages
        ) {
            return
        }

        val isLauncher = pkg in launcherPackages
        val snapshot = ForegroundWindowSnapshot(
            packageName = pkg,
            timestampMs = now,
            isLauncher = isLauncher
        )
        currentSnapshot = snapshot
        recentSnapshots.addFirst(snapshot)
        while (recentSnapshots.size > MAX_RECENT_SNAPSHOTS) {
            recentSnapshots.pollLast()
        }
    }

    override fun onInterrupt() {
        // No-op: this service only observes window package transitions.
    }

    override fun onDestroy() {
        isServiceConnected = false
        super.onDestroy()
    }

    private fun refreshSystemPackageCaches() {
        lastCacheRefreshMs = System.currentTimeMillis()
        launcherPackages = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())

        imePackages = runCatching {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val MAX_RECENT_SNAPSHOTS = 15

        @Volatile
        var isServiceConnected: Boolean = false
            private set

        @Volatile
        var currentSnapshot: ForegroundWindowSnapshot? = null
            private set

        private val recentSnapshots = ConcurrentLinkedDeque<ForegroundWindowSnapshot>()

        fun getRecentSnapshots(): List<ForegroundWindowSnapshot> {
            return recentSnapshots.toList()
        }
    }
}
