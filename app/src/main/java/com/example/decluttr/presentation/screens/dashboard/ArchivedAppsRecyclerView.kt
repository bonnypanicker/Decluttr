package com.example.decluttr.presentation.screens.dashboard

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decluttr.domain.model.ArchivedApp

@Composable
fun ArchivedAppsRecyclerView(
    items: List<ArchivedItem>,
    onAppClick: (String) -> Unit,
    onDeleteClick: (ArchivedApp) -> Unit,
    onAppStartDrag: (ArchivedApp) -> Unit,
    onAppDropOnApp: (ArchivedApp, ArchivedApp) -> Unit,
    onAppDropOnFolder: (ArchivedApp, String) -> Unit,
    onRemoveFolder: (List<ArchivedApp>) -> Unit,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val adapter = remember {
        ArchivedAppsAdapter(
            items = items,
            onAppClick = onAppClick,
            onDeleteClick = onDeleteClick,
            onAppStartDrag = onAppStartDrag,
            onAppDropOnApp = onAppDropOnApp,
            onAppDropOnFolder = onAppDropOnFolder,
            onRemoveFolder = onRemoveFolder,
            onFolderClick = onFolderClick
        )
    }

    // Update adapter whenever items change
    adapter.updateItems(items)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            RecyclerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // Assuming typical phone screen width, 4 columns is Standard.
                // You can base this on screen width dp if needed.
                val gridLayoutManager = GridLayoutManager(ctx, 4)
                
                // Allow our "Create Folder / Name Folder" UI (if it's an item) to span all columns
                // However, since that UI is currently built *outside* the items list in the parent composable
                // we just let the apps and folders occupy 1 span each.
                layoutManager = gridLayoutManager
                
                // Add some default spacing
                val padding = (16 * ctx.resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                clipToPadding = false
                
                this.adapter = adapter
            }
        },
        update = { recyclerView ->
            recyclerView.adapter = adapter
            // The adapter.updateItems handles notifying data set changed
        }
    )
}
