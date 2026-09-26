package ink.clearexams.cdranalyzer

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InvestigationNote(
    val id: Long,
    val category: String,
    val text: String,
    val createdAt: String
)

private fun loadInvestigationNotes(context: Context): List<InvestigationNote> {
    val prefs = context.getSharedPreferences("investigation_notes", Context.MODE_PRIVATE)
    return prefs.all.entries.mapNotNull { entry ->
        val id = entry.key.toLongOrNull() ?: return@mapNotNull null
        val raw = entry.value as? String ?: return@mapNotNull null
        val parts = raw.split("|~|", limit = 3)
        if (parts.size < 3) null else InvestigationNote(id, parts[0], parts[2], parts[1])
    }.sortedByDescending { it.id }
}

private fun saveInvestigationNote(context: Context, note: InvestigationNote) {
    context.getSharedPreferences("investigation_notes", Context.MODE_PRIVATE)
        .edit()
        .putString(note.id.toString(), "${note.category}|~|${note.createdAt}|~|${note.text}")
        .apply()
}

private fun deleteInvestigationNote(context: Context, id: Long) {
    context.getSharedPreferences("investigation_notes", Context.MODE_PRIVATE)
        .edit().remove(id.toString()).apply()
}

@Composable
fun InvestigationNotesScreen() {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(loadInvestigationNotes(context)) }
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Observation") }
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf("Observation", "Lead", "Important Number", "Tower Finding", "Device Finding", "Follow-up", "Other")

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text("Investigation workspace", style = MaterialTheme.typography.titleMedium)
        Text("Notes are stored locally on this device.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Category: $category")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { category = option; expanded = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Finding / note") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Button(
            onClick = {
                val clean = text.trim()
                if (clean.isNotEmpty()) {
                    val id = System.currentTimeMillis()
                    val time = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(id))
                    saveInvestigationNote(context, InvestigationNote(id, category, clean, time))
                    notes = loadInvestigationNotes(context)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Save note") }

        Spacer(Modifier.height(8.dp))
        Text("Saved notes (${notes.size})", style = MaterialTheme.typography.titleMedium)

        if (notes.isEmpty()) {
            Text("No investigation notes saved yet.", modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(notes, key = { it.id }) { note ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(note.category, style = MaterialTheme.typography.labelLarge)
                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                            Text(note.createdAt, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                            TextButton(onClick = {
                                deleteInvestigationNote(context, note.id)
                                notes = loadInvestigationNotes(context)
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
