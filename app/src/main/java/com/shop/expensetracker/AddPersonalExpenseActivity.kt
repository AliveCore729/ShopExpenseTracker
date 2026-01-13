package com.shop.expensetracker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddPersonalExpenseActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_personal_expense)

        val etAmount = findViewById<EditText>(R.id.etAmount)
        val etReason = findViewById<EditText>(R.id.etReason)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString()
            val reason = etReason.text.toString()

            if (amountText.isEmpty() || reason.isEmpty()) {
                toast("Fill all fields")
                return@setOnClickListener
            }

            savePersonalExpense(amountText.toLong(), reason)
        }
    }

    // 🔥 CORE PERSONAL EXPENSE LOGIC
    private fun savePersonalExpense(amount: Long, reason: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid

        val userRef = db.collection("users").document(userId)
        val expenseRef = db.collection("personal_transactions").document()

        db.runTransaction { transaction ->
            val userSnap = transaction.get(userRef)
            val balance = userSnap.getLong("balance") ?: 0

            if (balance < amount) {
                throw Exception("Insufficient balance")
            }

            transaction.update(userRef, "balance", balance - amount)

            val data = hashMapOf(
                "userId" to userId,
                "amount" to amount,
                "reason" to reason,
                "timestamp" to FieldValue.serverTimestamp()
            )

            transaction.set(expenseRef, data)
        }.addOnSuccessListener {
            toast("Expense added")
            finish()
        }.addOnFailureListener {
            toast(it.message ?: "Failed")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
