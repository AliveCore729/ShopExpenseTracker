package com.shop.expensetracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class UserTransactionAdapter(
    private val list: List<UserTransaction>
) : RecyclerView.Adapter<UserTransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val txn = list[position]

        // Title (Sent to / Received from / Personal / Shop)
        holder.tvTitle.text = txn.title

        // Amount formatting
        val sign = if (txn.isCredit) "+" else "-"
        holder.tvAmount.text = "$sign₹${txn.amount}"

        // Color coding
        if (txn.isCredit) {
            // IN / Credit -> GREEN
            holder.tvAmount.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            // OUT / Debit -> RED
            holder.tvAmount.setTextColor(Color.parseColor("#D32F2F"))
        }

        // Timestamp
        if (txn.timestamp != null) {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            holder.tvTime.text = sdf.format(txn.timestamp.toDate())
        } else {
            holder.tvTime.text = ""
        }
    }

    override fun getItemCount(): Int = list.size
}
