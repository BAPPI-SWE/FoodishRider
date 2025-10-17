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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

data class Order(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val fullAddress: String = "",
    val totalPrice: Double = 0.0,
    val items: List<Map<String, Any>> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

@Composable
fun MyDeliveriesScreen(onUpdateOrderStatus: (orderId: String, newStatus: String) -> Unit) {
    var activeOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

    val filteredOrders by remember(searchText, activeOrders) {
        derivedStateOf {
            if (searchText.isBlank()) {
                activeOrders
            } else {
                activeOrders.filter { order ->
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
        Firebase.firestore.collection("orders")
            .whereEqualTo("riderId", riderId)
            .whereIn("orderStatus", listOf("Accepted", "On the way"))
            .addSnapshotListener { snapshot, _ ->
                isLoading = false
                snapshot?.let {
                    activeOrders = it.documents.mapNotNull { doc ->
                        val address = "Building: ${doc.getString("building")}, Floor: ${doc.getString("floor")}, Room: ${doc.getString("room")}\n${doc.getString("userSubLocation")}, ${doc.getString("userBaseLocation")}"
                        doc.toObject(Order::class.java)?.copy(id = doc.id, fullAddress = address)
                    }.sortedBy { it.createdAt }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("My Active Deliveries", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search Active Deliveries") },
            placeholder = { Text("Search by name, phone, address...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") }
        )

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (filteredOrders.isEmpty()) {
            val emptyText = if (searchText.isBlank()) "You have no active deliveries." else "No active deliveries found for \"$searchText\""
            Text(emptyText, color = Color.Gray)
        } else {
            if (searchText.isNotBlank()) {
                Text(
                    text = "Found ${filteredOrders.size} deliveries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredOrders) { order ->
                    ActiveDeliveryCard(order = order, onStatusUpdate = onUpdateOrderStatus)
                }
            }
        }
    }
}

@Composable
fun ActiveDeliveryCard(order: Order, onStatusUpdate: (orderId: String, newStatus: String) -> Unit) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ongoing Delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(sdf.format(order.createdAt.toDate()), style = MaterialTheme.typography.bodySmall)
            }

            InfoRow(
                icon = Icons.Default.Storefront,
                title = "PICKUP FROM",
                primaryText = order.restaurantName
            )
            InfoRow(
                icon = Icons.Default.Home,
                title = "DELIVER TO",
                primaryText = order.userName,
                secondaryText = order.fullAddress
            )
            // Custom Row for Contact Info with action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Phone, contentDescription = "Contact",
                    modifier = Modifier.padding(top = 4.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("CONTACT", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(order.userPhone, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }

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
            Divider()
            Text("Items:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                order.items.forEach { item ->
                    val miniResName = item["miniResName"] as? String
                    val itemName = item["itemName"] as? String ?: "Unknown"
                    val quantity = item["quantity"]
                    val displayText = if (!miniResName.isNullOrBlank()) {
                        "• $quantity x $itemName ($miniResName)"
                    } else {
                        "• $quantity x $itemName"
                    }
                    Text(displayText)
                }
            }
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Order Total:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("৳${order.totalPrice}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = { onStatusUpdate(order.id, "On the way") }, modifier = Modifier.weight(1f)) {
                    Text("Picked Up")
                }
                Button(onClick = { onStatusUpdate(order.id, "Delivered") }, modifier = Modifier.weight(1f)) {
                    Text("Delivered")
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, primaryText: String, secondaryText: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier
            .padding(top = 4.dp)
            .size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(primaryText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (secondaryText != null) {
                Text(secondaryText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}