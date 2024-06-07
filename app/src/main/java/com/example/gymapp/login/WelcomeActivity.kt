package com.example.gymapp.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.gymapp.DBManager
import com.example.gymapp.DBPersonalData
import com.example.gymapp.GymViewModel
import com.example.gymapp.MainActivity
import com.example.gymapp.R
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.gymapp.databinding.ActivityWelcomeBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore


class WelcomeActivity : AppCompatActivity() {

    /* DB */
    private lateinit var db: FirebaseFirestore
    private lateinit var firebaseAuth: FirebaseAuth

    private val viewModel: GymViewModel by viewModels()
    private lateinit var binding : ActivityWelcomeBinding
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        this.onSignInResult(res)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult?) {
        db = Firebase.firestore
        firebaseAuth = FirebaseAuth.getInstance()

        if (result != null) {
            val response = result.idpResponse
            if (result.resultCode == RESULT_OK) {

                // Successfully signed in
                val user = firebaseAuth.currentUser // : FirebaseUser

                /* TODO : look for a good way to save birthDate: Date */
                if (response?.isNewUser == true) {
                    val defaultPersonalData = DBPersonalData(name = user?.displayName ?: DBPersonalData.DEFAULT_NAME) // default personal data values
                    db.collection(user!!.uid)
                        .document(DBManager.PERSONAL_DATA_DOCUMENT_NAME)
                        .set(defaultPersonalData.getHashMap())
                }

                // TODO => set viewmodel here (efficiency), not in mainActivity, there is another DB query there :(

                // Start MainActivity
                val intentMainActivity = Intent(this, MainActivity::class.java)
                intentMainActivity.putExtra("user", user)
                startActivity(intentMainActivity)

            } else {
                // LOGIN FAILED (toast, redirect to login page again, ...)
                Log.e("FirebaseAuth", "Login ERROR")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Firebase.firestore
        firebaseAuth = FirebaseAuth.getInstance()

        if (firebaseAuth.currentUser == null) {

            binding = ActivityWelcomeBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Choose authentication providers
            val providers = arrayListOf(
                AuthUI.IdpConfig.EmailBuilder().build(),
                // TODO AuthUI.IdpConfig.GoogleBuilder().build(),
                AuthUI.IdpConfig.AnonymousBuilder().build()
            )

            // Create and launch sign-in intent
            binding.btnLogin.setOnClickListener{
                val signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setIsSmartLockEnabled(false)
                    .setAvailableProviders(providers)
                    .build()
                signInLauncher.launch(signInIntent)
            }
        } else {

            // Start MainActivity
            val intentMainActivity = Intent(this, MainActivity::class.java)
            intentMainActivity.putExtra("user", firebaseAuth.currentUser)
            startActivity(intentMainActivity)
        }
    }

    /*
    Start MainActivity if already signed in
     */
    override fun onResume() {
        super.onResume()
        db = Firebase.firestore
        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser != null) {
            val intentMainActivity = Intent(this, MainActivity::class.java)
            intentMainActivity.putExtra("user", firebaseAuth.currentUser)
            startActivity(intentMainActivity)
        }
    }
}