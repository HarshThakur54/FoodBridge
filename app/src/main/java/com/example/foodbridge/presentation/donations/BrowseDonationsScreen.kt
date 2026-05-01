package com.example.foodbridge.presentation.donations

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.foodbridge.data.repository.canTrackNgo
import com.example.foodbridge.data.repository.donationDisplayStatus
import com.example.foodbridge.data.repository.expireOverdueDonations
import com.example.foodbridge.data.repository.formatExpiryCountdown
import com.example.foodbridge.data.repository.isDonationExpired
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.presentation.auth.AuthViewModel
import com.example.foodbridge.presentation.home.FoodBridgeBottomNav
import com.example.foodbridge.presentation.ngo.NgoBottomNav
import com.example.foodbridge.ui.theme.BackgroundLight
import com.example.foodbridge.ui.theme.GreenContainer
import com.example.foodbridge.ui.theme.GreenDark
import com.example.foodbridge.ui.theme.GreenPrimary
import com.example.foodbridge.ui.theme.OrangeAccent
import com.example.foodbridge.ui.theme.StatusCaution
import com.example.foodbridge.ui.theme.StatusExpired
import com.example.foodbridge.ui.theme.StatusSafe
import com.example.foodbridge.ui.theme.SurfaceWhite
import com.example.foodbridge.ui.theme.TextPrimary
import com.example.foodbridge.ui.theme.TextSecondary
import com.example.foodbridge.ui.theme.foodBridgeOutlinedTextFieldColors
import com.example.foodbridge.util.ResolvedLocation
import com.example.foodbridge.util.calculateDistanceKm
import com.example.foodbridge.util.coalesceAddress
import com.example.foodbridge.util.formatDistanceLabel
import com.example.foodbridge.util.isValidCoordinate
import com.example.foodbridge.util.openMapRoute
import com.example.foodbridge.util.resolveLocation
import com.example.foodbridge.util.routePriorityScore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class DonationItem(
    val id: String,
    val title: String,
    val donorId: String,
    val donorName: String,
    val distance: String,
    val quantity: String,
    val pickupWindow: String,
    val expiryLabel: String,
    val expiresAt: Long,
    val category: String,
    val isVeg: Boolean,
    val status: String,
    val emoji: String,
    val photoUrl: String,
    val pickupAddress: String,
    val pickupCity: String,
    val pickupPincode: String,
    val pickupLatitude: Double? = null,
    val pickupLongitude: Double? = null,
    val distanceKm: Double? = null,
    val routePriority: Double = Double.MAX_VALUE
)

private data class BrowseLoadResult(
    val currentUserId: String,
    val currentUserRole: String,
    val currentUserName: String,
    val currentUserLocation: ResolvedLocation,
    val donations: List<DonationItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseDonationsScreen(navController: NavController, authViewModel: AuthViewModel) {
    val filters = listOf("Nearby", "Vegetarian", "Expiring", "Meals", "Bakery")
    val authUiState by authViewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("Nearby") }
    var searchQuery by remember { mutableStateOf("") }
    var isListView by remember { mutableStateOf(true) }
    var claimingId by remember { mutableStateOf<String?>(null) }
    var donations by remember { mutableStateOf<List<DonationItem>>(emptyList()) }
    var isLoadingDonations by remember { mutableStateOf(true) }
    var currentUserId by remember { mutableStateOf("") }
    var currentUserRole by remember { mutableStateOf("") }
    var currentUserName by remember { mutableStateOf("Your NGO") }
    var currentUserLocation by remember { mutableStateOf(ResolvedLocation()) }
    var hasResolvedUserRole by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    fun loadDonations() {
        isLoadingDonations = true
        scope.launch {
            try {
                val loadResult = withContext(Dispatchers.IO) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    val currentUserDoc = uid.takeIf { it.isNotBlank() }
                        ?.let { db.collection("users").document(it).get().await() }

                    val role = currentUserDoc?.getString("role") ?: "DONOR"
                    val name = currentUserDoc?.getString("fullName") ?: "Your NGO"
                    val baseLocation = resolveLocation(
                        context = context.applicationContext,
                        address = currentUserDoc?.let(::profileAddressFromDoc).orEmpty(),
                        city = currentUserDoc?.getString("city").orEmpty(),
                        pincode = currentUserDoc?.getString("pincode").orEmpty(),
                        latitude = currentUserDoc?.getDouble("latitude"),
                        longitude = currentUserDoc?.getDouble("longitude")
                    )

                    val snapshot = db.collection("donations")
                        .whereEqualTo("status", "AVAILABLE")
                        .get()
                        .await()

                    val now = System.currentTimeMillis()
                    val expiredIds = expireOverdueDonations(db, snapshot.documents, now)
                    val donorDocs = snapshot.documents
                        .mapNotNull { it.getString("donorId") }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .associateWith { donorId ->
                            db.collection("users").document(donorId).get().await()
                        }

                    val loadedDonations = snapshot.documents.mapNotNull { doc ->
                        if (doc.id in expiredIds) return@mapNotNull null

                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        val displayStatus = donationDisplayStatus(
                            storedStatus = doc.getString("status") ?: "AVAILABLE",
                            expiresAt = expiresAt,
                            now = now
                        )
                        if (displayStatus == "EXPIRED") return@mapNotNull null

                        val donorId = doc.getString("donorId").orEmpty()
                        val donorDoc = donorDocs[donorId]
                        val donorName = doc.getString("donorName")
                            .orEmpty()
                            .ifBlank { donorDoc?.getString("fullName").orEmpty() }
                            .ifBlank { "Hotel donor" }
                        val pickupAddress = donationAddressFromDoc(doc, donorDoc)
                        val pickupCity = doc.getString("pickupCity")
                            .orEmpty()
                            .ifBlank { donorDoc?.getString("city").orEmpty() }
                        val pickupPincode = doc.getString("pickupPincode")
                            .orEmpty()
                            .ifBlank { donorDoc?.getString("pincode").orEmpty() }
                        val pickupLocation = resolveLocation(
                            context = context.applicationContext,
                            address = pickupAddress,
                            city = pickupCity,
                            pincode = pickupPincode,
                            latitude = doc.getDouble("pickupLatitude") ?: donorDoc?.getDouble("latitude"),
                            longitude = doc.getDouble("pickupLongitude") ?: donorDoc?.getDouble("longitude")
                        )

                        val samePincode = baseLocation.pincode.isNotBlank() &&
                            pickupLocation.pincode.isNotBlank() &&
                            baseLocation.pincode.equals(pickupLocation.pincode, ignoreCase = true)
                        val sameCity = baseLocation.city.isNotBlank() &&
                            pickupLocation.city.isNotBlank() &&
                            baseLocation.city.equals(pickupLocation.city, ignoreCase = true)
                        val distanceKm = if (baseLocation.hasCoordinates && pickupLocation.hasCoordinates) {
                            calculateDistanceKm(
                                originLatitude = baseLocation.latitude!!,
                                originLongitude = baseLocation.longitude!!,
                                destinationLatitude = pickupLocation.latitude!!,
                                destinationLongitude = pickupLocation.longitude!!
                            )
                        } else {
                            null
                        }

                        DonationItem(
                            id = doc.id,
                            title = doc.getString("title") ?: return@mapNotNull null,
                            donorId = donorId,
                            donorName = donorName,
                            distance = formatDistanceLabel(distanceKm, sameCity, samePincode),
                            quantity = doc.getString("quantity").orEmpty(),
                            pickupWindow = doc.getString("pickupTime").orEmpty(),
                            expiryLabel = formatExpiryCountdown(expiresAt, now),
                            expiresAt = expiresAt,
                            category = doc.getString("category").orEmpty(),
                            isVeg = doc.getBoolean("isVeg") ?: true,
                            status = displayStatus,
                            emoji = if (doc.getBoolean("isVeg") ?: true) "🥗" else "🍗",
                            photoUrl = doc.getString("photoUrl").orEmpty(),
                            pickupAddress = pickupLocation.address,
                            pickupCity = pickupLocation.city,
                            pickupPincode = pickupLocation.pincode,
                            pickupLatitude = pickupLocation.latitude,
                            pickupLongitude = pickupLocation.longitude,
                            distanceKm = distanceKm,
                            routePriority = routePriorityScore(
                                distanceKm = distanceKm,
                                expiresAt = expiresAt,
                                cityMatch = sameCity,
                                pincodeMatch = samePincode,
                                now = now
                            )
                        )
                    }

                    BrowseLoadResult(
                        currentUserId = uid,
                        currentUserRole = role,
                        currentUserName = name,
                        currentUserLocation = baseLocation,
                        donations = loadedDonations
                    )
                }

                currentUserId = loadResult.currentUserId
                currentUserRole = loadResult.currentUserRole
                currentUserName = loadResult.currentUserName
                currentUserLocation = loadResult.currentUserLocation
                donations = loadResult.donations
                hasResolvedUserRole = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentUserRole.isBlank()) {
                    currentUserRole = authUiState.currentUserRole.ifBlank { "DONOR" }
                }
                hasResolvedUserRole = true
                Toast.makeText(
                    context,
                    "Failed to load donations: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLoadingDonations = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDonations() }

    val isNgo = currentUserRole == "RECEIVER"

    val filteredDonations = donations
        .filter { donation ->
            val matchesQuery = searchQuery.isEmpty() ||
                donation.title.contains(searchQuery, ignoreCase = true) ||
                donation.donorName.contains(searchQuery, ignoreCase = true) ||
                donation.pickupAddress.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Vegetarian" -> donation.isVeg
                "Expiring" -> donation.status == "EXPIRING SOON"
                "Meals" -> donation.category == "Cooked food"
                "Bakery" -> donation.category == "Bakery"
                else -> true
            }

            matchesQuery && matchesFilter
        }
        .let { matches ->
            when (selectedFilter) {
                "Expiring" -> matches.sortedBy { it.expiresAt }
                else -> matches.sortedBy { it.routePriority }
            }
        }

    val routeSuggestions = if (isNgo) {
        filteredDonations
            .filter {
                it.pickupAddress.isNotBlank() || isValidCoordinate(it.pickupLatitude, it.pickupLongitude)
            }
            .sortedBy { it.routePriority }
            .take(3)
    } else {
        emptyList()
    }

    val openRouteForDonation: (DonationItem) -> Unit = { donation ->
        if (donation.pickupAddress.isBlank() &&
            !isValidCoordinate(donation.pickupLatitude, donation.pickupLongitude)
        ) {
            Toast.makeText(
                context,
                "This hotel still needs a pickup address in profile.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            openMapRoute(
                context = context,
                origin = currentUserLocation.takeIf { isNgo },
                destinationLabel = donation.donorName,
                destinationAddress = donation.pickupAddress,
                destinationLatitude = donation.pickupLatitude,
                destinationLongitude = donation.pickupLongitude
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Available Donations",
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                },
                actions = {
                    IconButton(onClick = { isListView = !isListView }) {
                        Icon(
                            if (isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                            null,
                            tint = SurfaceWhite
                        )
                    }
                    IconButton(onClick = { loadDonations() }) {
                        Icon(Icons.Default.Refresh, null, tint = SurfaceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        bottomBar = {
            if (hasResolvedUserRole) {
                if (isNgo) NgoBottomNav(navController, 1)
                else FoodBridgeBottomNav(navController, 1)
            } else {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search food, hotels, addresses...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50.dp),
                    colors = foodBridgeOutlinedTextFieldColors(),
                    singleLine = true
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenPrimary,
                                selectedLabelColor = SurfaceWhite
                            )
                        )
                    }
                }
            }

            if (isNgo) {
                item {
                    RouteOptimizerCard(
                        currentUserName = currentUserName,
                        currentUserLocation = currentUserLocation,
                        routeSuggestions = routeSuggestions,
                        onAddAddress = { navController.navigate(Screen.Profile.route) },
                        onOpenRoute = openRouteForDonation
                    )
                }
            }

            if (isLoadingDonations) {
                item {
                    BrowseLoadingSkeleton()
                }
            } else if (filteredDonations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🍽️", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isEmpty()) "No donations available right now"
                                else "No results for \"$searchQuery\"",
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(filteredDonations) { donation ->
                    DonationListCard(
                        donation = donation,
                        isClaiming = claimingId == donation.id,
                        onOpenRoute = { openRouteForDonation(donation) },
                        onClaim = {
                            if (!isNgo) {
                                Toast.makeText(
                                    context,
                                    "Only NGOs can claim donations",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@DonationListCard
                            }
                            if (currentUserId.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Please wait, loading your NGO profile...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@DonationListCard
                            }

                            claimingId = donation.id
                            scope.launch {
                                try {
                                    val donationRef = db.collection("donations").document(donation.id)
                                    val donationDoc = donationRef.get().await()
                                    val currentStatus = donationDoc.getString("status") ?: "AVAILABLE"
                                    val expiresAt = donationDoc.getLong("expiresAt") ?: 0L

                                    if (currentStatus != "AVAILABLE") {
                                        Toast.makeText(
                                            context,
                                            "This donation is no longer available.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        donations = donations.filter { it.id != donation.id }
                                        return@launch
                                    }

                                    if (isDonationExpired(expiresAt)) {
                                        donationRef.update(
                                            mapOf(
                                                "status" to "EXPIRED",
                                                "expiredAt" to System.currentTimeMillis()
                                            )
                                        ).await()
                                        donations = donations.filter { it.id != donation.id }
                                        Toast.makeText(
                                            context,
                                            "This donation has expired.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }

                                    val ngoDoc = db.collection("users").document(currentUserId).get().await()
                                    val ngoName = ngoDoc.getString("fullName") ?: "An NGO"
                                    val ngoAddress = coalesceAddress(
                                        ngoDoc.getString("address").orEmpty(),
                                        ngoDoc.getString("addressLine1").orEmpty(),
                                        ngoDoc.getString("addressLine2").orEmpty(),
                                        ngoDoc.getString("city").orEmpty(),
                                        ngoDoc.getString("state").orEmpty(),
                                        ngoDoc.getString("pincode").orEmpty()
                                    )
                                    val ngoLocation = resolveLocation(
                                        context = context.applicationContext,
                                        address = ngoAddress,
                                        city = ngoDoc.getString("city").orEmpty(),
                                        pincode = ngoDoc.getString("pincode").orEmpty(),
                                        latitude = ngoDoc.getDouble("latitude"),
                                        longitude = ngoDoc.getDouble("longitude")
                                    )
                                    val pickupPin = Random.nextInt(1000, 10_000).toString()

                                    donationRef.update(
                                        mapOf(
                                            "status" to "IN_PROGRESS",
                                            "claimedBy" to currentUserId,
                                            "claimedByName" to ngoName,
                                            "claimedAt" to System.currentTimeMillis(),
                                            "pickupPin" to pickupPin,
                                            "pinVerified" to false,
                                            "completedAt" to 0L,
                                            "ngoOriginAddress" to ngoLocation.address,
                                            "ngoLiveLatitude" to (ngoLocation.latitude ?: 0.0),
                                            "ngoLiveLongitude" to (ngoLocation.longitude ?: 0.0),
                                            "ngoLocationUpdatedAt" to System.currentTimeMillis()
                                        )
                                    ).await()

                                    val donorId = donationDoc.getString("donorId").orEmpty()
                                    val foodTitle = donationDoc.getString("title") ?: donation.title

                                    if (donorId.isNotBlank()) {
                                        db.collection("notifications").add(
                                            hashMapOf(
                                                "title" to "Pickup In Progress",
                                                "message" to "$ngoName started pickup for $foodTitle. Verify the NGO PIN when they arrive.",
                                                "type" to "IN_PROGRESS",
                                                "recipientId" to donorId,
                                                "createdAt" to System.currentTimeMillis(),
                                                "isUnread" to true,
                                                "donationId" to donation.id
                                            )
                                        ).await()
                                    }

                                    donations = donations.filter { it.id != donation.id }
                                    Toast.makeText(
                                        context,
                                        if (canTrackNgo("IN_PROGRESS", expiresAt)) {
                                            "Donation claimed. Pickup PIN generated."
                                        } else {
                                            "Donation claimed. Live NGO tracking is unavailable because no expiry limit is set."
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (canTrackNgo("IN_PROGRESS", expiresAt)) {
                                        navController.navigate(Screen.PickupRoute.createRoute(donation.id))
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Claim failed: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    claimingId = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(GreenContainer, RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        BrowseSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.54f)
                                .height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BrowseSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.30f)
                                .height(10.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        BrowseSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.44f)
                                .height(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    BrowseSkeletonLine(
                        modifier = Modifier
                            .width(60.dp)
                            .height(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseSkeletonLine(
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .background(TextSecondary.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
    )
}

@Composable
private fun RouteOptimizerCard(
    currentUserName: String,
    currentUserLocation: ResolvedLocation,
    routeSuggestions: List<DonationItem>,
    onAddAddress: () -> Unit,
    onOpenRoute: (DonationItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GreenContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(GreenPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Map, null, tint = GreenPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Nearby Pickup Suggestions",
                        fontWeight = FontWeight.Bold,
                        color = GreenDark,
                        fontSize = 16.sp
                    )
                    Text(
                        "Ranked hotel pickups near your NGO to help you plan the quickest route.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (currentUserLocation.address.isBlank()) {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Add your NGO address to unlock nearby hotel ranking.",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedButton(
                            onClick = onAddAddress,
                            border = BorderStroke(1.dp, GreenPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add NGO Address", color = GreenPrimary)
                        }
                    }
                }
            } else {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Base: $currentUserName",
                            fontWeight = FontWeight.Bold,
                            color = GreenDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currentUserLocation.address,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (routeSuggestions.isEmpty()) {
                    Text(
                        "No route suggestions yet. Add hotel addresses in profile or wait for fresh donations.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    routeSuggestions.forEachIndexed { index, donation ->
                        Surface(
                            color = SurfaceWhite,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(GreenPrimary, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = SurfaceWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${donation.donorName} • ${donation.distance}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        donation.title,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    if (donation.pickupWindow.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Pickup: ${donation.pickupWindow}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onOpenRoute(donation) },
                                    border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.45f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Map", color = GreenPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonationListCard(
    donation: DonationItem,
    isClaiming: Boolean,
    onOpenRoute: () -> Unit,
    onClaim: () -> Unit
) {
    val expiryColor = when (donation.status) {
        "EXPIRING SOON" -> StatusCaution
        "EXPIRED" -> StatusExpired
        else -> StatusSafe
    }
    val statusBg = when (donation.status) {
        "EXPIRING SOON" -> StatusCaution.copy(alpha = 0.15f)
        "EXPIRED" -> StatusExpired.copy(alpha = 0.15f)
        else -> StatusSafe.copy(alpha = 0.15f)
    }
    val canOpenMap = donation.pickupAddress.isNotBlank() ||
        isValidCoordinate(donation.pickupLatitude, donation.pickupLongitude)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
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
                    Text(donation.emoji, fontSize = 64.sp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Surface(color = statusBg, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            donation.status,
                            color = expiryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Surface(
                        color = if (donation.isVeg) GreenPrimary else OrangeAccent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (donation.isVeg) "VEG" else "NON-VEG",
                            color = SurfaceWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Store,
                        null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(donation.donorName, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("•", color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(donation.distance, color = GreenPrimary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PeopleAlt,
                        null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(donation.quantity, color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        Icons.Default.Timer,
                        null,
                        tint = expiryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        donation.expiryLabel,
                        color = expiryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (donation.pickupWindow.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Pickup: ${donation.pickupWindow}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                if (donation.pickupAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            donation.pickupAddress,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (canOpenMap) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenRoute,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary)
                        ) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Map", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onClaim,
                            enabled = !isClaiming,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                        ) {
                            if (isClaiming) {
                                CircularProgressIndicator(
                                    color = SurfaceWhite,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Claim Donation", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onClaim,
                        enabled = !isClaiming,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                    ) {
                        if (isClaiming) {
                            CircularProgressIndicator(
                                color = SurfaceWhite,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Claim Donation", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun profileAddressFromDoc(doc: DocumentSnapshot): String {
    return coalesceAddress(
        doc.getString("address").orEmpty(),
        doc.getString("addressLine1").orEmpty(),
        doc.getString("addressLine2").orEmpty(),
        doc.getString("city").orEmpty(),
        doc.getString("state").orEmpty(),
        doc.getString("pincode").orEmpty()
    )
}

private fun donationAddressFromDoc(
    donationDoc: DocumentSnapshot,
    donorDoc: DocumentSnapshot?
): String {
    return coalesceAddress(
        donationDoc.getString("pickupAddress").orEmpty(),
        donorDoc?.getString("address").orEmpty(),
        donorDoc?.getString("addressLine1").orEmpty(),
        donorDoc?.getString("addressLine2").orEmpty(),
        donorDoc?.getString("city").orEmpty(),
        donorDoc?.getString("state").orEmpty(),
        donorDoc?.getString("pincode").orEmpty()
    )
}
