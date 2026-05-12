package com.yumzy.riderapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderProfileScreen(
    onSaveProfile: (phone: String, vehicle: String, serviceableLocations: List<String>) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var selectedLocations by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch all possible locations from Firestore
    LaunchedEffect(key1 = Unit) {
        Firebase.firestore.collection("locations").get()
            .addOnSuccessListener { snapshot ->
                allLocations = snapshot.documents.mapNotNull { it.getString("name") }
                isLoading = false
            }
            .addOnFailureListener {
                // Optionally handle error, e.g., show a toast
                isLoading = false
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Complete Your Rider Profile") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = vehicle,
                onValueChange = { vehicle = it },
                label = { Text("Vehicle Details (e.g., Motorcycle, Bicycle)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Text("Serviceable Locations", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("Select where you can make deliveries.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
            } else {
                // Dynamically create a checkbox for each location
                allLocations.forEach { locationName ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLocations = if (selectedLocations.contains(locationName)) {
                                    selectedLocations - locationName
                                } else {
                                    selectedLocations + locationName
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selectedLocations.contains(locationName),
                            onCheckedChange = null // Handled by Row's clickable modifier
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(locationName)
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false)) // Pushes button towards bottom
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { onSaveProfile(phone, vehicle, selectedLocations.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Delivering", fontSize = 16.sp)
            }
        }
    }
}