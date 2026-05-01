package com.example.foodbridge.util

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class ResolvedLocation(
    val address: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val hasCoordinates: Boolean
        get() = isValidCoordinate(latitude, longitude)
}

fun buildAddress(vararg parts: String): String {
    return parts
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
}

fun coalesceAddress(primary: String, vararg fallbackParts: String): String {
    val cleanedPrimary = primary.trim()
    return if (cleanedPrimary.isNotBlank()) cleanedPrimary else buildAddress(*fallbackParts)
}

fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
    return latitude != null &&
        longitude != null &&
        !(abs(latitude) < 0.000001 && abs(longitude) < 0.000001)
}

suspend fun resolveLocation(
    context: Context,
    address: String,
    city: String = "",
    pincode: String = "",
    latitude: Double? = null,
    longitude: Double? = null
): ResolvedLocation {
    val cleanedAddress = address.trim()
    val coordinates = when {
        isValidCoordinate(latitude, longitude) -> latitude to longitude
        cleanedAddress.isBlank() -> null
        else -> geocodeAddress(context, cleanedAddress)
    }

    return ResolvedLocation(
        address = cleanedAddress,
        addressLine1 = cleanedAddress,
        city = city.trim(),
        pincode = pincode.trim(),
        latitude = coordinates?.first,
        longitude = coordinates?.second
    )
}

@Suppress("DEPRECATION")
suspend fun reverseGeocodeLocation(
    context: Context,
    latitude: Double,
    longitude: Double
): ResolvedLocation? {
    return withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null

        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = geocoder.getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return@runCatching null

            val line0 = address.getAddressLine(0).orEmpty()
            val line1 = address.subLocality.orEmpty()
            val city = address.locality.orEmpty()
            val state = address.adminArea.orEmpty()
            val pincode = address.postalCode.orEmpty()

            ResolvedLocation(
                address = buildAddress(line0, line1, city, state, pincode),
                addressLine1 = line0,
                addressLine2 = line1,
                city = city,
                state = state,
                pincode = pincode,
                latitude = latitude,
                longitude = longitude
            )
        }.getOrNull()
    }
}

@Suppress("DEPRECATION")
private suspend fun geocodeAddress(context: Context, address: String): Pair<Double, Double>? {
    return withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || address.isBlank()) return@withContext null

        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocationName(address, 1)
                ?.firstOrNull()
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
    }
}

fun calculateDistanceKm(
    originLatitude: Double,
    originLongitude: Double,
    destinationLatitude: Double,
    destinationLongitude: Double
): Double {
    val results = FloatArray(1)
    Location.distanceBetween(
        originLatitude,
        originLongitude,
        destinationLatitude,
        destinationLongitude,
        results
    )
    return results.first() / 1000.0
}

fun formatDistanceLabel(
    distanceKm: Double?,
    cityMatch: Boolean,
    pincodeMatch: Boolean
): String {
    return when {
        distanceKm != null && distanceKm < 1.0 -> "${(distanceKm * 1000).roundToInt()} m away"
        distanceKm != null -> String.format(Locale.US, "%.1f km away", distanceKm)
        pincodeMatch -> "Same pincode"
        cityMatch -> "Same city"
        else -> "Route needs address"
    }
}

fun routePriorityScore(
    distanceKm: Double?,
    expiresAt: Long,
    cityMatch: Boolean,
    pincodeMatch: Boolean,
    now: Long = System.currentTimeMillis()
): Double {
    val baseDistance = distanceKm ?: when {
        pincodeMatch -> 2.5
        cityMatch -> 8.0
        else -> 20.0
    }
    val hoursLeft = ((expiresAt - now).coerceAtLeast(0L)) / 3_600_000.0
    val urgencyBoost = when {
        hoursLeft <= 2.0 -> 2.0
        hoursLeft <= 6.0 -> 1.0
        else -> 0.0
    }
    return baseDistance - urgencyBoost
}

fun openMapRoute(
    context: Context,
    origin: ResolvedLocation? = null,
    destinationLabel: String,
    destinationAddress: String,
    destinationLatitude: Double? = null,
    destinationLongitude: Double? = null
) {
    val routeUri = when {
        origin?.hasCoordinates == true && isValidCoordinate(destinationLatitude, destinationLongitude) -> {
            Uri.parse(
                "https://www.google.com/maps/dir/?api=1" +
                    "&origin=${origin.latitude},${origin.longitude}" +
                    "&destination=$destinationLatitude,$destinationLongitude" +
                    "&travelmode=driving"
            )
        }

        isValidCoordinate(destinationLatitude, destinationLongitude) -> {
            Uri.parse(
                "geo:$destinationLatitude,$destinationLongitude" +
                    "?q=$destinationLatitude,$destinationLongitude(${Uri.encode(destinationLabel)})"
            )
        }

        destinationAddress.isNotBlank() -> {
            Uri.parse("geo:0,0?q=${Uri.encode(destinationAddress)}")
        }

        else -> null
    } ?: return

    val opened = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, routeUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    if (!opened) {
        val fallbackQuery = destinationAddress.ifBlank { destinationLabel }.trim()
        if (fallbackQuery.isBlank()) return

        runCatching {
            val fallbackIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/search?query=${Uri.encode(fallbackQuery)}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallbackIntent)
        }
    }
}
