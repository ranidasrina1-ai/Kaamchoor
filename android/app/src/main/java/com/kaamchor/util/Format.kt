package com.kaamchor.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT = DateTimeFormatter.ofPattern("MMM d")
private val FULL_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm")

fun parseIso(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(iso)
    }.recoverCatching {
        Instant.parse(iso.replace(" ", "T").let { if (it.endsWith("Z")) it else "${it}Z" })
    }.getOrNull()
}

fun relativeTime(iso: String?): String {
    val instant = parseIso(iso) ?: return ""
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    val zone = ZoneId.systemDefault()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> TIME_FMT.format(instant.atZone(zone))
        minutes < 60 * 24 * 7 -> DATE_FMT.format(instant.atZone(zone))
        else -> FULL_FMT.format(instant.atZone(zone))
    }
}

fun formatDuration(seconds: Double?): String {
    if (seconds == null || seconds <= 0) return "0:00"
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

fun formatMillis(ms: Long): String = formatDuration(ms / 1000.0)
