package com.example.foodbridge.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.foodbridge.data.repository.donationDisplayStatus
import com.example.foodbridge.data.repository.expireOverdueDonations
import com.example.foodbridge.data.repository.formatExpiryCountdown
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.foodbridge.presentation.auth.AuthViewModel
import kotlinx.coroutines.tasks.await
import java.util.Calendar

private data class HomeActivityRecord(
    val id: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val timestamp: Long
)

private fun formatPostedAgo(createdAt: Long, now: Long): String {
    val diffMillis = (now - createdAt).coerceAtLeast(0L)
    val minutes = diffMillis / 60_000L
    val hours = diffMillis / 3_600_000L
    val days = diffMillis / 86_400_000L

    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        hours < 24L -> "$hours hr ago"
        else -> "$days day${if (days == 1L) "" else "s"} ago"
    }
}

private fun currentGreeting(): String {
    val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Working late"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, authViewModel: AuthViewModel) {
    var userRole by remember { mutableStateOf("DONOR") }
    var userName by remember { mutableStateOf("Chef") }
    var totalPosted by remember { mutableStateOf(0) }
    var pendingCount by remember { mutableStateOf(0) }
    var collectedCount by remember { mutableStateOf(0) }
    var isDashboardLoading by remember { mutableStateOf(true) }
    var recentDonations by remember { mutableStateOf<List<HomeActivityRecord>>(emptyList()) }
    val greeting = remember { currentGreeting() }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(uid).get().await()

                    userRole = doc.getString("role") ?: "DONOR"
                    userName = doc.getString("fullName") ?: "Chef"
                    if (userRole == "RECEIVER") {
                        navController.navigate(Screen.NgoDashboard.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                        return@LaunchedEffect
                    }

                val snapshot = db
                    .collection("donations")
                    .whereEqualTo("donorId", uid)
                    .get()
                    .await()
                val now = System.currentTimeMillis()
                val expiredIds = expireOverdueDonations(db, snapshot.documents, now)

                val donationRecords = snapshot.documents.mapNotNull { donationDoc ->
                    val title = donationDoc.getString("title") ?: return@mapNotNull null
                    val createdAt = donationDoc.getLong("createdAt") ?: 0L
                    val expiresAt = donationDoc.getLong("expiresAt") ?: 0L
                    val storedStatus = donationDoc.getString("status") ?: "AVAILABLE"
                    val displayStatus = if (donationDoc.id in expiredIds) {
                        "EXPIRED"
                    } else {
                        donationDisplayStatus(storedStatus, expiresAt, now)
                    }

                    HomeActivityRecord(
                        id = donationDoc.id,
                        title = title,
                        subtitle = when (displayStatus) {
                            "IN_PROGRESS" -> "NGO is on the way"
                            "COMPLETED" -> "Pickup completed"
                            "EXPIRED" -> "Expired before pickup"
                            "EXPIRING SOON" -> formatExpiryCountdown(expiresAt, now)
                            else -> "Posted ${formatPostedAgo(createdAt, now)}"
                        },
                        statusLabel = when (displayStatus) {
                            "IN_PROGRESS" -> "IN PROGRESS"
                            "COMPLETED" -> "COMPLETED"
                            "EXPIRING SOON" -> "EXPIRING"
                            "AVAILABLE" -> "PENDING"
                            else -> displayStatus
                        },
                        timestamp = createdAt
                    )
                }.sortedByDescending { it.timestamp }

                totalPosted = donationRecords.size
                pendingCount = donationRecords.count {
                    it.statusLabel == "PENDING" ||
                        it.statusLabel == "EXPIRING" ||
                        it.statusLabel == "IN PROGRESS"
                }
                collectedCount = donationRecords.count { it.statusLabel == "COMPLETED" }
                recentDonations = donationRecords.take(3)
            } catch (_: Exception) {
                totalPosted = 0
                pendingCount = 0
                collectedCount = 0
                recentDonations = emptyList()
            } finally {
                isDashboardLoading = false
            }
        } else {
            isDashboardLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$greeting 👋", fontSize = 12.sp,
                            color = SurfaceWhite.copy(alpha = 0.85f))
                        Text(userName, fontWeight = FontWeight.Bold,
                            color = SurfaceWhite, fontSize = 18.sp)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Notifications.route)
                    }) {
                        Icon(Icons.Default.Notifications, null, tint = SurfaceWhite)
                    }
                    IconButton(onClick = {
                        navController.navigate(Screen.Profile.route)
                    }) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(GreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                userName.firstOrNull()?.toString() ?: "U",
                                color = SurfaceWhite, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.PostDonation.route) },
                containerColor = GreenPrimary,
                contentColor = SurfaceWhite,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Post New Donation", fontWeight = FontWeight.SemiBold) }
            )
        },
        bottomBar = { FoodBridgeBottomNav(navController, 0) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Manage your surplus donations for today.",
                    color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = "Total Posted",
                        value = if (isDashboardLoading) "--" else totalPosted.toString(),
                        change = "All donations",
                        color = GreenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Pending",
                        value = if (isDashboardLoading) "--" else pendingCount.toString(),
                        change = "Waiting pickup",
                        color = OrangeAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Collected",
                        value = if (isDashboardLoading) "--" else collectedCount.toString(),
                        change = "Picked up",
                        color = GreenLight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Quick Actions", color = SurfaceWhite,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Post a new donation", color = SurfaceWhite.copy(0.8f),
                                fontSize = 12.sp)
                        }
                        Button(
                            onClick = { navController.navigate(Screen.PostDonation.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Post Donation", color = GreenPrimary,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = GreenContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = GreenPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Ask AI Assistant",
                                    fontWeight = FontWeight.Bold,
                                    color = GreenDark,
                                    fontSize = 15.sp
                                )
                                Text(
                                    "Get quick help about donations and app usage",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = { navController.navigate(Screen.Chatbot.route) },
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Open", color = SurfaceWhite, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Activity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = {
                        navController.navigate(Screen.History.route)
                    }) { Text("View All", color = GreenPrimary) }
                }
            }

            if (recentDonations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("No donations yet", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDashboardLoading) {
                                    "Loading your latest donation activity..."
                                } else {
                                    "Your recent donation activity will appear here."
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(recentDonations, key = { it.id }) { donation ->
                    DonationActivityCard(
                        title = donation.title,
                        subtitle = donation.subtitle,
                        status = donation.statusLabel
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, change: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, DividerColor.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (change.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(change, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DonationActivityCard(title: String, subtitle: String, status: String) {
    val statusColor = when (status) {
        "COMPLETED"  -> GreenPrimary
        "IN PROGRESS" -> OrangeAccent
        "EXPIRING"   -> StatusCaution
        "PENDING"    -> OrangeAccent
        "EXPIRED"    -> StatusExpired
        else         -> TextSecondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, DividerColor.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(GreenContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Fastfood, null, tint = GreenPrimary) }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp)
                }
            }
            Surface(
                color = statusColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(status, color = statusColor, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
fun FoodBridgeBottomNav(navController: NavController, selected: Int) {
    Surface(
        color = SurfaceWhite,
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        NavigationBar(
            containerColor = SurfaceWhite,
            tonalElevation = 0.dp
        ) {
            val items = listOf(
                Triple("Home",      Icons.Default.Home,     Screen.Home.route),
                Triple("Donations", Icons.Default.Favorite, Screen.Donate.route),
                Triple("History",   Icons.Default.History,  Screen.History.route),
                Triple("Profile",   Icons.Default.Person,   Screen.Profile.route),
            )
            items.forEachIndexed { index, (label, icon, route) ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick  = {
                        if (route != navController.currentDestination?.route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon     = { Icon(icon, null) },
                    label    = { Text(label, fontSize = 11.sp) },
                    colors   = NavigationBarItemDefaults.colors(
                        selectedIconColor = GreenPrimary,
                        selectedTextColor = GreenPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor    = GreenContainer
                    )
                )
            }
        }
    }
}
