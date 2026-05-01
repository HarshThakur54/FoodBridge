package com.example.foodbridge.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Home : Screen("home")
    object Donate : Screen("donate")
    object Browse : Screen("browse")
    object DonationDetail : Screen("donation_detail")
    object NgoDashboard : Screen("ngo_dashboard")
    object Notifications : Screen("notifications")
    object Profile : Screen("profile")
    object NgoProfile : Screen("ngo_profile")
    object MyDonations : Screen("my_donations")
    object MyCollections : Screen("my_collections")
    object AdminDashboard : Screen("admin_dashboard")
    object PostDonation : Screen("post_donation")
    object History : Screen("history")
    object Chatbot : Screen("chatbot")
    object PickupRoute : Screen("pickup_route") {
        fun createRoute(donationId: String): String = "$route/$donationId"
    }
}
