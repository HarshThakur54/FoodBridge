package com.example.foodbridge.presentation.donation

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.foodbridge.data.repository.DonationExpiryOption
import com.example.foodbridge.data.repository.donationExpiryOptions
import com.example.foodbridge.data.repository.formatExpiryDateTime
import com.example.foodbridge.presentation.auth.AuthViewModel
import com.example.foodbridge.ui.theme.GreenContainer
import com.example.foodbridge.ui.theme.GreenDark
import com.example.foodbridge.ui.theme.GreenPrimary
import com.example.foodbridge.ui.theme.OrangeAccent
import com.example.foodbridge.ui.theme.SurfaceWhite
import com.example.foodbridge.ui.theme.TextSecondary
import com.example.foodbridge.ui.theme.foodBridgeOutlinedTextFieldColors
import com.example.foodbridge.util.coalesceAddress
import com.example.foodbridge.util.resolveLocation
import com.example.foodbridge.util.uploadImageToFirebaseStorage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

suspend fun createDonationPostedNotification(
    db: FirebaseFirestore,
    foodName: String,
    donorName: String
) {
    db.collection("notifications").add(
        hashMapOf(
            "title" to "New Donation Available 🍽️",
            "message" to "$donorName just posted: $foodName. Claim it before it's gone!",
            "type" to "NEW_DONATION",
            "recipientId" to "ALL",
            "createdAt" to System.currentTimeMillis(),
            "isUnread" to true
        )
    ).await()
}

suspend fun uploadDonationPhoto(
    context: Context,
    currentUserId: String,
    donationId: String,
    uri: Uri
): String {
    return uploadImageToFirebaseStorage(
        context = context,
        sourceUri = uri,
        remoteDirectory = "donation_photos/$currentUserId/$donationId",
        fileNamePrefix = "donation"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDonationScreen(navController: NavController, authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var foodName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf("Cooked food") }
    var selectedExpiryOption by remember { mutableStateOf(donationExpiryOptions[1]) }
    var isVeg by remember { mutableStateOf(true) }
    var isPosting by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    var showPickupTimeDialog by remember { mutableStateOf(false) }
    var selectedPickupTimestamp by remember { mutableStateOf<Long?>(null) }

    var currentUserId by remember { mutableStateOf("") }
    var currentUserRole by remember { mutableStateOf("") }
    var currentUserSubRole by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedPhotoUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedPhotoUri = pendingCameraUri
            pendingCameraUri = null
        } else {
            selectedPhotoUri = null
            pendingCameraUri = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        val imageFile = java.io.File.createTempFile("donation_", ".jpg", context.cacheDir)
        val imageUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
        pendingCameraUri = imageUri
        cameraLauncher.launch(imageUri)
    }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
            return@LaunchedEffect
        }

        try {
            val doc = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .await()

            currentUserId = uid
            currentUserRole = doc.getString("role") ?: "DONOR"
            currentUserSubRole = doc.getString("subRole") ?: "hotel"

            if (currentUserRole == "RECEIVER") {
                Toast.makeText(
                    context,
                    "Only hotels/donors can post donations",
                    Toast.LENGTH_SHORT
                ).show()
                navController.popBackStack()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading user: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val categories = listOf("Cooked food", "Bakery", "Raw", "Packaged", "Beverages")
    val pickupPreview = selectedPickupTimestamp?.let(::formatPickupScheduleLabel)
    val expiryPreview = selectedPickupTimestamp?.let {
        formatExpiryDateTime(it + selectedExpiryOption.durationMillis)
    }

    if (showPhotoDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text("Add Donation Photo", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Take Photo") },
                        leadingContent = { Icon(Icons.Default.CameraAlt, null, tint = GreenPrimary) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showPhotoDialog = false
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Choose from Gallery") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null, tint = GreenPrimary) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showPhotoDialog = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    )
                    if (selectedPhotoUri != null) {
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Remove Photo", color = androidx.compose.material3.MaterialTheme.colorScheme.error) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedPhotoUri = null
                                    pendingCameraUri = null
                                    showPhotoDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPickupTimeDialog) {
        val initialPickerTime = remember(selectedPickupTimestamp) {
            Calendar.getInstance().apply {
                timeInMillis = selectedPickupTimestamp ?: System.currentTimeMillis()
                if (selectedPickupTimestamp == null) {
                    add(Calendar.HOUR_OF_DAY, 1)
                    set(Calendar.MINUTE, 0)
                }
            }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initialPickerTime.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialPickerTime.get(Calendar.MINUTE),
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showPickupTimeDialog = false },
            title = { Text("Choose Pickup Time", fontWeight = FontWeight.Bold) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedPickupTimestamp = resolveNextPickupTimestamp(
                            hourOfDay = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        showPickupTimeDialog = false
                    }
                ) {
                    Text("Set Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPickupTimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post Donation", fontWeight = FontWeight.Bold, color = SurfaceWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = SurfaceWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GreenPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Donation Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Share your surplus food with those in need.",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GreenContainer)
                    .border(2.dp, GreenPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { showPhotoDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (selectedPhotoUri != null) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        color = SurfaceWhite.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            "Change Photo",
                            color = GreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            null,
                            tint = GreenPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap to add a food photo",
                            color = GreenPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text("Camera or gallery", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = foodName,
                onValueChange = { foodName = it },
                label = { Text("Food Name") },
                placeholder = { Text("e.g. Assorted Pastries, Veg Biryani") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    placeholder = { Text("e.g. 5 kg or 20 boxes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = foodBridgeOutlinedTextFieldColors()
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Category", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCat,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = foodBridgeOutlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            categories.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedCat = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text("Food Type", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = isVeg,
                    onClick = { isVeg = true },
                    label = { Text("Veg") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GreenContainer,
                        selectedLabelColor = GreenPrimary
                    )
                )
                FilterChip(
                    selected = !isVeg,
                    onClick = { isVeg = false },
                    label = { Text("Non-Veg") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OrangeAccent.copy(alpha = 0.12f),
                        selectedLabelColor = OrangeAccent
                    )
                )
            }

            Text("Preferred Pickup Time", fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { showPickupTimeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AccessTime, null, tint = GreenPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        pickupPreview ?: "Choose pickup time",
                        fontWeight = FontWeight.SemiBold,
                        color = if (pickupPreview == null) TextSecondary else GreenDark
                    )
                    Text(
                        "Open clock picker",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                pickupPreview?.let { "NGOs will see $it as your preferred pickup schedule." }
                    ?: "Choose the exact pickup time you want NGOs to target.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Text("Auto Expiry After Pickup Time", fontWeight = FontWeight.SemiBold)
            donationExpiryOptions.chunked(2).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            selected = selectedExpiryOption == option,
                            onClick = { selectedExpiryOption = option },
                            label = { Text(option.label, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Timelapse,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenContainer,
                                selectedLabelColor = GreenPrimary
                            )
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = GreenContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = GreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Expiry Preview",
                            fontWeight = FontWeight.SemiBold,
                            color = GreenDark
                        )
                    }
                    Text(
                        pickupPreview?.let {
                            "Pickup: $it"
                        } ?: "Pickup: Choose a time first",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        expiryPreview?.let {
                            "Auto-expires: $it (${selectedExpiryOption.label} after pickup)"
                        } ?: "Auto-expires once pickup time is selected",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Special Notes") },
                placeholder = { Text("e.g. Contains dairy, please bring containers...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Button(
                onClick = {
                    if (foodName.isBlank()) {
                        Toast.makeText(context, "Please enter food name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (quantity.isBlank()) {
                        Toast.makeText(context, "Please enter quantity", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedPickupTimestamp == null) {
                        Toast.makeText(context, "Please choose a pickup time", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (currentUserId.isBlank()) {
                        Toast.makeText(
                            context,
                            "User not loaded yet, please wait",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    isPosting = true
                    scope.launch {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            val now = System.currentTimeMillis()
                            val pickupScheduledAt = selectedPickupTimestamp!!
                            val expiresAt = pickupScheduledAt + selectedExpiryOption.durationMillis
                            val donationRef = db.collection("donations").document()
                            val donorDoc = db.collection("users").document(currentUserId).get().await()
                            val donorName = donorDoc.getString("fullName") ?: "A hotel"
                            val pickupAddress = coalesceAddress(
                                donorDoc.getString("address").orEmpty(),
                                donorDoc.getString("addressLine1").orEmpty(),
                                donorDoc.getString("addressLine2").orEmpty(),
                                donorDoc.getString("city").orEmpty(),
                                donorDoc.getString("state").orEmpty(),
                                donorDoc.getString("pincode").orEmpty()
                            )
                            val pickupCity = donorDoc.getString("city").orEmpty()
                            val pickupPincode = donorDoc.getString("pincode").orEmpty()
                            val pickupLocation = resolveLocation(
                                context = context.applicationContext,
                                address = pickupAddress,
                                city = pickupCity,
                                pincode = pickupPincode,
                                latitude = donorDoc.getDouble("latitude"),
                                longitude = donorDoc.getDouble("longitude")
                            )

                            if (pickupLocation.address.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Add your hotel registration address in Profile before posting a donation.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                isPosting = false
                                return@launch
                            }

                            val donationData = hashMapOf(
                                "title" to foodName,
                                "quantity" to quantity,
                                "category" to selectedCat,
                                "isVeg" to isVeg,
                                "pickupTime" to formatPickupScheduleLabel(pickupScheduledAt),
                                "pickupScheduledAt" to pickupScheduledAt,
                                "expiresAt" to expiresAt,
                                "expiryDurationLabel" to selectedExpiryOption.label,
                                "expiryOffsetMillis" to selectedExpiryOption.durationMillis,
                                "notes" to notes,
                                "status" to "AVAILABLE",
                                "donorId" to currentUserId,
                                "donorName" to donorName,
                                "donorSubRole" to currentUserSubRole,
                                "pickupAddress" to pickupLocation.address,
                                "pickupCity" to pickupLocation.city,
                                "pickupPincode" to pickupLocation.pincode,
                                "pickupLatitude" to (pickupLocation.latitude ?: 0.0),
                                "pickupLongitude" to (pickupLocation.longitude ?: 0.0),
                                "photoUrl" to "",
                                "createdAt" to now,
                                "claimedBy" to "",
                                "claimedAt" to 0L
                            )
                            donationRef.set(donationData).await()

                            var photoUploadError: String? = null
                            selectedPhotoUri?.let { uri ->
                                runCatching {
                                    uploadDonationPhoto(
                                        context = context,
                                        currentUserId = currentUserId,
                                        donationId = donationRef.id,
                                        uri = uri
                                    )
                                }.onSuccess { uploadedPhotoUrl ->
                                    runCatching {
                                        donationRef.update("photoUrl", uploadedPhotoUrl).await()
                                    }.onFailure {
                                        photoUploadError = it.message ?: "Couldn't attach uploaded photo."
                                    }
                                }.onFailure {
                                    photoUploadError = it.message ?: "Please try another image."
                                }
                            }

                            runCatching {
                                createDonationPostedNotification(db, foodName, donorName)
                            }

                            val successMessage = if (photoUploadError == null) {
                                "Donation posted successfully! 🎉"
                            } else {
                                "Donation posted, but photo upload failed. Please try another image."
                            }
                            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Failed to post: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isPosting = false
                        }
                    }
                },
                enabled = !isPosting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        color = SurfaceWhite,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Post Donation", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                "By posting, you agree to our food safety guidelines and Terms of Service",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

private fun resolveNextPickupTimestamp(
    hourOfDay: Int,
    minute: Int,
    now: Long = System.currentTimeMillis()
): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= now) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return calendar.timeInMillis
}

private fun formatPickupScheduleLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val selected = Calendar.getInstance().apply { timeInMillis = timestamp }
    val dayLabel = when {
        now.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR) -> "Today"
        now.get(Calendar.YEAR) == selected.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) + 1 == selected.get(Calendar.DAY_OF_YEAR) -> "Tomorrow"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
    val timeLabel = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    return "$dayLabel • $timeLabel"
}
