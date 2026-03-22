package com.tool.decluttr.presentation.screens.dashboard

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
import androidx.compose.foundation.lazy.grid.items
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
import com.tool.decluttr.domain.model.ArchivedApp
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
        // Native search bar — identical pattern to DiscoveryDashboard
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            factory = { ctx ->
                val searchBarView = android.view.LayoutInflater.from(ctx)
                    .inflate(com.tool.decluttr.R.layout.item_search_bar, null, false)

                val searchEditText = searchBarView
                    .findViewById<android.widget.EditText>(com.tool.decluttr.R.id.search_edit_text)
                val clearButton = searchBarView
                    .findViewById<android.widget.ImageView>(com.tool.decluttr.R.id.clear_button)

                searchEditText.hint = "Search"

                val callback = SearchQueryCallback()
                searchBarView.setTag(com.tool.decluttr.R.id.archive_search_callback, callback)

                searchEditText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val q = s.toString()
                        clearButton.visibility =
                            if (q.isEmpty()) android.view.View.GONE
                            else android.view.View.VISIBLE
                        callback.onQueryChange?.invoke(q)
                    }
                })

                clearButton.setOnClickListener {
                    searchEditText.setText("")
                }

                searchBarView
            },
            update = { view ->
                val callback = view.getTag(com.tool.decluttr.R.id.archive_search_callback) as SearchQueryCallback
                callback.onQueryChange = { searchQuery = it }

                val editText = view.findViewById<android.widget.EditText>(com.tool.decluttr.R.id.search_edit_text)
                if (editText.text.toString() != searchQuery) {
                    editText.setText(searchQuery)
                    editText.setSelection(searchQuery.length)
                }
            }
        )

        // Category Pills
        if (categories.size > 1) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                factory = { ctx ->
                    android.widget.HorizontalScrollView(ctx).apply {
                        isHorizontalScrollBarEnabled = false
                        clipToPadding = false
                        val dp16 = (16 * ctx.resources.displayMetrics.density).toInt()
                        setPadding(dp16, 0, dp16, 0)

                        addView(
                            android.widget.LinearLayout(ctx).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                id = com.tool.decluttr.R.id.chip_container
                            }
                        )
                    }
                },
                update = { hsv ->
                    val container = hsv.findViewById<android.widget.LinearLayout>(com.tool.decluttr.R.id.chip_container)
                    container.removeAllViews()

                    val density = hsv.context.resources.displayMetrics.density
                    val dp8 = (8 * density).toInt()

                    categories.forEach { category ->
                        com.google.android.material.chip.Chip(hsv.context).apply {
                            text = category
                            isCheckable = true
                            isChecked = selectedCategory == category

                            // Material 3 styling
                            chipBackgroundColor = android.content.res.ColorStateList(
                                arrayOf(
                                    intArrayOf(android.R.attr.state_checked),
                                    intArrayOf()
                                ),
                                intArrayOf(
                                    com.google.android.material.R.attr.colorPrimary
                                        .let { attr ->
                                            val ta = hsv.context.obtainStyledAttributes(intArrayOf(attr))
                                            ta.getColor(0, 0).also { ta.recycle() }
                                        },
                                    com.google.android.material.R.attr.colorSurfaceVariant
                                        .let { attr ->
                                            val ta = hsv.context.obtainStyledAttributes(intArrayOf(attr))
                                            ta.getColor(0, 0).also { ta.recycle() }
                                        }
                                )
                            )

                            setOnClickListener { selectedCategory = category }

                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginEnd = dp8
                            }
                        }.also { container.addView(it) }
                    }
                }
            )
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
                val context = LocalContext.current

                // Show native AlertDialog when drag-drop creates a new folder pair
                androidx.compose.runtime.LaunchedEffect(newFolderAppPair) {
                    val pair = newFolderAppPair ?: return@LaunchedEffect

                    // 1. Create the folder with a default name immediately
                    val defaultName = "New Folder"
                    onAppUpdate(pair.first.copy(folderName = defaultName))
                    onAppUpdate(pair.second.copy(folderName = defaultName))
                    newFolderAppPair = null

                    // 2. Open the folder overlay immediately for the user to rename
                    //    (Small delay to let recomposition settle with the new folder)
                    kotlinx.coroutines.delay(150)
                    expandedFolder = defaultName
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
                    onAppDropOnEmptySpace = { draggedApp ->
                        onAppUpdate(draggedApp.copy(folderName = null))
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

    val context = LocalContext.current

    // Keep track of the folder overlay controller
    val folderOverlay = remember { mutableStateOf<FolderExpandOverlay?>(null) }

    // Modern folder expansion with dolly-zoom
    // We only trigger when expandedFolder changes. If it's not null, we update apps inside if they change.
    androidx.compose.runtime.LaunchedEffect(expandedFolder) {
        val folderName = expandedFolder
        if (folderName == null) {
            folderOverlay.value?.dismiss {}
            folderOverlay.value = null
            return@LaunchedEffect
        }

        val folderApps = apps.filter { it.folderName == folderName }
        if (folderApps.isEmpty()) {
            expandedFolder = null
            folderOverlay.value?.dismiss {}
            folderOverlay.value = null
            return@LaunchedEffect
        }

        // Get the activity's root decorView as parent for the overlay
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val rootView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)

        val overlay = FolderExpandOverlay(context, rootView)
        folderOverlay.value = overlay

        overlay.show(
            folderName = folderName,
            folderApps = folderApps,
            anchorView = null,
            onAppClick = { packageId ->
                expandedFolder = null
                onAppClick(packageId)
            },
            onFolderRenamed = { newName ->
                // Rename all apps in the folder
                folderApps.forEach { app ->
                    onAppUpdate(app.copy(folderName = newName))
                }
            },
            onDragStartFromFolder = {
                expandedFolder = null
            },
            onDismiss = {
                if (expandedFolder == folderName) {
                    expandedFolder = null
                }
                folderOverlay.value = null
            }
        )
    }

    // Keep the overlay's apps in sync if the apps list changes while it's open
    androidx.compose.runtime.LaunchedEffect(apps) {
        val currentFolder = expandedFolder
        if (currentFolder != null && folderOverlay.value != null) {
            val updatedFolderApps = apps.filter { it.folderName == currentFolder }
            if (updatedFolderApps.isEmpty()) {
                expandedFolder = null
            } else {
                folderOverlay.value?.updateApps(updatedFolderApps)
            }
        }
    }
}

sealed class ArchivedItem {
    data class App(val app: ArchivedApp) : ArchivedItem()
    data class Folder(val name: String, val apps: List<ArchivedApp>) : ArchivedItem()
}
