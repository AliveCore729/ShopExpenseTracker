package com.shop.expensetracker

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat // ✅ Added Import
import java.text.SimpleDateFormat
import java.util.*

class UserDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvUserName: TextView
    private lateinit var tvUserBalance: TextView
    private lateinit var btnTransfer: Button
    private lateinit var btnExportCsv: Button
    private lateinit var rvTransactions: androidx.recyclerview.widget.RecyclerView

    private val transactionList = mutableListOf<UserTransaction>()
    private lateinit var adapter: UserTransactionAdapter

    private lateinit var userId: String
    private lateinit var userName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_detail)

        tvUserName = findViewById(R.id.tvUserName)
        tvUserBalance = findViewById(R.id.tvUserBalance)
        btnTransfer = findViewById(R.id.btnTransfer)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        rvTransactions = findViewById(R.id.rvUserTransactions)

        userId = intent.getStringExtra("userId") ?: return
        userName = intent.getStringExtra("userName") ?: "User"

        tvUserName.text = userName

        val loggedInUserId = auth.currentUser?.uid
        btnTransfer.visibility =
            if (loggedInUserId == userId) View.GONE else View.VISIBLE

        adapter = UserTransactionAdapter(transactionList)
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter

        loadUserBalance()
        loadUserTransactions()

        btnTransfer.setOnClickListener {
            showTransferDialog()
        }

        btnExportCsv.setOnClickListener {
            exportCsv()
        }
    }

    // 🔹 LIVE BALANCE (Updated with Indian Commas)
    private fun loadUserBalance() {
        db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                val bal = snap?.getLong("balance") ?: 0

                // ✅ CHANGED: Added Indian Number Format
                val formatter = NumberFormat.getInstance(Locale("en", "IN"))
                tvUserBalance.text = "Balance: ₹" + formatter.format(bal)
            }
    }

    // 🔹 MERGED HISTORY
    private fun loadUserTransactions() {
        transactionList.clear()

        fun refresh() {
            transactionList.sortByDescending { it.timestamp }
            adapter.notifyDataSetChanged()
        }

        db.collection("shop_transactions")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { s, _ ->
                s?.forEach {
                    transactionList.add(UserTransaction.fromShopTransaction(it))
                }
                refresh()
            }

        db.collection("user_transfers")
            .whereEqualTo("fromUserId", userId)
            .addSnapshotListener { s, _ ->
                s?.forEach {
                    transactionList.add(UserTransaction.fromTransfer(it, userId))
                }
                refresh()
            }

        db.collection("user_transfers")
            .whereEqualTo("toUserId", userId)
            .addSnapshotListener { s, _ ->
                s?.forEach {
                    transactionList.add(UserTransaction.fromTransfer(it, userId))
                }
                refresh()
            }

        db.collection("personal_transactions")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { s, _ ->
                s?.forEach {
                    transactionList.add(UserTransaction.fromPersonalExpense(it))
                }
                refresh()
            }
    }

    // 📤 EXPORT CSV
    private fun exportCsv() {
        if (transactionList.isEmpty()) {
            toast("No transactions to export")
            return
        }

        val fileName = "user_${userName}_transactions.csv"
        val resolver = contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
        }

        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return

        resolver.openOutputStream(uri)?.use { output ->
            output.write("Date,Type,Amount,Description\n".toByteArray())

            val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())

            transactionList.forEach {
                val date = it.timestamp?.toDate()?.let { d -> sdf.format(d) } ?: ""
                val type = if (it.isCredit) "CREDIT" else "DEBIT"
                val line = "$date,$type,${it.amount},\"${it.title}\"\n"
                output.write(line.toByteArray())
            }
        }

        toast("CSV exported to Downloads")
    }

    // 🔁 TRANSFER LOGIC (UNCHANGED)
    // 🔁 REPLACED: New Custom Transfer Dialog
    private fun showTransferDialog() {
        // Inflate the custom layout
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_transfer_money, null)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        // Transparent background so rounded corners show correctly
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Initialize Views from the custom layout
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etAmount = dialogView.findViewById<EditText>(R.id.etTransferAmount)
        val etReason = dialogView.findViewById<EditText>(R.id.etTransferReason)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancelTransfer)
        val btnTransfer = dialogView.findViewById<android.widget.Button>(R.id.btnConfirmTransfer)

        // Set the title dynamically
        tvTitle.text = "Transfer to $userName"

        // Focus the amount field automatically
        etAmount.requestFocus()

        // Button Click Listeners
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnTransfer.setOnClickListener {
            val amountStr = etAmount.text.toString().trim()
            val reason = etReason.text.toString().trim()

            if (amountStr.isEmpty() || reason.isEmpty()) {
                toast("Please fill all fields")
                return@setOnClickListener
            }

            val amount = amountStr.toLongOrNull()
            if (amount == null || amount <= 0) {
                toast("Enter a valid amount")
                return@setOnClickListener
            }

            // Call the existing transfer logic
            transferMoney(amount, reason)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun transferMoney(amount: Long, reason: String) {
        val fromId = auth.currentUser?.uid ?: return
        val toId = userId

        val fromRef = db.collection("users").document(fromId)
        val toRef = db.collection("users").document(toId)
        val ref = db.collection("user_transfers").document()

        db.runTransaction { tx ->
            val f = tx.get(fromRef)
            val t = tx.get(toRef)

            val fb = f.getLong("balance") ?: 0
            val tb = t.getLong("balance") ?: 0

            if (fb < amount) throw Exception("Insufficient balance")

            tx.update(fromRef, "balance", fb - amount)
            tx.update(toRef, "balance", tb + amount)

            tx.set(ref, hashMapOf(
                "fromUserId" to fromId,
                "fromUserName" to f.getString("name"),
                "toUserId" to toId,
                "toUserName" to t.getString("name"),
                "amount" to amount,
                "reason" to reason,
                "timestamp" to FieldValue.serverTimestamp()
            ))
        }.addOnSuccessListener {
            toast("Transfer successful")
        }.addOnFailureListener {
            toast(it.message ?: "Failed")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}