package com.shop.expensetracker

import android.app.AlertDialog
import android.content.Intent // ✅ Added
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View // ✅ Added
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView // ✅ Added
import android.widget.Toast
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

    // ✅ Admin List (Must match other files)
    private val ADMIN_EMAILS = listOf(
        "vilasksable@gmail.com",
        "joker72096@gmail.com"
    )

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val itemList = mutableListOf<GroceryItem>()
    private val masterList = mutableListOf<String>()
    private lateinit var adapter: GroceryAdapter
    private var currentUserName = "Staff"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grocery)

        fetchCurrentUserName()
        loadMasterList()

        val rvGrocery = findViewById<RecyclerView>(R.id.rvGrocery)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAddGrocery)
        val btnManageMaster = findViewById<ImageView>(R.id.btnManageMaster) // ✅ Find button

        // ✅ CHECK ADMIN STATUS
        val currentUserEmail = auth.currentUser?.email
        if (currentUserEmail in ADMIN_EMAILS) {
            btnManageMaster.visibility = View.VISIBLE
            btnManageMaster.setOnClickListener {
                startActivity(Intent(this, MasterListActivity::class.java))
            }
        } else {
            btnManageMaster.visibility = View.GONE
        }

        adapter = GroceryAdapter(itemList) { item ->
            toggleItemStatus(item)
        }

        rvGrocery.layoutManager = LinearLayoutManager(this)
        rvGrocery.adapter = adapter

        loadGroceryItems()
        cleanupOldItems()

        fabAdd.setOnClickListener { showAddItemDialog() }
    }

    // ... (Keep all your existing functions: loadMasterList, loadGroceryItems, etc. exactly the same) ...
    // Copy the rest of your functions from the file you sent me previously.

    private fun loadMasterList() {
        db.collection("master_grocery_list").addSnapshotListener { snap, _ ->
            masterList.clear()
            snap?.forEach { doc ->
                val name = doc.getString("name")
                if (name != null) masterList.add(name)
            }
        }
    }

    private fun loadGroceryItems() {
        db.collection("grocery_items")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                itemList.clear()
                snapshot?.forEach { doc ->
                    try {
                        val id = doc.id
                        val name = doc.getString("name") ?: ""
                        val addedBy = doc.getString("addedBy") ?: ""
                        val isDone = doc.getBoolean("isDone") ?: false
                        val timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                        val completedTimestamp = doc.getTimestamp("completedTimestamp")
                        val item = GroceryItem(id, name, addedBy, isDone, timestamp, completedTimestamp)
                        itemList.add(item)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun cleanupOldItems() {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        db.collection("grocery_items").whereEqualTo("isDone", true).get().addOnSuccessListener { snapshot ->
            for (doc in snapshot) {
                val completedTimestamp = doc.getTimestamp("completedTimestamp")
                if (completedTimestamp != null) {
                    if (completedTimestamp.toDate().time < twentyFourHoursAgo) {
                        db.collection("grocery_items").document(doc.id).delete()
                    }
                }
            }
        }
    }

    private fun showAddItemDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_add_grocery, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etItem = dialogView.findViewById<AutoCompleteTextView>(R.id.etGroceryItem)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAdd)

        val suggestAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, masterList)
        etItem.setAdapter(suggestAdapter)
        etItem.threshold = 1
        etItem.requestFocus()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val text = etItem.text.toString().trim()
            if (text.isNotEmpty()) {
                addItemToFirestore(text)
                addToMasterList(text)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter an item name", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun addToMasterList(name: String) {
        val exists = masterList.any { it.equals(name, ignoreCase = true) }
        if (!exists) {
            val data = hashMapOf("name" to name)
            db.collection("master_grocery_list").document(name.lowercase()).set(data)
        }
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