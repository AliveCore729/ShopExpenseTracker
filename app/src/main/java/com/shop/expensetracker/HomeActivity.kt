package com.shop.expensetracker

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var btnAddPersonalExpense: Button   // ✅ NEW

    private val userList = mutableListOf<User>()
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        tvShopBalance = findViewById(R.id.tvShopBalance)
        rvUsers = findViewById(R.id.rvUsers)
        btnIn = findViewById(R.id.btnIn)
        btnOut = findViewById(R.id.btnOut)
        btnMainTransactions = findViewById(R.id.btnMainTransactions)
        btnLogout = findViewById(R.id.btnLogout)
        btnAddPersonalExpense = findViewById(R.id.btnAddPersonalExpense) // ✅

        adapter = UserAdapter(userList) { user ->
            val intent = Intent(this, UserDetailActivity::class.java)
            intent.putExtra("userId", user.id)
            intent.putExtra("userName", user.name)
            startActivity(intent)
        }

        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = adapter

        loadShopBalance()
        loadUsers()

        btnIn.setOnClickListener {
            showAmountDialog("IN")
        }

        btnOut.setOnClickListener {
            showAmountDialog("OUT")
        }

        btnMainTransactions.setOnClickListener {
            startActivity(Intent(this, MainTransactionsActivity::class.java))
        }

        // ✅ ADD PERSONAL EXPENSE
        btnAddPersonalExpense.setOnClickListener {
            startActivity(
                Intent(this, AddPersonalExpenseActivity::class.java)
            )
        }

        // ✅ LOGOUT FIXED
        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // 🔹 Amount input dialog
    private fun showAmountDialog(type: String) {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter amount"

        AlertDialog.Builder(this)
            .setTitle("$type Money")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                val amountText = input.text.toString()
                if (amountText.isEmpty()) {
                    toast("Enter amount")
                    return@setPositiveButton
                }
                processInOut(type, amountText.toLong())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 🔥 SHOP IN / OUT
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
