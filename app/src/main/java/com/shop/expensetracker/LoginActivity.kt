package com.shop.expensetracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    // ✅ Removed the hardcoded ADMIN_EMAILS list

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = FirebaseFirestore.getInstance()

    private val RC_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val btnGoogleLogin = findViewById<CardView>(R.id.cardGoogleLogin)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogleLogin.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)

                val credential = GoogleAuthProvider.getCredential(account.idToken, null)

                auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        val user = auth.currentUser
                        if (user != null) {
                            checkWhitelistAndProceed(user)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Authentication failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkWhitelistAndProceed(user: FirebaseUser) {
        val email = user.email ?: ""

        // ✅ 1. Check if user is in the 'admins' database collection
        db.collection("admins").document(email).get()
            .addOnSuccessListener { adminDoc ->
                if (adminDoc.exists()) {
                    // User is an Admin
                    checkUserRegistration(user, isAdmin = true)
                } else {
                    // ✅ 2. Not an Admin, check if in 'whitelisted_users'
                    db.collection("whitelisted_users").document(email).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                checkUserRegistration(user, isAdmin = false)
                            } else {
                                performLogout()
                                Toast.makeText(this, "Access Denied: You are not authorized.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener {
                            performLogout()
                            Toast.makeText(this, "Verification failed. Try again.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                performLogout()
                Toast.makeText(this, "Admin check failed. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performLogout() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    // ✅ Updated to receive 'isAdmin' to avoid unnecessary secondary checks
    private fun checkUserRegistration(user: FirebaseUser, isAdmin: Boolean) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // ✅ User exists: Update Notification Token & Go Home
                    updateFcmToken()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    // New user or deleted profile
                    if (isAdmin) {
                        startActivity(Intent(this, GoogleNameActivity::class.java))
                        finish()
                    } else {
                        val email = user.email ?: ""
                        db.collection("whitelisted_users").document(email).get().addOnSuccessListener { whiteDoc ->
                            if (whiteDoc.exists()) {
                                startActivity(Intent(this, GoogleNameActivity::class.java))
                                finish()
                            } else {
                                performLogout()
                                Toast.makeText(this, "Your account profile was removed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // ✅ Auto-login: Update Notification Token & Go Home
                    updateFcmToken()
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    performLogout()
                }
            }
        }
    }

    // ✅ NEW FUNCTION: Saves the notification token to Firestore
    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener

            val token = task.result
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener

            // 1. Save individual token (for personal money transfers)
            db.collection("users").document(uid).update("fcmToken", token)

            // 2. Subscribe to general topic (for grocery updates)
            FirebaseMessaging.getInstance().subscribeToTopic("grocery_updates")
        }
    }
}