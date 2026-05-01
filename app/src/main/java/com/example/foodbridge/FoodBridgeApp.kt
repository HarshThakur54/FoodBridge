package com.example.foodbridge

import android.app.Application
import com.example.foodbridge.presentation.notifications.createFoodBridgeNotificationChannel
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FoodBridgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createFoodBridgeNotificationChannel(this)
    }
}
