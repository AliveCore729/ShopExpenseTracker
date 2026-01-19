package com.shop.expensetracker

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class UserTransaction(
    val docId: String,        // ✅ ADDED: ID to find the document
    val collection: String,   // ✅ ADDED: Which collection (shop/personal/transfer)
    val title: String,
    val amount: Long,
    val isCredit: Boolean,
    val timestamp: Timestamp?
) {
    companion object {

        // 🏪 Shop IN / OUT
        fun fromShopTransaction(doc: DocumentSnapshot): UserTransaction {
            val type = doc.getString("type") ?: "OUT"
            val amount = doc.getLong("amount") ?: 0

            return UserTransaction(
                docId = doc.id,
                collection = "shop_transactions", // ✅ Defined Collection
                title = if (type == "IN") "Shop IN" else "Shop OUT",
                amount = amount,
                isCredit = type == "OUT",
                timestamp = doc.getTimestamp("timestamp")
            )
        }

        // 🔁 User ↔ User transfer
        fun fromTransfer(
            doc: DocumentSnapshot,
            currentUserId: String
        ): UserTransaction {

            val fromId = doc.getString("fromUserId") ?: ""
            val toId = doc.getString("toUserId") ?: ""
            val fromName = doc.getString("fromUserName") ?: "Unknown"
            val toName = doc.getString("toUserName") ?: "Unknown"
            val amount = doc.getLong("amount") ?: 0
            val reason = doc.getString("reason") ?: "Transfer"

            val isCredit = currentUserId == toId

            val title = if (isCredit) {
                "Received from $fromName • $reason"
            } else {
                "Sent to $toName • $reason"
            }

            return UserTransaction(
                docId = doc.id,
                collection = "user_transfers", // ✅ Defined Collection
                title = title,
                amount = amount,
                isCredit = isCredit,
                timestamp = doc.getTimestamp("timestamp")
            )
        }

        // 👤 Personal expense
        fun fromPersonalExpense(doc: DocumentSnapshot): UserTransaction {
            val amount = doc.getLong("amount") ?: 0
            val reason = doc.getString("reason") ?: "Expense"
            val title = doc.getString("title") ?: reason // Use title if available

            return UserTransaction(
                docId = doc.id,
                collection = "personal_transactions", // ✅ Defined Collection
                title = "Personal Expense • $title",
                amount = amount,
                isCredit = false,
                timestamp = doc.getTimestamp("timestamp")
            )
        }
    }
}