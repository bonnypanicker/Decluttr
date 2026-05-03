package com.tool.decluttr.presentation.screens.wishlist

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.color.MaterialColors
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.WishlistApp

class WishlistAdapter(
    private val onDeleteClick: (WishlistApp) -> Unit,
    private val onPlayStoreClick: (WishlistApp) -> Unit,
    private val onNotesClick: (WishlistApp) -> Unit,
    private val onUpgradeClick: () -> Unit
) : ListAdapter<WishlistApp, WishlistAdapter.ViewHolder>(WishlistDiffCallback()) {

    private var isPremium: Boolean = false

    fun setPremium(premium: Boolean) {
        if (isPremium != premium) {
            isPremium = premium
            notifyItemRangeChanged(0, itemCount, PAYLOAD_PREMIUM_CHANGED)
        }
    }

    companion object {
        private const val PAYLOAD_PREMIUM_CHANGED = "premium_changed"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wishlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PREMIUM_CHANGED)) {
            holder.bindNoteButton(getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.iv_icon)
        private val nameView: TextView = itemView.findViewById(R.id.tv_name)
        private val descView: TextView = itemView.findViewById(R.id.tv_desc)
        private val notesView: TextView = itemView.findViewById(R.id.tv_notes)
        private val btnNotes: ImageButton = itemView.findViewById(R.id.btn_notes)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(app: WishlistApp) {
            nameView.text = app.name
            descView.text = app.description.ifBlank { "No description available" }

            if (app.notes.isNotBlank()) {
                notesView.text = app.notes
                notesView.visibility = View.VISIBLE
            } else {
                notesView.visibility = View.GONE
            }

            if (app.iconUrl.isNotBlank()) {
                iconView.load(app.iconUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(radius = 24f))
                    placeholder(R.drawable.ic_app_placeholder)
                    error(R.drawable.ic_app_placeholder)
                }
            } else {
                // Attempt to load from local package manager
                runCatching {
                    val pm = itemView.context.packageManager
                    iconView.setImageDrawable(pm.getApplicationIcon(app.packageId))
                }.onFailure {
                    iconView.setImageResource(R.drawable.ic_app_placeholder)
                }
            }

            bindNoteButton(app)

            btnDelete.setOnClickListener {
                onDeleteClick(app)
            }

            itemView.setOnClickListener {
                onPlayStoreClick(app)
            }
        }

        fun bindNoteButton(app: WishlistApp) {
            val tint = MaterialColors.getColor(
                itemView,
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )
            btnNotes.setColorFilter(tint)
            if (isPremium) {
                btnNotes.setImageResource(R.drawable.ic_edit_note)
                btnNotes.alpha = 1f
                btnNotes.contentDescription = if (app.notes.isBlank()) "Add note" else "Edit note"
                btnNotes.setOnClickListener { onNotesClick(app) }
            } else {
                btnNotes.setImageResource(R.drawable.ic_lock)
                btnNotes.alpha = 0.45f
                btnNotes.contentDescription = "Notes — Premium feature"
                btnNotes.setOnClickListener { onUpgradeClick() }
            }
        }
    }

    class WishlistDiffCallback : DiffUtil.ItemCallback<WishlistApp>() {
        override fun areItemsTheSame(oldItem: WishlistApp, newItem: WishlistApp): Boolean {
            return oldItem.packageId == newItem.packageId
        }

        override fun areContentsTheSame(oldItem: WishlistApp, newItem: WishlistApp): Boolean {
            return oldItem == newItem
        }
    }
}
