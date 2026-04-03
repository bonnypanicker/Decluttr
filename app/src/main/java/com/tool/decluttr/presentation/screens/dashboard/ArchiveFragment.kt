package com.tool.decluttr.presentation.screens.dashboard

import android.content.ClipDescription
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ArchiveFragment : Fragment(R.layout.fragment_archive) {

    private val viewModel: DashboardViewModel by activityViewModels()

    private var searchQuery = ""
    private var selectedCategory = "All"
    private var expandedFolder: String? = null
    private var folderOverlay: FolderExpandOverlay? = null

    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var searchClear: ImageView
    private lateinit var chipContainerScrollView: View
    private lateinit var chipContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateContainer: View
    private lateinit var tvEmptyMessage: TextView
    private lateinit var btnFindApps: Button

    private lateinit var adapter: ArchivedAppsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun initViews(v: View) {
        searchBar = v.findViewById(R.id.archive_search_bar)
        searchInput = searchBar.findViewById(R.id.search_edit_text)
        searchClear = searchBar.findViewById(R.id.clear_button)
        chipContainerScrollView = v.findViewById(R.id.category_scroll_view)
        chipContainer = v.findViewById(R.id.chip_container)
        recyclerView = v.findViewById(R.id.archive_recycler_view)
        emptyStateContainer = v.findViewById(R.id.empty_state_container)
        tvEmptyMessage = v.findViewById(R.id.tv_empty_message)
        btnFindApps = v.findViewById(R.id.btn_find_apps)

        searchInput.hint = "Search"
    }

    private fun setupRecyclerView() {
        adapter = ArchivedAppsAdapter(
            onAppClick = { pkg -> openNativeAppDetails(pkg) },
            onDeleteClick = { app -> viewModel.deleteArchivedApp(app) },
            onAppStartDrag = { },
            onAppDropOnApp = { draggedApp, targetApp -> handleAppDropOnApp(draggedApp, targetApp) },
            onAppDropOnFolder = { draggedApp, folderName ->
                viewModel.updateArchivedApp(draggedApp.copy(folderName = folderName))
            },
            onRemoveFolder = { folderApps ->
                folderApps.forEach { app -> viewModel.updateArchivedApp(app.copy(folderName = null)) }
            },
            onFolderClick = { folderName ->
                expandedFolder = folderName
                showFolderOverlay(folderName)
            }
        )

        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        val enterAnim = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in).apply { duration = 200 }
        val controller = LayoutAnimationController(enterAnim, 0.05f)
        controller.order = LayoutAnimationController.ORDER_NORMAL
        recyclerView.layoutAnimation = controller

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 200
            moveDuration = 300
            changeDuration = 200
        }

        recyclerView.setOnDragListener { rv, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val ok = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    android.util.Log.d("ArchiveFragment", "DRAG_STARTED ok=$ok")
                    ok
                }
                DragEvent.ACTION_DROP -> {
                    val draggedApp = event.localState as? ArchivedApp
                    android.util.Log.d("ArchiveFragment", "DROP rv hit test. dragged=${draggedApp?.packageId}")
                    if (draggedApp != null && draggedApp.folderName != null) {
                        (rv as RecyclerView).post {
                            val childUnder = rv.findChildViewUnder(event.x, event.y)
                            if (childUnder == null) {
                                try {
                                    viewModel.updateArchivedApp(draggedApp.copy(folderName = null))
                                } catch (t: Throwable) {
                                    android.util.Log.e("ArchiveFragment", "Failed to remove from folder on DROP", t)
                                }
                            } else {
                                android.util.Log.d("ArchiveFragment", "DROP landed on a child view; ignoring RV handler")
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Restoration is handled in adapter drag listeners.
                    true
                }
                else -> true
            }
        }
    }

    private fun setupListeners() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString()
                searchClear.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                if (searchQuery != q) {
                    searchQuery = q
                    updateUI(viewModel.archivedApps.value)
                }
            }
        })
        searchClear.setOnClickListener { searchInput.setText("") }

        btnFindApps.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)?.selectedItemId = R.id.nav_discover
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.archivedApps.collect { apps ->
                        updateUI(apps)

                        if (expandedFolder != null && folderOverlay != null) {
                            val folderApps = apps.filter { it.folderName == expandedFolder }
                            if (folderApps.isEmpty()) {
                                expandedFolder = null
                                folderOverlay?.dismiss {}
                                folderOverlay = null
                            } else {
                                folderOverlay?.updateApps(folderApps)
                            }
                        }
                    }
                }
                launch {
                    viewModel.undoDeleteEvent.collect { deletedApp ->
                        Snackbar.make(requireView(), getString(R.string.archive_undo_message, deletedApp.name), Snackbar.LENGTH_LONG)
                            .setAction(R.string.archive_undo_action) {
                                viewModel.restoreArchivedApp(deletedApp)
                            }
                            .show()
                    }
                }
            }
        }
    }

    private fun updateUI(apps: List<ArchivedApp>) {
        val categories = listOf("All") + apps.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().sorted()

        if (categories.size > 1) {
            chipContainerScrollView.visibility = View.VISIBLE
            chipContainer.removeAllViews()
            categories.forEach { category ->
                val chip = Chip(requireContext()).apply {
                    text = category
                    isCheckable = true
                    isChecked = selectedCategory == category
                    setOnClickListener {
                        selectedCategory = category
                        updateUI(viewModel.archivedApps.value)
                    }
                    val margin = (8 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = margin
                    }
                }
                chipContainer.addView(chip)
            }
        } else {
            chipContainerScrollView.visibility = View.GONE
        }

        val filteredApps = apps.filter { app ->
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

        val standalones = filteredApps.filter { it.folderName == null }.map { ArchivedItem.App(it) }
        val folders = filteredApps.filter { it.folderName != null }
            .groupBy { it.folderName!! }
            .map { (name, fapps) -> ArchivedItem.Folder(name, fapps) }
        val groupedItems = (standalones + folders).sortedBy {
            when (it) {
                is ArchivedItem.App -> it.app.name.lowercase()
                is ArchivedItem.Folder -> it.name.lowercase()
            }
        }

        if (groupedItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateContainer.visibility = View.VISIBLE
            if (apps.isEmpty()) {
                tvEmptyMessage.text = getString(R.string.archive_empty_message)
                btnFindApps.visibility = View.VISIBLE
            } else {
                tvEmptyMessage.text = getString(R.string.archive_empty_filtered)
                btnFindApps.visibility = View.GONE
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyStateContainer.visibility = View.GONE
            adapter.submitList(groupedItems)
        }
    }

    private fun handleAppDropOnApp(draggedApp: ArchivedApp, targetApp: ArchivedApp) {
        android.util.Log.d("ArchiveFragment", "handleAppDropOnApp dragged=${draggedApp.packageId} -> target=${targetApp.packageId}")
        if (draggedApp.packageId == targetApp.packageId) return
        val apps = viewModel.archivedApps.value
        val latestDragged = apps.firstOrNull { it.packageId == draggedApp.packageId } ?: return
        val latestTarget = apps.firstOrNull { it.packageId == targetApp.packageId } ?: return

        if (latestDragged.folderName != null && latestDragged.folderName == latestTarget.folderName) return

        val defaultName = nextDefaultFolderName(apps)
        try {
            viewModel.updateArchivedApp(latestDragged.copy(folderName = defaultName))
            viewModel.updateArchivedApp(latestTarget.copy(folderName = defaultName))
        } catch (t: Throwable) {
            android.util.Log.e("ArchiveFragment", "Failed to assign folder $defaultName", t)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            expandedFolder = defaultName
            runCatching { showFolderOverlay(defaultName) }
                .onFailure { android.util.Log.e("ArchiveFragment", "showFolderOverlay failed", it) }
        }
    }

    private fun nextDefaultFolderName(existingApps: List<ArchivedApp>): String {
        val usedNames = existingApps.mapNotNull { it.folderName }.toSet()
        val base = "New Folder"
        if (base !in usedNames) return base
        var index = 2
        while (true) {
            val candidate = "$base $index"
            if (candidate !in usedNames) return candidate
            index++
        }
    }

    private fun showFolderOverlay(folderName: String) {
        val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        val overlay = FolderExpandOverlay(requireContext(), root)
        folderOverlay = overlay

        val apps = viewModel.archivedApps.value.filter { it.folderName == folderName }
        if (apps.isEmpty()) return

        overlay.show(
            folderName = folderName,
            folderApps = apps,
            anchorView = null,
            onAppClick = { pkg ->
                expandedFolder = null
                openNativeAppDetails(pkg)
            },
            onFolderRenamed = { newName ->
                viewModel.archivedApps.value.filter { it.folderName == folderName }.forEach { app ->
                    viewModel.updateArchivedApp(app.copy(folderName = newName))
                }
            },
            onDragStartFromFolder = {
                expandedFolder = null
            },
            onDismiss = {
                if (expandedFolder == folderName) expandedFolder = null
                folderOverlay = null
            }
        )
    }

    private fun openNativeAppDetails(packageId: String) {
        val app = viewModel.archivedApps.value.find { it.packageId == packageId } ?: return
        NativeAppDetailsDialog(
            context = requireContext(),
            app = app,
            onNotesUpdated = { notes ->
                viewModel.updateArchivedApp(app.copy(notes = notes))
            },
            onDelete = {
                viewModel.deleteArchivedApp(app)
            },
            onDismissRequest = {}
        ).show()
    }
}
