package com.example.util

import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}.0x"
    } else {
        String.format(Locale.US, "%.2fx", speed).trimEnd('0').let {
            if (it.endsWith(".")) "${it}0x" else it
        }
    }
}

fun guessSubtitleMimeType(uriString: String, displayName: String? = null): String {
    val target = (displayName ?: uriString).lowercase()
    return when {
        target.endsWith(".vtt") -> androidx.media3.common.MimeTypes.TEXT_VTT
        target.endsWith(".ass") || target.endsWith(".ssa") -> androidx.media3.common.MimeTypes.TEXT_SSA
        else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
    }
}
