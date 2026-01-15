package com.shop.expensetracker

import android.os.Bundle
import android.transition.TransitionManager
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddPersonalExpenseActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // UI Elements
    private lateinit var tvAmount: TextView // Changed from EditText
    private lateinit var etReason: EditText
    private lateinit var btnSave: Button
    private lateinit var gridLayoutKeypad: GridLayout
    private lateinit var btnBackspace: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var cardReason: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_personal_expense)

        // 1. Initialize Views
        rootLayout = findViewById(R.id.rootLayout)
        tvAmount = findViewById(R.id.tvAmount) // Note: This matches the new ID in XML
        etReason = findViewById(R.id.etReason)
        btnSave = findViewById(R.id.btnSave)
        gridLayoutKeypad = findViewById(R.id.gridLayoutKeypad)
        btnBackspace = findViewById(R.id.btnBackspace)
        btnBack = findViewById(R.id.btnBack)
        cardReason = findViewById(R.id.cardReason)

        // 2. Setup Back Button
        btnBack.setOnClickListener { finish() }

        // 3. Setup Custom Keypad Logic
        setupKeypad()

        // 4. Setup Reason Box Animation
        setupReasonAnimation()

        // 5. Save Button Logic
        btnSave.setOnClickListener {
            // Remove the "₹" symbol before parsing
            val rawAmount = tvAmount.text.toString().replace("₹", "")
            val amount = rawAmount.toLongOrNull() ?: 0
            val reason = etReason.text.toString()

            if (amount <= 0 || reason.isEmpty()) {
                toast("Please enter a valid amount and reason")
                return@setOnClickListener
            }

            savePersonalExpense(amount, reason)
        }
    }

    private fun setupKeypad() {
        // Iterate through all buttons in the Grid Layout
        for (i in 0 until gridLayoutKeypad.childCount) {
            val view = gridLayoutKeypad.getChildAt(i)
            if (view is Button) {
                view.setOnClickListener {
                    val digit = view.text.toString()
                    val currentText = tvAmount.text.toString().replace("₹", "")

                    if (currentText == "0") {
                        tvAmount.text = "₹$digit"
                    } else {
                        tvAmount.text = "₹$currentText$digit"
                    }
                }
            }
        }

        // Backspace Logic
        btnBackspace.setOnClickListener {
            val currentText = tvAmount.text.toString().replace("₹", "")
            if (currentText.isNotEmpty()) {
                val newText = currentText.dropLast(1)
                tvAmount.text = if (newText.isEmpty()) "₹0" else "₹$newText"
            }
        }
    }

    private fun setupReasonAnimation() {
        etReason.setOnFocusChangeListener { _, hasFocus ->
            // Animate layout changes
            TransitionManager.beginDelayedTransition(rootLayout)
            val params = cardReason.layoutParams

            if (hasFocus) {
                // Expand
                params.height = (120 * resources.displayMetrics.density).toInt() // ~120dp
                etReason.gravity = Gravity.TOP or Gravity.START
            } else {
                // Collapse
                params.height = (56 * resources.displayMetrics.density).toInt() // 56dp
                etReason.gravity = Gravity.CENTER_VERTICAL
            }
            cardReason.layoutParams = params
        }
    }

    // 🔥 CORE PERSONAL EXPENSE LOGIC (Unchanged)
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