package com.shop.expensetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter(
    private val users: List<User>,
    private val onClick: (User) -> Unit,
    private val onLongClick: (User) -> Unit // ✅ Added this parameter
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
        holder.tvBalance.text = "₹${user.balance}"

        // Normal Click (View Details)
        holder.itemView.setOnClickListener {
            onClick(user)
        }

        // ✅ Long Click (Delete User)
        holder.itemView.setOnLongClickListener {
            onLongClick(user)
            true // Return true to indicate the click was handled
        }
    }

    override fun getItemCount(): Int = users.size
}