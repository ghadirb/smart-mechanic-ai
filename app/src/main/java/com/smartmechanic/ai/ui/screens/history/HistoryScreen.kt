package com.smartmechanic.ai.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartmechanic.ai.data.model.InputType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val history by viewModel.history.collectAsState()
    val dateFormat = remember_SimpleDateFormat()

    Scaffold(topBar = { TopAppBar(title = { Text("سوابق تشخیص") }) }) { padding ->
        if (history.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("هنوز هیچ تشخیصی ثبت نشده است.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                items(history, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dateFormat.format(Date(item.timestampMillis)), style = MaterialTheme.typography.bodyMedium)
                                Text(inputTypeLabel(item.inputType), style = MaterialTheme.typography.bodyMedium)
                                Text(item.summary, style = MaterialTheme.typography.titleMedium)
                                Text("فوریت: ${item.urgency}", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { viewModel.delete(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun inputTypeLabel(type: InputType): String = when (type) {
    InputType.TEXT -> "🔧 متن"
    InputType.IMAGE -> "📷 عکس"
    InputType.AUDIO -> "🎙️ صدا"
    InputType.VIDEO -> "🎥 ویدئو"
}

private fun remember_SimpleDateFormat(): SimpleDateFormat =
    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
