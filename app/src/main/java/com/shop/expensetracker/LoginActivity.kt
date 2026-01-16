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

        if (email == BOSS_EMAIL) {
            checkUserRegistration(user)
            return
        }

        db.collection("whitelisted_users").document(email).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    checkUserRegistration(user)
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

    private fun performLogout() {
        auth.signOut()
        googleSignInClient.signOut()
    }

    // ✅ UPDATED: Added a final check to see if the user profile still exists
    private fun checkUserRegistration(user: FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // User is valid and exists in DB
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    // NEW user (or a user whose DB profile was deleted)
                    // We only send them to Name Setup if they are whitelisted or Boss
                    val email = user.email ?: ""

                    // Re-check whitelist for safety before allowing Name Setup
                    db.collection("whitelisted_users").document(email).get().addOnSuccessListener { whiteDoc ->
                        if (whiteDoc.exists() || email == BOSS_EMAIL) {
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

    // ✅ UPDATED: Auto-login now verifies if the account is still in the DB
    override fun onStart() {
        super.onStart()
        val user = auth.currentUser
        if (user != null) {
            // Don't just go to HomeActivity; verify they still exist in DB first
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // If DB record is gone, force log out
                    performLogout()
                }
            }
        }
    }
}