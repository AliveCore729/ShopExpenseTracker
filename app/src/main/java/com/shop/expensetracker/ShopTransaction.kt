package com.shop.expensetracker

import com.google.firebase.Timestamp

data class ShopTransaction(
    var docId: String = "", // ✅ ADDED: To store the document ID
    var userId: String = "",
    var userName: String = "",
    var type: String = "",
    var amount: Long = 0,
    var timestamp: Timestamp? = null
)