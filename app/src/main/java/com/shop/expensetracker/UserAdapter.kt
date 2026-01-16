package com.shop.expensetracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class UserAdapter(
    private val users: List<User>,
    private val onClick: (User) -> Unit,
    private val onLongClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvBalance: TextView = view.findViewById(R.id.tvUserBalance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.name

        // ✅ FORMATTING: Indian Rupee System (e.g. ₹ 1,20,500)
        val formatter = NumberFormat.getInstance(Locale("en", "IN"))
        holder.tvBalance.text = "₹ " + formatter.format(user.balance)

        // ✅ COLOR LOGIC: Red if negative, Dark Blue if positive
        if (user.balance < 0) {
            holder.tvBalance.setTextColor(Color.parseColor("#FF5252")) // Red
        } else {
            holder.tvBalance.setTextColor(Color.parseColor("#0F172A")) // Dark Blue/Black
        }

        // Normal Click (View Details)
        holder.itemView.setOnClickListener {
            onClick(user)
        }

        // Long Click (Delete User)
        holder.itemView.setOnLongClickListener {
            onLongClick(user)
            true // Return true to indicate the click was handled
        }
    }

    override fun getItemCount(): Int = users.size
}