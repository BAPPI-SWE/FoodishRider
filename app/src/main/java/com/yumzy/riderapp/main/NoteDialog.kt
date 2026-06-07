package com.yumzy.riderapp.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * A small icon button that shows whether an order has a note.
 * Filled amber icon = note exists. Outline grey = no note.
 * Clicking opens [NoteDialog].
 */
@Composable
fun NoteIconButton(
    orderId: String,
    existingNote: String
) {
    var showDialog by remember { mutableStateOf(false) }
    // Keep local copy so icon updates immediately after save without waiting for snapshot
    var localNote by remember(existingNote) { mutableStateOf(existingNote) }

    if (showDialog) {
        NoteDialog(
            orderId = orderId,
            initialNote = localNote,
            onDismiss = { showDialog = false },
            onSaved = { saved ->
                localNote = saved
                showDialog = false
            }
        )
    }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = if (localNote.isNotBlank()) Icons.Default.NoteAlt else Icons.Default.EditNote,
            contentDescription = if (localNote.isNotBlank()) "View note" else "Add note",
            tint = if (localNote.isNotBlank()) Color(0xFFF59E0B) else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun NoteDialog(
    orderId: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(initialNote) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.NoteAlt,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp)
                )
                Text("Rider Note", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Add a private note for this order (e.g. money pending, item missing).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    placeholder = { Text("Write your note here...") },
                    maxLines = 5,
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    Firebase.firestore.collection("orders").document(orderId)
                        .update("note", noteText.trim())
                        .addOnCompleteListener {
                            isSaving = false
                            onSaved(noteText.trim())
                        }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}