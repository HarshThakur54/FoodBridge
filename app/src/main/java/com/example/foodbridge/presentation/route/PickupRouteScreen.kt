package com.example.foodbridge.presentation.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.example.foodbridge.data.repository.canShowPickupPin
import com.example.foodbridge.data.repository.canTrackNgo
import com.example.foodbridge.data.repository.isDonationExpired
import com.example.foodbridge.ui.theme.GreenContainer
import com.example.foodbridge.ui.theme.GreenDark
import com.example.foodbridge.ui.theme.GreenLight
import com.example.foodbridge.ui.theme.GreenPrimary
import com.example.foodbridge.ui.theme.OrangeAccent
import com.example.foodbridge.ui.theme.StatusExpired
import com.example.foodbridge.ui.theme.SurfaceWhite
import com.example.foodbridge.ui.theme.TextPrimary
import com.example.foodbridge.ui.theme.TextSecondary
import com.example.foodbridge.ui.theme.foodBridgeOutlinedTextFieldColors
import com.example.foodbridge.util.ResolvedLocation
import com.example.foodbridge.util.isValidCoordinate
import com.example.foodbridge.util.openMapRoute
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val routeLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)

private data class PickupRouteUiState(
    val donationId: String = "",
    val donationTitle: String = "",
    val status: String = "",
    val donorName: String = "Hotel",
    val donorAddress: String = "",
    val donorLatitude: Double? = null,
    val donorLongitude: Double? = null,
    val ngoName: String = "NGO",
    val ngoAddress: String = "",
    val ngoLatitude: Double? = null,
    val ngoLongitude: Double? = null,
    val expiresAt: Long = 0L,
    val pickupPin: String = "",
    val ngoLocationUpdatedAt: Long = 0L
) {
    val hasRouteCoordinates: Boolean
        get() = isValidCoordinate(ngoLatitude, ngoLongitude) &&
            isValidCoordinate(donorLatitude, donorLongitude)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupRouteScreen(
    navController: NavController,
    donationId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }
    val donationRef = remember(donationId) { db.collection("donations").document(donationId) }
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    var routeState by remember { mutableStateOf(PickupRouteUiState(donationId = donationId)) }
    var currentUserRole by remember { mutableStateOf("DONOR") }
    var isLoading by remember { mutableStateOf(true) }
    var pinInput by remember { mutableStateOf("") }
    var isCompleting by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember {
        mutableStateOf(
            hasLocationPermission(context)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        locationPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }

        runCatching {
            val userDoc = db.collection("users").document(uid).get().await()
            currentUserRole = userDoc.getString("role") ?: "DONOR"
        }

        if (!locationPermissionGranted) {
            permissionLauncher.launch(routeLocationPermissions)
        }
    }

    DisposableEffect(donationId) {
        val listener: ListenerRegistration = donationRef.addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) {
                isLoading = false
                return@addSnapshotListener
            }

            routeState = PickupRouteUiState(
                donationId = snapshot.id,
                donationTitle = snapshot.getString("title").orEmpty(),
                status = snapshot.getString("status").orEmpty(),
                donorName = snapshot.getString("donorName").orEmpty().ifBlank { "Hotel" },
                donorAddress = snapshot.getString("pickupAddress").orEmpty(),
                donorLatitude = snapshot.getDouble("pickupLatitude"),
                donorLongitude = snapshot.getDouble("pickupLongitude"),
                ngoName = snapshot.getString("claimedByName").orEmpty().ifBlank { "NGO" },
                ngoAddress = snapshot.getString("ngoOriginAddress").orEmpty(),
                ngoLatitude = snapshot.getDouble("ngoLiveLatitude"),
                ngoLongitude = snapshot.getDouble("ngoLiveLongitude"),
                expiresAt = snapshot.getLong("expiresAt") ?: 0L,
                pickupPin = snapshot.get("pickupPin")
                    ?.let { pinValue ->
                        when (pinValue) {
                            is Number -> pinValue.toInt().toString().padStart(4, '0')
                            else -> pinValue.toString().trim()
                        }
                    }
                    .orEmpty(),
                ngoLocationUpdatedAt = snapshot.getLong("ngoLocationUpdatedAt") ?: 0L
            )
            isLoading = false
        }

        onDispose { listener.remove() }
    }

    DisposableEffect(currentUserRole, donationId, locationPermissionGranted) {
        if (currentUserRole != "RECEIVER" ||
            !locationPermissionGranted ||
            locationManager == null ||
            donationId.isBlank()
        ) {
            return@DisposableEffect onDispose {}
        }

        val hasFinePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFinePermission && !hasCoarsePermission) {
            return@DisposableEffect onDispose {}
        }

        var lastSharedAt = 0L
        var lastLocation: Location? = null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter(locationManager::isProviderEnabled)

        val locationListener = android.location.LocationListener { location ->
            val now = System.currentTimeMillis()
            val movedEnough = lastLocation?.distanceTo(location)?.let { it >= 25f } ?: true
            val waitedEnough = now - lastSharedAt >= 10_000L

            if (!movedEnough && !waitedEnough) return@LocationListener

            lastLocation = location
            lastSharedAt = now

            scope.launch {
                runCatching {
                    donationRef.update(
                        mapOf(
                            "ngoLiveLatitude" to location.latitude,
                            "ngoLiveLongitude" to location.longitude,
                            "ngoLocationUpdatedAt" to now
                        )
                    ).await()
                }
            }
        }

        shareLastKnownLocation(locationManager, providers, locationListener)

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    5_000L,
                    20f,
                    locationListener
                )
            }
        }

        onDispose {
            runCatching { locationManager.removeUpdates(locationListener) }
        }
    }

    val actionsExpired = isDonationExpired(routeState.expiresAt)
    val effectiveStatus = when {
        routeState.status == "COMPLETED" -> "COMPLETED"
        routeState.status == "EXPIRED" || actionsExpired -> "EXPIRED"
        routeState.status == "CLAIMED" -> "IN_PROGRESS"
        else -> routeState.status
    }
    val trackingAvailable = canTrackNgo(routeState.status, routeState.expiresAt)
    val pickupPinAvailable = canShowPickupPin(routeState.status, routeState.expiresAt)

    val statusLabel = when (effectiveStatus) {
        "IN_PROGRESS", "CLAIMED" -> "IN PROGRESS"
        "COMPLETED" -> "COMPLETED"
        "EXPIRED" -> "EXPIRED"
        else -> effectiveStatus.ifBlank { "UNKNOWN" }
    }
    val statusColor = when (effectiveStatus) {
        "IN_PROGRESS", "CLAIMED" -> OrangeAccent
        "COMPLETED" -> GreenPrimary
        "EXPIRED" -> StatusExpired
        else -> TextSecondary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pickup Route", fontWeight = FontWeight.Bold, color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SurfaceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { padding ->
        if (isLoading) {
            PickupRouteLoadingSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = GreenContainer,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        routeState.donationTitle.ifBlank { "Pickup tracking" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GreenDark
                    )
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        "Hotel: ${routeState.donorName}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        routeState.donorAddress.ifBlank { "Hotel address missing" },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (trackingAvailable) {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, null, tint = GreenPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Live NGO to hotel route",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        if (routeState.hasRouteCoordinates) {
                            LiveRouteMap(
                                ngoName = routeState.ngoName,
                                hotelName = routeState.donorName,
                                ngoLatitude = routeState.ngoLatitude!!,
                                ngoLongitude = routeState.ngoLongitude!!,
                                hotelLatitude = routeState.donorLatitude!!,
                                hotelLongitude = routeState.donorLongitude!!
                            )
                        } else {
                            RouteMapSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp),
                                title = "Waiting for live route coordinates",
                                supportingText = if (currentUserRole == "RECEIVER") {
                                    "Turn on location permission to start sharing your route."
                                } else {
                                    "The NGO route will appear here once live location sharing starts."
                                }
                            )
                        }

                        Button(
                            onClick = {
                                openMapRoute(
                                    context = context,
                                    origin = ResolvedLocation(
                                        latitude = routeState.ngoLatitude,
                                        longitude = routeState.ngoLongitude,
                                        address = routeState.ngoAddress
                                    ),
                                    destinationLabel = routeState.donorName,
                                    destinationAddress = routeState.donorAddress,
                                    destinationLatitude = routeState.donorLatitude,
                                    destinationLongitude = routeState.donorLongitude
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LocationOn, null)
                            Text("Open Navigation", modifier = Modifier.padding(start = 8.dp))
                        }

                        if (routeState.ngoLocationUpdatedAt > 0L) {
                            Text(
                                "Last NGO location update: ${formatTrackingTime(routeState.ngoLocationUpdatedAt)}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else if (effectiveStatus == "EXPIRED" || routeState.expiresAt <= 0L) {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (effectiveStatus == "EXPIRED") {
                                "Live tracking unavailable"
                            } else {
                                "Tracking not enabled"
                            },
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            if (effectiveStatus == "EXPIRED") {
                                "This donation has expired, so tracking and PIN verification are no longer available."
                            } else {
                                "This donation does not have an expiry limit, so the Track NGO option is hidden."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (currentUserRole == "RECEIVER" && pickupPinAvailable) {
                Surface(
                    color = GreenLight.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PinDrop, null, tint = GreenPrimary)
                            Text(
                                " Pickup PIN",
                                fontWeight = FontWeight.Bold,
                                color = GreenDark
                            )
                        }
                        Text(
                            routeState.pickupPin.ifBlank { "Generating..." },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = GreenPrimary
                        )
                        Text(
                            "Show this PIN to the hotel. The donation stays in progress until the hotel verifies it.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (currentUserRole != "RECEIVER" && pickupPinAvailable) {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = GreenPrimary)
                            Text(
                                " Verify NGO pickup PIN",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            "Enter the PIN shown by the NGO to complete this donation.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it.take(6).filter(Char::isDigit) },
                            label = { Text("Pickup PIN") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = foodBridgeOutlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (pinInput != routeState.pickupPin) {
                                    Toast.makeText(
                                        context,
                                        "PIN does not match. Please check with the NGO.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }

                                isCompleting = true
                                scope.launch {
                                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                                    try {
                                        donationRef.update(
                                            mapOf(
                                                "status" to "COMPLETED",
                                                "completedAt" to System.currentTimeMillis(),
                                                "pinVerified" to true,
                                                "verifiedByDonorId" to currentUserId
                                            )
                                        ).await()

                                        val ngoRecipientId = db.collection("donations")
                                            .document(donationId)
                                            .get()
                                            .await()
                                            .getString("claimedBy")
                                            .orEmpty()

                                        if (ngoRecipientId.isNotBlank()) {
                                            db.collection("notifications").add(
                                                hashMapOf(
                                                    "title" to "Pickup Verified Successfully",
                                                    "message" to "The hotel verified your pickup PIN for ${routeState.donationTitle}. Donation completed.",
                                                    "type" to "COMPLETED",
                                                    "recipientId" to ngoRecipientId,
                                                    "createdAt" to System.currentTimeMillis(),
                                                    "isUnread" to true,
                                                    "donationId" to donationId
                                                )
                                            ).await()
                                        }

                                        Toast.makeText(
                                            context,
                                            "Donation completed successfully.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        navController.popBackStack()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Couldn't complete donation: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isCompleting = false
                                    }
                                }
                            },
                            enabled = !isCompleting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCompleting) {
                                CircularProgressIndicator(
                                    color = SurfaceWhite,
                                    modifier = Modifier.width(18.dp).height(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Complete Donation")
                            }
                        }
                    }
                }
            } else if (effectiveStatus == "COMPLETED") {
                Surface(
                    color = GreenLight.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Donation completed",
                            fontWeight = FontWeight.Bold,
                            color = GreenDark,
                            fontSize = 16.sp
                        )
                        Text(
                            "The hotel verified the pickup PIN and this donation is now finished.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickupRouteLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmer = rememberInfiniteTransition(label = "pickupRouteLoading")
    val pulse by shimmer.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.64f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pickupRouteLoadingPulse"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = GreenContainer,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(20.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.28f)
                        .height(30.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(14.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(14.dp),
                    pulse = pulse
                )
            }
        }

        Surface(
            color = SurfaceWhite,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonDot(pulse = pulse)
                    Spacer(modifier = Modifier.width(8.dp))
                    SkeletonLine(
                        modifier = Modifier
                            .fillMaxWidth(0.46f)
                            .height(16.dp),
                        pulse = pulse
                    )
                }

                RouteMapSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    title = "Loading pickup route",
                    supportingText = "Syncing hotel details, NGO location and live route."
                )

                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.44f)
                        .height(12.dp),
                    pulse = pulse
                )
            }
        }

        Surface(
            color = GreenLight.copy(alpha = 0.14f),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonDot(pulse = pulse)
                    Spacer(modifier = Modifier.width(8.dp))
                    SkeletonLine(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp),
                        pulse = pulse
                    )
                }
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(32.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(12.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(12.dp),
                    pulse = pulse
                )
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    modifier: Modifier,
    pulse: Float
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextSecondary.copy(alpha = 0.12f + pulse * 0.22f))
    )
}

@Composable
private fun SkeletonDot(
    pulse: Float
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(GreenPrimary.copy(alpha = 0.18f + pulse * 0.28f))
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveRouteMap(
    ngoName: String,
    hotelName: String,
    ngoLatitude: Double,
    ngoLongitude: Double,
    hotelLatitude: Double,
    hotelLongitude: Double
) {
    val html = remember(
        ngoName,
        hotelName,
        ngoLatitude,
        ngoLongitude,
        hotelLatitude,
        hotelLongitude
    ) {
        buildRouteMapHtml(
            ngoName = ngoName,
            hotelName = hotelName,
            ngoLatitude = ngoLatitude,
            ngoLongitude = ngoLongitude,
            hotelLatitude = hotelLatitude,
            hotelLongitude = hotelLongitude
        )
    }

    var isMapReady by remember(html) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isMapReady) 1f else 0.02f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isMapReady = true
                        }
                    }
                    tag = html
                    loadDataWithBaseURL(
                        "https://foodbridge.local/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                if (webView.tag != html) {
                    isMapReady = false
                    webView.tag = html
                    webView.loadDataWithBaseURL(
                        "https://foodbridge.local/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        )

        if (!isMapReady) {
            RouteMapSkeleton(
                modifier = Modifier.fillMaxSize(),
                title = "Loading live route",
                supportingText = "Preparing NGO and hotel positions on the map."
            )
        }
    }
}

@Composable
private fun RouteMapSkeleton(
    modifier: Modifier = Modifier,
    title: String,
    supportingText: String
) {
    val shimmer = rememberInfiniteTransition(label = "routeMapSkeleton")
    val pulse by shimmer.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "routeMapSkeletonPulse"
    )
    val tileColor = SurfaceWhite.copy(alpha = 0.46f + pulse * 0.10f)
    val roadColor = SurfaceWhite.copy(alpha = 0.72f)
    val routeGlowColor = OrangeAccent.copy(alpha = 0.16f + pulse * 0.10f)
    val routeColor = OrangeAccent.copy(alpha = 0.46f + pulse * 0.24f)
    val ngoMarkerColor = GreenPrimary.copy(alpha = 0.60f + pulse * 0.18f)
    val hotelMarkerColor = OrangeAccent.copy(alpha = 0.60f + pulse * 0.18f)
    val ringColor = SurfaceWhite.copy(alpha = 0.24f + pulse * 0.16f)
    val panelColor = SurfaceWhite.copy(alpha = 0.70f + pulse * 0.08f)
    val chipColor = SurfaceWhite.copy(alpha = 0.54f + pulse * 0.08f)
    val borderColor = GreenPrimary.copy(alpha = 0.08f + pulse * 0.06f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceWhite,
                        GreenContainer,
                        GreenContainer.copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val ngoCenter = Offset(width * 0.24f, height * 0.70f)
            val hotelCenter = Offset(width * 0.79f, height * 0.34f)
            val curvedRoute = Path().apply {
                moveTo(ngoCenter.x, ngoCenter.y)
                cubicTo(
                    width * 0.33f,
                    height * 0.63f,
                    width * 0.56f,
                    height * 0.26f,
                    hotelCenter.x,
                    hotelCenter.y
                )
            }

            drawRoundRect(
                color = tileColor,
                topLeft = Offset(width * 0.07f, height * 0.16f),
                size = Size(width * 0.24f, height * 0.18f),
                cornerRadius = CornerRadius(26f, 26f)
            )
            drawRoundRect(
                color = tileColor,
                topLeft = Offset(width * 0.66f, height * 0.12f),
                size = Size(width * 0.21f, height * 0.20f),
                cornerRadius = CornerRadius(26f, 26f)
            )
            drawRoundRect(
                color = tileColor,
                topLeft = Offset(width * 0.15f, height * 0.56f),
                size = Size(width * 0.20f, height * 0.18f),
                cornerRadius = CornerRadius(24f, 24f)
            )
            drawRoundRect(
                color = tileColor,
                topLeft = Offset(width * 0.58f, height * 0.58f),
                size = Size(width * 0.24f, height * 0.16f),
                cornerRadius = CornerRadius(24f, 24f)
            )

            drawLine(
                color = roadColor,
                start = Offset(width * 0.08f, height * 0.24f),
                end = Offset(width * 0.92f, height * 0.24f),
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = roadColor,
                start = Offset(width * 0.14f, height * 0.52f),
                end = Offset(width * 0.88f, height * 0.52f),
                strokeWidth = 18f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = roadColor.copy(alpha = 0.7f),
                start = Offset(width * 0.22f, height * 0.12f),
                end = Offset(width * 0.22f, height * 0.88f),
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = roadColor.copy(alpha = 0.7f),
                start = Offset(width * 0.72f, height * 0.08f),
                end = Offset(width * 0.72f, height * 0.82f),
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = roadColor.copy(alpha = 0.62f),
                start = Offset(width * 0.40f, height * 0.18f),
                end = Offset(width * 0.56f, height * 0.84f),
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )

            drawPath(
                path = curvedRoute,
                color = routeGlowColor,
                style = Stroke(width = 22f, cap = StrokeCap.Round)
            )
            drawPath(
                path = curvedRoute,
                color = routeColor,
                style = Stroke(
                    width = 10f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(32f, 18f),
                        phase = 14f * pulse
                    )
                )
            )

            drawCircle(
                color = ringColor,
                radius = 34f + pulse * 8f,
                center = ngoCenter
            )
            drawCircle(
                color = ringColor,
                radius = 30f + pulse * 6f,
                center = hotelCenter
            )
            drawCircle(
                color = ngoMarkerColor,
                radius = 19f,
                center = ngoCenter
            )
            drawCircle(
                color = hotelMarkerColor,
                radius = 19f,
                center = hotelCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.96f),
                radius = 8f,
                center = ngoCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.96f),
                radius = 8f,
                center = hotelCenter
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBadge(
                text = "Live NGO",
                color = chipColor,
                dotColor = ngoMarkerColor
            )
            SkeletonBadge(
                text = "Hotel stop",
                color = chipColor,
                dotColor = hotelMarkerColor
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            color = panelColor,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonDot(pulse = pulse)
                Text(
                    "Preview",
                    color = GreenDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            color = panelColor,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "ETA syncing",
                    color = GreenDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                SkeletonLine(
                    modifier = Modifier
                        .width(78.dp)
                        .height(8.dp),
                    pulse = pulse
                )
                SkeletonLine(
                    modifier = Modifier
                        .width(56.dp)
                        .height(8.dp),
                    pulse = pulse
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp),
            color = panelColor,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    color = GreenDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    supportingText,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                HorizontalDivider(color = borderColor)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonDot(pulse = pulse)
                    SkeletonLine(
                        modifier = Modifier
                            .fillMaxWidth(0.56f)
                            .height(10.dp),
                        pulse = pulse
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonDot(pulse = pulse)
                    SkeletonLine(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(10.dp),
                        pulse = pulse
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonBadge(
    text: String,
    color: Color,
    dotColor: Color
) {
    Surface(
        color = color,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = GreenPrimary.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = GreenDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun buildRouteMapHtml(
    ngoName: String,
    hotelName: String,
    ngoLatitude: Double,
    ngoLongitude: Double,
    hotelLatitude: Double,
    hotelLongitude: Double
): String {
    val safeNgoName = ngoName.replace("'", "\\'")
    val safeHotelName = hotelName.replace("'", "\\'")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link
                rel="stylesheet"
                href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
            />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    background: #E8F5E9;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                const ngo = [$ngoLatitude, $ngoLongitude];
                const hotel = [$hotelLatitude, $hotelLongitude];
                const map = L.map('map', { zoomControl: false });

                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19,
                    attribution: '&copy; OpenStreetMap contributors'
                }).addTo(map);

                const ngoMarker = L.circleMarker(ngo, {
                    radius: 9,
                    color: '#2E7D32',
                    fillColor: '#2E7D32',
                    fillOpacity: 1
                }).addTo(map).bindPopup('$safeNgoName');

                const hotelMarker = L.circleMarker(hotel, {
                    radius: 9,
                    color: '#FF7A00',
                    fillColor: '#FF7A00',
                    fillOpacity: 1
                }).addTo(map).bindPopup('$safeHotelName');

                const routeLine = L.polyline([ngo, hotel], {
                    color: '#FF7A00',
                    weight: 4,
                    opacity: 0.9,
                    dashArray: '8 10'
                }).addTo(map);

                map.fitBounds(routeLine.getBounds(), { padding: [32, 32] });
                ngoMarker.openPopup();
            </script>
        </body>
        </html>
    """.trimIndent()
}

@SuppressLint("MissingPermission")
private fun shareLastKnownLocation(
    locationManager: LocationManager,
    providers: List<String>,
    locationListener: android.location.LocationListener
) {
    providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
        ?.let(locationListener::onLocationChanged)
}

private fun formatTrackingTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        else -> java.text.SimpleDateFormat(
            "dd MMM • hh:mm a",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val hasFinePermission = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCoarsePermission = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    return hasFinePermission || hasCoarsePermission
}
