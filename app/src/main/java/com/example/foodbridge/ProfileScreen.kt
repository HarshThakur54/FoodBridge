package com.example.foodbridge.presentation.profile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodbridge.presentation.home.FoodBridgeBottomNav
import com.example.foodbridge.presentation.ngo.NgoBottomNav
import coil.compose.AsyncImage
import com.example.foodbridge.util.buildAddress
import com.example.foodbridge.util.coalesceAddress
import com.example.foodbridge.util.resolveLocation
import com.example.foodbridge.util.uploadImageToFirebaseStorage
import com.example.foodbridge.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── State ────────────────────────────────────────────────────
    var isLoading    by remember { mutableStateOf(true) }
    var isStatsLoading by remember { mutableStateOf(true) }
    var isSaving     by remember { mutableStateOf(false) }
    var isEditMode   by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    // Common fields
    var fullName     by remember { mutableStateOf("") }
    var email        by remember { mutableStateOf("") }
    var phone        by remember { mutableStateOf("") }
    var photoUrl     by remember { mutableStateOf("") }
    var role         by remember { mutableStateOf("DONOR") }
    var subRole      by remember { mutableStateOf("hotel") }

    // Address fields
    var addressLine1 by remember { mutableStateOf("") }
    var addressLine2 by remember { mutableStateOf("") }
    var city         by remember { mutableStateOf("") }
    var state        by remember { mutableStateOf("") }
    var pincode      by remember { mutableStateOf("") }
    var latitude     by remember { mutableStateOf<Double?>(null) }
    var longitude    by remember { mutableStateOf<Double?>(null) }
    var savedAddress by remember { mutableStateOf("") }

    // Hotel-specific
    var hotelLicense by remember { mutableStateOf("") }
    var hotelType    by remember { mutableStateOf("") }  // Restaurant / Bakery / Caterer / Cloud Kitchen

    // NGO-specific
    var ngoRegNumber    by remember { mutableStateOf("") }
    var ngoAreaServed   by remember { mutableStateOf("") }
    var ngoCapacity     by remember { mutableStateOf("") }  // How many people they can serve

    // Stats
    var totalDonations by remember { mutableStateOf(0) }
    var totalClaimed   by remember { mutableStateOf(0) }
    var totalPeople    by remember { mutableStateOf(0) }

    // Photo
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoDialog  by remember { mutableStateOf(false) }

    val uid = FirebaseAuth.getInstance().currentUser?.uid

    // ── Photo Launchers ──────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedPhotoUri = uri }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (!success) selectedPhotoUri = null }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = java.io.File.createTempFile("profile_", ".jpg", context.cacheDir)
            val uri  = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            selectedPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Load user data ───────────────────────────────────────────
    LaunchedEffect(uid) {
        if (uid == null) { navController.popBackStack(); return@LaunchedEffect }
        try {
            val db  = FirebaseFirestore.getInstance()
            val doc = db.collection("users").document(uid).get().await()
            val loadedRole = doc.getString("role") ?: "DONOR"

            fullName     = doc.getString("fullName")     ?: ""
            email        = doc.getString("email")        ?: FirebaseAuth.getInstance().currentUser?.email ?: ""
            phone        = doc.getString("phone")        ?: ""
            photoUrl     = doc.getString("photoUrl")     ?: ""
            role         = loadedRole
            subRole      = doc.getString("subRole")      ?: "hotel"
            addressLine1 = doc.getString("addressLine1") ?: ""
            addressLine2 = doc.getString("addressLine2") ?: ""
            city         = doc.getString("city")         ?: ""
            state        = doc.getString("state")        ?: ""
            pincode      = doc.getString("pincode")      ?: ""
            latitude     = doc.getDouble("latitude")
            longitude    = doc.getDouble("longitude")
            savedAddress = coalesceAddress(
                doc.getString("address").orEmpty(),
                addressLine1,
                addressLine2,
                city,
                state,
                pincode
            )

            // Render the profile shell immediately; stats can finish loading afterward.
            isLoading = false

            if (loadedRole == "DONOR") {
                hotelLicense = doc.getString("hotelLicense") ?: ""
                hotelType    = doc.getString("hotelType")    ?: ""
                val donSnap = db.collection("donations")
                    .whereEqualTo("donorId", uid).get().await()
                totalDonations = donSnap.size()
                totalClaimed   = donSnap.documents.count {
                    val status = it.getString("status")
                    status == "CLAIMED" || status == "IN_PROGRESS" || status == "COMPLETED"
                }
                totalPeople = totalClaimed * 5 // estimate
            } else {
                ngoRegNumber  = doc.getString("ngoRegNumber")  ?: ""
                ngoAreaServed = doc.getString("ngoAreaServed") ?: ""
                ngoCapacity   = doc.getString("ngoCapacity")   ?: ""
                // Count donations claimed
                val claimedSnap = db.collection("donations")
                    .whereEqualTo("claimedBy", uid).get().await()
                totalClaimed   = claimedSnap.size()
                totalPeople    = totalClaimed * 5
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            isLoading = false
        } finally {
            isStatsLoading = false
        }
    }

    // ── Photo dialog ─────────────────────────────────────────────
    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text("Change Photo", fontWeight = FontWeight.Bold) },
            text  = {
                Column {
                    ListItem(
                        headlineContent = { Text("Take Photo") },
                        leadingContent  = { Icon(Icons.Default.CameraAlt, null, tint = GreenPrimary) },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            showPhotoDialog = false
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Choose from Gallery") },
                        leadingContent  = { Icon(Icons.Default.PhotoLibrary, null, tint = GreenPrimary) },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            showPhotoDialog = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    if (photoUrl.isNotBlank() || selectedPhotoUri != null) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Remove Photo", color = MaterialTheme.colorScheme.error) },
                            leadingContent  = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                selectedPhotoUri = null
                                photoUrl = ""
                                showPhotoDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Scaffold ─────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold, color = SurfaceWhite) },
                actions = {
                    if (!isLoading) {
                        if (isEditMode) {
                            TextButton(onClick = { isEditMode = false }) {
                                Text("Cancel", color = SurfaceWhite)
                            }
                        } else {
                            IconButton(onClick = { isEditMode = true }) {
                                Icon(Icons.Default.Edit, null, tint = SurfaceWhite)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        },
        bottomBar = {
            if (!isLoading) {
                if (role == "RECEIVER") {
                    NgoBottomNav(navController = navController, selected = 3)
                } else {
                    FoodBridgeBottomNav(navController = navController, selected = 3)
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            ProfileLoadingSkeleton(
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

            // ── Profile Header Card ───────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar
                    Box(contentAlignment = Alignment.BottomEnd) {
                        val avatarModifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(3.dp, GreenPrimary, CircleShape)

                        if (selectedPhotoUri != null) {
                            AsyncImage(
                                model = selectedPhotoUri,
                                contentDescription = "Profile photo",
                                modifier = avatarModifier,
                                contentScale = ContentScale.Crop
                            )
                        } else if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Profile photo",
                                modifier = avatarModifier,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = avatarModifier.background(GreenContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    fullName.firstOrNull()?.uppercase() ?: "?",
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenPrimary
                                )
                            }
                        }
                        if (isEditMode) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary)
                                    .clickable { showPhotoDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, null,
                                    tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isEditMode) {
                        OutlinedTextField(
                            value = fullName, onValueChange = { fullName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = foodBridgeOutlinedTextFieldColors()
                        )
                    } else {
                        Text(fullName.ifBlank { "No name set" },
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Role badge
                    Surface(
                        color = if (role == "DONOR") GreenContainer else OrangeAccent.copy(0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            if (role == "DONOR") "🏨 Hotel / Donor" else "🤝 NGO / Receiver",
                            color = if (role == "DONOR") GreenPrimary else OrangeAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Stats Card ────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = GreenContainer),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (role == "DONOR") {
                        StatItem("📦", if (isStatsLoading) "--" else totalDonations.toString(), "Posted")
                        VerticalDivider(modifier = Modifier.height(50.dp))
                        StatItem("✅", if (isStatsLoading) "--" else totalClaimed.toString(), "Claimed")
                        VerticalDivider(modifier = Modifier.height(50.dp))
                        StatItem("👥", if (isStatsLoading) "--" else "${totalPeople}+", "Fed")
                    } else {
                        StatItem("🍽️", if (isStatsLoading) "--" else totalClaimed.toString(), "Claimed")
                        VerticalDivider(modifier = Modifier.height(50.dp))
                        StatItem("👥", if (isStatsLoading) "--" else "${totalPeople}+", "Served")
                        VerticalDivider(modifier = Modifier.height(50.dp))
                        StatItem("⭐", "4.8", "Rating")
                    }
                }
            }

            // ── Contact Info ──────────────────────────────────────
            SectionCard(title = "Contact Information", icon = Icons.Default.ContactPhone) {
                ProfileField(
                    label = "Email",
                    value = email,
                    isEdit = false, // Email not editable
                    icon = Icons.Default.Email,
                    onValueChange = {}
                )
                ProfileField(
                    label = "Phone Number",
                    value = phone,
                    isEdit = isEditMode,
                    icon = Icons.Default.Phone,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                    onValueChange = { phone = it }
                )
            }

            // ── Address ───────────────────────────────────────────
            SectionCard(title = "Address", icon = Icons.Default.LocationOn) {
                ProfileField(
                    label = "Address Line 1",
                    value = addressLine1,
                    isEdit = isEditMode,
                    icon = Icons.Default.Home,
                    placeholder = "Street, Building, Area",
                    onValueChange = { addressLine1 = it }
                )
                ProfileField(
                    label = "Address Line 2",
                    value = addressLine2,
                    isEdit = isEditMode,
                    icon = Icons.Default.Home,
                    placeholder = "Landmark, Near (optional)",
                    onValueChange = { addressLine2 = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ProfileField(
                            label = "City",
                            value = city,
                            isEdit = isEditMode,
                            icon = Icons.Default.LocationCity,
                            onValueChange = { city = it }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ProfileField(
                            label = "Pincode",
                            value = pincode,
                            isEdit = isEditMode,
                            icon = Icons.Default.PinDrop,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            onValueChange = { pincode = it }
                        )
                    }
                }
                ProfileField(
                    label = "State",
                    value = state,
                    isEdit = isEditMode,
                    icon = Icons.Default.Map,
                    onValueChange = { state = it }
                )
            }

            // ── Hotel-specific ────────────────────────────────────
            if (role == "DONOR") {
                SectionCard(title = "Hotel Details", icon = Icons.Default.Store) {
                    // Hotel Type dropdown
                    if (isEditMode) {
                        val hotelTypes = listOf("Restaurant", "Bakery", "Caterer", "Cloud Kitchen", "Canteen", "Other")
                        var expanded by remember { mutableStateOf(false) }
                        Column {
                            Text("Hotel Type", fontSize = 12.sp, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                OutlinedTextField(
                                    value = hotelType.ifBlank { "Select type" },
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = foodBridgeOutlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    hotelTypes.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type) },
                                            onClick = { hotelType = type; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        ProfileField(
                            label = "Hotel Type",
                            value = hotelType,
                            isEdit = false,
                            icon = Icons.Default.Restaurant,
                            onValueChange = {}
                        )
                    }
                    ProfileField(
                        label = "FSSAI / License Number",
                        value = hotelLicense,
                        isEdit = isEditMode,
                        icon = Icons.Default.Badge,
                        placeholder = "Enter food license number",
                        onValueChange = { hotelLicense = it }
                    )
                }
            }

            // ── NGO-specific ──────────────────────────────────────
            if (role == "RECEIVER") {
                SectionCard(title = "NGO Details", icon = Icons.Default.VolunteerActivism) {
                    ProfileField(
                        label = "NGO Registration Number",
                        value = ngoRegNumber,
                        isEdit = isEditMode,
                        icon = Icons.Default.Badge,
                        placeholder = "e.g. 123/2020/NGO",
                        onValueChange = { ngoRegNumber = it }
                    )
                    ProfileField(
                        label = "Area Served",
                        value = ngoAreaServed,
                        isEdit = isEditMode,
                        icon = Icons.Default.Map,
                        placeholder = "e.g. South Chennai, Velachery, Adyar",
                        onValueChange = { ngoAreaServed = it }
                    )
                    ProfileField(
                        label = "Daily Capacity (people)",
                        value = ngoCapacity,
                        isEdit = isEditMode,
                        icon = Icons.Default.PeopleAlt,
                        placeholder = "e.g. 200",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        onValueChange = { ngoCapacity = it }
                    )
                }
            }

            SectionCard(title = "Contact Us", icon = Icons.Default.SupportAgent) {
                ProfileField(
                    label = "Developer Email",
                    value = "harshthakur54@gmail.com",
                    isEdit = false,
                    icon = Icons.Default.Email,
                    onValueChange = {}
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:harshthakur54@gmail.com")
                        ).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "FoodBridge Support")
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    "No email app found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.Default.Send, null, tint = GreenPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Contact Developer", color = GreenPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Save Button ───────────────────────────────────────
            if (isEditMode) {
                Button(
                    onClick = {
                        if (fullName.isBlank()) {
                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            try {
                                val db = FirebaseFirestore.getInstance()

                                // Upload new profile photo if selected
                                var finalPhotoUrl = photoUrl
                                if (selectedPhotoUri != null) {
                                    isUploadingPhoto = true
                                    finalPhotoUrl = uploadImageToFirebaseStorage(
                                        context = context,
                                        sourceUri = selectedPhotoUri!!,
                                        remoteDirectory = "profiles/${uid}",
                                        fileNamePrefix = "profile"
                                    )
                                    isUploadingPhoto = false
                                }

                                val combinedAddress = buildAddress(
                                    addressLine1,
                                    addressLine2,
                                    city,
                                    state,
                                    pincode
                                )
                                val resolvedLocation = resolveLocation(
                                    context = context.applicationContext,
                                    address = combinedAddress,
                                    city = city,
                                    pincode = pincode,
                                    latitude = latitude.takeIf { savedAddress == combinedAddress },
                                    longitude = longitude.takeIf { savedAddress == combinedAddress }
                                )

                                val updates = hashMapOf<String, Any>(
                                    "fullName"     to fullName,
                                    "phone"        to phone,
                                    "photoUrl"     to finalPhotoUrl,
                                    "addressLine1" to addressLine1,
                                    "addressLine2" to addressLine2,
                                    "city"         to city,
                                    "state"        to state,
                                    "pincode"      to pincode,
                                    "address"      to resolvedLocation.address,
                                    "latitude"     to (resolvedLocation.latitude ?: 0.0),
                                    "longitude"    to (resolvedLocation.longitude ?: 0.0)
                                )

                                if (role == "DONOR") {
                                    updates["hotelLicense"] = hotelLicense
                                    updates["hotelType"]    = hotelType
                                } else {
                                    updates["ngoRegNumber"]  = ngoRegNumber
                                    updates["ngoAreaServed"] = ngoAreaServed
                                    updates["ngoCapacity"]   = ngoCapacity
                                }

                                db.collection("users").document(uid!!).update(updates).await()
                                photoUrl   = finalPhotoUrl
                                latitude   = resolvedLocation.latitude
                                longitude  = resolvedLocation.longitude
                                savedAddress = resolvedLocation.address
                                isEditMode = false
                                val successMessage =
                                    if (combinedAddress.isNotBlank() && resolvedLocation.latitude == null) {
                                        "Profile updated. Route ranking will improve once this address resolves."
                                    } else {
                                        "Profile updated! ✅"
                                    }
                                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving         = false
                                isUploadingPhoto = false
                            }
                        }
                    },
                    enabled  = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) {
                    if (isSaving) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = SurfaceWhite,
                                modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isUploadingPhoto) "Uploading photo..." else "Saving...")
                        }
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Sign Out ──────────────────────────────────────────
            if (!isEditMode) {
                OutlinedButton(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GreenContainer.copy(alpha = 0.58f),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Made with love for food rescue",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fast. Local. Reliable. FoodBridge.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Reusable Components ───────────────────────────────────────────

@Composable
private fun ProfileLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(GreenContainer)
                )
                ProfileSkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.46f)
                        .height(20.dp)
                )
                ProfileSkeletonLine(
                    modifier = Modifier
                        .fillMaxWidth(0.30f)
                        .height(12.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = GreenContainer),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileSkeletonLine(
                            modifier = Modifier
                                .width(38.dp)
                                .height(18.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ProfileSkeletonLine(
                            modifier = Modifier
                                .width(56.dp)
                                .height(10.dp)
                        )
                    }
                }
            }
        }

        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(GreenContainer)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ProfileSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .height(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    repeat(3) { lineIndex ->
                        ProfileSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(if (lineIndex == 2) 0.52f else 0.92f)
                                .height(12.dp)
                        )
                        if (lineIndex < 2) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSkeletonLine(
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextSecondary.copy(alpha = 0.14f))
    )
}

@Composable
fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border    = BorderStroke(1.dp, DividerColor.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ProfileField(
    label: String,
    value: String,
    isEdit: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String = "",
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    if (isEdit) {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            label         = { Text(label) },
            placeholder   = { Text(placeholder.ifBlank { label }, color = TextSecondary) },
            leadingIcon   = { Icon(icon, null, tint = GreenPrimary, modifier = Modifier.size(20.dp)) },
            modifier      = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape         = RoundedCornerShape(16.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            singleLine    = true,
            colors        = foodBridgeOutlinedTextFieldColors()
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontSize = 11.sp, color = TextSecondary)
                Text(
                    value.ifBlank { "Not set" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (value.isBlank()) TextSecondary else Color.Unspecified
                )
            }
        }
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
    }
}

@Composable
fun StatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}
