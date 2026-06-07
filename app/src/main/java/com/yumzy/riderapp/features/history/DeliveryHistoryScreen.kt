package com.yumzy.riderapp.features.history

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.riderapp.main.Order
import com.yumzy.riderapp.main.NoteIconButton
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryHistoryScreen(onBackClicked: () -> Unit) {
    var completedOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val filteredOrders by remember(searchText, completedOrders, startDate, endDate) {
        derivedStateOf {
            val calendar = Calendar.getInstance()
            val startMillis = startDate?.let {
                calendar.timeInMillis = it
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            val endMillis = endDate?.let {
                calendar.timeInMillis = it
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                calendar.timeInMillis
            }

            var orders = completedOrders
            if (startMillis != null) orders = orders.filter { it.createdAt.toDate().time >= startMillis }
            if (endMillis != null) orders = orders.filter { it.createdAt.toDate().time <= endMillis }

            if (searchText.isNotBlank()) {
                orders.filter { order ->
                    order.userName.contains(searchText, ignoreCase = true) ||
                            order.userPhone.contains(searchText, ignoreCase = true) ||
                            order.fullAddress.contains(searchText, ignoreCase = true) ||
                            order.restaurantName.contains(searchText, ignoreCase = true)
                }
            } else orders
        }
    }

    // New logic for calculating broken-down totals
    val totalDeliveries = filteredOrders.size
    val totalEarnings = filteredOrders.sumOf { it.totalPrice }
    val codEarnings = filteredOrders.filter { it.payment == "COD" }.sumOf { it.totalPrice }
    val onlineEarnings = filteredOrders.filter { it.payment != "COD" }.sumOf { it.totalPrice }

    LaunchedEffect(key1 = Unit) {
        val riderId = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("orders")
            .whereEqualTo("riderId", riderId)
            .whereEqualTo("orderStatus", "Delivered")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                isLoading = false
                if (error != null) return@addSnapshotListener
                snapshot?.let {
                    completedOrders = it.documents.mapNotNull { doc ->
                        val address = "Building: ${doc.getString("building")}, Floor: ${doc.getString("floor")}, Room: ${doc.getString("room")}\n${doc.getString("userSubLocation")}, ${doc.getString("userBaseLocation")}"
                        doc.toObject(Order::class.java)?.copy(id = doc.id, fullAddress = address)
                    }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delivery History") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search History") },
                placeholder = { Text("Search by name, phone, address...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showDatePicker { startDate = it } }, modifier = Modifier.weight(1f)) {
                    Text(startDate?.let { SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(Date(it)) } ?: "Start Date")
                }
                Button(onClick = { showDatePicker { endDate = it } }, modifier = Modifier.weight(1f)) {
                    Text(endDate?.let { SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(Date(it)) } ?: "End Date")
                }
                TextButton(onClick = { startDate = null; endDate = null }) { Text("Clear") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (completedOrders.isEmpty() && searchText.isBlank()){
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No completed deliveries yet.", color = Color.Gray) }
            }
            else {
                // Pass new parameters to SummaryCard
                SummaryCard(
                    totalDeliveries = totalDeliveries,
                    totalEarnings = totalEarnings,
                    codEarnings = codEarnings,
                    onlineEarnings = onlineEarnings
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (filteredOrders.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No deliveries found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredOrders) { order ->
                            CompletedDeliveryCard(order = order)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(totalDeliveries: Int, totalEarnings: Double, codEarnings: Double, onlineEarnings: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Orders", style = MaterialTheme.typography.bodySmall)
                    Text(totalDeliveries.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Earnings", style = MaterialTheme.typography.bodySmall)
                    Text("৳${"%.2f".format(totalEarnings)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Collected (COD)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("৳${"%.1f".format(codEarnings)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Online Paid", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("৳${"%.1f".format(onlineEarnings)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
fun CompletedDeliveryCard(order: Order) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Delivered on ${sdf.format(order.createdAt.toDate())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                NoteIconButton(orderId = order.id, existingNote = order.note)
            }
            InfoRow(Icons.Default.Storefront, "PICKED UP FROM", order.restaurantName)
            InfoRow(Icons.Default.Home, "DELIVERED TO", order.userName, order.fullAddress)
            InfoRow(Icons.Default.Phone, "CUSTOMER CONTACT", order.userPhone)

            Divider()
            Text("Items:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                order.items.forEach { item ->
                    val itemName = item["itemName"] as? String ?: "Unknown"
                    val quantity = item["quantity"]
                    Text("• $quantity x $itemName")
                }
            }
            Divider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val paymentText = if (order.payment == "COD") "(COD)" else "(Online)"
                Text("Order Total:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("৳${order.totalPrice} $paymentText", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, primaryText: String, secondaryText: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.padding(top = 4.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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