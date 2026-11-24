package com.centinela.privacidad

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.centinela.privacidad.blocker.BlockActionResult
import com.centinela.privacidad.blocker.SensorBlockerController
import com.centinela.privacidad.data.AuditedAppInfo
import com.centinela.privacidad.hardening.HardeningActionResult
import com.centinela.privacidad.hardening.HardeningSnapshot
import com.centinela.privacidad.hardening.SystemHardeningController
import com.centinela.privacidad.monitor.PermissionAuditor
import com.centinela.privacidad.monitor.SensorMonitorService
import com.centinela.privacidad.ui.AuditScreen
import com.centinela.privacidad.ui.BlockerScreen
import com.centinela.privacidad.ui.HardeningScreen
import com.centinela.privacidad.ui.HistoryScreen
import com.centinela.privacidad.ui.MonitorScreen
import com.centinela.privacidad.ui.PermissionCheckState
import com.centinela.privacidad.ui.theme.CentinelaTheme
import com.centinela.privacidad.ui.theme.SlateBg
import com.centinela.privacidad.ui.theme.SlateSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as CentinelaApp).repository

        setContent {
            CentinelaTheme {
                CentinelaRoot(repository = repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CentinelaRoot(repository: com.centinela.privacidad.data.SensorRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val blockerController = remember { SensorBlockerController(context) }
    val hardeningController = remember { SystemHardeningController(context) }

    val isMonitoring by repository.isMonitoring.collectAsStateWithLifecycle()
    val activeSessions by repository.activeSessions.collectAsStateWithLifecycle()
    val events by repository.events.collectAsStateWithLifecycle()
    val preferences by repository.preferences.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var auditedApps by remember { mutableStateOf<List<AuditedAppInfo>>(emptyList()) }
    var isAuditLoading by remember { mutableStateOf(true) }
    var blockerStatus by remember { mutableStateOf(blockerController.getStatus()) }
    var hardeningSnapshot by remember { mutableStateOf<HardeningSnapshot?>(null) }
    var isHardeningLoading by remember { mutableStateOf(true) }

    fun readPermissionState(): PermissionCheckState {
        return PermissionCheckState(
            notificationsGranted = PermissionAuditor.hasNotificationPermission(context),
            usageAccessGranted = PermissionAuditor.hasUsageStatsPermission(context),
            accessibilityEnabled = PermissionAuditor.isAccessibilityServiceEnabled(context)
        )
    }

    var permissionState by remember { mutableStateOf(readPermissionState()) }

    fun showFeedback(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun handleBlockResult(result: BlockActionResult) {
        blockerStatus = blockerController.getStatus()
        when (result) {
            is BlockActionResult.Success -> showFeedback(result.message)
            is BlockActionResult.RequiresElevation -> showFeedback(result.message)
            is BlockActionResult.Error -> showFeedback(result.message)
        }
    }

    fun refreshAudit() {
        scope.launch {
            isAuditLoading = true
            auditedApps = withContext(Dispatchers.IO) {
                PermissionAuditor.auditAppsWithSensorPermissions(context)
            }
            isAuditLoading = false
        }
    }

    fun refreshHardening(showSpinner: Boolean = false) {
        scope.launch {
            if (showSpinner) isHardeningLoading = true
            hardeningSnapshot = withContext(Dispatchers.IO) {
                hardeningController.getSnapshot()
            }
            isHardeningLoading = false
        }
    }

    fun handleHardeningResult(result: HardeningActionResult) {
        refreshHardening(showSpinner = false)
        when (result) {
            is HardeningActionResult.Success -> showFeedback(result.message)
            is HardeningActionResult.RequiresPermission -> showFeedback(result.message)
            is HardeningActionResult.Error -> showFeedback(result.message)
        }
    }

    fun copyToClipboard(label: String, text: String, feedback: String) {
        if (text.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        }
        showFeedback(feedback)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionState = readPermissionState()
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        blockerStatus = blockerController.getStatus()
    }

    // Refresh permission checklist, blocker status, and app audit whenever returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = readPermissionState()
                blockerStatus = blockerController.getStatus()
                repository.reloadEvents()
                refreshAudit()
                refreshHardening(showSpinner = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshAudit()
        refreshHardening(showSpinner = true)
    }

    Scaffold(
        containerColor = SlateBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Centinela Privacidad",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SlateSurface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Radar, contentDescription = "Monitor") },
                    label = { Text("Monitor") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Lock, contentDescription = "Bloqueo") },
                    label = { Text("Bloqueo") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.History, contentDescription = "Historial") },
                    label = { Text("Historial") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.Policy, contentDescription = "Auditoría") },
                    label = { Text("Auditoría") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Rounded.CleaningServices, contentDescription = "Blindaje") },
                    label = { Text("Blindaje") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MonitorScreen(
                    isMonitoring = isMonitoring,
                    activeSessions = activeSessions,
                    recentEvents = events,
                    permissionState = permissionState,
                    preferences = preferences,
                    onToggleMonitoring = {
                        if (isMonitoring) {
                            SensorMonitorService.stop(context)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !permissionState.notificationsGranted
                            ) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                            SensorMonitorService.start(context)
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    },
                    onOpenUsageAccess = { PermissionAuditor.openUsageAccessSettings(context) },
                    onOpenAccessibility = { PermissionAuditor.openAccessibilitySettings(context) },
                    onOpenPrivacyDashboard = { PermissionAuditor.openAndroidPrivacyDashboard(context) },
                    onOpenAppSettings = { pkg -> PermissionAuditor.openAppSettings(context, pkg) },
                    onToggleAlerts = { repository.setAlertNotificationsEnabled(it) },
                    onToggleAutoStart = { repository.setAutoStartOnBoot(it) },
                    onViewAllHistory = { selectedTab = 2 }
                )
                1 -> BlockerScreen(
                    status = blockerStatus,
                    onToggleCameraBlock = { block ->
                        handleBlockResult(blockerController.setCameraBlocked(block))
                    },
                    onToggleMicBlock = { block ->
                        handleBlockResult(blockerController.setMicrophoneBlocked(block))
                    },
                    onCopyAdbCommand = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Comando ADB Device Owner",
                                SensorBlockerController.ADB_DEVICE_OWNER_COMMAND
                            )
                        )
                        showFeedback("Comando ADB copiado al portapapeles.")
                    },
                    onRequestLegacyDeviceAdmin = {
                        deviceAdminLauncher.launch(blockerController.createRequestDeviceAdminIntent())
                    },
                    onClearDeviceOwner = {
                        handleBlockResult(blockerController.clearDeviceOwner())
                        refreshAudit()
                    },
                    onOpenPrivacySettings = { PermissionAuditor.openPrivacySettings(context) },
                    onOpenDeveloperOptions = { PermissionAuditor.openDeveloperOptions(context) }
                )
                2 -> HistoryScreen(
                    events = events,
                    onOpenAppSettings = { pkg -> PermissionAuditor.openAppSettings(context, pkg) },
                    onClearHistory = { repository.clearHistory() }
                )
                3 -> AuditScreen(
                    auditedApps = auditedApps,
                    isLoading = isAuditLoading,
                    isDeviceOwner = blockerStatus.isDeviceOwner,
                    onRefresh = { refreshAudit() },
                    onOpenAppSettings = { pkg -> PermissionAuditor.openAppSettings(context, pkg) },
                    onToggleAppPermission = { pkg, permission, block ->
                        handleBlockResult(
                            blockerController.setAppPermissionBlocked(pkg, permission, block)
                        )
                        refreshAudit()
                    }
                )
                4 -> HardeningScreen(
                    snapshot = hardeningSnapshot,
                    isLoading = isHardeningLoading,
                    onRefresh = { refreshHardening(showSpinner = true) },
                    onSelectDnsPreset = { preset ->
                        handleHardeningResult(hardeningController.setPrivateDns(preset))
                    },
                    onToggleTelemetry = { blocked ->
                        handleHardeningResult(hardeningController.setTelemetryBlocked(blocked))
                    },
                    onToggleAdTracking = { blocked ->
                        handleHardeningResult(hardeningController.setAdTrackingBlocked(blocked))
                    },
                    onToggleBackgroundScan = { blocked ->
                        handleHardeningResult(hardeningController.setBackgroundScanningBlocked(blocked))
                    },
                    onApplyFullHardening = {
                        handleHardeningResult(hardeningController.applyFullSystemHardening())
                    },
                    onRevertHardening = {
                        handleHardeningResult(hardeningController.revertSystemHardening())
                    },
                    onTogglePackageDisabled = { pkg, disable ->
                        handleHardeningResult(hardeningController.setPackageDisabled(pkg, disable))
                    },
                    onDisableAllRecommendedPackages = {
                        handleHardeningResult(hardeningController.disableAllRecommendedPackages())
                    },
                    onRestoreAllPackages = {
                        handleHardeningResult(hardeningController.restoreAllCentinelaHiddenPackages())
                    },
                    onCopyText = { label, text, feedback ->
                        copyToClipboard(label, text, feedback)
                    },
                    onOpenAppSettings = { pkg -> PermissionAuditor.openAppSettings(context, pkg) },
                    onOpenWirelessSettings = { hardeningController.openWirelessOrDnsSettings(context) },
                    onOpenPrivacySettings = { PermissionAuditor.openPrivacySettings(context) }
                )
            }
        }
    }
}
