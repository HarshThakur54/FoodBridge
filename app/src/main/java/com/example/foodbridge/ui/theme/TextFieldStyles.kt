package com.example.foodbridge.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

@Composable
fun foodBridgeOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        disabledTextColor = TextSecondary,
        errorTextColor = TextPrimary,
        focusedContainerColor = BackgroundLight,
        unfocusedContainerColor = BackgroundLight,
        disabledContainerColor = BackgroundLight,
        errorContainerColor = BackgroundLight,
        cursorColor = GreenPrimary,
        focusedBorderColor = GreenPrimary,
        unfocusedBorderColor = DividerColor,
        disabledBorderColor = DividerColor,
        errorBorderColor = StatusExpired,
        focusedLabelColor = GreenPrimary,
        unfocusedLabelColor = TextSecondary,
        disabledLabelColor = TextSecondary,
        errorLabelColor = StatusExpired,
        focusedPlaceholderColor = TextSecondary,
        unfocusedPlaceholderColor = TextSecondary,
        disabledPlaceholderColor = TextSecondary,
        errorPlaceholderColor = TextSecondary,
        focusedLeadingIconColor = GreenPrimary,
        unfocusedLeadingIconColor = TextSecondary,
        disabledLeadingIconColor = TextSecondary,
        errorLeadingIconColor = StatusExpired,
        focusedTrailingIconColor = GreenPrimary,
        unfocusedTrailingIconColor = TextSecondary,
        disabledTrailingIconColor = TextSecondary,
        errorTrailingIconColor = StatusExpired
    )
}
