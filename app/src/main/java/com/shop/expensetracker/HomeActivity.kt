package com.shop.expensetracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvShopBalance: TextView
    private lateinit var rvUsers: androidx.recyclerview.widget.RecyclerView

    // ✅ NEW BUTTON ADDED HERE
    private lateinit var btnAddBalance: Button
    private lateinit var btnIn: Button
    private lateinit var btnOut: Button
    private lateinit var btnMainTransactions: Button
    private lateinit var btnLogout: Button
    private lateinit var btnAddPersonalExpense: Button

    private lateinit var tvHeaderName: TextView
    private lateinit var tvAvatarInitial: TextView

    private val userList = mutableListOf<User>()
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Initialize Views
        tvShopBalance = findViewById(R.id.tvShopBalance)
        rvUsers = findViewById(R.id.rvUsers)

        // ✅ NEW BUTTON INITIALIZATION
        btnAddBalance = findViewById(R.id.btnAddBalance)

        btnIn = findViewById(R.id.btnIn)
        btnOut = findViewById(R.id.btnOut)
        btnMainTransactions = findViewById(R.id.btnMainTransactions)
        btnLogout = findViewById(R.id.btnLogout)
        btnAddPersonalExpense = findViewById(R.id.btnAddPersonalExpense)

        tvHeaderName = findViewById(R.id.tvHeaderName)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)

        // FETCH USER DETAILS
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val fullName = document.getString("name") ?: "User"
                        tvHeaderName.text = "Hi, $fullName!"
                        if (fullName.isNotEmpty()) {
                            tvAvatarInitial.text = fullName.first().toString().uppercase()
                        }
                    }
                }
        }

        // Setup Adapter
        adapter = UserAdapter(userList) { user ->
            val intent = Intent(this, UserDetailActivity::class.java)
            intent.putExtra("userId", user.id)
            intent.putExtra("userName", user.name)
            startActivity(intent)
        }

        rvUsers.layoutManager = GridLayoutManager(this, 2)
        rvUsers.adapter = adapter

        // Load Data
        loadShopBalance()
        loadUsers()

        // ✅ NEW BUTTON ACTION
        btnAddBalance.setOnClickListener {
            showAmountDialog("DEPOSIT")
        }

        btnIn.setOnClickListener {
            showAmountDialog("IN")
        }

        btnOut.setOnClickListener {
            showAmountDialog("OUT")
        }

        btnMainTransactions.setOnClickListener {
            startActivity(Intent(this, MainTransactionsActivity::class.java))
        }

        btnAddPersonalExpense.setOnClickListener {
            startActivity(Intent(this, AddPersonalExpenseActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // 🔹 UPDATED: Handles IN, OUT, and DEPOSIT
    private fun showAmountDialog(type: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_amount, null)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvDialogSubtitle)
        val etAmount = dialogView.findViewById<EditText>(R.id.etDialogAmount)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

        // Customize UI based on Type
        when (type) {
            "IN" -> {
                tvTitle.text = "Money IN"
                tvSubtitle.text = "Transfer from You -> Shop"
                btnConfirm.text = "Transfer"
                btnConfirm.setBackgroundColor(Color.parseColor("#2E7D32")) // Green
            }
            "OUT" -> {
                tvTitle.text = "Money OUT"
                tvSubtitle.text = "Withdraw from Shop -> You"
                btnConfirm.text = "Withdraw"
                btnConfirm.setBackgroundColor(Color.parseColor("#D32F2F")) // Red
            }
            "DEPOSIT" -> { // ✅ NEW CASE
                tvTitle.text = "Add Balance"
                tvSubtitle.text = "Add external funds to Shop"
                btnConfirm.text = "Add Funds"
                btnConfirm.setBackgroundColor(Color.parseColor("#8200FF")) // Purple
            }
        }

        btnConfirm.setOnClickListener {
            val amountText = etAmount.text.toString()
            if (amountText.isEmpty()) {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processInOut(type, amountText.toLong())
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // 🔥 UPDATED LOGIC FOR TRANSACTIONS
    private fun processInOut(type: String, amount: Long) {
        val firebaseUser = auth.currentUser ?: return
        val userId = firebaseUser.uid

        val shopRef = db.collection("shop").document("main")
        val userRef = db.collection("users").document(userId)
        val txnRef = db.collection("shop_transactions").document()

        db.runTransaction { transaction ->
            val shopSnap = transaction.get(shopRef)
            val userSnap = transaction.get(userRef)

            val shopBalance = shopSnap.getLong("balance") ?: 0
            val userBalance = userSnap.getLong("balance") ?: 0
            val userName = userSnap.getString("name") ?: "Unknown"

            var newShopBalance = shopBalance
            var newUserBalance = userBalance

            // Logic Switch
            when (type) {
                "IN" -> {
                    if (userBalance < amount) {
                        throw Exception("You have insufficient balance")
                    }
                    newShopBalance = shopBalance + amount
                    newUserBalance = userBalance - amount
                }
                "OUT" -> {
                    if (shopBalance < amount) {
                        throw Exception("Shop has insufficient balance")
                    }
                    newShopBalance = shopBalance - amount
                    newUserBalance = userBalance + amount
                }
                "DEPOSIT" -> {
                    // ✅ External Money: Only Shop increases. User stays same.
                    newShopBalance = shopBalance + amount
                }
            }

            // Write Updates
            transaction.update(shopRef, "balance", newShopBalance)

            // Only update user balance if it was IN or OUT
            if (type != "DEPOSIT") {
                transaction.update(userRef, "balance", newUserBalance)
            }

            // Create Transaction Record
            val data = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "type" to type,
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp()
            )

            transaction.set(txnRef, data)
        }.addOnSuccessListener {
            toast("Transaction Successful")
        }.addOnFailureListener {
            toast(it.message ?: "Transaction failed")
        }
    }

    private fun loadShopBalance() {
        db.collection("shop").document("main")
            .addSnapshotListener { snapshot, _ ->
                val balance = snapshot?.getLong("balance") ?: 0
                tvShopBalance.text = "₹ $balance"
            }
    }

    private fun loadUsers() {
        db.collection("users")
            .addSnapshotListener { snapshot, _ ->
                userList.clear()
                snapshot?.forEach {
                    val user = it.toObject(User::class.java)
                    user.id = it.id
                    userList.add(user)
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}