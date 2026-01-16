package com.shop.expensetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat // ✅ Added Import
import java.text.SimpleDateFormat
import java.util.*

class ShopTransactionAdapter(
    private val list: List<ShopTransaction>
) : RecyclerView.Adapter<ShopTransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvUser: TextView = view.findViewById(R.id.tvUser)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shop_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val txn = list[position]

        // ✅ 1. FORMATTING: Indian Rupee System
        val formatter = NumberFormat.getInstance(Locale("en", "IN"))
        val formattedAmount = formatter.format(txn.amount)

        // Set text values with formatted amount
        holder.tvAmount.text = "₹$formattedAmount"
        holder.tvType.text = txn.type
        holder.tvUser.text = "By: ${txn.userName}"

        // Date formatting
        txn.timestamp?.let {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            holder.tvDate.text = sdf.format(it.toDate())
        }

        // ✅ COLOR LOGIC
        if (txn.type == "IN") {
            // GREEN for IN
            val greenColor = android.graphics.Color.parseColor("#2E7D32")
            holder.tvAmount.setTextColor(greenColor)
            holder.tvType.setTextColor(greenColor)
        } else {
            // RED for OUT (or any other type)
            val redColor = android.graphics.Color.parseColor("#D32F2F")
            holder.tvAmount.setTextColor(redColor)
            holder.tvType.setTextColor(redColor)
        }
    }

    override fun getItemCount(): Int = list.size
}