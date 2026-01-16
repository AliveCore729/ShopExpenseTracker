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

class LoginActivity : AppCompatActivity() {

    // ⚠️ REPLACE THIS WITH YOUR REAL GMAIL ADDRESS
    private val BOSS_EMAIL = "joker72096@gmail.com"

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = FirebaseFirestore.getInstance()

    private val RC_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val btnGoogleLogin = findViewById<CardView>(R.id.cardGoogleLogin)

        // Google Sign-In config
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
                            // 🔥 STEP 1: Check Security Clearance
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

    // 🔒 THE SECURITY GATEKEEPER
    private fun checkWhitelistAndProceed(user: FirebaseUser) {
        val email = user.email ?: ""

        // 1. Always allow the Boss
        if (email == BOSS_EMAIL) {
            checkUserRegistration(user)
            return
        }

        // 2. Check if email is in 'whitelisted_users' collection
        db.collection("whitelisted_users").document(email).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // ✅ Access Granted
                    checkUserRegistration(user)
                } else {
                    // ⛔ Access Denied
                    performLogout()
                    Toast.makeText(this, "Access Denied: You are not authorized.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                performLogout()
                Toast.makeText(this, "Verification failed. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    // ⛔ Helper to kick unauthorized users out
    private fun performLogout() {
        auth.signOut()
        googleSignInClient.signOut() // Clears the Google account selection so they can try another
    }

    // 🏠 EXISTING LOGIC: Route to Home or Name Setup
    private fun checkUserRegistration(user: FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // User already set up -> Go to Home
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    // New user -> Go to Name Setup
                    startActivity(Intent(this, GoogleNameActivity::class.java))
                }
                finish()
            }
    }

    // ✅ AUTO LOGIN (Optional: You can add checks here too if you want strictly secure startups)
    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}