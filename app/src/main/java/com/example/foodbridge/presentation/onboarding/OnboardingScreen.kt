package com.example.foodbridge.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.ui.theme.*

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String
)

@Composable
fun OnboardingScreen(navController: NavController) {
    val pages = listOf(
        OnboardingPage("🍱", "Rescue Surplus Food",
            "A data can easily list excess food instead of letting it go to waste."),
        OnboardingPage("🤝", "Connect with NGOs",
            "Partner with local organizations to distribute food to those who need it most."),
        OnboardingPage("📍", "Track in Real-time",
            "Know exactly where your donation is going with live pickup tracking.")
    )

    var currentPage by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(GreenContainer, SurfaceWhite))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    Text("Food", fontWeight = FontWeight.ExtraBold,
                        color = GreenDark, fontSize = 18.sp)
                    Text("Bridge", fontWeight = FontWeight.ExtraBold,
                        color = GreenPrimary, fontSize = 18.sp)
                }
                TextButton(onClick = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }) {
                    Text("Skip", color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Image area
            Box(
                modifier = Modifier.fillMaxWidth().height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GreenContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(pages[currentPage].emoji, fontSize = 100.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == currentPage) GreenPrimary else DividerColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = pages[currentPage].title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = pages[currentPage].description,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Next button
            Button(
                onClick = {
                    if (currentPage < pages.size - 1) currentPage++
                    else navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Text(
                    if (currentPage < pages.size - 1) "Next →" else "Get Started",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}