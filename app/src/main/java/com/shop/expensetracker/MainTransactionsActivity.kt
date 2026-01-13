package com.shop.expensetracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainTransactionsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val list = mutableListOf<ShopTransaction>()
    private lateinit var adapter: ShopTransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_transactions)

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvMainTransactions)

        adapter = ShopTransactionAdapter(list)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadTransactions()
    }

    private fun loadTransactions() {
        db.collection("shop_transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                list.clear()
                snapshot?.forEach {
                    list.add(it.toObject(ShopTransaction::class.java))
                }
                adapter.notifyDataSetChanged()
            }
    }
}
