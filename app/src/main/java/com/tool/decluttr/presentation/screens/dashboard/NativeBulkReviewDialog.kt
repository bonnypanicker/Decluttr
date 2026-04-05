package com.tool.decluttr.presentation.screens.dashboard

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.ArchivedApp
import com.tool.decluttr.presentation.util.AppIconModel

/**
 * Native bulk review dialog shown AFTER apps have been archived & uninstalled.
 * Lets the user swipe through each archived app and add a "Why I kept this" note.
 */
class NativeBulkReviewDialog(
    context: Context,
    private val archivedApps: List<ArchivedApp>,
    private val onComplete: (Map<String, String>) -> Unit,
    private val onCancel: () -> Unit
) {
    private val dialog: Dialog = DashboardModalDialogWrapper(
        context = context,
        contentLayoutRes = R.layout.dialog_bulk_review,
        dismissOnOutside = false,
        maxWidthDp = 680,
        horizontalMarginDp = 12
    ).build()
    private val notesMap = mutableMapOf<String, String>()

    init {
        dialog.setOnCancelListener { onCancel() }

        val viewPager = dialog.findViewById<ViewPager2>(R.id.view_pager)
        val dotsContainer = dialog.findViewById<LinearLayout>(R.id.dots_container)
        val btnSkipAll = dialog.findViewById<MaterialButton>(R.id.btn_skip_all)
        val btnNext = dialog.findViewById<MaterialButton>(R.id.btn_next)
        val btnDone = dialog.findViewById<MaterialButton>(R.id.btn_done)
        val subtitle = dialog.findViewById<TextView>(R.id.tv_bulk_review_subtitle)

        subtitle.text = context.getString(
            R.string.bulk_review_subtitle_with_count,
            archivedApps.size
        )

        val adapter = PagerAdapter()
        viewPager.adapter = adapter

        // Setup Dots
        val dotSize = (8 * context.resources.displayMetrics.density).toInt()
        val dotMargin = (4 * context.resources.displayMetrics.density).toInt()
        val dots = mutableListOf<View>()
        for (i in archivedApps.indices) {
            val dot = View(context).apply {
                val lp = LinearLayout.LayoutParams(dotSize, dotSize)
                lp.setMargins(dotMargin, 0, dotMargin, 0)
                layoutParams = lp
                setBackgroundResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
            }
            dots.add(dot)
            dotsContainer.addView(dot)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dots.forEachIndexed { index, dot ->
                    dot.setBackgroundResource(
                        if (index == position) R.drawable.dot_active else R.drawable.dot_inactive
                    )
                }
                btnNext.visibility = if (position < archivedApps.size - 1) View.VISIBLE else View.GONE
                btnDone.visibility = if (position == archivedApps.size - 1) View.VISIBLE else View.GONE
            }
        })

        // Initial button state
        if (archivedApps.size <= 1) {
            btnNext.visibility = View.GONE
            btnDone.visibility = View.VISIBLE
        } else {
            btnNext.visibility = View.VISIBLE
            btnDone.visibility = View.GONE
        }

        btnSkipAll.setOnClickListener {
            dialog.dismiss()
            onComplete(emptyMap())
        }

        btnNext.setOnClickListener {
            saveCurrentPage(viewPager.currentItem)
            val next = viewPager.currentItem + 1
            if (next < archivedApps.size) {
                viewPager.setCurrentItem(next, true)
            }
        }

        btnDone.setOnClickListener {
            saveCurrentPage(viewPager.currentItem)
            dialog.dismiss()
            onComplete(notesMap.filterValues { it.isNotBlank() })
        }
    }

    private fun saveCurrentPage(position: Int) {
        val recyclerView = dialog.findViewById<ViewPager2>(R.id.view_pager)
            .getChildAt(0) as? RecyclerView ?: return
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        val editText = viewHolder?.itemView?.findViewById<TextInputEditText>(R.id.notes_input)
        val text = editText?.text?.toString()?.trim() ?: ""
        if (text.isNotBlank()) {
            notesMap[archivedApps[position].packageId] = text
        }
    }

    fun show() = dialog.show()
    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PagerAdapter.PageViewHolder>() {
        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val category: TextView = view.findViewById(R.id.app_category)
            val pageCount: TextView = view.findViewById(R.id.page_count)
            val notesInput: TextInputEditText = view.findViewById(R.id.notes_input)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bulk_review_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val app = archivedApps[position]
            holder.name.text = app.name
            holder.category.text = app.category ?: "Uncategorized"
            holder.pageCount.text = "${position + 1}/${archivedApps.size}"
            holder.icon.load(AppIconModel(app.packageId)) {
                memoryCacheKey(app.packageId)
                crossfade(false)
            }
            // Restore saved notes if user navigated back
            holder.notesInput.setText(notesMap[app.packageId] ?: app.notes ?: "")
        }

        override fun getItemCount() = archivedApps.size
    }
}
