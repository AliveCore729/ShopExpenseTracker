package com.shop.expensetracker

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    // ⚠️ IMPORTANT: REPLACE THIS WITH YOUR REAL GMAIL ADDRESS
    private val BOSS_EMAIL = "joker72096@gmail.com"

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Views to Toggle Visibility
    private lateinit var cvShopBalanceContainer: View
    private lateinit var layoutUsersHeader: View
    private lateinit var rvUsers: androidx.recyclerview.widget.RecyclerView
    private lateinit var tvShopBalance: TextView
    private lateinit var tvHeaderName: TextView
    private lateinit var tvAvatarInitial: TextView

    // Buttons
    private lateinit var btnAddBalance: View
    private lateinit var btnIn: View
    private lateinit var btnOut: View
    private lateinit var btnMainTransactions: View
    private lateinit var btnLogout: View
    private lateinit var btnAddPersonalExpense: View
    private lateinit var btnWhitelistUser: View
    private lateinit var btnGrocery: View // ✅ New Grocery Button

    private val userList = mutableListOf<User>()
    private lateinit var adapter: UserAdapter

    // Admin Flag
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1. Initialize Views
        cvShopBalanceContainer = findViewById(R.id.cvShopBalanceContainer)
        layoutUsersHeader = findViewById(R.id.layoutUsersHeader)
        rvUsers = findViewById(R.id.rvUsers)
        tvShopBalance = findViewById(R.id.tvShopBalance)
        tvHeaderName = findViewById(R.id.tvHeaderName)
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)

        // Buttons
        btnAddBalance = findViewById(R.id.btnAddBalance)
        btnIn = findViewById(R.id.btnIn)
        btnOut = findViewById(R.id.btnOut)
        btnMainTransactions = findViewById(R.id.btnMainTransactions)
        btnLogout = findViewById(R.id.btnLogout)
        btnAddPersonalExpense = findViewById(R.id.btnAddPersonalExpense)
        btnWhitelistUser = findViewById(R.id.btnWhitelistUser)
        btnGrocery = findViewById(R.id.btnGrocery) // ✅ Initialize

        // 2. Check User Role & Setup UI (Boss vs Staff vs User)
        checkUserRoleAndSetupUI()

        // 3. Setup RecyclerView
        adapter = UserAdapter(userList,
            onClick = { user ->
                // Normal Click: Details
                val intent = Intent(this, UserDetailActivity::class.java)
                intent.putExtra("userId", user.id)
                intent.putExtra("userName", user.name)
                startActivity(intent)
            },
            onLongClick = { user ->
                // Long Click: Delete (Admin Only)
                if (isAdmin) showDeleteUserDialog(user)
            }
        )

        rvUsers.layoutManager = GridLayoutManager(this, 2)
        rvUsers.adapter = adapter

        // 4. Button Actions
        btnAddBalance.setOnClickListener { if (isAdmin) showAmountDialog("DEPOSIT") }
        btnIn.setOnClickListener { if (isAdmin) showAmountDialog("IN") }
        btnOut.setOnClickListener { if (isAdmin) showAmountDialog("OUT") }

        btnWhitelistUser.setOnClickListener { if (isAdmin) showWhitelistDialog() }

        btnMainTransactions.setOnClickListener {
            startActivity(Intent(this, MainTransactionsActivity::class.java))
        }

        btnAddPersonalExpense.setOnClickListener {
            startActivity(Intent(this, AddPersonalExpenseActivity::class.java))
        }

        // ✅ Grocery Button Action
        btnGrocery.setOnClickListener {
            startActivity(Intent(this, GroceryActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // 🔹 Logic to Determine Role
    private fun checkUserRoleAndSetupUI() {
        val email = auth.currentUser?.email ?: ""
        isAdmin = (email == BOSS_EMAIL)

        if (isAdmin) {
            setupAdminView()
            fetchUserDetails()
            loadShopBalance()
            loadUsers()
        } else {
            // Check if user is marked as 'Staff' in whitelist
            db.collection("whitelisted_users").document(email).get()
                .addOnSuccessListener { doc ->
                    val isStaff = doc.getBoolean("isStaff") ?: false

                    if (isStaff) {
                        setupStaffView() // 🔒 HIDE EVERYTHING
                    } else {
                        setupNormalUserView() // Show balance but hide admin buttons
                        fetchUserDetails()
                        loadShopBalance()
                        loadUsers()
                    }
                }
                .addOnFailureListener {
                    // Fallback to normal view if check fails
                    setupNormalUserView()
                    loadShopBalance()
                    loadUsers()
                }
        }
    }

    // 🔹 VIEW MODE: ADMIN (Boss)
    private fun setupAdminView() {
        cvShopBalanceContainer.visibility = View.VISIBLE
        rvUsers.visibility = View.VISIBLE
        layoutUsersHeader.visibility = View.VISIBLE
        btnAddPersonalExpense.visibility = View.VISIBLE
        btnGrocery.visibility = View.VISIBLE
        btnWhitelistUser.visibility = View.VISIBLE

        // Show Admin Buttons inside Card
        (btnAddBalance.parent as View).visibility = View.VISIBLE
        (btnIn.parent as View).visibility = View.VISIBLE
        (btnOut.parent as View).visibility = View.VISIBLE
    }

    // 🔹 VIEW MODE: NORMAL USER
    private fun setupNormalUserView() {
        cvShopBalanceContainer.visibility = View.VISIBLE
        rvUsers.visibility = View.VISIBLE
        layoutUsersHeader.visibility = View.VISIBLE
        btnAddPersonalExpense.visibility = View.VISIBLE
        btnGrocery.visibility = View.VISIBLE

        // Hide Admin Buttons
        btnWhitelistUser.visibility = View.GONE
        (btnAddBalance.parent as View).visibility = View.GONE
        (btnIn.parent as View).visibility = View.GONE
        (btnOut.parent as View).visibility = View.GONE
    }

    // 🔹 VIEW MODE: STAFF (Grocery Only)
    private fun setupStaffView() {
        // Hide Everything relating to money/users
        cvShopBalanceContainer.visibility = View.GONE
        rvUsers.visibility = View.GONE
        layoutUsersHeader.visibility = View.GONE
        btnAddPersonalExpense.visibility = View.GONE
        btnWhitelistUser.visibility = View.GONE

        // Show Only Grocery
        btnGrocery.visibility = View.VISIBLE

        // Update Header
        tvHeaderName.text = "Staff Mode"
        tvAvatarInitial.text = "S"
    }

    private fun fetchUserDetails() {
        val userId = auth.currentUser?.uid ?: return
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

    // 🔹 Whitelist Dialog (Updated with Staff Checkbox)
    private fun showWhitelistDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_whitelist_user, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etEmail = dialogView.findViewById<EditText>(R.id.etWhitelistEmail)
        val cbIsStaff = dialogView.findViewById<CheckBox>(R.id.cbIsStaff) // ✅ Checkbox
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmAdd)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val isStaff = cbIsStaff.isChecked

            if (email.isNotEmpty()) {
                val data = hashMapOf(
                    "email" to email,
                    "addedBy" to BOSS_EMAIL,
                    "isStaff" to isStaff, // ✅ Save Role
                    "timestamp" to FieldValue.serverTimestamp()
                )
                db.collection("whitelisted_users").document(email).set(data)
                    .addOnSuccessListener {
                        val role = if(isStaff) "Staff" else "User"
                        toast("$role Whitelisted Successfully!")
                        dialog.dismiss()
                    }
                    .addOnFailureListener { toast("Error adding user.") }
            } else {
                toast("Please enter an email")
            }
        }
        dialog.show()
    }

    // 🔹 Delete User Dialog
    private fun showDeleteUserDialog(user: User) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remove_user, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        tvMessage.text = "Are you sure you want to remove ${user.name}? This will permanently delete their data."

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<Button>(R.id.btnConfirmRemove).setOnClickListener {
            db.collection("users").document(user.id).delete()
                .addOnSuccessListener { toast("User removed successfully") }
                .addOnFailureListener { toast("Error removing user") }
            dialog.dismiss()
        }
        dialog.show()
    }

    // 🔹 Transaction Dialog
    private fun showAmountDialog(type: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_amount, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvDialogSubtitle)
        val etAmount = dialogView.findViewById<EditText>(R.id.etDialogAmount)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirm)

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }

        when (type) {
            "IN" -> {
                tvTitle.text = "Money IN"
                tvSubtitle.text = "Transfer from You -> Shop"
                btnConfirm.text = "Transfer"
                btnConfirm.setBackgroundColor(Color.parseColor("#2E7D32"))
            }
            "OUT" -> {
                tvTitle.text = "Money OUT"
                tvSubtitle.text = "Withdraw from Shop -> You"
                btnConfirm.text = "Withdraw"
                btnConfirm.setBackgroundColor(Color.parseColor("#D32F2F"))
            }
            "DEPOSIT" -> {
                tvTitle.text = "Add Balance"
                tvSubtitle.text = "Add external funds to Shop"
                btnConfirm.text = "Add Funds"
                btnConfirm.setBackgroundColor(Color.parseColor("#8200FF"))
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
        dialog.show()
    }

    // 🔹 Transaction Logic
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

            when (type) {
                "IN" -> {
                    if (userBalance < amount) throw Exception("Insufficient balance")
                    newShopBalance = shopBalance + amount
                    newUserBalance = userBalance - amount
                }
                "OUT" -> {
                    if (shopBalance < amount) throw Exception("Shop has insufficient balance")
                    newShopBalance = shopBalance - amount
                    newUserBalance = userBalance + amount
                }
                "DEPOSIT" -> {
                    newShopBalance = shopBalance + amount
                }
            }

            transaction.update(shopRef, "balance", newShopBalance)
            if (type != "DEPOSIT") {
                transaction.update(userRef, "balance", newUserBalance)
            }

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