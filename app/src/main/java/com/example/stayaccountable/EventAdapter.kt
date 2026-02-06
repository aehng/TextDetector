// Generated with AI
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

/**
 * RecyclerView adapter for displaying a list of [Event] objects.
 *
 * This adapter takes a list of events and binds them to the `item_event` layout
 * for display in a RecyclerView. It also includes logic to change the item's
 * appearance based on the event's severity.
 *
 * @param events The list of events to be displayed.
 */
class EventAdapter(private val events: List<Event>) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    /**
     * ViewHolder for an individual event item.
     * It holds references to the UI components within the item layout.
     */
    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.item_root)
        val desc: TextView = itemView.findViewById(R.id.event_description)
        val sev: TextView = itemView.findViewById(R.id.event_severity)
        val date: TextView = itemView.findViewById(R.id.event_date)
        // val screenshot: ImageView = itemView.findViewById(R.id.event_screenshot) // Screenshot functionality was removed
    }

    /**
     * Called when RecyclerView needs a new [EventViewHolder] to represent an item.
     * @return A new EventViewHolder that holds the View for an event item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the [EventViewHolder] to reflect the event at the given position.
     * @param holder The ViewHolder which should be updated.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        // Set the event data to the corresponding views
        holder.desc.text = event.description
        holder.sev.text = event.severity.toString()
        // Format the timestamp into a readable date-time string
        holder.date.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))

        Log.d("EventAdapter", "Binding event: ${event.description}, severity: ${event.severity}")

        // Dynamically change the background and text color based on the event severity
        when {
            event.severity > 7 -> {
                holder.root.setBackgroundColor("#FF9800".toColorInt()) // High severity: Orange background
                holder.desc.setTextColor(Color.BLACK)
                holder.sev.setTextColor(Color.BLACK)
                holder.date.setTextColor(Color.DKGRAY)
            }
            event.severity > 4 -> {
                holder.root.setBackgroundColor("#fc6100".toColorInt()) // Medium severity: Lighter Orange background
                holder.desc.setTextColor(Color.BLACK)
                holder.sev.setTextColor(Color.BLACK)
                holder.date.setTextColor(Color.DKGRAY)
            }
            else -> {
                // Low severity or default: Transparent background with light text
                holder.root.setBackgroundColor(Color.TRANSPARENT)
                holder.desc.setTextColor(Color.WHITE)
                holder.sev.setTextColor(Color.WHITE)
                holder.date.setTextColor(Color.DKGRAY)
            }
        }

        // Screenshot-related UI logic has been removed.
        // holder.screenshot.visibility = View.GONE
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of events.
     */
    override fun getItemCount() = events.size
}
