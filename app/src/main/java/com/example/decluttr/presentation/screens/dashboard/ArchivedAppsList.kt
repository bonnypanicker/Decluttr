package com.example.decluttr.presentation.screens.dashboard

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.decluttr.domain.model.ArchivedApp
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedAppsList(
    apps: List<ArchivedApp>,
    onAppClick: (String) -> Unit,
    onDeleteClick: (ArchivedApp) -> Unit,
    onAppUpdate: (ArchivedApp) -> Unit, // New callback for saving folders
    onNavigateToDiscover: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>("All") }

    // Derive categories for pills
    val categories = remember(apps) {
        val list = apps.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("All") + list
    }

    // Advanced search filter
    val filteredApps = remember(apps, searchQuery, selectedCategory) {
        apps.filter { app ->
            val matchesCategory = selectedCategory == "All" || app.category == selectedCategory
            val query = searchQuery.lowercase().trim()
            val matchesQuery = query.isEmpty() ||
                app.name.lowercase().contains(query) ||
                (app.category?.lowercase()?.contains(query) == true) ||
                (app.notes?.lowercase()?.contains(query) == true) ||
                app.tags.any { it.lowercase().contains(query) } ||
                (app.folderName?.lowercase()?.contains(query) == true)

            matchesCategory && matchesQuery
        }
    }

    var newFolderAppPair by remember { mutableStateOf<Pair<ArchivedApp, ArchivedApp>?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var expandedFolder by remember { mutableStateOf<String?>(null) }
    
    // Group apps into folders or standalones
    val groupedItems = remember(filteredApps) {
        val standalones = filteredApps.filter { it.folderName == null }.map { ArchivedItem.App(it) }
        val folders = filteredApps.filter { it.folderName != null }
            .groupBy { it.folderName!! }
            .map { (name, apps) -> ArchivedItem.Folder(name, apps) }
        (standalones + folders).sortedBy { 
            when (it) {
                is ArchivedItem.App -> it.app.name.lowercase()
                is ArchivedItem.Folder -> it.name.lowercase()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(100),
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        // Category Pills
        if (categories.size > 1) { // Only show if we have actual categories beside "All"
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    CategoryPill(
                        text = category,
                        isSelected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // App Grid
        if (groupedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (apps.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Your archive is currently empty.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(onClick = onNavigateToDiscover) {
                            Text("Find rarely used apps to Decluttr")
                        }
                    }
                } else {
                    Text(
                        text = "No apps match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (newFolderAppPair != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Name your new folder", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                val folderName = newFolderName.trim().ifEmpty { "New Folder" }
                                newFolderAppPair?.let { (app1, app2) ->
                                    onAppUpdate(app1.copy(folderName = folderName))
                                    onAppUpdate(app2.copy(folderName = folderName))
                                }
                                newFolderAppPair = null
                                newFolderName = ""
                            }
                        ) {
                            Text("Create Folder")
                        }
                    }
                }

                ArchivedAppsRecyclerView(
                    items = groupedItems,
                    onAppClick = onAppClick,
                    onDeleteClick = onDeleteClick,
                    onAppStartDrag = { /* Only needed for compose-drawn drag shadow, native draws own */ },
                    onAppDropOnApp = { draggedApp, targetApp ->
                        newFolderAppPair = Pair(draggedApp, targetApp)
                    },
                    onAppDropOnFolder = { draggedApp, folderName ->
                        onAppUpdate(draggedApp.copy(folderName = folderName))
                    },
                    onRemoveFolder = { folderApps ->
                        folderApps.forEach { app ->
                            onAppUpdate(app.copy(folderName = null))
                        }
                    },
                    onFolderClick = { folderName ->
                        expandedFolder = folderName
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (expandedFolder != null) {
        val folderApps = apps.filter { it.folderName == expandedFolder }
        if (folderApps.isNotEmpty()) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { expandedFolder = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = expandedFolder!!,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            androidx.compose.foundation.lazy.grid.items(folderApps) { app ->
                                AppDrawerItemDraggable(
                                    app = app,
                                    isDragging = false,
                                    onClick = { 
                                        expandedFolder = null
                                        onAppClick(app.packageId)
                                    },
                                    onDeleteClick = { onDeleteClick(app) },
                                    onDragStart = {},
                                    onDrag = {},
                                    onDragEnd = {}
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.TextButton(
                            onClick = { expandedFolder = null }
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        } else {
            expandedFolder = null
        }
    }
}

sealed class ArchivedItem {
    data class App(val app: ArchivedApp) : ArchivedItem()
    data class Folder(val name: String, val apps: List<ArchivedApp>) : ArchivedItem()
}

@Composable
fun CategoryPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerItemDraggable(
    app: ArchivedApp,
    isDragging: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val context = LocalContext.current

    val imageRequest = remember(app.packageId) {
        coil.request.ImageRequest.Builder(context)
            .data(com.example.decluttr.presentation.util.AppIconModel(app.packageId))
            .memoryCacheKey(app.packageId)
            .size(112) // 56dp * density
            .crossfade(false)
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { 
                    onDragStart()
                }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val modifier = if (isDragging) Modifier.size(56.dp).graphicsLayer { alpha = 0.5f } else Modifier.size(56.dp)
        coil.compose.AsyncImage(
            model = imageRequest,
            contentDescription = "App Icon",
            modifier = modifier
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.2
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderDrawerItem(
    folderName: String,
    apps: List<ArchivedApp>,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Folder Icon grid (preview up to 4 apps)
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val previewApps = apps.take(4)
                val rows = previewApps.chunked(2)
                for (row in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (app in row) {
                            val context = LocalContext.current
                            val imageRequest = remember(app.packageId) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(com.example.decluttr.presentation.util.AppIconModel(app.packageId))
                                    .memoryCacheKey(app.packageId)
                                    .size(44) // 22dp * density
                                    .crossfade(false)
                                    .build()
                            }
                            coil.compose.AsyncImage(
                                model = imageRequest,
                                contentDescription = null, 
                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = folderName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Remove Folder") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDeleteClick() }
            )
        }
    }
}

@Composable
fun PlaceholderIcon(modifier: Modifier = Modifier.size(56.dp)) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    )
}
