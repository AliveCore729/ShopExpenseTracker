package com.shop.expensetracker

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore

class MasterListActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val list = mutableListOf<String>()
    private lateinit var adapter: MasterListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_list)

        val rv = findViewById<RecyclerView>(R.id.rvMasterList)
        val fab = findViewById<FloatingActionButton>(R.id.fabAddMaster)

        adapter = MasterListAdapter(list,
            onEdit = { oldName -> showAddEditDialog(oldName) },
            onDelete = { name -> showDeleteDialog(name) }
        )

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadMasterList()

        fab.setOnClickListener { showAddEditDialog(null) }
    }

    private fun loadMasterList() {
        db.collection("master_grocery_list")
            .addSnapshotListener { snap, _ ->
                list.clear()
                snap?.forEach { doc ->
                    val name = doc.getString("name")
                    if (name != null) list.add(name)
                }
                list.sort() // Alphabetical order
                adapter.notifyDataSetChanged()
            }
    }

    // Reuse the existing grocery dialog for a consistent look
    private fun showAddEditDialog(oldName: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_grocery, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val etItem = dialogView.findViewById<EditText>(R.id.etGroceryItem)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAdd = dialogView.findViewById<Button>(R.id.btnAdd)

        // Hide the subtitle or icon if you want, or just leave it
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle) // Ensure your XML has this ID or remove this line
        // If your dialog XML doesn't have an ID for the title, it will just say "Add Grocery Item" which is fine

        if (oldName != null) {
            etItem.setText(oldName)
            btnAdd.text = "Update"
        } else {
            btnAdd.text = "Add"
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val newName = etItem.text.toString().trim()
            if (newName.isNotEmpty()) {
                if (oldName != null) {
                    updateItem(oldName, newName)
                } else {
                    saveItem(newName)
                }
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun saveItem(name: String) {
        val data = hashMapOf("name" to name)
        // Use lowercase name as ID to prevent duplicates
        db.collection("master_grocery_list").document(name.lowercase()).set(data)
    }

    private fun updateItem(oldName: String, newName: String) {
        // Since Firestore IDs are immutable, we delete the old one and add the new one
        db.collection("master_grocery_list").document(oldName.lowercase()).delete()
        saveItem(newName)
    }

    private fun showDeleteDialog(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Item?")
            .setMessage("Remove '$name' from auto-suggestions?")
            .setPositiveButton("Remove") { _, _ ->
                db.collection("master_grocery_list").document(name.lowercase()).delete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}