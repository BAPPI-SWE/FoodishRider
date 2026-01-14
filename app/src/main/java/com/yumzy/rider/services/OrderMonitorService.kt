package com.yumzy.rider.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.rider.MainActivity
import com.yumzy.rider.R

class OrderMonitorService : Service() {

    private var firestoreListener: ListenerRegistration? = null
    private val CHANNEL_ID_FOREGROUND = "yumzy_service_channel"
    private val CHANNEL_ID_ALERTS = "yumzy_order_alerts"
    private val NOTIFICATION_ID_SERVICE = 1
    private val NOTIFICATION_ID_ALERT = 999

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Start the service in foreground immediately
        startForeground(NOTIFICATION_ID_SERVICE, createForegroundNotification())

        // Start listening for orders
        startFirestoreListener()
    }

    private fun startFirestoreListener() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            stopSelf() // Stop service if not logged in
            return
        }

        val db = Firebase.firestore

        // 1. First, get Rider Profile to know "serviceableLocations"
        db.collection("riders").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val locations = document.get("serviceableLocations") as? List<String> ?: emptyList()

                if (locations.isNotEmpty()) {
                    // 2. Listen to Orders matching location
                    firestoreListener = db.collection("orders")
                        .whereEqualTo("orderStatus", "Pending")
                        .whereEqualTo("orderType", "Instant")
                        .whereIn("userBaseLocation", locations)
                        .addSnapshotListener { snapshots, e ->
                            if (e != null) {
                                Log.e("OrderService", "Listen failed.", e)
                                return@addSnapshotListener
                            }

                            if (snapshots != null) {
                                for (dc in snapshots.documentChanges) {
                                    // ONLY trigger for NEW documents added while listening
                                    if (dc.type == DocumentChange.Type.ADDED) {
                                        val restaurantName = dc.document.getString("restaurantName") ?: "New Order"
                                        triggerSoundAndNotification(restaurantName)
                                    }
                                }
                            }
                        }
                }
            }
    }

    private fun triggerSoundAndNotification(restaurantName: String) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Intent to open app when clicked
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Ensure this icon exists
            .setContentTitle("New Order Received!")
            .setContentText("Pickup from: $restaurantName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Vibrate pattern
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createForegroundNotification(): Notification {
        // This notification just shows "App is running" to keep Android happy
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle("Foodish Rider Active")
            .setContentText("Searching for new orders...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. Silent Channel for the Background Service Indicator
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "Yumzy Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)

            // 2. Loud Channel for New Orders
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "New Order Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new incoming orders"
                enableVibration(true)
                setSound(soundUri, audioAttributes)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove() // Stop listening to Firestore to save data/battery when explicitly killed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}