package com.example.decluttr.presentation.screens.dashboard

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.decluttr.domain.model.ArchivedApp

@Composable
fun ArchivedAppsList(
    apps: List<ArchivedApp>,
    onAppClick: (String) -> Unit,
    onDeleteClick: (ArchivedApp) -> Unit
) {
    if (apps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Your archive is clean and empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            items(apps, key = { it.packageId }) { app ->
                ArchivedAppCard(
                    app = app,
                    onClick = { onAppClick(app.packageId) },
                    onDeleteClick = { onDeleteClick(app) }
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Composable
fun ArchivedAppCard(
    app: ArchivedApp,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (app.iconBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
            } else {
                Box(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (!app.category.isNullOrBlank()) {
                    Text(text = app.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            // Re-install button (Play Store)
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageId}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to browser
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageId}"))
                    context.startActivity(webIntent)
                }
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reinstall", tint = MaterialTheme.colorScheme.primary)
            }
            
            // Delete record
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Record", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
