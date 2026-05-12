package com.yumzy.riderapp.notifications

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object OneSignalNotificationHelper {
    private const val ONE_SIGNAL_APP_ID = "dabb9362-80ed-4e54-be89-32ffc7dbf383"
    // Use your REST API Key from OneSignal dashboard (Settings → Keys & IDs)
    private const val ONE_SIGNAL_REST_API_KEY = "os_v2_app_3k5zgyua5vhfjpujgl74pw7tqmiktp2boauu6mfv2jy2uzkby4dlytf4grj6snhhm6e6pgy5ia3fb6wodbr2y372dcpd72uy3fuuoda"

    private const val ONE_SIGNAL_API_URL = "https://onesignal.com/api/v1/notifications"
    private val client = OkHttpClient()

    suspend fun sendOrderStatusNotification(
        userId: String,
        orderId: String,
        newStatus: String,
        restaurantName: String,
        orderAmount: Double? = null
    ) {
        Log.d("OneSignal", "Starting notification process for userId: $userId, orderId: $orderId, status: $newStatus")

        try {
            Log.d("OneSignal", "Fetching playerId for userId: $userId")
            val userDoc = Firebase.firestore.collection("users").document(userId)
                .get().await()
            val playerId = userDoc.getString("oneSignalPlayerId")
            Log.d("OneSignal", "Fetched playerId: $playerId")

            if (playerId == null) {
                Log.w("OneSignal", "No playerId found for userId: $userId")
                return
            }

            // Custom messages based on status
            val headingText: String
            val contentText: String
            when (newStatus) {
                "Accepted" -> {
                    headingText = "Your Order Accepted 😍"
                    contentText = "Your order from $restaurantName is now Accepted.\nYou will get your Food Soon 😋"
                }
                "On the way" -> {
                    headingText = "Your Order On-the-way 🚴"
                    val amountText = if (orderAmount != null && orderAmount > 0) {
                        "👉 Please prepare ৳${orderAmount.toInt()} 💰"
                    } else {
                        "Please prepare cash"
                    }
                    contentText = "Your order from $restaurantName is now On the way.\n $amountText"
                }
                "Delivered" -> {
                    headingText = "Your Order Delivered 🥳"
                    contentText = "Your order from $restaurantName is now Delivered.\nThanks for Using Foodish💗"
                }
                else -> {
                    headingText = "Order Status Update"
                    contentText = "Your order from $restaurantName is now $newStatus."
                }
            }

            Log.d("OneSignal", "Creating notification payload")
            val payload = JSONObject().apply {
                put("app_id", ONE_SIGNAL_APP_ID)
                put("include_player_ids", JSONArray().put(playerId))
                put("headings", JSONObject().put("en", headingText))
                put("contents", JSONObject().put("en", contentText))
                put("data", JSONObject().put("orderId", orderId))
            }
            Log.d("OneSignal", "Payload created: $payload")

            Log.d("OneSignal", "Sending HTTP request to OneSignal API")
            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            Log.d("OneSignal", "Request Headers: Authorization=Basic $ONE_SIGNAL_REST_API_KEY")
            val request = Request.Builder()
                .url(ONE_SIGNAL_API_URL)
                .addHeader("Authorization", "Basic $ONE_SIGNAL_REST_API_KEY")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            withContext(Dispatchers.IO) {
                Log.d("OneSignal", "Executing network call")
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: "No response body"
                    Log.d("OneSignal", "API Response: Code ${response.code}, Body $responseBody")
                    if (response.isSuccessful) {
                        Log.d("OneSignal", "Notification sent successfully for order $orderId: $responseBody")
                    } else {
                        Log.e("OneSignal", "Failed to send notification for order $orderId: Code ${response.code}, Body $responseBody")
                    }
                }
            }
            Log.d("OneSignal", "Notification process completed for order $orderId")
        } catch (e: Exception) {
            Log.e("OneSignal", "Error in notification process for order $orderId: ${e.message}", e)
        }
    }
}
