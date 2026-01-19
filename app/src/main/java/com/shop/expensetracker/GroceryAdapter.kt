package com.shop.expensetracker

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class GroceryAdapter(
    private val items: List<GroceryItem>,
    private val onCheckClick: (GroceryItem) -> Unit
) : RecyclerView.Adapter<GroceryAdapter.GroceryViewHolder>() {

    class GroceryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvGroceryName)
        val tvAddedBy: TextView = view.findViewById(R.id.tvAddedBy)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val ivDone: ImageView = view.findViewById(R.id.ivDone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroceryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grocery, parent, false)
        return GroceryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroceryViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvAddedBy.text = "Added by ${item.addedBy}"

        // ✅ UPDATED: Format Date & Time (e.g., "19 Jan, 07:30 PM")
        if (item.timestamp != null) {
            val date = item.timestamp.toDate()
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            holder.tvDate.text = sdf.format(date)
            holder.tvDate.visibility = View.VISIBLE
        } else {
            holder.tvDate.visibility = View.GONE
        }

        // Set initial state
        updateIconState(holder, item.isDone)

        holder.ivDone.setOnClickListener {
            val newStatus = !item.isDone
            updateIconState(holder, newStatus)
            onCheckClick(item)
        }
    }

    private fun updateIconState(holder: GroceryViewHolder, isDone: Boolean) {
        if (isDone) {
            holder.ivDone.setImageResource(R.drawable.shape_circle_checked)
            holder.tvName.paintFlags = holder.tvName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvName.alpha = 0.5f
        } else {
            holder.ivDone.setImageResource(R.drawable.shape_circle_unchecked)
            holder.tvName.paintFlags = holder.tvName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvName.alpha = 1.0f
        }
    }

    override fun getItemCount() = items.size
}