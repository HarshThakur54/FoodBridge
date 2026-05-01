package com.example.foodbridge.presentation.auth

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.foodbridge.domain.model.UserRole
import com.example.foodbridge.util.reverseGeocodeLocation
import com.example.foodbridge.ui.theme.*
import kotlinx.coroutines.launch

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)

data class RoleOption(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val subRole: String,
    val role: UserRole
)

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    onSignupSuccess: (String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    var fullName        by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedIndex   by remember { mutableStateOf(0) }
    var addressLine1    by remember { mutableStateOf("") }
    var addressLine2    by remember { mutableStateOf("") }
    var city            by remember { mutableStateOf("") }
    var regionState     by remember { mutableStateOf("") }
    var pincode         by remember { mutableStateOf("") }
    var detectedLatitude by remember { mutableStateOf<Double?>(null) }
    var detectedLongitude by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    fun fillAddressFromCurrentLocation() {
        val manager = locationManager
        if (manager == null) {
            Toast.makeText(context, "Location service unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isFetchingLocation = true
            try {
                val lastLocation = readLastKnownLocation(context, manager)
                if (lastLocation == null) {
                    Toast.makeText(
                        context,
                        "Couldn't read current location. Enter address manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val resolved = reverseGeocodeLocation(
                    context = context.applicationContext,
                    latitude = lastLocation.latitude,
                    longitude = lastLocation.longitude
                )

                if (resolved == null) {
                    Toast.makeText(
                        context,
                        "Couldn't convert location to address. Enter it manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                addressLine1 = resolved.addressLine1.ifBlank { resolved.address }
                addressLine2 = resolved.addressLine2
                city = resolved.city
                regionState = resolved.state
                pincode = resolved.pincode
                detectedLatitude = resolved.latitude
                detectedLongitude = resolved.longitude

                Toast.makeText(context, "Address filled from current location", Toast.LENGTH_SHORT).show()
            } finally {
                isFetchingLocation = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            fillAddressFromCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Only Hotel and NGO
    val roleOptions = listOf(
        RoleOption(Icons.Default.Hotel, "Hotel / Restaurant",
            "Post surplus food donations", "hotel", UserRole.DONOR),
        RoleOption(Icons.Default.Favorite, "NGO / Organization",
            "Claim and distribute food", "ngo", UserRole.RECEIVER)
    )

    // Set default role on first load
    LaunchedEffect(Unit) {
        viewModel.onRoleSelected(roleOptions[0].role, roleOptions[0].subRole)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess && uiState.navigateTo.isNotEmpty()) {
            onSignupSuccess(uiState.navigateTo)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(GreenContainer, BackgroundLight))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(text = "🍽️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "FoodBridge",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold, color = GreenDark
                )
            )
            Text(
                text = "Connecting surplus food to those in need",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(containerColor = DividerColor)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf("Login", "Sign Up").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(50.dp))
                                .background(
                                    if (index == 1) SurfaceWhite
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { if (index == 0) onNavigateToLogin() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (index == 1) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (index == 1) GreenPrimary else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Full Name
            OutlinedTextField(
                value = fullName, onValueChange = { fullName = it },
                label = { Text("Full Name / Organization Name") },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Email
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, null, tint = TextSecondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Create a Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextSecondary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility, null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "I am registering as a:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Role Cards — Hotel and NGO side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                roleOptions.forEachIndexed { index, option ->
                    val isSelected = selectedIndex == index
                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(0.9f)
                            .clickable {
                                selectedIndex = index
                                viewModel.onRoleSelected(option.role, option.subRole)
                            }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) GreenPrimary else DividerColor,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) GreenContainer else SurfaceWhite
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.label,
                                tint = if (isSelected) GreenPrimary else TextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = option.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GreenPrimary else TextPrimary,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = option.description,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null, tint = GreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Registration Address",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = addressLine1,
                onValueChange = { addressLine1 = it },
                label = { Text("Address Line 1") },
                leadingIcon = { Icon(Icons.Default.Home, null, tint = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = addressLine2,
                onValueChange = { addressLine2 = it },
                label = { Text("Address Line 2 (Optional)") },
                leadingIcon = { Icon(Icons.Default.Place, null, tint = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    leadingIcon = { Icon(Icons.Default.LocationCity, null, tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = foodBridgeOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = pincode,
                    onValueChange = { pincode = it },
                    label = { Text("Pincode") },
                    leadingIcon = { Icon(Icons.Default.PinDrop, null, tint = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = foodBridgeOutlinedTextFieldColors()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = regionState,
                onValueChange = { regionState = it },
                label = { Text("State") },
                leadingIcon = { Icon(Icons.Default.Map, null, tint = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = foodBridgeOutlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val hasPermission = hasLocationPermission(context)

                    if (hasPermission) {
                        fillAddressFromCurrentLocation()
                    } else {
                        locationPermissionLauncher.launch(locationPermissions)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isFetchingLocation
            ) {
                if (isFetchingLocation) {
                    CircularProgressIndicator(
                        color = GreenPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.MyLocation, null, tint = GreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Current Location", color = GreenPrimary)
                }
            }

            // Error
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatusExpired.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error, color = StatusExpired,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Create Account Button
            Button(
                onClick = {
                    viewModel.signUp(
                        email = email,
                        password = password,
                        fullName = fullName,
                        addressLine1 = addressLine1,
                        addressLine2 = addressLine2,
                        city = city,
                        state = regionState,
                        pincode = pincode,
                        latitude = detectedLatitude,
                        longitude = detectedLongitude
                    )
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                enabled = !uiState.isLoading && fullName.isNotBlank()
                        && email.isNotBlank() && password.isNotBlank()
                        && addressLine1.isNotBlank()
                        && city.isNotBlank()
                        && regionState.isNotBlank()
                        && pincode.isNotBlank()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = SurfaceWhite, modifier = Modifier.size(20.dp))
                } else {
                    Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already have an account? ", color = TextSecondary)
                Text(
                    text = "Login", color = GreenPrimary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "By continuing, you agree to FoodBridge's\nTerms of Service and Privacy Policy",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun readLastKnownLocation(
    context: Context,
    locationManager: LocationManager
): Location? {
    val hasFinePermission = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCoarsePermission = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!hasFinePermission && !hasCoarsePermission) return null

    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter(locationManager::isProviderEnabled)

    return providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }
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
