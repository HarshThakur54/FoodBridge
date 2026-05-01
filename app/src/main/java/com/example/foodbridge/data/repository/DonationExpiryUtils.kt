package com.example.foodbridge.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class DonationExpiryOption(
    val label: String,
    val durationMillis: Long
)

val donationExpiryOptions = listOf(
    DonationExpiryOption("+15 min", TimeUnit.MINUTES.toMillis(15)),
    DonationExpiryOption("+30 min", TimeUnit.MINUTES.toMillis(30)),
    DonationExpiryOption("+45 min", TimeUnit.MINUTES.toMillis(45)),
    DonationExpiryOption("+1 hour", TimeUnit.HOURS.toMillis(1))
)

fun isDonationExpired(expiresAt: Long, now: Long = System.currentTimeMillis()): Boolean {
    return expiresAt > 0L && expiresAt <= now
}

private fun normalizedDonationStatus(status: String): String {
    return when (status.trim().uppercase(Locale.US)) {
        "CLAIMED" -> "IN_PROGRESS"
        else -> status.trim().uppercase(Locale.US)
    }
}

fun canTrackNgo(
    status: String,
    expiresAt: Long,
    now: Long = System.currentTimeMillis()
): Boolean {
    return normalizedDonationStatus(status) == "IN_PROGRESS" &&
        expiresAt > 0L &&
        !isDonationExpired(expiresAt, now)
}

fun canShowPickupPin(
    status: String,
    expiresAt: Long,
    now: Long = System.currentTimeMillis()
): Boolean {
    return normalizedDonationStatus(status) == "IN_PROGRESS" &&
        !isDonationExpired(expiresAt, now)
}

fun donationDisplayStatus(
    storedStatus: String,
    expiresAt: Long,
    now: Long = System.currentTimeMillis()
): String {
    val normalizedStatus = when (storedStatus) {
        "CLAIMED" -> "IN_PROGRESS"
        else -> storedStatus
    }

    return when {
        normalizedStatus == "EXPIRED" -> "EXPIRED"
        normalizedStatus == "AVAILABLE" && isDonationExpired(expiresAt, now) -> "EXPIRED"
        normalizedStatus == "AVAILABLE" && expiresAt > now &&
            expiresAt - now <= TimeUnit.MINUTES.toMillis(30) -> "EXPIRING SOON"
        else -> normalizedStatus
    }
}

fun formatExpiryCountdown(
    expiresAt: Long,
    now: Long = System.currentTimeMillis()
): String {
    if (expiresAt <= 0L) return "No expiry set"
    val remainingMillis = expiresAt - now
    if (remainingMillis <= 0L) return "Expired"

    val totalMinutes = max(1, TimeUnit.MILLISECONDS.toMinutes(remainingMillis))
    return when {
        totalMinutes < 60 -> "Expires in $totalMinutes min"
        totalMinutes < 24 * 60 -> {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (minutes == 0L) "Expires in $hours hr"
            else "Expires in ${hours}h ${minutes}m"
        }
        else -> {
            val days = totalMinutes / (24 * 60)
            "Expires in $days day"
        }
    }
}

fun formatExpiryDateTime(expiresAt: Long): String {
    if (expiresAt <= 0L) return "No expiry set"
    return SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(Date(expiresAt))
}

suspend fun expireOverdueDonations(
    db: FirebaseFirestore,
    documents: List<DocumentSnapshot>,
    now: Long = System.currentTimeMillis()
): Set<String> {
    val overdue = documents.filter { document ->
        val status = document.getString("status") ?: "AVAILABLE"
        val expiresAt = document.getLong("expiresAt") ?: 0L
        status == "AVAILABLE" && isDonationExpired(expiresAt, now)
    }

    if (overdue.isEmpty()) return emptySet()

    val batch = db.batch()
    overdue.forEach { document ->
        batch.update(
            document.reference,
            mapOf(
                "status" to "EXPIRED",
                "expiredAt" to now
            )
        )
    }
    batch.commit().await()
    return overdue.map { it.id }.toSet()
}
