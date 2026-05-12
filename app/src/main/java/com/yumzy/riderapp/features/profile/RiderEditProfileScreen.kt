package com.yumzy.riderapp.features.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderEditProfileScreen(
    onSaveChanges: (phone: String, vehicle: String, serviceableLocations: List<String>) -> Unit,
    onBackClicked: () -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var selectedLocations by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = Unit) {
        val userId = Firebase.auth.currentUser?.uid
        // Fetch all possible locations
        Firebase.firestore.collection("locations").get().addOnSuccessListener { snapshot ->
            allLocations = snapshot.documents.mapNotNull { it.getString("name") }
        }
        // Fetch rider's current data
        if (userId != null) {
            Firebase.firestore.collection("riders").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        phone = document.getString("phone") ?: ""
                        vehicle = document.getString("vehicle") ?: ""
                        val locations = document.get("serviceableLocations") as? List<String> ?: emptyList()
                        selectedLocations = locations.toSet()
                    }
                    isLoading = false
                }
        } else { isLoading = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = { IconButton(onClick = onBackClicked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = vehicle, onValueChange = { vehicle = it }, label = { Text("Vehicle Details") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(8.dp))
                Text("Update Serviceable Locations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                allLocations.forEach { locationName ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedLocations = if (selectedLocations.contains(locationName)) {
                                selectedLocations - locationName
                            } else {
                                selectedLocations + locationName
                            }
                        }.padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = selectedLocations.contains(locationName), onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(locationName)
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSaveChanges(phone, vehicle, selectedLocations.toList()) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Changes")
                }
            }
        }
    }
}