package com.example.decluttr.presentation.screens.dashboard

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.decluttr.domain.model.ArchivedApp
import kotlin.math.roundToInt

private const val FOLDER_PREFIX = "folder:"

private sealed class ArchiveGridItem {
    data class Folder(val name: String, val apps: List<ArchivedApp>) : ArchiveGridItem()
    data class App(val app: ArchivedApp) : ArchiveGridItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedAppsList(
    apps: List<ArchivedApp>,
    onUpdateApp: (ArchivedApp) -> Unit,
    onDeleteClick: (ArchivedApp) -> Unit,
    onNavigateToDiscover: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var openedFolder by remember { mutableStateOf<String?>(null) }
    var detailApp by remember { mutableStateOf<ArchivedApp?>(null) }
    var pendingDropPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var folderNameInput by remember { mutableStateOf("") }

    val categoryValues = remember(apps) {
        apps.mapNotNull { it.category?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
    val folderValues = remember(apps) {
        apps.mapNotNull { it.folderName() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val filterValues = remember(categoryValues, folderValues) {
        buildList {
            add("All")
            addAll(categoryValues)
            addAll(folderValues.map { folderFilterLabel(it) })
        }
    }

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        val query = searchQuery.lowercase().trim()
        apps.filter { app ->
            val folderName = app.folderName()
            val matchesFilter = when {
                selectedFilter == "All" -> true
                selectedFilter.startsWith("Folder: ") -> folderName == selectedFilter.removePrefix("Folder: ")
                else -> app.category == selectedFilter
            }
            val matchesQuery = query.isEmpty() ||
                app.name.lowercase().contains(query) ||
                (app.category?.lowercase()?.contains(query) == true) ||
                (app.notes?.lowercase()?.contains(query) == true) ||
                app.tags.any { it.lowercase().contains(query) } ||
                (folderName?.lowercase()?.contains(query) == true)
            matchesFilter && matchesQuery
        }
    }

    val visibleItems = remember(filteredApps, openedFolder) {
        if (openedFolder != null) {
            filteredApps.filter { it.folderName() == openedFolder }.map { ArchiveGridItem.App(it) }
        } else {
            val folderGroups = filteredApps
                .groupBy { it.folderName() }
                .filterKeys { !it.isNullOrBlank() }
                .map { ArchiveGridItem.Folder(it.key.orEmpty(), it.value.sortedBy { app -> app.name.lowercase() }) }
                .sortedBy { it.name.lowercase() }
            val looseApps = filteredApps
                .filter { it.folderName() == null }
                .sortedBy { it.name.lowercase() }
                .map { ArchiveGridItem.App(it) }
            folderGroups + looseApps
        }
    }

    val draggingIdState = remember { mutableStateOf<String?>(null) }
    val draggingOffsetState = remember { mutableStateOf(Offset.Zero) }
    val hoverTargetState = remember { mutableStateOf<String?>(null) }
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    val allAppsById = remember(apps) { apps.associateBy { it.packageId } }

    LaunchedEffect(openedFolder, visibleItems) {
        if (openedFolder != null) {
            itemBounds.clear()
        }
    }

    if (pendingDropPair != null) {
        val pair = pendingDropPair!!
        val sourceApp = allAppsById[pair.first]
        val targetApp = allAppsById[pair.second]
        if (sourceApp != null && targetApp != null) {
            val suggested = targetApp.folderName() ?: sourceApp.folderName() ?: "${targetApp.name.take(10)} Folder"
            if (folderNameInput.isBlank()) {
                folderNameInput = suggested
            }
            Dialog(onDismissRequest = {
                pendingDropPair = null
                folderNameInput = ""
            }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Create Folder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = folderNameInput,
                            onValueChange = { folderNameInput = it },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    pendingDropPair = null
                                    folderNameInput = ""
                                }
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val trimmed = folderNameInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        val targetFolder = targetApp.folderName()
                                        if (targetFolder.isNullOrBlank()) {
                                            onUpdateApp(targetApp.withFolder(trimmed))
                                        }
                                        onUpdateApp(sourceApp.withFolder(trimmed))
                                        openedFolder = trimmed
                                    }
                                    pendingDropPair = null
                                    folderNameInput = ""
                                }
                            ) { Text("Save") }
                        }
                    }
                }
            }
        } else {
            pendingDropPair = null
            folderNameInput = ""
        }
    }

    if (detailApp != null) {
        val app = detailApp!!
        val context = LocalContext.current
        var whyKept by remember(app.packageId) { mutableStateOf(app.notes.orEmpty()) }
        val iconBitmap = remember(app.iconBytes) {
            app.iconBytes?.let { bytes -> 
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() 
                } catch (e: Exception) {
                    null
                }
            }
        }
        Dialog(onDismissRequest = { detailApp = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = "${app.name} icon",
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        PlaceholderIcon()
                    }
                    Text(text = app.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = app.category ?: "Uncategorized",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageId}")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }.getOrElse {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageId}"))
                                context.startActivity(webIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Reinstall")
                    }
                    OutlinedTextField(
                        value = whyKept,
                        onValueChange = {
                            whyKept = it
                            val latest = (allAppsById[app.packageId] ?: app).copy(notes = it.trim().takeIf { text -> text.isNotEmpty() })
                            onUpdateApp(latest)
                            detailApp = latest
                        },
                        label = { Text("Why I Kept This") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4
                    )
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        if (filterValues.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterValues) { filter ->
                    CategoryPill(
                        text = filter,
                        isSelected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (openedFolder != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openedFolder = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = openedFolder!!,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (visibleItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (apps.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Your archive is currently empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToDiscover) {
                            Text("Find rarely used apps to Decluttr")
                        }
                    }
                } else {
                    Text("No apps match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = visibleItems,
                        key = {
                            when (it) {
                                is ArchiveGridItem.App -> it.app.packageId
                                is ArchiveGridItem.Folder -> "folder-${it.name}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is ArchiveGridItem.Folder -> FolderDrawerItem(
                                folderName = item.name,
                                apps = item.apps,
                                onClick = { openedFolder = item.name }
                            )
                            is ArchiveGridItem.App -> {
                                val app = item.app
                                val isDragEnabled = openedFolder == null && app.folderName() == null
                                AppDrawerItem(
                                    app = app,
                                    onClick = {
                                        detailApp = app
                                    },
                                    onDeleteClick = { onDeleteClick(app) },
                                    isDragTarget = hoverTargetState.value == app.packageId && draggingIdState.value != app.packageId,
                                    enableDrag = isDragEnabled,
                                    onPositioned = { id, rect ->
                                        if (isDragEnabled) itemBounds[id] = rect else itemBounds.remove(id)
                                    },
                                    onDragStart = { id, start ->
                                        draggingIdState.value = id
                                        val bounds = itemBounds[id]
                                        draggingOffsetState.value = if (bounds != null) {
                                            Offset(bounds.left + start.x, bounds.top + start.y)
                                        } else {
                                            start
                                        }
                                        hoverTargetState.value = null
                                    },
                                    onDrag = { delta ->
                                        draggingOffsetState.value += delta
                                        val draggingId = draggingIdState.value
                                        hoverTargetState.value = itemBounds.entries.firstOrNull { (key, rect) ->
                                            draggingId != null && key != draggingId && rect.contains(draggingOffsetState.value)
                                        }?.key
                                    },
                                    onDragEnd = {
                                        val sourceId = draggingIdState.value
                                        val targetId = hoverTargetState.value
                                        if (sourceId != null && targetId != null && sourceId != targetId) {
                                            pendingDropPair = sourceId to targetId
                                        }
                                        draggingIdState.value = null
                                        hoverTargetState.value = null
                                    },
                                    onDragCancel = {
                                        draggingIdState.value = null
                                        hoverTargetState.value = null
                                    }
                                )
                            }
                        }
                    }
                }

                val draggingApp = draggingIdState.value?.let { allAppsById[it] }
                if (draggingApp != null) {
                    val bitmap = remember(draggingApp.iconBytes) {
                        draggingApp.iconBytes?.let { bytes -> 
                            try {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() 
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .offset {
                                        androidx.compose.ui.unit.IntOffset(
                                            (draggingOffsetState.value.x - 28f).roundToInt(),
                                            (draggingOffsetState.value.y - 28f).roundToInt()
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ArchivedApp.folderName(): String? {
    return tags.firstOrNull { it.startsWith(FOLDER_PREFIX) }?.removePrefix(FOLDER_PREFIX)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun ArchivedApp.withFolder(folderName: String): ArchivedApp {
    val cleanedTags = tags.filterNot { it.startsWith(FOLDER_PREFIX) }
    return copy(tags = cleanedTags + "$FOLDER_PREFIX${folderName.trim()}")
}

private fun folderFilterLabel(folderName: String): String = "Folder: $folderName"

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

@Composable
private fun FolderDrawerItem(
    folderName: String,
    apps: List<ArchivedApp>,
    onClick: () -> Unit
) {
    val previewApps = apps.take(4)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            previewApps.forEach { app ->
                val bitmap = remember(app.iconBytes) {
                    app.iconBytes?.let { bytes -> 
                        try {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() 
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    AppIconFallback(
                        label = app.name,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = folderName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${apps.size} apps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerItem(
    app: ArchivedApp,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDragTarget: Boolean = false,
    enableDrag: Boolean = false,
    onPositioned: (String, Rect) -> Unit = { _, _ -> },
    onDragStart: (String, Offset) -> Unit = { _, _ -> },
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val cachedBitmap = remember(app.iconBytes) {
        app.iconBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val dragModifier = if (enableDrag) {
        Modifier.pointerInput(app.packageId) {
            detectDragGesturesAfterLongPress(
                onDragStart = { startOffset ->
                    onDragStart(app.packageId, startOffset)
                },
                onDrag = { change, dragAmount ->
                    onDrag(dragAmount)
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragCancel() }
            )
        }
    } else {
        Modifier
    }
    val interactionModifier = if (enableDrag) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { showMenu = true }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onPositioned(app.packageId, coordinates.boundsInRoot())
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDragTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent
            )
            .then(dragModifier)
            .then(interactionModifier)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cachedBitmap != null) {
            Image(
                bitmap = cachedBitmap,
                contentDescription = "App Icon",
                modifier = Modifier.size(56.dp)
            )
        } else {
            AppIconFallback(label = app.name)
        }

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

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Re-install App") },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = {
                    showMenu = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageId}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }.getOrElse {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageId}"))
                        context.startActivity(webIntent)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text("Delete from DB") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDeleteClick()
                }
            )
        }
    }
}

@Composable
fun PlaceholderIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    )
}

@Composable
private fun AppIconFallback(
    label: String,
    modifier: Modifier = Modifier
) {
    val text = label.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
