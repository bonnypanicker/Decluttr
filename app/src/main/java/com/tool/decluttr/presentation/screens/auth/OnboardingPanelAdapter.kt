package com.tool.decluttr.presentation.screens.auth

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.tool.decluttr.R

data class OnboardingPanel(
    val iconRes: Int,
    val accentColor: Int,
    val tag: String,
    val title: String,
    val body: String,
    val supportText: String? = null
)

class OnboardingPanelAdapter(
    private val panels: List<OnboardingPanel>
) : RecyclerView.Adapter<OnboardingPanelAdapter.PanelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PanelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_panel, parent, false)
        return PanelViewHolder(view)
    }

    override fun onBindViewHolder(holder: PanelViewHolder, position: Int) {
        holder.bind(panels[position])
    }

    override fun getItemCount(): Int = panels.size

    class PanelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tag = itemView.findViewById<TextView>(R.id.panel_tag)
        private val icon = itemView.findViewById<ImageView>(R.id.panel_icon)
        private val title = itemView.findViewById<TextView>(R.id.panel_title)
        private val body = itemView.findViewById<TextView>(R.id.panel_body)
        private val support = itemView.findViewById<TextView>(R.id.panel_support)
        private val accentBar = itemView.findViewById<View>(R.id.panel_accent_bar)

        fun bind(panel: OnboardingPanel) {
            tag.text = panel.tag
            icon.setImageResource(panel.iconRes)
            icon.imageTintList = ColorStateList.valueOf(panel.accentColor)
            tag.setTextColor(panel.accentColor)
            accentBar.backgroundTintList = ColorStateList.valueOf(panel.accentColor)
            title.text = panel.title
            body.text = panel.body
            support.text = panel.supportText
            support.isVisible = !panel.supportText.isNullOrBlank()
        }
    }
}
