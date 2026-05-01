package com.example.foodbridge.presentation.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodbridge.R
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.ui.theme.SurfaceWhite
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@Composable
fun SplashScreen(navController: NavController) {
    val scale = remember { Animatable(0f) }
    var skipRequested by remember { mutableStateOf(false) }

    suspend fun openNextScreen() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        } else {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("users").document(uid).get().await()
                val role = doc.getString("role") ?: "DONOR"
                val destination = if (role == "RECEIVER") Screen.NgoDashboard.route
                else Screen.Home.route
                navController.navigate(destination) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            } catch (e: Exception) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(true) {
        scale.animateTo(
            1f, animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(1800)
        if (!skipRequested) {
            openNextScreen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.foodbridge_brand),
            contentDescription = "FoodBridge logo",
            modifier = Modifier
                .size(320.dp)
                .scale(scale.value)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopEnd)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            Text(
                text = "Skip",
                color = SurfaceWhite,
                fontSize = 15.sp,
                modifier = Modifier.clickable {
                    if (!skipRequested) {
                        skipRequested = true
                    }
                }
            )
        }
    }

    LaunchedEffect(skipRequested) {
        if (skipRequested) {
            openNextScreen()
        }
    }
}
