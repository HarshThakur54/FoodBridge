package com.example.foodbridge.presentation.chatbot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ChatbotViewModel : ViewModel() {

    var messages by mutableStateOf(
        listOf(
            ChatMessage(
                text = "Hi! I'm the free FoodBridge Assistant. I can help with posting donations, claiming food, camera upload, history, notifications, and food safety.",
                isUser = false
            )
        )
    )
        private set

    fun sendMessage(userText: String) {
        val cleanedMessage = userText.trim()
        if (cleanedMessage.isBlank()) return

        messages = messages + ChatMessage(cleanedMessage, true)

        val botReply = getBotReply(cleanedMessage)
        messages = messages + ChatMessage(botReply, false)
    }

    private fun getBotReply(input: String): String {
        val msg = input.lowercase()

        return when {

            "post" in msg && "donation" in msg ->
                "To post a donation, open Post Donation, fill food details, add quantity, pickup time, and upload a photo if needed."

            "claim" in msg ->
                "To claim food, open Browse Donations and tap Claim Donation. Only NGO users can claim donations."

            "notification" in msg ->
                "Open the notification bell to see live alerts. The app now listens for new donation and claim updates while it is open on your device."

            "photo" in msg || "camera" in msg || "upload" in msg ->
                "In Post Donation, tap the photo area to choose Camera or Gallery. After you capture or pick an image, it is attached to the donation before posting."

            "history" in msg ->
                "Use the History tab in the bottom bar to review previous donations or claimed pickups with their latest status."

            "ngo" in msg ->
                "NGO accounts can browse and claim donations but cannot post donations."

            "hotel" in msg || "donor" in msg ->
                "Hotel or donor accounts can post food donations and receive claim notifications."

            "free" in msg || "llm" in msg || "ai" in msg || "chatbot" in msg ->
                "This version uses a free built-in assistant inside the app, so it works without any API key. If you later want a hosted LLM, I can connect the same screen to an external provider."

            "safe" in msg || "safety" in msg ->
                "Only donate fresh and properly stored food. Avoid spoiled, contaminated, or unsafe items."

            "hello" in msg || "hi" in msg ->
                "Hello! How can I help you with FoodBridge today?"

            // ----------- NEW CASES (ADDED BELOW) -----------

            "signup" in msg || "register" in msg ->
                "To create an account, open Sign Up, select your role (Donor or NGO), enter details, and submit."

            "login" in msg ->
                "Enter your email and password on the login screen to access your account."

            "logout" in msg ->
                "Go to Profile or Settings and tap Logout."

            "profile" in msg ->
                "Open Profile to view or edit your personal or organization details."

            "edit" in msg || "update" in msg ->
                "You can update your details anytime from the Profile section."

            "delete account" in msg ->
                "Go to Settings and select Delete Account. This action is permanent."

            "location" in msg || "address" in msg ->
                "Provide a clear pickup location while posting donations so NGOs can find it easily."

            "map" in msg ->
                "The app may show donation locations on a map for easier navigation."

            "time" in msg || "pickup time" in msg ->
                "Always set a proper pickup time so NGOs know when to collect the food."

            "cancel" in msg ->
                "You can cancel a donation before it is claimed from your active posts."

            "status" in msg ->
                "Check donation or claim status in the History section."

            "expired" in msg ->
                "Donations expire after the pickup time to ensure safety."

            "contact" in msg ->
                "Use the Help or Contact section to reach support."

            "help" in msg || "support" in msg ->
                "Open Help & Support from the menu for assistance."

            "language" in msg ->
                "Language settings may be available in Settings."

            "dark" in msg ->
                "Enable Dark Mode from Settings for better viewing at night."

            "internet" in msg || "offline" in msg ->
                "An internet connection is required for real-time updates."

            "error" in msg || "issue" in msg || "bug" in msg ->
                "Try restarting the app or contact support if the issue continues."

            "update app" in msg ->
                "Update the app regularly for new features and fixes."

            "verification" in msg ->
                "Some accounts may require verification for safety and trust."

            "terms" in msg || "policy" in msg ->
                "You can find Terms and Privacy Policy in the Settings or About section."

            "about" in msg ->
                "FoodBridge helps connect donors with NGOs to reduce food waste and support communities."

            else ->
                "I can help with donation posting, claiming, notifications, food photo upload, NGO access, and safety rules."
        }
    }
}
