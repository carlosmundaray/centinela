package com.centinela.privacidad.blocker

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.UserManager

enum class ProtectionTier(val label: String) {
    DEVICE_OWNER("Modo Propietario (Bloqueo total incluso apps del sistema)"),
    DEVICE_ADMIN_LEGACY("Administrador de dispositivo (Android 8-9)"),
    STANDARD("Modo Estándar (Sin privilegios de sistema)")
}

data class BlockerStatus(
    val protectionTier: ProtectionTier,
    val isDeviceOwner: Boolean,
    val isDeviceAdmin: Boolean,
    val isCameraBlocked: Boolean,
    val isMicBlocked: Boolean,
    val isSystemMicMuted: Boolean,
    val canBlockCameraProgrammatically: Boolean,
    val canEnforceHardMicBlock: Boolean
)

sealed class BlockActionResult {
    data class Success(val message: String) : BlockActionResult()
    data class RequiresElevation(val message: String) : BlockActionResult()
    data class Error(val message: String) : BlockActionResult()
}

class SensorBlockerController(private val context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val adminComponent = CentinelaDeviceAdminReceiver.getComponentName(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStatus(): BlockerStatus {
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(appContext.packageName) }.getOrDefault(false)
        val isDeviceAdmin = runCatching { dpm.isAdminActive(adminComponent) }.getOrDefault(false)
        val isLegacyAdminAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && isDeviceAdmin

        val tier = when {
            isDeviceOwner -> ProtectionTier.DEVICE_OWNER
            isLegacyAdminAllowed -> ProtectionTier.DEVICE_ADMIN_LEGACY
            else -> ProtectionTier.STANDARD
        }

        val cameraDisabledByPolicy = runCatching {
            if (isDeviceOwner || isLegacyAdminAllowed) {
                dpm.getCameraDisabled(adminComponent)
            } else {
                dpm.getCameraDisabled(null)
            }
        }.getOrDefault(false)

        val micRestrictedByPolicy = if (isDeviceOwner) {
            runCatching {
                dpm.getUserRestrictions(adminComponent)
                    .getBoolean(UserManager.DISALLOW_UNMUTE_MICROPHONE, false)
            }.getOrDefault(false)
        } else {
            false
        }

        val micGuardEnabled = prefs.getBoolean(KEY_MIC_BLOCK_DESIRED, false)
        val systemMicMuted = runCatching { audioManager.isMicrophoneMute }.getOrDefault(false)

        return BlockerStatus(
            protectionTier = tier,
            isDeviceOwner = isDeviceOwner,
            isDeviceAdmin = isDeviceAdmin,
            isCameraBlocked = cameraDisabledByPolicy,
            isMicBlocked = micRestrictedByPolicy || micGuardEnabled,
            isSystemMicMuted = systemMicMuted,
            canBlockCameraProgrammatically = isDeviceOwner || isLegacyAdminAllowed,
            canEnforceHardMicBlock = isDeviceOwner
        )
    }

    fun isMicGuardEnabled(): Boolean {
        return prefs.getBoolean(KEY_MIC_BLOCK_DESIRED, false)
    }

    fun setCameraBlocked(blocked: Boolean): BlockActionResult {
        val status = getStatus()
        if (!status.canBlockCameraProgrammatically) {
            return BlockActionResult.RequiresElevation(
                "Android 10+ requiere Modo Propietario (Device Owner) o el interruptor de privacidad nativo del sistema para bloquear la cámara a todas las apps."
            )
        }

        return runCatching {
            dpm.setCameraDisabled(adminComponent, blocked)
            if (blocked) {
                BlockActionResult.Success("Cámara bloqueada a nivel de sistema (incluye apps de sistema).")
            } else {
                BlockActionResult.Success("Cámara desbloqueada.")
            }
        }.getOrElse { err ->
            BlockActionResult.Error("No se pudo cambiar el bloqueo de cámara: ${err.message ?: "error de política"}")
        }
    }

    fun setMicrophoneBlocked(blocked: Boolean): BlockActionResult {
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(appContext.packageName) }.getOrDefault(false)
        prefs.edit().putBoolean(KEY_MIC_BLOCK_DESIRED, blocked).apply()

        if (isDeviceOwner) {
            return runCatching {
                if (blocked) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_UNMUTE_MICROPHONE)
                    audioManager.isMicrophoneMute = true
                    BlockActionResult.Success(
                        "Micrófono bloqueado a nivel de sistema (incluye apps de sistema; ninguna app puede reactivarlo)."
                    )
                } else {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNMUTE_MICROPHONE)
                    audioManager.isMicrophoneMute = false
                    BlockActionResult.Success("Micrófono desbloqueado.")
                }
            }.getOrElse { err ->
                BlockActionResult.Error("No se pudo aplicar la restricción de micrófono: ${err.message}")
            }
        }

        // Standard mode: mute hardware mic input via AudioManager and re-enforce on active recordings
        return runCatching {
            audioManager.isMicrophoneMute = blocked
            if (blocked) {
                BlockActionResult.Success(
                    "Micrófono silenciado en modo estándar. Para impedir que apps de sistema lo reactiven, activa el Modo Propietario o el interruptor nativo de Android 12+."
                )
            } else {
                BlockActionResult.Success("Silenciado de micrófono desactivado.")
            }
        }.getOrElse { err ->
            BlockActionResult.Error("No se pudo silenciar el micrófono: ${err.message}")
        }
    }

    /**
     * Re-asserts microphone mute whenever SensorMonitorService detects a recording while mic guard is active.
     */
    fun enforceMicGuardIfActive() {
        if (!isMicGuardEnabled()) return
        runCatching {
            if (!audioManager.isMicrophoneMute) {
                audioManager.isMicrophoneMute = true
            }
        }
    }

    /**
     * Blocks or restores CAMERA or RECORD_AUDIO permission for a specific package (including system apps)
     * when running in Device Owner mode.
     */
    fun setAppPermissionBlocked(
        packageName: String,
        permission: String,
        blocked: Boolean
    ): BlockActionResult {
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(appContext.packageName) }.getOrDefault(false)
        if (!isDeviceOwner) {
            return BlockActionResult.RequiresElevation(
                "Activa el Modo Propietario para revocar/bloquear permisos directamente desde aquí, o usa los ajustes de Android."
            )
        }
        val state = if (blocked) {
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        } else {
            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        }
        return runCatching {
            val ok = dpm.setPermissionGrantState(adminComponent, packageName, permission, state)
            if (ok) {
                val label = if (permission == Manifest.permission.CAMERA) "Cámara" else "Micrófono"
                if (blocked) {
                    BlockActionResult.Success("Permiso de $label bloqueado para $packageName.")
                } else {
                    BlockActionResult.Success("Restricción de $label eliminada para $packageName.")
                }
            } else {
                BlockActionResult.Error(
                    "Android no permitió modificar este permiso para $packageName (puede estar protegido por el sistema)."
                )
            }
        }.getOrElse { err ->
            BlockActionResult.Error("Error al modificar permiso: ${err.message}")
        }
    }

    fun isAppPermissionDeniedByPolicy(packageName: String, permission: String): Boolean {
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(appContext.packageName) }.getOrDefault(false)
        if (!isDeviceOwner) return false
        return runCatching {
            dpm.getPermissionGrantState(adminComponent, packageName, permission) ==
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        }.getOrDefault(false)
    }

    fun createRequestDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Permite a Centinela Privacidad bloquear el acceso a la cámara a nivel de política de dispositivo."
            )
        }
    }

    @Suppress("DEPRECATION")
    fun clearDeviceOwner(): BlockActionResult {
        val isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(appContext.packageName) }.getOrDefault(false)
        if (!isDeviceOwner) {
            return BlockActionResult.Error("La app no está en Modo Propietario.")
        }
        return runCatching {
            // Release all active hardware locks and hidden packages before stepping down
            com.centinela.privacidad.hardening.SystemHardeningController(appContext)
                .restoreAllCentinelaHiddenPackages()
            dpm.setCameraDisabled(adminComponent, false)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNMUTE_MICROPHONE)
            audioManager.isMicrophoneMute = false
            prefs.edit().putBoolean(KEY_MIC_BLOCK_DESIRED, false).apply()
            dpm.clearDeviceOwnerApp(appContext.packageName)
            BlockActionResult.Success("Modo Propietario desactivado. Ya puedes desinstalar la app normalmente si lo deseas.")
        }.getOrElse { err ->
            BlockActionResult.Error("No se pudo desactivar el Modo Propietario: ${err.message}")
        }
    }

    companion object {
        private const val PREFS_NAME = "centinela_blocker_prefs"
        private const val KEY_MIC_BLOCK_DESIRED = "mic_block_desired"

        const val ADB_DEVICE_OWNER_COMMAND =
            "adb shell dpm set-device-owner com.centinela.privacidad/.blocker.CentinelaDeviceAdminReceiver"
    }
}
