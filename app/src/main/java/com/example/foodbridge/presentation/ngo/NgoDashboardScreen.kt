package com.example.foodbridge.presentation.ngo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.foodbridge.data.repository.donationDisplayStatus
import com.example.foodbridge.data.repository.expireOverdueDonations
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.presentation.home.StatCard
import com.example.foodbridge.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgoDashboardScreen(navController: NavController) {
    var userName by remember { mutableStateOf("NGO") }
    var userRole by remember { mutableStateOf("RECEIVER") }
    var availableNowCount by remember { mutableStateOf(0) }
    var expiringSoonCount by remember { mutableStateOf(0) }
    var claimedByYouCount by remember { mutableStateOf(0) }
    var totalClaimedCount by remember { mutableStateOf(0) }
    var isStatsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db
                    .collection("users").document(uid).get().await()
                userRole = doc.getString("role") ?: "DONOR"
                if (userRole != "RECEIVER") {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.NgoDashboard.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    return@LaunchedEffect
                }
                userName = doc.getString("fullName") ?: "NGO"

                val availableSnapshot = db
                    .collection("donations")
                    .whereEqualTo("status", "AVAILABLE")
                    .get().await()
                val now = System.currentTimeMillis()
                val expiredIds = expireOverdueDonations(db, availableSnapshot.documents, now)
                val liveAvailableStatuses = availableSnapshot.documents.mapNotNull { donationDoc ->
                    if (donationDoc.id in expiredIds) return@mapNotNull null

                    val displayStatus = donationDisplayStatus(
                        storedStatus = donationDoc.getString("status") ?: "AVAILABLE",
                        expiresAt = donationDoc.getLong("expiresAt") ?: 0L,
                        now = now
                    )
                    if (displayStatus == "EXPIRED") null else displayStatus
                }
                availableNowCount = liveAvailableStatuses.size
                expiringSoonCount = liveAvailableStatuses.count { it == "EXPIRING SOON" }

                val claimedByYou = db
                    .collection("donations")
                    .whereEqualTo("claimedBy", uid)
                    .get().await()
                claimedByYouCount = claimedByYou.size()

                val totalClaimed = db
                    .collection("donations")
                    .whereEqualTo("status", "CLAIMED")
                    .get().await()
                totalClaimedCount = totalClaimed.size()
            } catch (_: Exception) {
                availableNowCount = 0
                expiringSoonCount = 0
                claimedByYouCount = 0
                totalClaimedCount = 0
            } finally {
                isStatsLoading = false
            }
        } else {
            isStatsLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Welcome back,", fontSize = 12.sp,
                            color = SurfaceWhite.copy(0.8f))
                        Text(userName, fontWeight = FontWeight.Bold, color = SurfaceWhite)
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
                                userName.firstOrNull()?.toString() ?: "N",
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
                onClick = { navController.navigate(Screen.Browse.route) },
                containerColor = GreenPrimary,
                contentColor = SurfaceWhite,
                icon = { Icon(Icons.Default.Search, null) },
                text = { Text("Browse Available Food") }
            )
        },
        bottomBar = { NgoBottomNav(navController, 0) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = "Available",
                        value = if (isStatsLoading) "--" else availableNowCount.toString(),
                        change = "Ready to claim",
                        color = GreenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Claimed",
                        value = if (isStatsLoading) "--" else claimedByYouCount.toString(),
                        change = "By your NGO",
                        color = GreenLight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        navController.navigate(Screen.Browse.route)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = GreenPrimary,
                            modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Browse Available Food",
                                fontWeight = FontWeight.Bold, color = GreenDark)
                            Text("Claim donations before they expire",
                                fontSize = 12.sp, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = GreenPrimary)
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
                                    "Get quick help about claims, pickups and app usage",
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Weekly Impact", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Keep claiming donations to feed more people!",
                            color = TextSecondary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { minOf(claimedByYouCount / 10f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = GreenPrimary,
                            trackColor = GreenContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${if (isStatsLoading) "--" else claimedByYouCount} donations claimed by your NGO",
                            fontSize = 12.sp, color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                Text("Impact Statistics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label = "Expiring Soon",
                        value = if (isStatsLoading) "--" else expiringSoonCount.toString(),
                        change = "Priority pickup",
                        color = OrangeAccent,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Claimed Total",
                        value = if (isStatsLoading) "--" else totalClaimedCount.toString(),
                        change = "Across app",
                        color = GreenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ActivePickupCard(title: String, location: String, eta: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    .background(GreenContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.LocalShipping, null, tint = GreenPrimary) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(location, color = TextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ETA", fontSize = 11.sp, color = TextSecondary)
                Text(eta, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenPrimary)
            }
        }
    }
}

@Composable
fun NgoBottomNav(navController: NavController, selected: Int) {
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
                Triple("Home",    Icons.Default.Home,   Screen.NgoDashboard.route),
                Triple("Browse",  Icons.Default.Search, Screen.Browse.route),
                Triple("History", Icons.Default.History, Screen.History.route),
                Triple("Profile", Icons.Default.Person, Screen.Profile.route),
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
