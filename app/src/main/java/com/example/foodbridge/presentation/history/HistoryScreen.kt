package com.example.foodbridge.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodbridge.data.repository.canTrackNgo
import com.example.foodbridge.data.repository.donationDisplayStatus
import com.example.foodbridge.data.repository.expireOverdueDonations
import com.example.foodbridge.data.repository.formatExpiryDateTime
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.presentation.auth.AuthViewModel
import com.example.foodbridge.presentation.home.FoodBridgeBottomNav
import com.example.foodbridge.presentation.ngo.NgoBottomNav
import com.example.foodbridge.ui.theme.GreenContainer
import com.example.foodbridge.ui.theme.GreenDark
import com.example.foodbridge.ui.theme.GreenLight
import com.example.foodbridge.ui.theme.GreenPrimary
import com.example.foodbridge.ui.theme.OrangeAccent
import com.example.foodbridge.ui.theme.StatusExpired
import com.example.foodbridge.ui.theme.SurfaceWhite
import com.example.foodbridge.ui.theme.TextPrimary
import com.example.foodbridge.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DonationHistoryItem(
    val id: String,
    val title: String,
    val quantity: String,
    val status: String,
    val partnerName: String,
    val createdAt: Long,
    val expiresAt: Long,
    val completedAt: Long,
    val expiredAt: Long,
    val pickupAddress: String
)

private fun historyStatusColor(status: String): Color {
    return when (status) {
        "COMPLETED" -> GreenPrimary
        "IN_PROGRESS" -> OrangeAccent
        "EXPIRED" -> StatusExpired
        else -> GreenLight
    }
}

private fun historyStatusLabel(status: String): String {
    return status.replace("_", " ")
}

private fun historyTitle(
    status: String,
    partnerName: String,
    isReceiver: Boolean
): String {
    return when (status) {
        "COMPLETED" -> when {
            partnerName.isBlank() && isReceiver -> "Collection completed"
            partnerName.isBlank() -> "Pickup completed"
            isReceiver -> "Collected from $partnerName"
            else -> "Collected by $partnerName"
        }
        "IN_PROGRESS" -> when {
            partnerName.isBlank() && isReceiver -> "Collection in progress"
            partnerName.isBlank() -> "Pickup in progress"
            isReceiver -> "Collecting from $partnerName"
            else -> "$partnerName is on the way"
        }
        "EXPIRED" -> if (isReceiver) "Donation expired before collection" else "Donation expired before pickup"
        else -> "Donation update"
    }
}

private fun historyTimestamp(item: DonationHistoryItem): Long {
    return when (item.status) {
        "COMPLETED" -> item.completedAt.takeIf { it > 0L } ?: item.createdAt
        "EXPIRED" -> item.expiredAt.takeIf { it > 0L } ?: item.expiresAt
        else -> item.createdAt
    }
}

private fun formatHistoryDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Not available"
    return SimpleDateFormat(
        "dd MMM yyyy • hh:mm a",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun historyTimestampLabel(item: DonationHistoryItem): String {
    return when (item.status) {
        "COMPLETED" -> "Completed ${formatHistoryDate(historyTimestamp(item))}"
        "EXPIRED" -> "Expired ${formatHistoryDate(historyTimestamp(item))}"
        "IN_PROGRESS" -> "Claimed ${formatHistoryDate(historyTimestamp(item))}"
        else -> "Posted ${formatHistoryDate(item.createdAt)}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUserRole by remember { mutableStateOf(authViewModel.uiState.value.currentUserRole) }
    val historyItems = remember { mutableStateListOf<DonationHistoryItem>() }
    val currentUserId = authViewModel.uiState.value.currentUserId
        .ifBlank { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }

        try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(currentUserId).get().await()
            currentUserRole = userDoc.getString("role")
                ?: authViewModel.uiState.value.currentUserRole
                ?: "DONOR"
            val historyField = if (currentUserRole == "RECEIVER") "claimedBy" else "donorId"
            val snapshot = db
                .collection("donations")
                .whereEqualTo(historyField, currentUserId)
                .get()
                .await()

            val now = System.currentTimeMillis()
            val expiredIds = expireOverdueDonations(db, snapshot.documents, now)

            val mappedItems = snapshot.documents.mapNotNull { document ->
                val expiresAt = document.getLong("expiresAt") ?: 0L
                val storedStatus = document.getString("status") ?: "AVAILABLE"
                val displayStatus = if (document.id in expiredIds) {
                    "EXPIRED"
                } else {
                    donationDisplayStatus(storedStatus, expiresAt, now)
                }

                if (displayStatus == "AVAILABLE" || displayStatus == "EXPIRING SOON") {
                    return@mapNotNull null
                }

                DonationHistoryItem(
                    id = document.id,
                    title = document.getString("title") ?: return@mapNotNull null,
                    quantity = document.getString("quantity").orEmpty(),
                    status = displayStatus,
                    partnerName = if (currentUserRole == "RECEIVER") {
                        document.getString("donorName").orEmpty()
                    } else {
                        document.getString("claimedByName").orEmpty()
                    },
                    createdAt = document.getLong("createdAt") ?: 0L,
                    expiresAt = expiresAt,
                    completedAt = document.getLong("completedAt") ?: 0L,
                    expiredAt = document.getLong("expiredAt") ?: 0L,
                    pickupAddress = document.getString("pickupAddress").orEmpty()
                )
            }.sortedByDescending(::historyTimestamp)

            historyItems.clear()
            historyItems.addAll(mappedItems)
        } catch (_: Exception) {
            historyItems.clear()
        } finally {
            isLoading = false
        }
    }

    val activeCount = historyItems.count { it.status == "IN_PROGRESS" }
    val completedCount = historyItems.count { it.status == "COMPLETED" }
    val expiredCount = historyItems.count { it.status == "EXPIRED" }
    val resolvedCount = completedCount + expiredCount
    val rescueRate = if (resolvedCount == 0) 0f else completedCount.toFloat() / resolvedCount.toFloat()
    val isReceiver = currentUserRole == "RECEIVER"

    val filteredItems = historyItems.filter {
        when (selectedFilter) {
            1 -> it.status == "IN_PROGRESS"
            2 -> it.status == "COMPLETED"
            3 -> it.status == "EXPIRED"
            else -> true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isReceiver) "Collection History" else "Donation History",
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Text(
                            text = if (isReceiver) {
                                "Track claimed, collected, and missed pickups."
                            } else {
                                "Track what happened after each hotel post."
                            },
                            fontSize = 12.sp,
                            color = SurfaceWhite.copy(alpha = 0.82f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        bottomBar = {
            if (isReceiver) {
                NgoBottomNav(navController = navController, selected = 2)
            } else {
                FoodBridgeBottomNav(navController = navController, selected = 2)
            }
        }
    ) { padding ->
        if (isLoading) {
            HistoryLoadingState(padding = padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HistoryHeroCard(
                        completedCount = completedCount,
                        activeCount = activeCount,
                        expiredCount = expiredCount,
                        rescueRate = rescueRate,
                        isReceiver = isReceiver
                    )
                }

                item {
                    HistoryStatsRow(
                        completedCount = completedCount,
                        activeCount = activeCount,
                        expiredCount = expiredCount,
                        isReceiver = isReceiver
                    )
                }

                item {
                    HistoryFilterRow(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it }
                    )
                }

                if (filteredItems.isEmpty()) {
                    item {
                        EmptyHistoryState(
                            showPostAction = !isReceiver && historyItems.isEmpty(),
                            onPostDonation = { navController.navigate(Screen.PostDonation.route) }
                        )
                    }
                } else {
                    items(filteredItems) { item ->
                        HistoryCard(
                            item = item,
                            isReceiver = isReceiver,
                            onTrackRoute = if (canTrackNgo(item.status, item.expiresAt)) {
                                { navController.navigate(Screen.PickupRoute.createRoute(item.id)) }
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
private fun HistoryHeroCard(
    completedCount: Int,
    activeCount: Int,
    expiredCount: Int,
    rescueRate: Float,
    isReceiver: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GreenPrimary)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isReceiver) "NGO performance" else "Hotel performance",
                        color = SurfaceWhite.copy(alpha = 0.82f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isReceiver) {
                            "$completedCount successful collections"
                        } else {
                            "$completedCount successful pickups"
                        },
                        color = SurfaceWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceWhite.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = SurfaceWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { rescueRate },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = SurfaceWhite,
                trackColor = SurfaceWhite.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isReceiver) {
                    "${(rescueRate * 100).toInt()}% collection success • $activeCount active • $expiredCount missed"
                } else {
                    "${(rescueRate * 100).toInt()}% rescue rate • $activeCount active • $expiredCount missed"
                },
                color = SurfaceWhite.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HistoryStatsRow(
    completedCount: Int,
    activeCount: Int,
    expiredCount: Int,
    isReceiver: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HistoryStatCard(
            label = if (isReceiver) "Collected" else "Rescued",
            value = completedCount.toString(),
            helper = if (isReceiver) "Completed collections" else "Completed pickups",
            color = GreenLight,
            modifier = Modifier.weight(1f)
        )
        HistoryStatCard(
            label = "Active",
            value = activeCount.toString(),
            helper = if (isReceiver) "Scheduled collections" else "NGOs on the way",
            color = OrangeAccent,
            modifier = Modifier.weight(1f)
        )
        HistoryStatCard(
            label = "Missed",
            value = expiredCount.toString(),
            helper = "Expired donations",
            color = StatusExpired,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HistoryStatCard(
    label: String,
    value: String,
    helper: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = helper, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFilterRow(
    selectedFilter: Int,
    onFilterSelected: (Int) -> Unit
) {
    val filters = listOf("All", "Active", "Completed", "Expired")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedFilter == index,
                onClick = { onFilterSelected(index) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenContainer,
                    selectedLabelColor = GreenDark
                )
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: DonationHistoryItem,
    isReceiver: Boolean,
    onTrackRoute: (() -> Unit)?
) {
    val tone = historyStatusColor(item.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .clip(CircleShape)
                            .background(tone.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                "COMPLETED" -> Icons.Default.CheckCircle
                                "EXPIRED" -> Icons.Default.Warning
                                else -> Icons.Default.LocalShipping
                            },
                            contentDescription = null,
                            tint = tone
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = historyTitle(
                                status = item.status,
                                partnerName = item.partnerName,
                                isReceiver = isReceiver
                            ),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tone.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = historyStatusLabel(item.status),
                        color = tone,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HistoryMetaRow(
                icon = Icons.Default.History,
                label = historyTimestampLabel(item)
            )
            if (item.quantity.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HistoryMetaRow(
                    icon = Icons.Default.Schedule,
                    label = item.quantity
                )
            }
            if (item.status != "EXPIRED" && item.expiresAt > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                HistoryMetaRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Pickup window ended ${formatExpiryDateTime(item.expiresAt)}"
                )
            }
            if (item.pickupAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HistoryMetaRow(
                    icon = Icons.Default.Place,
                    label = item.pickupAddress
                )
            }

            if (onTrackRoute != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onTrackRoute,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Track NGO pickup")
                }
            }
        }
    }
}

@Composable
private fun HistoryMetaRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyHistoryState(
    showPostAction: Boolean,
    onPostDonation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "📚", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No history to show yet",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (showPostAction) {
                    "Once a donation is claimed, completed, or expires, it will appear here."
                } else {
                    "No items match this filter right now."
                },
                color = TextSecondary
            )
            if (showPostAction) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPostDonation,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    Text("Post a donation")
                }
            }
        }
    }
}

@Composable
private fun HistoryLoadingState(
    padding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(4) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(18.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(TextSecondary.copy(alpha = 0.14f))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(TextSecondary.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(TextSecondary.copy(alpha = 0.1f))
                    )
                }
            }
        }
    }
}
