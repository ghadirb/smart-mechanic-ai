package com.smartmechanic.ai.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Creates a stable anonymous Firebase identity for each installation.
 *
 * The identity, not a client-side secret, will authorize calls to the credit
 * backend. Firebase keeps the same anonymous user across normal app restarts.
 */
object FirebaseSession {
    private const val TAG = "FirebaseSession"

    fun ensureSignedIn() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return

        auth.signInAnonymously().addOnFailureListener { error ->
            // Do not surface a technical Firebase message to the user here;
            // diagnosis requests will display the existing friendly error UI.
            Log.w(TAG, "Anonymous Firebase sign-in failed", error)
        }
    }
}
