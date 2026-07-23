package com.aiah.tracker

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class TrackItem(
    val device: String,
    val date: String,
    val points: Int = 0
)

class TracksAdapter(
    private val context: Context,
    private var items: List<TrackItem> = emptyList(),
    private val onClick: (TrackItem) -> Unit
) : RecyclerView.Adapter<TracksAdapter.ViewHolder>() {

    fun update(items: List<TrackItem>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = "${item.device} — ${item.date}"
        holder.subtitle.text = if (item.points > 0) "${item.points} точек" else "Нажмите для загрузки"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
    }
}