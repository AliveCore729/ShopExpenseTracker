package com.shop.expensetracker

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GroceryAdapter(
    private val items: List<GroceryItem>,
    private val onCheckClick: (GroceryItem) -> Unit
) : RecyclerView.Adapter<GroceryAdapter.GroceryViewHolder>() {

    class GroceryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvGroceryName)
        val tvAddedBy: TextView = view.findViewById(R.id.tvAddedBy)
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

        // Set initial state
        updateIconState(holder, item.isDone)

        holder.ivDone.setOnClickListener {
            // 1. Instant Visual Feedback (User sees change immediately)
            val newStatus = !item.isDone
            updateIconState(holder, newStatus)

            // 2. Send to Database
            onCheckClick(item)
        }
    }

    // Helper function to swap colors
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