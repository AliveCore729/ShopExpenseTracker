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
    private lateinit var btnIn: Button
    private lateinit var btnOut: Button
    private lateinit var btnMainTransactions: Button
    private lateinit var btnLogout: Button
    private lateinit var btnAddPersonalExpense: Button

    // ✅ New Header Views
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
        btnIn = findViewById(R.id.btnIn)
        btnOut = findViewById(R.id.btnOut)
        btnMainTransactions = findViewById(R.id.btnMainTransactions)
        btnLogout = findViewById(R.id.btnLogout)
        btnAddPersonalExpense = findViewById(R.id.btnAddPersonalExpense)

        // ✅ Initialize Header Views
        tvHeaderName = findViewById(R.id.tvHeaderName)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)

        // ✅ FETCH CURRENT USER NAME FOR HEADER
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Get the name (default to "User" if empty)
                        val fullName = document.getString("name") ?: "User"

                        // Update the Greeting
                        tvHeaderName.text = "Hi, $fullName!"

                        // Update the Avatar Circle (First Letter)
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

        // Setup Layout Manager (Grid - 2 Columns)
        rvUsers.layoutManager = GridLayoutManager(this, 2)
        rvUsers.adapter = adapter

        // Load Data
        loadShopBalance()
        loadUsers()

        // Button Actions
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

    // 🔹 UPDATED: Custom Dialog Implementation
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

        if (type == "IN") {
            tvTitle.text = "Money IN"
            tvSubtitle.text = "Add money to shop balance"
            btnConfirm.text = "Add Money"
            btnConfirm.setBackgroundColor(Color.parseColor("#2E7D32")) // Green
        } else {
            tvTitle.text = "Money OUT"
            tvSubtitle.text = "Take money from shop balance"
            btnConfirm.text = "Withdraw"
            btnConfirm.setBackgroundColor(Color.parseColor("#D32F2F")) // Red
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

    // 🔥 SHOP IN / OUT LOGIC
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

            val newShopBalance: Long
            val newUserBalance: Long

            if (type == "IN") {
                if (userBalance < amount) {
                    throw Exception("You have insufficient balance")
                }
                newShopBalance = shopBalance + amount
                newUserBalance = userBalance - amount
            } else {
                if (shopBalance < amount) {
                    throw Exception("Shop has insufficient balance")
                }
                newShopBalance = shopBalance - amount
                newUserBalance = userBalance + amount
            }

            transaction.update(shopRef, "balance", newShopBalance)
            transaction.update(userRef, "balance", newUserBalance)

            val data = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "type" to type,
                "amount" to amount,
                "timestamp" to FieldValue.serverTimestamp()
            )

            transaction.set(txnRef, data)
        }.addOnSuccessListener {
            toast("$type successful")
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