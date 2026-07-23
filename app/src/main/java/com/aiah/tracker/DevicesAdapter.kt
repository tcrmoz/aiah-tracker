package com.aiah.tracker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DeviceItem(val name: String)

class DevicesAdapter(
    private val context: Context,
    private var items: List<DeviceItem> = emptyList(),
    private val onClick: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {

    fun update(names: List<String>) {
        items = names.map { DeviceItem(it) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(iconForDevice(item.name))
        holder.name.text = item.name
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    private fun iconForDevice(name: String): Int {
        val n = name.lowercase()
        return when {
            "car" in n || "pad" in n || "auto" in n -> R.drawable.ic_device_car
            "phone" in n || "redmi" in n || "note" in n || "pixel" in n || "samsung" in n || "iphone" in n ->
                R.drawable.ic_device_phone
            else -> R.drawable.ic_device_generic
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.deviceIcon)
        val name: TextView = view.findViewById(R.id.deviceName)
    }
}