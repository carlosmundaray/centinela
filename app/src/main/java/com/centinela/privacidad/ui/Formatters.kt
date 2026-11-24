package com.centinela.privacidad.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val dateTimeFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale("es", "ES"))
    private val timeOnlyFormat = SimpleDateFormat("HH:mm:ss", Locale("es", "ES"))

    fun formatTimestamp(timestampMs: Long): String {
        return synchronized(dateTimeFormat) {
            dateTimeFormat.format(Date(timestampMs))
        }
    }

    fun formatTimeOnly(timestampMs: Long): String {
        return synchronized(timeOnlyFormat) {
            timeOnlyFormat.format(Date(timestampMs))
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> "${seconds}s"
        }
    }
}

@Composable
fun rememberAppIcon(context: Context, packageName: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        if (packageName.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToBitmap(drawable).asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
