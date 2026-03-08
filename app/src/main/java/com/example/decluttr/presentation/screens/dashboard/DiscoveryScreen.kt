package com.example.decluttr.presentation.screens.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.decluttr.domain.usecase.GetInstalledAppsUseCase

@Composable
fun DiscoveryScreen(
    unusedApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    allApps: List<GetInstalledAppsUseCase.InstalledAppInfo>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onBatchUninstall: (Set<String>) -> Unit
) {
    var showAllApps by remember { mutableStateOf(false) }
    
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val displayList = if (showAllApps) allApps else unusedApps
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAllApps) "All Apps (${allApps.size})" else "Rarely Used (${unusedApps.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Button(onClick = { showAllApps = !showAllApps }) {
                Text(if (showAllApps) "Show Suggestions" else "Show All Apps")
            }
        }
        
        if (selectedApps.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = {
                        val ids = selectedApps.toSet()
                        selectedApps = emptySet()
                        // Call the provided action instead of empty logic
                        onBatchUninstall(ids)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Archive & Uninstall Selected (${selectedApps.size})")
                }
            }
        }

        if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        "No apps to display.", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(displayList, key = { it.packageId }) { app ->
                    val isSelected = selectedApps.contains(app.packageId)
                    
                    AppListCard(
                        app = app,
                        isSelected = isSelected,
                        onToggle = {
                            selectedApps = if (isSelected) {
                                selectedApps - app.packageId
                            } else {
                                selectedApps + app.packageId
                            }
                        }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
fun AppListCard(
    app: GetInstalledAppsUseCase.InstalledAppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
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
                Text(
                    text = app.name, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${app.apkSizeBytes / (1024 * 1024)} MB", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
