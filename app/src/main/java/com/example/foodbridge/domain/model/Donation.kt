package com.example.foodbridge.domain.model

data class Donation(
    val id: String = "",
    val donorId: String = "",
    val donorName: String = "",
    val title: String = "",
    val description: String = "",
    val foodType: FoodType = FoodType.COOKED,
    val quantity: Int = 0,
    val quantityUnit: String = "portions",
    val images: List<String> = emptyList(),
    val expiryTime: Long = 0L,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: DonationStatus = DonationStatus.AVAILABLE,
    val claimedBy: String = "",
    val safetyStatus: SafetyStatus = SafetyStatus.SAFE,
    val isVeg: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class FoodType    { COOKED, RAW, PACKAGED, BAKERY }
enum class DonationStatus { AVAILABLE, CLAIMED, IN_PROGRESS, COMPLETED, EXPIRED }
enum class SafetyStatus   { SAFE, CAUTION, EXPIRED }
