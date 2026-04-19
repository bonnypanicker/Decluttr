package com.tool.decluttr.presentation.screens.auth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tool.decluttr.R

data class OnboardingPanel(
    val iconRes: Int,
    val title: String,
    val body: String
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
        private val icon = itemView.findViewById<ImageView>(R.id.panel_icon)
        private val title = itemView.findViewById<TextView>(R.id.panel_title)
        private val body = itemView.findViewById<TextView>(R.id.panel_body)

        fun bind(panel: OnboardingPanel) {
            icon.setImageResource(panel.iconRes)
            title.text = panel.title
            body.text = panel.body
        }
    }
}
