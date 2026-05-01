package com.example.foodbridge.presentation.donations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodbridge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationDetailScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donation Details", fontWeight = FontWeight.Bold, color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = SurfaceWhite)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Share, null, tint = SurfaceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CLAIM DONATION", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero image
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp)
                    .background(GreenContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("🍛", fontSize = 80.sp)
                Box(modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentAlignment = Alignment.TopStart) {
                    Surface(color = StatusSafe.copy(0.15f), shape = RoundedCornerShape(20.dp)) {
                        Text("AVAILABLE NOW", color = StatusSafe,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Title
                Text("Vegetable Biryani & Sides", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Donated by Grand Plaza Hotel", color = TextSecondary, fontSize = 13.sp)

                Divider(color = DividerColor)

                // Info section
                Text("INFORMATION", fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = TextSecondary)

                InfoRow(Icons.Default.Fastfood, "Food Items",
                    "Veg Biryani, Raita, Mixed Veg Curry")
                InfoRow(Icons.Default.PeopleAlt, "Serves",
                    "Approx. 25 Servings")
                InfoRow(Icons.Default.Schedule, "Pickup Window",
                    "Today, 09:30 PM - 10:45 PM")
                InfoRow(Icons.Default.LocationOn, "Address",
                    "124 Green Valley Road, Sector 4, New Delhi")

                Divider(color = DividerColor)

                // Notes
                Text("DONOR NOTES", fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = TextSecondary)
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Freshly cooked but excess from a corporate banquet. Please bring your own containers if possible. Entry via the service gate at the back.",
                        modifier = Modifier.padding(12.dp),
                        color = GreenDark, fontSize = 13.sp, lineHeight = 20.sp
                    )
                }

                // Map placeholder
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Map, null, tint = GreenPrimary,
                            modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Open in Google Maps", color = GreenPrimary,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}