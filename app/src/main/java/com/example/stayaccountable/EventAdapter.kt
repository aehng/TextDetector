// EventAdapter.kt
// Adapter for displaying event logs in a RecyclerView in StayAccountable.

package com.example.stayaccountable

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt

// EventAdapter: Binds event data to RecyclerView items
class EventAdapter(private val events: List<Event>) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {
    // ViewHolder for each event item
    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.item_root)
        val desc: TextView = itemView.findViewById(R.id.event_description)
        val sev: TextView = itemView.findViewById(R.id.event_severity)
        val date: TextView = itemView.findViewById(R.id.event_date)
        // val screenshot: ImageView = itemView.findViewById(R.id.event_screenshot) // Removed screenshot ImageView
    }

    // Inflates the item layout for each event
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    // Binds event data to the ViewHolder
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.desc.text = event.description
        holder.sev.text = event.severity.toString()
        holder.date.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))
        Log.d("EventAdapter", "Binding event: ${event.description}, severity: ${event.severity}")
        // Highlight background and text color based on severity
        if (event.severity > 7) {
            holder.root.setBackgroundColor("#FF9800".toColorInt()) // orange
            holder.desc.setTextColor(Color.BLACK)
            holder.sev.setTextColor(Color.BLACK)
            holder.date.setTextColor(Color.DKGRAY)
        } else if (event.severity > 4) {
            holder.root.setBackgroundColor("#fc6100".toColorInt()) // lighter orange
            holder.desc.setTextColor(Color.BLACK)
            holder.sev.setTextColor(Color.BLACK)
            holder.date.setTextColor(Color.DKGRAY)
        } else {
            holder.root.setBackgroundColor(Color.TRANSPARENT)
            holder.desc.setTextColor(Color.WHITE)
            holder.sev.setTextColor(Color.WHITE)
            holder.date.setTextColor(Color.DKGRAY)
        }
        // Remove screenshot logic
        // holder.screenshot.visibility = View.GONE
    }

    // Returns the number of events
    override fun getItemCount() = events.size
}
