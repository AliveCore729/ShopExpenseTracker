package com.shop.expensetracker
import com.google.firebase.Timestamp

data class GroceryItem(
    var id: String = "",
    val name: String = "",
    val addedBy: String = "",
    val isDone: Boolean = false,
    val timestamp: Timestamp? = null,
    val completedTimestamp: Timestamp? = null
)