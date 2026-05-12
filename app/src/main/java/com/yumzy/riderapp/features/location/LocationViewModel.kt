package com.yumzy.riderapp.features.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationState(
    val allBaseLocations: List<String> = emptyList()
)

class LocationViewModel : ViewModel() {
    private val _state = MutableStateFlow(LocationState())
    val state = _state.asStateFlow()

    init {
        fetchLocations()
    }

    private fun fetchLocations() {
        viewModelScope.launch {
            Firebase.firestore.collection("locations").get()
                .addOnSuccessListener { snapshot ->
                    val locationNames = snapshot.documents.mapNotNull { it.getString("name") }
                    _state.update { it.copy(allBaseLocations = locationNames) }
                }
        }
    }
}