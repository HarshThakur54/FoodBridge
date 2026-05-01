package com.example.foodbridge.presentation.donation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.foodbridge.data.repository.canTrackNgo
import com.example.foodbridge.data.repository.donationDisplayStatus
import com.example.foodbridge.data.repository.expireOverdueDonations
import com.example.foodbridge.data.repository.formatExpiryCountdown
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.presentation.auth.AuthViewModel
import com.example.foodbridge.presentation.home.FoodBridgeBottomNav
import com.example.foodbridge.presentation.ngo.NgoBottomNav
import com.example.foodbridge.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class MyDonation(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val status: String = "",
    val emoji: String = "",
    val quantity: String = "",
    val expiryInfo: String = "",
    val expiresAt: Long = 0L,
    val timestamp: Long = 0L,
    val photoUrl: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("All", "Available", "In Progress", "Completed", "Expired")
    var donations by remember { mutableStateOf<List<MyDonation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUserRole by remember { mutableStateOf("") }
    var hasResolvedUserRole by remember { mutableStateOf(false) }

    val uid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(Unit) {
        if (uid != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val userDoc = db.collection("users").document(uid).get().await()
                currentUserRole = userDoc.getString("role")
                    ?: authViewModel.uiState.value.currentUserRole.ifBlank { "DONOR" }
                hasResolvedUserRole = true
                if (currentUserRole == "RECEIVER") {
                    navController.navigate(Screen.Browse.route) {
                        popUpTo(Screen.Donate.route) { inclusive = true }
                        launchSingleTop = true
                    }
                    return@LaunchedEffect
                }
                val snapshot = db
                    .collection("donations")
                    .whereEqualTo("donorId", uid)
                    .get().await()
                val now = System.currentTimeMillis()
                val expiredIds = expireOverdueDonations(db, snapshot.documents, now)
                donations = snapshot.documents.mapNotNull { doc ->
                    val expiresAt = doc.getLong("expiresAt") ?: 0L
                    val storedStatus = doc.getString("status") ?: "AVAILABLE"
                    val status = if (doc.id in expiredIds) {
                        "EXPIRED"
                    } else {
                        donationDisplayStatus(storedStatus, expiresAt, now)
                    }

                    MyDonation(
                        id       = doc.id,
                        title    = doc.getString("title") ?: return@mapNotNull null,
                        date     = java.text.SimpleDateFormat(
                            "dd MMM yyyy • hh:mm a",
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(doc.getLong("createdAt") ?: 0L)),
                        status   = status,
                        quantity = doc.getString("quantity") ?: "",
                        expiryInfo = formatExpiryCountdown(expiresAt, now),
                        emoji    = when (doc.getString("category")) {
                            "Bakery"    -> "🥖"
                            "Beverages" -> "🥤"
                            "Raw"       -> "🥦"
                            "Packaged"  -> "📦"
                            else        -> "🍛"
                        },
                        timestamp = doc.getLong("createdAt") ?: 0L,
                        expiresAt = expiresAt,
                        photoUrl = doc.getString("photoUrl").orEmpty()
                    )
                }.sortedByDescending { it.timestamp }
            } catch (_: Exception) {
                currentUserRole = authViewModel.uiState.value.currentUserRole.ifBlank { "DONOR" }
                hasResolvedUserRole = true
            }
            finally { isLoading = false }
        } else {
            currentUserRole = authViewModel.uiState.value.currentUserRole.ifBlank { "DONOR" }
            hasResolvedUserRole = true
            isLoading = false
        }
    }

    val filteredDonations = donations.filter {
        when (selectedTab) {
            1    -> it.status == "AVAILABLE" || it.status == "EXPIRING SOON"
            2    -> it.status == "IN_PROGRESS"
            3    -> it.status == "COMPLETED"
            4    -> it.status == "EXPIRED"
            else -> true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Donations", fontWeight = FontWeight.Bold, color = SurfaceWhite) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        floatingActionButton = if (hasResolvedUserRole && currentUserRole != "RECEIVER") {
            {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.PostDonation.route) },
                    containerColor = GreenPrimary
                ) {
                    Icon(Icons.Default.Add, null, tint = SurfaceWhite)
                }
            }
        } else {
            {}
        },
        bottomBar = {
            if (!hasResolvedUserRole) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            } else if (currentUserRole == "RECEIVER") {
                NgoBottomNav(navController, 1)
            } else {
                FoodBridgeBottomNav(navController, 1)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceWhite,
                contentColor = GreenPrimary,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = {
                            Text(
                                tab,
                                fontWeight = if (selectedTab == index)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                DonationScreenSkeleton()
            } else if (filteredDonations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍽️", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (currentUserRole == "RECEIVER") "No claimed donations yet" else "No donations yet",
                            color = TextSecondary
                        )
                        if (currentUserRole != "RECEIVER") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { navController.navigate(Screen.PostDonation.route) },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                            ) { Text("Post Your First Donation") }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDonations) { donation ->
                        MyDonationCard(
                            donation = donation,
                            onTrackRoute = if (canTrackNgo(donation.status, donation.expiresAt)) {
                                { navController.navigate(Screen.PickupRoute.createRoute(donation.id)) }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonationScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GreenContainer)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        DonationSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.56f)
                                .height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DonationSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .height(10.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        DonationSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.46f)
                                .height(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    DonationSkeletonLine(
                        modifier = Modifier
                            .width(72.dp)
                            .height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DonationSkeletonLine(
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextSecondary.copy(alpha = 0.14f))
    )
}

@Composable
fun MyDonationCard(
    donation: MyDonation,
    onTrackRoute: (() -> Unit)? = null
) {
    val statusColor = when (donation.status) {
        "AVAILABLE" -> StatusSafe
        "EXPIRING SOON" -> StatusCaution
        "IN_PROGRESS" -> OrangeAccent
        "COMPLETED" -> GreenPrimary
        "EXPIRED"   -> StatusExpired
        else        -> TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(GreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (donation.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = donation.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(donation.emoji, fontSize = 28.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(donation.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(donation.date, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(donation.quantity, color = TextSecondary, fontSize = 12.sp)
                    if (donation.expiryInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(donation.expiryInfo, color = statusColor, fontSize = 12.sp)
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        donation.status.replace("_", " "), color = statusColor,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (onTrackRoute != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onTrackRoute,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Icon(Icons.Default.Map, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Track NGO & Verify PIN")
                }
            }
        }
    }
}
