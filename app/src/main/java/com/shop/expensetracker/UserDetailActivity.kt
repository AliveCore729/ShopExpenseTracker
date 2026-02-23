package com.shop.expensetracker

import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class UserDetailActivity : AppCompatActivity() {

    private val ADMIN_EMAILS = listOf(
        "vilasksable@gmail.com",
        "joker72096@gmail.com",
        "pawanhingane@gmail.com",
        "arjunasable@gmail.com"
    )

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvUserName: TextView
    private lateinit var tvUserBalance: TextView
    private lateinit var btnTransfer: Button
    private lateinit var btnExportCsv: Button
    private lateinit var rvTransactions: RecyclerView

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
        val loggedInEmail = auth.currentUser?.email

        btnTransfer.visibility =
            if (loggedInUserId == userId) View.GONE else View.VISIBLE

        adapter = UserTransactionAdapter(transactionList)
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = adapter

        // ✅ ADDED: Swipe to Delete Feature
        setupSwipeToDelete(loggedInEmail)

        loadUserBalance()
        loadUserTransactions()

        btnTransfer.setOnClickListener {
            showTransferDialog()
        }

        btnExportCsv.setOnClickListener {
            exportCsv()
        }
    }

    // 🔴 UPDATED: Swipe to Delete Logic with CUSTOM UI
    private fun setupSwipeToDelete(currentUserEmail: String?) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val txn = transactionList[position]

                // 🔒 Security Check: Only Admins can delete
                if (currentUserEmail !in ADMIN_EMAILS) {
                    Toast.makeText(this@UserDetailActivity, "Only Admins can delete transactions", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position) // Snap back
                    return
                }

                // ✅ SHOW CUSTOM DIALOG
                val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirmation, null)
                val builder = AlertDialog.Builder(this@UserDetailActivity)
                builder.setView(dialogView)
                val dialog = builder.create()

                // Transparent background for rounded corners
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                // Find Views
                val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
                val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
                val btnDelete = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

                // Set Message
                tvMessage.text = "Are you sure you want to delete this?\n\n${txn.title}\n₹${txn.amount}"

                // Button Actions
                btnCancel.setOnClickListener {
                    adapter.notifyItemChanged(position) // Snap back on cancel
                    dialog.dismiss()
                }

                btnDelete.setOnClickListener {
                    deleteTransactionFromDB(txn)
                    dialog.dismiss()
                }

                // Handle outside touch cancellation
                dialog.setOnCancelListener {
                    adapter.notifyItemChanged(position)
                }

                dialog.show()
            }

            // 🎨 Draw Red Background
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.parseColor("#D32F2F")) // Red Color

                if (dX > 0) { // Swiping Right
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                } else if (dX < 0) { // Swiping Left
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                } else {
                    background.setBounds(0, 0, 0, 0)
                }
                background.draw(c)
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(rvTransactions)
    }

    // 🗑 Database Delete
    private fun deleteTransactionFromDB(txn: UserTransaction) {
        db.collection(txn.collection).document(txn.docId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Transaction Deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 LIVE BALANCE
    private fun loadUserBalance() {
        db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                val bal = snap?.getLong("balance") ?: 0
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
                transactionList.removeAll { it.collection == "shop_transactions" }
                s?.forEach { transactionList.add(UserTransaction.fromShopTransaction(it)) }
                refresh()
            }

        db.collection("user_transfers")
            .whereEqualTo("fromUserId", userId)
            .addSnapshotListener { s, _ ->
                transactionList.removeAll { it.collection == "user_transfers" && !it.isCredit }
                s?.forEach { transactionList.add(UserTransaction.fromTransfer(it, userId)) }
                refresh()
            }

        db.collection("user_transfers")
            .whereEqualTo("toUserId", userId)
            .addSnapshotListener { s, _ ->
                transactionList.removeAll { it.collection == "user_transfers" && it.isCredit }
                s?.forEach { transactionList.add(UserTransaction.fromTransfer(it, userId)) }
                refresh()
            }

        db.collection("personal_transactions")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { s, _ ->
                transactionList.removeAll { it.collection == "personal_transactions" }
                s?.forEach { transactionList.add(UserTransaction.fromPersonalExpense(it)) }
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

    // 🔁 TRANSFER LOGIC
    private fun showTransferDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_transfer_money, null)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etAmount = dialogView.findViewById<EditText>(R.id.etTransferAmount)
        val etReason = dialogView.findViewById<EditText>(R.id.etTransferReason)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancelTransfer)
        val btnTransfer = dialogView.findViewById<android.widget.Button>(R.id.btnConfirmTransfer)

        tvTitle.text = "Transfer to $userName"
        etAmount.requestFocus()

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