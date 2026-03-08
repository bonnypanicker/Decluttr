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
    onBatchUninstall: (Set<String>) -> Unit,
    onBatchUninstallOnly: (Set<String>) -> Unit,
    hasUsagePermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var showAllApps by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Check permission on resume
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onRequestPermission()
                onRefresh()
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    // Only show unused if we have permission, otherwise fallback to empty to trigger prompt
    val safeUnusedApps = if (hasUsagePermission) unusedApps else emptyList()
    val displayList = if (showAllApps) allApps else safeUnusedApps
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSideloadWarning by remember { mutableStateOf(false) }
    var appsToUninstall by remember { mutableStateOf<Set<String>>(emptySet()) }

    if (showSideloadWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSideloadWarning = false },
            title = { Text("Sideloaded Apps Detected", fontWeight = FontWeight.Bold) },
            text = { Text("One or more of the selected apps were not installed from the Google Play Store. It is not possible to archive sideloaded apps.\n\nDo you wish to uninstall them anyway?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showSideloadWarning = false
                        val playStoreIds = displayList.filter { it.packageId in appsToUninstall && it.isPlayStoreInstalled }.map { it.packageId }.toSet()
                        val sideloadedIds = displayList.filter { it.packageId in appsToUninstall && !it.isPlayStoreInstalled }.map { it.packageId }.toSet()
                        selectedApps = emptySet()
                        if (playStoreIds.isNotEmpty()) onBatchUninstall(playStoreIds)
                        if (sideloadedIds.isNotEmpty()) onBatchUninstallOnly(sideloadedIds)
                    }
                ) {
                    Text("Uninstall Anyway")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showSideloadWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val ids = selectedApps.toSet()
                        selectedApps = emptySet()
                        onBatchUninstallOnly(ids)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Uninstall Only", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Button(
                    onClick = {
                        val ids = selectedApps.toSet()
                        val hasSideloaded = displayList.any { it.packageId in ids && !it.isPlayStoreInstalled }
                        if (hasSideloaded) {
                            appsToUninstall = ids
                            showSideloadWarning = true
                        } else {
                            selectedApps = emptySet()
                            onBatchUninstall(ids)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Archive &\nUninstall", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        if (!showAllApps && !hasUsagePermission) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        "We need usage access to determine which apps are rarely used.", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(onClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // ignored
                        }
                    }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else if (displayList.isEmpty()) {
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
