package com.yumzy.rider.main

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// --- DATA CLASSES ---
data class RiderProfile(
    val name: String = "",
    val serviceableLocations: List<String> = emptyList()
)

data class OrderRequest(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val totalPrice: Double = 0.0,
    val items: List<Map<String, Any>> = emptyList(),
    val fullAddress: String = "",
    val userPhone: String = "",
    val userBaseLocation: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

@Composable
fun NewOrdersScreen(onAcceptOrder: (orderId: String) -> Unit) {
    var riderProfile by remember { mutableStateOf<RiderProfile?>(null) }
    var availableOrders by remember { mutableStateOf<List<OrderRequest>>(emptyList()) }
    var isAvailable by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

    val filteredOrders by remember(searchText, availableOrders) {
        derivedStateOf {
            if (searchText.isBlank()) {
                availableOrders
            } else {
                availableOrders.filter { order ->
                    order.userName.contains(searchText, ignoreCase = true) ||
                            order.userPhone.contains(searchText, ignoreCase = true) ||
                            order.fullAddress.contains(searchText, ignoreCase = true) ||
                            order.restaurantName.contains(searchText, ignoreCase = true)
                }
            }
        }
    }

    LaunchedEffect(key1 = Unit) {
        val riderId = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
        val db = Firebase.firestore
        val riderDocRef = db.collection("riders").document(riderId)

        riderDocRef.addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val profile = doc.toObject(RiderProfile::class.java)
                riderProfile = profile
                isAvailable = doc.getBoolean("isAvailable") ?: false

                if (profile?.serviceableLocations?.isNotEmpty() == true) {
                    db.collection("orders")
                        .whereEqualTo("orderStatus", "Pending")
                        .whereEqualTo("orderType", "Instant")
                        .whereIn("userBaseLocation", profile.serviceableLocations)
                        .addSnapshotListener { snapshot, _ ->
                            snapshot?.let {
                                availableOrders = it.documents.mapNotNull { orderDoc ->
                                    val address = "Building: ${orderDoc.getString("building")}, Floor: ${orderDoc.getString("floor")}, Room: ${orderDoc.getString("room")}\n${orderDoc.getString("userSubLocation")}, ${orderDoc.getString("userBaseLocation")}"
                                    orderDoc.toObject(OrderRequest::class.java)?.copy(
                                        id = orderDoc.id,
                                        fullAddress = address
                                    )
                                }.sortedByDescending { it.createdAt }
                            }
                        }
                }
            }
            isLoading = false
        }
    }

    fun updateAvailability(newStatus: Boolean) {
        val riderId = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("riders").document(riderId).update("isAvailable", newStatus)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Welcome, ${riderProfile?.name ?: "..."}")
                    Text(if (isAvailable) "You are Online" else "You are Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isAvailable) Color(0xFF2E7D32) else Color.Gray)
                }
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Switch(checked = isAvailable, onCheckedChange = { updateAvailability(it) }, modifier = Modifier.scale(1.2f))
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search Orders") },
            placeholder = { Text("Search by name, phone, address...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") }
        )

        Text("Available Deliveries", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (!isAvailable) {
            Text("You are offline. Go online to see new orders.", color = Color.Gray)
        } else if (filteredOrders.isEmpty()) {
            val emptyText = if (searchText.isBlank()) "No new orders right now." else "No orders found for \"$searchText\""
            Text(emptyText, color = Color.Gray)
        } else {
            if (searchText.isNotBlank()) {
                Text(
                    text = "Found ${filteredOrders.size} orders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredOrders) { order ->
                    OrderRequestCard(order = order, onAccept = { onAcceptOrder(order.id) })
                }
            }
        }
    }
}

@Composable
fun OrderRequestCard(order: OrderRequest, onAccept: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(order.restaurantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(sdf.format(order.createdAt.toDate()), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Divider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Phone, contentDescription = "Phone", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(order.userPhone, modifier = Modifier.weight(1f))

                // Call Button
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.userPhone}"))
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Call, contentDescription = "Call User", tint = MaterialTheme.colorScheme.primary)
                }

                // WhatsApp Button
                IconButton(onClick = {
                    try {
                        var formattedNumber = order.userPhone.replace(Regex("[^0-9+]"), "")
                        if (formattedNumber.startsWith("01") && formattedNumber.length == 11) {
                            formattedNumber = "+880${formattedNumber.substring(1)}"
                        }
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber")
                            setPackage("com.whatsapp")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Chat, contentDescription = "Message on WhatsApp", tint = Color(0xFF25D366))
                }
            }
            Text("Deliver to:", fontWeight = FontWeight.SemiBold)
            Text(order.userName, style = MaterialTheme.typography.bodyLarge)
            Text(order.fullAddress)
            Divider()
            Text("Items:", fontWeight = FontWeight.SemiBold)
            order.items.forEach { item ->
                val miniResName = item["miniResName"] as? String
                val itemName = item["itemName"] as? String ?: "Unknown"
                val quantity = item["quantity"]
                val displayText = if (!miniResName.isNullOrBlank()) {
                    "- $quantity x $itemName ($miniResName)"
                } else {
                    "- $quantity x $itemName"
                }
                Text(displayText)
            }
            Divider()
            Text("Order Total: ৳${order.totalPrice}", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Accept Order")
            }
        }
    }
}