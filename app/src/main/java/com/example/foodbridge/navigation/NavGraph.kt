package com.example.foodbridge.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.foodbridge.presentation.auth.AuthViewModel
import com.example.foodbridge.presentation.auth.LoginScreen
import com.example.foodbridge.presentation.auth.SignupScreen
import com.example.foodbridge.presentation.chatbot.ChatbotScreen
import com.example.foodbridge.presentation.donation.DonationScreen
import com.example.foodbridge.presentation.donation.PostDonationScreen
import com.example.foodbridge.presentation.donations.BrowseDonationsScreen
import com.example.foodbridge.presentation.donations.DonationDetailScreen
import com.example.foodbridge.presentation.history.HistoryScreen
import com.example.foodbridge.presentation.home.HomeScreen
import com.example.foodbridge.presentation.ngo.NgoDashboardScreen
import com.example.foodbridge.presentation.notifications.RealtimeNotificationObserver
import com.example.foodbridge.presentation.notifications.NotificationsScreen
import com.example.foodbridge.presentation.onboarding.OnboardingScreen
import com.example.foodbridge.presentation.profile.ProfileScreen
import com.example.foodbridge.presentation.route.PickupRouteScreen
import com.example.foodbridge.presentation.splash.SplashScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()

    Box {
        RealtimeNotificationObserver()

        NavHost(navController = navController, startDestination = Screen.Splash.route) {
            composable(Screen.Splash.route) { SplashScreen(navController) }
            composable(Screen.Onboarding.route) { OnboardingScreen(navController) }
            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToSignup = { navController.navigate(Screen.Signup.route) },
                    onLoginSuccess = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Signup.route) {
                SignupScreen(
                    onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                    onSignupSuccess = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Signup.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(navController = navController, authViewModel = authViewModel)
            }
            composable(Screen.Donate.route) {
                DonationScreen(navController = navController, authViewModel = authViewModel)
            }
            composable(Screen.Browse.route) {
                BrowseDonationsScreen(navController = navController, authViewModel = authViewModel)
            }
            composable(Screen.DonationDetail.route) { DonationDetailScreen(navController) }
            composable(Screen.NgoDashboard.route) { NgoDashboardScreen(navController) }
            composable(Screen.Notifications.route) { NotificationsScreen(navController) }
            composable(Screen.Profile.route) { ProfileScreen(navController) }
            composable(Screen.PostDonation.route) {
                PostDonationScreen(navController = navController, authViewModel = authViewModel)
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    navController = navController,
                    authViewModel = authViewModel
                )
            }
            composable(Screen.Chatbot.route) { ChatbotScreen(navController = navController) }
            composable(
                route = "${Screen.PickupRoute.route}/{donationId}",
                arguments = listOf(
                    navArgument("donationId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                PickupRouteScreen(
                    navController = navController,
                    donationId = backStackEntry.arguments?.getString("donationId").orEmpty()
                )
            }
        }
    }
}
