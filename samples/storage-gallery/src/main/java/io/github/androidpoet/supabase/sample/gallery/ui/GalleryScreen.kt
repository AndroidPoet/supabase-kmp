package io.github.androidpoet.supabase.sample.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun GalleryScreen(
    bucket: String,
    images: List<String>,
    previewName: String?,
    preview: ImageBitmap?,
    urls: String,
    status: String,
    error: String?,
    busy: Boolean,
    onPick: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Storage Gallery", style = MaterialTheme.typography.headlineSmall)
        Text("bucket: $bucket", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPick, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Upload photo") }
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
        }

        if (preview != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    previewName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    Image(
                        bitmap = preview,
                        contentDescription = previewName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                    )
                    if (urls.isNotBlank()) {
                        Text(urls, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

        Text("Objects (${images.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(images) { name ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onOpen(name) }, enabled = !busy) { Text("Open") }
                        TextButton(onClick = { onDelete(name) }, enabled = !busy) { Text("Delete") }
                    }
                }
            }
        }
    }
}
