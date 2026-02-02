package com.example.stayaccountable

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase

class AuthRepository {
    private lateinit var auth: FirebaseAuth

    fun initAuth() {
        auth = FirebaseAuth.getInstance()
    }

    fun getCurrentUser() = if (::auth.isInitialized) auth.currentUser else null

    fun isUserSignedIn(): Boolean = getCurrentUser() != null


    fun createAccount(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception)
                }
            }
    }

    fun signIn(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception)
                }
            }
    }


    fun getCurrentUserEmail(): String? {
        val currentUser = getCurrentUser()
        return currentUser?.email
    }

    fun signOut() {
        auth.signOut()
    }


}