package com.example.foodbridge.domain.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: UserRole = UserRole.DONOR,
    val subRole: String = "",
    val photoUrl: String = "",
    val phone: String = "",
    val addressLine1: String = "",
    val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val rating: Float = 0f,
    val totalDonations: Int = 0,
    val totalPickups: Int = 0,
    val isVerified: Boolean = false,
    val fcmToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class UserRole { DONOR, RECEIVER, ADMIN }
