package com.yumzy.rider

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.rider.auth.AuthScreen
import com.yumzy.rider.auth.AuthViewModel
import com.yumzy.rider.auth.GoogleAuthUiClient
import com.yumzy.rider.features.history.DeliveryHistoryScreen
import com.yumzy.rider.features.profile.RiderAccountScreen
import com.yumzy.rider.features.profile.RiderEditProfileScreen
import com.yumzy.rider.navigation.MainScreen
import com.yumzy.rider.notifications.OneSignalNotificationHelper
import com.yumzy.rider.services.OrderMonitorService // Import your new service
import com.yumzy.rider.ui.theme.YumzyRiderTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private val googleAuthUiClient by lazy {
        GoogleAuthUiClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- NEW: Request Notification Permission for Android 13+ ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        setContent {
            YumzyRiderTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "auth") {
                    composable("auth") {
                        val viewModel = viewModel<AuthViewModel>()
                        val state by viewModel.state.collectAsStateWithLifecycle()

                        LaunchedEffect(key1 = Unit) {
                            val currentUser = googleAuthUiClient.getSignedInUser()
                            if (currentUser != null) {
                                checkRiderProfile(currentUser.userId, navController)
                            }
                        }

                        val launcher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartIntentSenderForResult()
                        ) { result ->
                            if (result.resultCode == RESULT_OK) {
                                lifecycleScope.launch {
                                    val signInResult = googleAuthUiClient.signInWithIntent(
                                        intent = result.data ?: return@launch
                                    )
                                    viewModel.onSignInResult(signInResult)
                                }
                            }
                        }

                        LaunchedEffect(key1 = state.isSignInSuccessful) {
                            if (state.isSignInSuccessful) {
                                val userId = googleAuthUiClient.getSignedInUser()?.userId
                                if (userId != null) {
                                    checkRiderProfile(userId, navController)
                                }
                                viewModel.resetState()
                            }
                        }

                        AuthScreen(
                            onSignInClick = {
                                lifecycleScope.launch {
                                    val signInIntentSender = googleAuthUiClient.signIn()
                                    launcher.launch(
                                        IntentSenderRequest.Builder(
                                            signInIntentSender ?: return@launch
                                        ).build()
                                    )
                                }
                            }
                        )
                    }
                    composable("create_profile") {
                        // Note: Ensure RiderProfileScreen is imported or available in your package
                        RiderProfileScreen(
                            onSaveProfile = { phone, vehicle, serviceableLocations ->
                                val userId = Firebase.auth.currentUser?.uid ?: return@RiderProfileScreen

                                val riderProfile = hashMapOf(
                                    "name" to (Firebase.auth.currentUser?.displayName ?: "N/A"),
                                    "phone" to phone,
                                    "vehicle" to vehicle,
                                    "serviceableLocations" to serviceableLocations,
                                    "isAvailable" to false,
                                    "uid" to userId
                                )
                                Firebase.firestore.collection("riders").document(userId)
                                    .set(riderProfile)
                                    .addOnSuccessListener {
                                        navController.navigate("main") { popUpTo("auth") { inclusive = true } }
                                    }
                            }
                        )
                    }
                    composable("main") {
                        // --- NEW: Start the background service when entering Main screen ---
                        LaunchedEffect(Unit) {
                            startOrderService()
                        }

                        MainScreen(
                            onAcceptOrder = { orderId ->
                                val riderId = Firebase.auth.currentUser?.uid
                                val riderName = Firebase.auth.currentUser?.displayName
                                if (riderId != null) {
                                    lifecycleScope.launch {
                                        try {
                                            Log.d("OrderDebug", "Attempting to accept order $orderId")
                                            // Fetch order to get userId, restaurantName and amount
                                            val orderDoc = Firebase.firestore.collection("orders").document(orderId)
                                                .get().await()
                                            val userId = orderDoc.getString("userId")
                                            val restaurantName = orderDoc.getString("restaurantName") ?: "Unknown Restaurant"
                                            val orderAmount = orderDoc.getDouble("totalPrice") ?: 0.0
                                            Log.d("OrderDebug", "Fetched order data - userId: $userId, restaurantName: $restaurantName, amount: $orderAmount")

                                            // Update order status
                                            Firebase.firestore.collection("orders").document(orderId)
                                                .update(
                                                    mapOf(
                                                        "orderStatus" to "Accepted",
                                                        "riderId" to riderId,
                                                        "riderName" to riderName
                                                    )
                                                ).await()
                                            Log.d("OrderDebug", "Order $orderId updated to Accepted")

                                            Toast.makeText(applicationContext, "Order Accepted!", Toast.LENGTH_SHORT).show()

                                            // Send notification to user
                                            if (userId != null) {
                                                Log.d("OrderDebug", "Sending notification for userId: $userId, orderId: $orderId, status: Accepted")
                                                OneSignalNotificationHelper.sendOrderStatusNotification(
                                                    userId = userId,
                                                    orderId = orderId,
                                                    newStatus = "Accepted",
                                                    restaurantName = restaurantName,
                                                    orderAmount = orderAmount
                                                )
                                                Log.d("OrderDebug", "Notification call completed for order $orderId")
                                            } else {
                                                Log.w("OrderDebug", "No userId found for order $orderId")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("OrderDebug", "Failed to accept order $orderId: ${e.message}", e)
                                            Toast.makeText(applicationContext, "Failed to accept order: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onUpdateOrderStatus = { orderId, newStatus ->
                                lifecycleScope.launch {
                                    try {
                                        Log.d("OrderDebug", "Attempting to update order $orderId to status $newStatus")
                                        // Fetch order to get userId, restaurantName and amount
                                        val orderDoc = Firebase.firestore.collection("orders").document(orderId)
                                            .get().await()
                                        val userId = orderDoc.getString("userId")
                                        val restaurantName = orderDoc.getString("restaurantName") ?: "Unknown Restaurant"
                                        val orderAmount = orderDoc.getDouble("totalPrice") ?: 0.0
                                        Log.d("OrderDebug", "Fetched order data - userId: $userId, restaurantName: $restaurantName, amount: $orderAmount")

                                        // Update order status
                                        Firebase.firestore.collection("orders").document(orderId)
                                            .update("orderStatus", newStatus)
                                            .await()
                                        Log.d("OrderDebug", "Order $orderId updated to $newStatus")

                                        Toast.makeText(applicationContext, "Order marked as $newStatus", Toast.LENGTH_SHORT).show()

                                        // Send notification to user
                                        if (userId != null) {
                                            Log.d("OrderDebug", "Sending notification for userId: $userId, orderId: $orderId, status: $newStatus")
                                            OneSignalNotificationHelper.sendOrderStatusNotification(
                                                userId = userId,
                                                orderId = orderId,
                                                newStatus = newStatus,
                                                restaurantName = restaurantName,
                                                orderAmount = orderAmount
                                            )
                                            Log.d("OrderDebug", "Notification call completed for order $orderId")
                                        } else {
                                            Log.w("OrderDebug", "No userId found for order $orderId")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("OrderDebug", "Failed to update order $orderId: ${e.message}", e)
                                        Toast.makeText(applicationContext, "Failed to update order status: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onSignOut = {
                                lifecycleScope.launch {
                                    // --- NEW: Stop service on sign out ---
                                    stopOrderService()

                                    googleAuthUiClient.signOut()
                                    navController.navigate("auth") {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                }
                            },
                            onNavigateToEditProfile = {
                                navController.navigate("edit_profile")
                            },
                            onNavigateToHistory = {
                                navController.navigate("delivery_history")
                            }
                        )
                    }
                    composable("edit_profile") {
                        RiderEditProfileScreen(
                            onBackClicked = { navController.popBackStack() },
                            onSaveChanges = { phone, vehicle, serviceableLocations ->
                                val userId = Firebase.auth.currentUser?.uid ?: return@RiderEditProfileScreen
                                val updates = mapOf(
                                    "phone" to phone,
                                    "vehicle" to vehicle,
                                    "serviceableLocations" to serviceableLocations
                                )
                                Firebase.firestore.collection("riders").document(userId)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(applicationContext, "Profile Updated", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                            }
                        )
                    }
                    composable("account") {
                        RiderAccountScreen(
                            onNavigateToEditProfile = { navController.navigate("edit_profile") },
                            onNavigateToHistory = { navController.navigate("delivery_history") },
                            onBackClicked = { navController.popBackStack() },
                            onSignOut = {
                                lifecycleScope.launch {
                                    // --- NEW: Stop service on sign out ---
                                    stopOrderService()

                                    googleAuthUiClient.signOut()
                                    navController.navigate("auth") {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
                    composable("delivery_history") {
                        DeliveryHistoryScreen(onBackClicked = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private fun checkRiderProfile(userId: String, navController: NavController) {
        Firebase.firestore.collection("riders").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    navController.navigate("main") { popUpTo("auth") { inclusive = true } }
                } else {
                    navController.navigate("create_profile") { popUpTo("auth") { inclusive = true } }
                }
            }
    }

    // --- NEW: Service Helper Methods ---
    private fun startOrderService() {
        val serviceIntent = Intent(this, OrderMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopOrderService() {
        val serviceIntent = Intent(this, OrderMonitorService::class.java)
        stopService(serviceIntent)
    }
}