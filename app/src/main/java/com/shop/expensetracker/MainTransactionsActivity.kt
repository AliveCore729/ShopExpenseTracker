package com.shop.expensetracker

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainTransactionsActivity : AppCompatActivity() {

    // ✅ List of Admins
    private val ADMIN_EMAILS = listOf(
        "vilasksable@gmail.com",
        "joker72096@gmail.com",
        "pawanhingane@gmail.com"
    )

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val list = mutableListOf<ShopTransaction>()
    private lateinit var adapter: ShopTransactionAdapter
    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_transactions)

        rv = findViewById(R.id.rvMainTransactions)

        adapter = ShopTransactionAdapter(list)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // ✅ Enable Swipe to Delete
        setupSwipeToDelete()

        loadTransactions()
    }

    private fun loadTransactions() {
        db.collection("shop_transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                list.clear()
                snapshot?.forEach { doc ->
                    // ✅ Map Data AND save the Document ID
                    val txn = doc.toObject(ShopTransaction::class.java)
                    txn.docId = doc.id
                    list.add(txn)
                }
                adapter.notifyDataSetChanged()
            }
    }

    // 🔴 SWIPE TO DELETE LOGIC (UPDATED UI)
    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val txn = list[position]
                val currentUserEmail = auth.currentUser?.email

                // 🔒 Security Check: Only Admins can delete
                if (currentUserEmail !in ADMIN_EMAILS) {
                    Toast.makeText(this@MainTransactionsActivity, "Only Admins can delete transactions", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position) // Snap back
                    return
                }

                // ✅ SHOW CUSTOM DIALOG
                val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirmation, null)
                val builder = AlertDialog.Builder(this@MainTransactionsActivity)
                builder.setView(dialogView)
                val dialog = builder.create()

                // Transparent background needed for CardView rounded corners to show correctly
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                // Find Views in custom layout
                val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
                val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelDelete)
                val btnDelete = dialogView.findViewById<Button>(R.id.btnConfirmDelete)

                // Set Message
                tvMessage.text = "Are you sure you want to delete this?\n\n${txn.type}: ₹${txn.amount}"

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

            // 🎨 Red Background when Swiping
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
        itemTouchHelper.attachToRecyclerView(rv)
    }

    private fun deleteTransactionFromDB(txn: ShopTransaction) {
        if (txn.docId.isEmpty()) return

        db.collection("shop_transactions").document(txn.docId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Transaction Deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
    }
}