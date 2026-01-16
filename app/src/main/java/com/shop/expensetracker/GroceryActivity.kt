package com.shop.expensetracker

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class GroceryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val itemList = mutableListOf<GroceryItem>()
    private lateinit var adapter: GroceryAdapter
    private var currentUserName = "Staff"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grocery)

        fetchCurrentUserName()

        val rvGrocery = findViewById<RecyclerView>(R.id.rvGrocery)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddGrocery)

        adapter = GroceryAdapter(itemList) { item ->
            toggleItemStatus(item)
        }

        rvGrocery.layoutManager = LinearLayoutManager(this)
        rvGrocery.adapter = adapter

        loadGroceryItems()
        cleanupOldItems() // Runs in background safely

        fabAdd.setOnClickListener { showAddItemDialog() }
    }

    private fun loadGroceryItems() {
        db.collection("grocery_items")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                itemList.clear()
                snapshot?.forEach { doc ->
                    try {
                        // ✅ MANUAL SAFE MAPPING (Prevents the crash)
                        val id = doc.id
                        val name = doc.getString("name") ?: ""
                        val addedBy = doc.getString("addedBy") ?: ""
                        val isDone = doc.getBoolean("isDone") ?: false
                        val timestamp = doc.getTimestamp("timestamp")
                        val completedTimestamp = doc.getTimestamp("completedTimestamp")

                        val item = GroceryItem(id, name, addedBy, isDone, timestamp, completedTimestamp)
                        itemList.add(item)
                    } catch (e: Exception) {
                        // If one item fails, just skip it. Don't crash the whole list.
                        e.printStackTrace()
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun cleanupOldItems() {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        db.collection("grocery_items")
            .whereEqualTo("isDone", true)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot) {
                    val completedTimestamp = doc.getTimestamp("completedTimestamp")
                    if (completedTimestamp != null) {
                        val time = completedTimestamp.toDate().time
                        if (time < twentyFourHoursAgo) {
                            db.collection("grocery_items").document(doc.id).delete()
                        }
                    }
                }
            }
    }

    private fun showAddItemDialog() {
        // Inflate the custom layout
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_grocery, null)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()

        // Transparent background so rounded corners show correctly
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Initialize Views
        val etItem = dialogView.findViewById<EditText>(R.id.etGroceryItem)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        val btnAdd = dialogView.findViewById<android.widget.Button>(R.id.btnAdd)

        // Focus the input field automatically (Optional UX improvement)
        etItem.requestFocus()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnAdd.setOnClickListener {
            val text = etItem.text.toString().trim()
            if (text.isNotEmpty()) {
                addItemToFirestore(text)
                dialog.dismiss()
            } else {
                android.widget.Toast.makeText(this, "Please enter an item name", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun addItemToFirestore(name: String) {
        val newItem = hashMapOf(
            "name" to name,
            "addedBy" to currentUserName,
            "isDone" to false,
            "timestamp" to FieldValue.serverTimestamp(),
            "completedTimestamp" to null
        )
        db.collection("grocery_items").add(newItem)
    }

    private fun toggleItemStatus(item: GroceryItem) {
        val newStatus = !item.isDone
        val updates = hashMapOf<String, Any?>(
            "isDone" to newStatus,
            "completedTimestamp" to (if (newStatus) FieldValue.serverTimestamp() else null)
        )
        db.collection("grocery_items").document(item.id).update(updates)
    }

    private fun fetchCurrentUserName() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener {
            currentUserName = it.getString("name") ?: "Staff"
        }
    }
}