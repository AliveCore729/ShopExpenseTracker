package com.shop.expensetracker

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class UserTransaction(
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
                title = if (type == "IN") "Shop IN" else "Shop OUT",
                amount = amount,
                isCredit = type == "OUT",
                timestamp = doc.getTimestamp("timestamp")
            )
        }

        // 🔁 User ↔ User transfer (WITH NAMES)
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

            return UserTransaction(
                title = "Personal Expense • $reason",
                amount = amount,
                isCredit = false,
                timestamp = doc.getTimestamp("timestamp")
            )
        }
    }
}
