package com.example.foodbridge.presentation.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.foodbridge.MainActivity
import com.example.foodbridge.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

const val FOODBRIDGE_NOTIFICATION_CHANNEL_ID = "foodbridge_live_alerts"

fun createFoodBridgeNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        FOODBRIDGE_NOTIFICATION_CHANNEL_ID,
        "FoodBridge Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Instant donation and claim alerts"
    }
    manager.createNotificationChannel(channel)
}

private fun showFoodBridgeNotification(
    context: Context,
    notificationId: Int,
    title: String,
    message: String
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(
        context,
        FOODBRIDGE_NOTIFICATION_CHANNEL_ID
    )
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
}

@Composable
fun RealtimeNotificationObserver() {
    val appContext = LocalContext.current.applicationContext

    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        var listenerRegistration: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            listenerRegistration?.remove()
            listenerRegistration = null

            val uid = firebaseAuth.currentUser?.uid ?: return@AuthStateListener
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { userDoc ->
                    val isNgo = (userDoc.getString("role") ?: "DONOR") == "RECEIVER"
                    val recipients = if (isNgo) listOf(uid, "ALL") else listOf(uid)
                    var isInitialSnapshot = true

                    listenerRegistration = FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .whereIn("recipientId", recipients)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null || snapshot == null) return@addSnapshotListener

                            if (isInitialSnapshot) {
                                isInitialSnapshot = false
                                return@addSnapshotListener
                            }

                            snapshot.documentChanges
                                .filter { it.type == DocumentChange.Type.ADDED }
                                .forEach { change ->
                                    val title = change.document.getString("title").orEmpty()
                                    val message = change.document.getString("message").orEmpty()
                                    if (title.isNotBlank() || message.isNotBlank()) {
                                        showFoodBridgeNotification(
                                            context = appContext,
                                            notificationId = change.document.id.hashCode(),
                                            title = title.ifBlank { "FoodBridge update" },
                                            message = message.ifBlank { "You have a new alert." }
                                        )
                                    }
                                }
                        }
                }
        }

        auth.addAuthStateListener(authListener)

        onDispose {
            listenerRegistration?.remove()
            auth.removeAuthStateListener(authListener)
        }
    }
}
